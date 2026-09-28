-- UTF-8. Explicit local repair, not a bootstrap seed or a numbered migration.
-- Prerequisites: Lead/assignment/task schema as of 2026-09-27, and the
-- DefaultDBFieldHandler + LeadAssignmentTimeoutScheduler fixes deployed FIRST.
-- Caller MUST set @repair_tenant and @repair_at (Beijing DATETIME), back up scope,
-- START TRANSACTION before sourcing, then verify and explicitly COMMIT or ROLLBACK.
--
-- Background: LeadAssignmentTimeoutScheduler has been failing since 2026-09-23
-- because PerformanceSnapshotService inserted a NULL creator into
-- zsjos_performance_attribution (NOT NULL column). processExpired() is
-- @Transactional, so the whole batch rolled back on every run -- the leads kept
-- their stale pending_acceptance state, and processUnassignedRetries() never ran.
--
-- Scope: leads stuck in pending_acceptance whose pending_expires_at has passed.
-- The fix does NOT re-dispatch them here: once this script leaves them unassigned,
-- processExpired/processUnassignedRetries pick them up within 5s and continue
-- through the normal rules. That is why the code fix must be deployed first --
-- otherwise the scheduler crashes again and re-strands them.
--
-- This script records the timeout that the scheduler never managed to write, so
-- the assignment history stays traceable. It does not delete data, change
-- permissions, reconstruct ownership, or invent historical rules.
-- Re-run is a no-op after success.
SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TEMPORARY TABLE repair_guard (ok INT NOT NULL);
INSERT INTO repair_guard VALUES (IF(@repair_tenant > 0 AND @repair_at IS NOT NULL, 1, NULL));

-- Identify the stranded leads. Only auto dispatch, only expired pending offers,
-- only leads that still have no owner.
CREATE TEMPORARY TABLE repair_stuck AS
SELECT l.id AS lead_id, l.tenant_id, l.pending_assignee_user_id AS candidate_user_id,
       l.assignment_attempt_count AS attempt_no, l.pending_expires_at AS expired_at,
       l.lead_no
FROM zsjos_lead l
WHERE l.tenant_id = @repair_tenant
  AND l.deleted = b'0'
  AND l.assignment_status = 'pending_acceptance'
  AND l.owner_user_id IS NULL
  AND l.pending_assignee_user_id IS NOT NULL
  AND l.pending_expires_at IS NOT NULL
  AND l.pending_expires_at < @repair_at
  -- Exclude specified dispatch: those offers do not expire and must not be re-dispatched.
  AND l.dispatch_mode <> 'specified';

-- Guard against a partial fix: every candidate must have a matching dispatch history
-- and no timeout history yet. Otherwise the caller must re-check the scope manually.
INSERT INTO repair_guard
SELECT IF(COUNT(*) = 0, 1, NULL) FROM repair_stuck s
WHERE NOT EXISTS (SELECT 1 FROM zsjos_lead_assignment_history h
                  WHERE h.tenant_id = s.tenant_id AND h.lead_id = s.lead_id
                    AND h.deleted = b'0' AND h.action_type = 'dispatch'
                    AND h.candidate_user_id = s.candidate_user_id)
   OR EXISTS (SELECT 1 FROM zsjos_lead_assignment_history t
              WHERE t.tenant_id = s.tenant_id AND t.lead_id = s.lead_id
                AND t.deleted = b'0' AND t.action_type = 'timeout'
                AND t.occurred_at >= s.expired_at);

-- --- 1. Record the timeout that the scheduler failed to write ------------------
-- occurred_at is the original expiry, not @repair_at, so the history timeline
-- reflects when the offer actually lapsed.
INSERT INTO zsjos_lead_assignment_history
 (lead_id, action_type, from_owner_user_id, to_owner_user_id, operator_user_id, reason,
  occurred_at, assignment_rule_id, attempt_no, candidate_user_id, expires_at, response_at,
  owner_identity_snapshot, creator, updater, deleted, tenant_id)
SELECT s.lead_id, 'timeout', NULL, NULL, 1,
       '自动派单超时（超时任务故障补录）',
       s.expired_at, 1, s.attempt_no, s.candidate_user_id, NULL, s.expired_at,
       NULL, 'lead-stuck-pending-recovery', 'lead-stuck-pending-recovery', b'0', s.tenant_id
FROM repair_stuck s
WHERE NOT EXISTS (SELECT 1 FROM zsjos_lead_assignment_history t
                  WHERE t.tenant_id = s.tenant_id AND t.lead_id = s.lead_id
                    AND t.deleted = b'0' AND t.action_type = 'timeout'
                    AND t.occurred_at = s.expired_at);

-- --- 2. Release the stale offer so the scheduler can re-dispatch ---------------
UPDATE zsjos_lead l
JOIN repair_stuck s ON l.id = s.lead_id AND l.tenant_id = s.tenant_id
SET l.assignment_status = 'unassigned',
    l.pending_assignee_user_id = NULL,
    l.pending_expires_at = NULL,
    l.current_assignment_history_id = NULL,
    l.current_assignment_first_follow_up_at = NULL,
    l.current_assignment_first_follow_up_deadline_at = NULL,
    l.next_follow_up_at = NULL,
    l.last_activity_at = GREATEST(COALESCE(l.last_activity_at, s.expired_at), s.expired_at),
    l.updater = 'lead-stuck-pending-recovery'
WHERE l.assignment_status = 'pending_acceptance';

-- --- 3. Close the dangling "待接客资" to-do items -------------------------------
UPDATE zsjos_business_task t
JOIN repair_stuck s ON t.biz_id = s.lead_id AND t.tenant_id = s.tenant_id
SET t.status = 'cancelled',
    t.cancelled_at = s.expired_at,
    t.cancel_reason = '自动派单超时（超时任务故障补录）',
    t.updater = 'lead-stuck-pending-recovery'
WHERE t.deleted = b'0'
  AND t.biz_type = 'lead'
  AND t.task_type = 'lead_assignment_accept'
  AND t.status = 'pending';

-- --- Verification (run before COMMIT) -----------------------------------------
SELECT s.lead_id, s.lead_no, s.expired_at,
       l.assignment_status, l.pending_assignee_user_id,
       (SELECT COUNT(*) FROM zsjos_lead_assignment_history h
        WHERE h.tenant_id = s.tenant_id AND h.lead_id = s.lead_id
          AND h.action_type = 'timeout' AND h.occurred_at = s.expired_at) AS timeout_rows,
       (SELECT COUNT(*) FROM zsjos_business_task t
        WHERE t.tenant_id = s.tenant_id AND t.biz_id = s.lead_id
          AND t.task_type = 'lead_assignment_accept' AND t.status = 'pending') AS open_tasks
FROM repair_stuck s
JOIN zsjos_lead l ON l.id = s.lead_id AND l.tenant_id = s.tenant_id
ORDER BY s.lead_id;
-- Expect: assignment_status='unassigned', pending_assignee_user_id=NULL,
--         timeout_rows=1, open_tasks=0 for every row.

-- After COMMIT the scheduler re-dispatches within ~5s. Confirm in the log that
-- LeadAssignmentTimeoutScheduler no longer logs
-- "Column 'creator' cannot be null", and that each lead gains a new dispatch row.
