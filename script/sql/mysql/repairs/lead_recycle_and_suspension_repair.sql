-- UTF-8. Explicit local repair, not a bootstrap seed or a numbered migration.
-- Prerequisites: current Lead/task/event schema, zsjos_lead_follow_up_rule row code='default',
-- zsjos_performance_attribution, and the 6800/6802/6803 System menus.
-- Caller MUST set @repair_tenant and @repair_at (Beijing DATETIME), back up scope,
-- START TRANSACTION before sourcing, then verify and explicitly COMMIT or ROLLBACK.
-- No deletes, no ownership reconstruction, no new business roles.
-- Re-run is a no-op after success. Rollback before commit is complete.
--
-- Two independent defects, both confirmed by absence of the corresponding
-- business_event row (a real recycle always writes lead_recycled; a real
-- suspension always writes lead_suspended with a matching task cancellation):
--
-- A. 104 leads carry assignment_status='recycle_pending' with owner_user_id still
--    populated and recycle_source_owner_user_id NULL. No lead_recycled event and
--    no 'recycle' assignment_history row exists for them; update_time is uniformly
--    2026-09-21 12:01:48. The recycle never happened, so the status is rolled back
--    to 'owned' and owner_user_id is left untouched. This restores the dominant
--    valid+owned combination already present for 4013 leads.
--
-- B. 74 leads are suspended. 63 share suspended_at='2026-09-26 02:57:30' with
--    operator_user_id NULL and reason '有效性判定超时' - the qualification timeout
--    scheduler, not a business action. Restoring them to 'submitted' WITHOUT
--    resetting qualification_deadline_at would let LeadQualificationTimeoutScheduler
--    re-suspend them within 60 seconds, so each lead gets a new round with a fresh
--    deadline derived from the current enabled rule.
SET NAMES utf8mb4;
SET time_zone = '+08:00';
CREATE TEMPORARY TABLE repair_guard (ok INT NOT NULL);
INSERT INTO repair_guard VALUES (IF(@repair_tenant > 0 AND @repair_at IS NOT NULL, 1, NULL));

-- ---------------------------------------------------------------------------
-- A. Roll back the never-completed recycle to owned ownership.
-- ---------------------------------------------------------------------------
-- Lock the affected leads first, in the same lead-first order the application uses.
UPDATE zsjos_lead SET version=version
 WHERE tenant_id=@repair_tenant AND deleted=b'0'
 AND assignment_status='recycle_pending' AND recycle_source_owner_user_id IS NULL;

CREATE TEMPORARY TABLE repair_recycle_rollback AS
 SELECT l.id AS lead_id, l.tenant_id, l.lead_no, l.owner_user_id, l.status
 FROM zsjos_lead l
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0'
 AND l.assignment_status='recycle_pending' AND l.recycle_source_owner_user_id IS NULL
 -- Keep genuine history out of scope. Verified 2026-09-26: the 104 rows carrying
 -- this shape split into two populations that must not be treated alike.
 --
 --  * 93 rows: no lead_recycled event, no 'recycle' history row, public_pool_at
 --    NULL, and exactly one history row (the 'other' legacy baseline) whose
 --    to_owner_user_id equals the current owner_user_id. No recycle ever ran;
 --    assignment_status was overwritten in bulk at 2026-09-21 12:01:48.
 --  * 11 rows: a real 'recycle' history row imported from the old CRM
 --    (reason 'legacy-assignment-log-* 删除'), public_pool_at populated, and
 --    update_time 2026-09-21 10:23:49. These were genuinely recycled and their
 --    owner_user_id is the stale field, so rolling them back to owned would
 --    resurrect ownership that was deliberately removed.
 AND l.public_pool_at IS NULL
 AND NOT EXISTS (SELECT 1 FROM zsjos_business_event e
   WHERE e.tenant_id=l.tenant_id AND e.aggregate_id=l.id AND e.deleted=b'0'
   AND e.event_type='lead_recycled')
 AND NOT EXISTS (SELECT 1 FROM zsjos_lead_assignment_history h
   WHERE h.tenant_id=l.tenant_id AND h.lead_id=l.id AND h.deleted=b'0'
   AND h.action_type='recycle')
 -- Require the current history to still be a legacy baseline that already names
 -- the present owner, so no intervening assignment is silently discarded.
 AND EXISTS (SELECT 1 FROM zsjos_lead_assignment_history b
   WHERE b.tenant_id=l.tenant_id AND b.lead_id=l.id AND b.deleted=b'0'
   AND b.action_type='other' AND b.to_owner_user_id <=> l.owner_user_id
   AND NOT EXISTS (SELECT 1 FROM zsjos_lead_assignment_history n
     WHERE n.tenant_id=b.tenant_id AND n.lead_id=b.lead_id AND n.deleted=b'0'
     AND (n.occurred_at > b.occurred_at
       OR (n.occurred_at = b.occurred_at AND n.id > b.id))));

UPDATE zsjos_lead l JOIN repair_recycle_rollback r ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 SET l.assignment_status='owned', l.recycle_source_owner_user_id=NULL,
     l.updater='lead-recycle-suspension-repair';

-- A2. Complete the recycle for rows whose recycle really happened but whose owner
--     was never cleared. These carry a real 'recycle' history row imported from the
--     old CRM (reason 'legacy-assignment-log-* 删除'), and their owner_user_id equals
--     that row's from_owner_user_id, so ownership was deliberately removed and the
--     column is the stale field. Rolling these back to 'owned' would resurrect a
--     removed assignment, so they are completed instead: owner moves to the recycle
--     source, matching the 5 already-correct recycle_pending leads.
CREATE TEMPORARY TABLE repair_recycle_complete AS
 SELECT l.id AS lead_id, l.tenant_id, l.lead_no, l.owner_user_id
 FROM zsjos_lead l
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0'
 AND l.assignment_status='recycle_pending' AND l.recycle_source_owner_user_id IS NULL
 AND l.owner_user_id IS NOT NULL
 AND EXISTS (SELECT 1 FROM zsjos_lead_assignment_history h
   WHERE h.tenant_id=l.tenant_id AND h.lead_id=l.id AND h.deleted=b'0'
   AND h.action_type='recycle' AND h.reason LIKE 'legacy-assignment-log-%'
   AND h.from_owner_user_id <=> l.owner_user_id
   -- Only when that recycle log is the last real ownership action. 'other' rows are
   -- migration audit baselines written at 2026-09-21 01:49:06, so they sort after
   -- every legacy business row and must not veto the match.
   AND NOT EXISTS (SELECT 1 FROM zsjos_lead_assignment_history n
     WHERE n.tenant_id=h.tenant_id AND n.lead_id=h.lead_id AND n.deleted=b'0'
     AND n.action_type<>'other'
     AND (n.occurred_at > h.occurred_at
       OR (n.occurred_at = h.occurred_at AND n.id > h.id))));

UPDATE zsjos_lead l JOIN repair_recycle_complete c ON l.id=c.lead_id AND l.tenant_id=c.tenant_id
 SET l.recycle_source_owner_user_id=l.owner_user_id,
     l.owner_user_id=NULL, l.owner_identity=NULL,
     l.status='submitted',
     l.updater='lead-recycle-suspension-repair';

-- ---------------------------------------------------------------------------
-- B. Restore suspended leads to submitted with a fresh qualification round.
-- ---------------------------------------------------------------------------
UPDATE zsjos_lead SET version=version
 WHERE tenant_id=@repair_tenant AND deleted=b'0' AND status='suspended';

-- Only leads with a live owner. Disabled owners are reported, not silently restored.
CREATE TEMPORARY TABLE repair_suspended AS
 SELECT l.id AS lead_id, l.tenant_id, l.lead_no, l.owner_user_id,
        COALESCE(l.qualification_round_no,0) AS old_round,
        COALESCE(l.qualification_round_no,0)+1 AS new_round,
        r.id AS rule_id, r.version AS rule_version,
        r.qualification_timeout_minutes AS minutes
 FROM zsjos_lead l
 JOIN system_users u ON u.id=l.owner_user_id AND u.deleted=b'0' AND u.status=0
 JOIN zsjos_lead_follow_up_rule r ON r.tenant_id=l.tenant_id
   AND r.code='default' AND r.status=0 AND r.deleted=b'0'
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0'
 AND l.status='suspended' AND l.assignment_status='owned' AND l.owner_user_id IS NOT NULL;

-- Report anything excluded from the restore so the operator can handle it by hand.
CREATE TEMPORARY TABLE repair_suspended_skipped AS
 SELECT l.id AS lead_id, l.lead_no, l.owner_user_id,
        CASE WHEN l.owner_user_id IS NULL THEN 'no_owner'
             WHEN u.id IS NULL THEN 'owner_missing'
             WHEN u.status<>0 THEN 'owner_disabled'
             ELSE 'other' END AS skip_reason
 FROM zsjos_lead l
 LEFT JOIN system_users u ON u.id=l.owner_user_id
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0'
 AND l.status='suspended' AND l.assignment_status='owned'
 AND NOT EXISTS (SELECT 1 FROM repair_suspended s WHERE s.lead_id=l.id);

UPDATE zsjos_lead l JOIN repair_suspended r ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 SET l.status='submitted',
     l.suspended_at=NULL,
     l.qualification_round_no=r.new_round,
     l.qualification_started_at=@repair_at,
     l.qualification_deadline_at=DATE_ADD(@repair_at,INTERVAL r.minutes MINUTE),
     l.qualification_rule_snapshot=JSON_OBJECT('ruleId',r.rule_id,'ruleVersion',r.rule_version,
         'timeoutMinutes',r.minutes,'startedAt',DATE_FORMAT(@repair_at,'%Y-%m-%dT%H:%i:%s'),
         'repairSource','lead-recycle-suspension-repair'),
     l.updater='lead-recycle-suspension-repair';

-- A fresh round needs a pending qualification task, matching createQualificationTask.
INSERT INTO zsjos_business_task (task_type,biz_type,biz_id,status,assignee_type,assignee_id,
 title_snapshot,action_code,due_at,payload,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_qualification','lead',r.lead_id,'pending','user',r.owner_user_id,
 CONCAT('有效性判定：',r.lead_no),'OPEN_LEAD_FOLLOW_UP',
 DATE_ADD(@repair_at,INTERVAL r.minutes MINUTE),
 JSON_OBJECT('ruleId',r.rule_id,'roundNo',r.new_round,'ruleVersion',r.rule_version,
   'timeoutMinutes',r.minutes,'repairSource','lead-recycle-suspension-repair'),
 CONCAT('lead-qualification:',r.lead_id,':',r.new_round),
 'lead-recycle-suspension-repair','lead-recycle-suspension-repair',r.tenant_id
 FROM repair_suspended r
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_task t
   WHERE t.tenant_id=r.tenant_id AND t.deleted=b'0'
   AND t.idempotency_key=CONCAT('lead-qualification:',r.lead_id,':',r.new_round));

-- Record the restore as a real repair-time event, never backdated to look historical.
INSERT INTO zsjos_business_event (event_type,aggregate_type,aggregate_id,operator_user_id,
 from_status,to_status,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_restored','lead',r.lead_id,NULL,'suspended','submitted','有效性判定超时自动挂起后恢复',
 JSON_OBJECT('roundNo',r.new_round,'ownerUserId',r.owner_user_id,
   'repairSource','lead-recycle-suspension-repair'),
 @repair_at, CONCAT('lead-restored-repair:',r.lead_id,':',r.new_round),
 'lead-recycle-suspension-repair','lead-recycle-suspension-repair',r.tenant_id
 FROM repair_suspended r
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_event e
   WHERE e.tenant_id=r.tenant_id AND e.aggregate_id=r.lead_id AND e.deleted=b'0'
   AND e.event_type='lead_restored'
   AND e.idempotency_key=CONCAT('lead-restored-repair:',r.lead_id,':',r.new_round));

-- Snapshot the new round for performance attribution, matching PerformanceSnapshotService.
INSERT INTO zsjos_performance_attribution (fact_type,fact_id,user_id,user_name,dept_id,dept_name,
 center_id,center_name,lead_id,assignment_id,received_at,channel_code,channel_label,source_group,
 outcome,completed_at,tenant_id)
 SELECT 'QUALIFICATION',t.id,t.assignee_id,u.nickname,
        COALESCE(o.dept_id,u.dept_id),d.name,
        mo.center_id,cd.name,
        r.lead_id,l.current_assignment_history_id,l.ownership_started_at,
        l.source_channel_id,l.source_channel_label_snapshot,
        CASE WHEN l.source_type IN ('internal_new_media','partner') THEN 'inbound'
             WHEN l.source_type='sales_self_sourced' THEN 'self' ELSE 'unknown' END,
        'pending',NULL,r.tenant_id
 FROM repair_suspended r
 JOIN zsjos_lead l ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 JOIN zsjos_business_task t ON t.tenant_id=r.tenant_id AND t.deleted=b'0'
   AND t.idempotency_key=CONCAT('lead-qualification:',r.lead_id,':',r.new_round)
 LEFT JOIN system_users u ON u.id=t.assignee_id
 LEFT JOIN system_dept d ON d.id=u.dept_id
 LEFT JOIN zsjos_performance_org o ON o.dept_id=u.dept_id AND o.deleted=b'0'
 LEFT JOIN zsjos_performance_org mo ON mo.id=o.center_id AND mo.deleted=b'0'
 LEFT JOIN system_dept cd ON cd.id=mo.center_id
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_performance_attribution p
   WHERE p.tenant_id=r.tenant_id AND p.fact_type='QUALIFICATION'
   AND p.fact_id=t.id AND p.deleted=b'0');

-- The expired round's attribution stays as-is: it is a true record of the timeout.
-- Close the cancelled round's pending follow-up bookkeeping for the new round.
UPDATE zsjos_business_task t JOIN repair_suspended r
   ON t.tenant_id=r.tenant_id AND t.biz_id=r.lead_id
 SET t.assignee_id=r.owner_user_id, t.updater='lead-recycle-suspension-repair'
 WHERE t.deleted=b'0' AND t.biz_type='lead' AND t.status='pending'
 AND t.task_type IN ('lead_first_follow_up','lead_follow_up_reminder');

-- ---------------------------------------------------------------------------
-- C. 异常客资 role menus (6800 查询 / 6802 处置) are administrator-owned.
--    A repair script must not mutate system_role_menu: authorization belongs to the
--    System UI, and the deployment static check rejects any role-menu write in SQL.
--    This section only REPORTS which expected grants are missing, so the operator
--    can restore them via 系统管理 → 角色管理.
-- ---------------------------------------------------------------------------
CREATE TEMPORARY TABLE repair_role_menu_expected (role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL,
 role_label VARCHAR(64) NOT NULL, PRIMARY KEY (role_id,menu_id));
INSERT INTO repair_role_menu_expected (role_id,menu_id,role_label) VALUES
 (3006,6800,'销售主管'),(3006,6802,'销售主管'),
 (3002,6800,'部门主管'),(3002,6802,'部门主管'),
 (3004,6800,'新媒体运营'),(3004,6802,'新媒体运营'),
 (3001,6800,'中心负责人');

SELECT e.role_id, e.role_label, e.menu_id,
       CASE WHEN x.role_id IS NULL THEN 'MISSING - restore via role management UI'
            ELSE 'present' END AS grant_state
 FROM repair_role_menu_expected e
 LEFT JOIN system_role_menu x
   ON x.role_id=e.role_id AND x.menu_id=e.menu_id AND x.deleted=b'0'
 ORDER BY e.role_id, e.menu_id;

-- Report the rows this repair deliberately left alone, so the operator can act on them.
SELECT 'recycle_not_rolled_back' AS section, COUNT(*) AS rows_kept
 FROM zsjos_lead l
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0'
 AND l.assignment_status='recycle_pending' AND l.recycle_source_owner_user_id IS NULL
 AND NOT EXISTS (SELECT 1 FROM repair_recycle_rollback r WHERE r.lead_id=l.id)
 AND NOT EXISTS (SELECT 1 FROM repair_recycle_complete c WHERE c.lead_id=l.id);
SELECT skip_reason, COUNT(*) AS rows_kept, GROUP_CONCAT(lead_no ORDER BY lead_id) AS lead_nos
 FROM repair_suspended_skipped GROUP BY skip_reason;

-- Drop the temporary tables so the session leaves no schema behind.
DROP TEMPORARY TABLE IF EXISTS repair_role_menu_expected;
DROP TEMPORARY TABLE IF EXISTS repair_suspended_skipped;
DROP TEMPORARY TABLE IF EXISTS repair_suspended;
DROP TEMPORARY TABLE IF EXISTS repair_recycle_complete;
DROP TEMPORARY TABLE IF EXISTS repair_recycle_rollback;
DROP TEMPORARY TABLE IF EXISTS repair_guard;

-- Verify before COMMIT:
--   SELECT assignment_status,COUNT(*) FROM zsjos_lead WHERE deleted=b'0' GROUP BY assignment_status;
--   SELECT status,COUNT(*) FROM zsjos_lead WHERE deleted=b'0' GROUP BY status;
--   SELECT COUNT(*) FROM zsjos_lead WHERE status='suspended' AND qualification_deadline_at<=NOW();
-- Then COMMIT, or ROLLBACK to abandon every write above.
