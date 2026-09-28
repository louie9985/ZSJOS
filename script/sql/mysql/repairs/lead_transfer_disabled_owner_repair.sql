-- UTF-8. Explicit local repair, not a bootstrap seed or a numbered migration.
-- Prerequisites: current Lead/task/event schema, zsjos_lead_follow_up_rule code='default',
-- zsjos_performance_attribution, and zsjos_performance_org.
-- Caller MUST set @repair_tenant, @repair_at (Beijing DATETIME), @target_user_id and
-- @operator_user_id (a real admin acting on behalf of the supervisor), back up scope,
-- START TRANSACTION before sourcing, then verify and COMMIT or ROLLBACK.
-- No deletes. Re-run is a no-op after success.
--
-- Companion to lead_recycle_and_suspension_repair.sql. That script restored 69 of the
-- 74 suspended leads. The 5 remaining ones (295, 381, 3133, 5322, 5346) were skipped
-- because their owners (305 陈坤和, 57 汪根松) are disabled accounts. This transfers
-- them to an explicitly named active sales instead of restoring to a disabled owner.
--
-- Mirrors LeadQualificationServiceImpl.transfer(): status back to submitted, new
-- ownership history row, new qualification round from the current enabled rule, a
-- fresh pending qualification task, and a real repair-time event. The expired round's
-- attribution stays untouched as a true record of the timeout.
SET NAMES utf8mb4;
SET time_zone = '+08:00';
CREATE TEMPORARY TABLE repair_guard (ok INT NOT NULL);
INSERT INTO repair_guard VALUES (IF(@repair_tenant > 0 AND @repair_at IS NOT NULL
 AND @target_user_id > 0 AND @operator_user_id > 0, 1, NULL));

-- The operator is a real admin acting on the supervisor's behalf, as recorded in
-- the audit log; it must be a live enabled account in the same tenant.
CREATE TEMPORARY TABLE repair_operator AS
 SELECT u.id AS user_id FROM system_users u
 WHERE u.id=@operator_user_id AND u.deleted=b'0' AND u.status=0;
CREATE TEMPORARY TABLE repair_operator_guard (ok INT NOT NULL);
INSERT INTO repair_operator_guard SELECT IF(COUNT(*)=1, 1, NULL) FROM repair_operator;

-- Target must be a live, enabled user in the same tenant, otherwise fail loudly.
CREATE TEMPORARY TABLE repair_target AS
 SELECT u.id AS user_id, u.nickname, u.dept_id
 FROM system_users u
 WHERE u.id=@target_user_id AND u.deleted=b'0' AND u.status=0
 AND EXISTS (SELECT 1 FROM system_user_post up JOIN system_post p ON p.id=up.post_id
   WHERE up.user_id=u.id AND up.deleted=b'0' AND p.deleted=b'0'
   AND p.code='sales_specialist');
CREATE TEMPORARY TABLE repair_target_guard (ok INT NOT NULL);
INSERT INTO repair_target_guard
 SELECT IF(COUNT(*)=1, 1, NULL) FROM repair_target;

-- Only suspended+owned leads whose owner is a disabled account. This is exactly the
-- set the companion repair skipped, so the two scripts cannot overlap.
CREATE TEMPORARY TABLE repair_transfer AS
 SELECT l.id AS lead_id, l.tenant_id, l.lead_no, l.owner_user_id AS from_owner,
        COALESCE(l.qualification_round_no,0) AS old_round,
        COALESCE(l.qualification_round_no,0)+1 AS new_round,
        r.id AS rule_id, r.version AS rule_version,
        r.qualification_timeout_minutes AS minutes
 FROM zsjos_lead l
 JOIN system_users u ON u.id=l.owner_user_id
 JOIN zsjos_lead_follow_up_rule r ON r.tenant_id=l.tenant_id
   AND r.code='default' AND r.status=0 AND r.deleted=b'0'
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0'
 AND l.status='suspended' AND l.assignment_status='owned'
 AND l.owner_user_id IS NOT NULL
 AND (u.deleted=b'1' OR u.status<>0)
 AND l.owner_user_id <> @target_user_id;

-- Lock the leads before deriving further evidence, same lead-first order as the app.
UPDATE zsjos_lead SET version=version
 WHERE tenant_id=@repair_tenant AND deleted=b'0' AND status='suspended'
 AND id IN (SELECT lead_id FROM repair_transfer);

-- Cancel the stale round without inventing a new cancelled_at. cancelQualificationTask
-- only marks a task the scheduler already cancelled; anything still pending is closed
-- at repair time so the lead is not left with two live qualification tasks.
UPDATE zsjos_business_task t JOIN repair_transfer r ON t.tenant_id=r.tenant_id AND t.biz_id=r.lead_id
 SET t.status='cancelled', t.cancelled_at=COALESCE(t.cancelled_at,@repair_at),
     t.cancel_reason=COALESCE(t.cancel_reason,'客资改派'),
     t.updater='lead-transfer-disabled-owner-repair'
 WHERE t.deleted=b'0' AND t.biz_type='lead' AND t.task_type='lead_qualification'
 AND t.status='pending';

-- New ownership history row per lead, mirroring addHistory(ACTION_TRANSFER,...).
INSERT INTO zsjos_lead_assignment_history (lead_id,action_type,from_owner_user_id,to_owner_user_id,
 operator_user_id,reason,occurred_at,creator,updater,tenant_id)
 SELECT r.lead_id,'transfer',r.from_owner,@target_user_id,@operator_user_id,
 '原责任销售账号已停用，主管改派',@repair_at,
 'lead-transfer-disabled-owner-repair','lead-transfer-disabled-owner-repair',r.tenant_id
 FROM repair_transfer r;

-- Point the lead at the row just written and reset the new assignment's follow-up state,
-- matching transfer() which clears first-follow-up and next-follow-up deadlines.
UPDATE zsjos_lead l JOIN repair_transfer r ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 JOIN zsjos_lead_assignment_history h ON h.tenant_id=r.tenant_id AND h.lead_id=r.lead_id
   AND h.action_type='transfer' AND h.occurred_at=@repair_at AND h.deleted=b'0'
 SET l.status='submitted',
     l.suspended_at=NULL,
     l.owner_user_id=@target_user_id,
     l.owner_identity='sales',
     l.recycle_source_owner_user_id=NULL,
     l.ownership_started_at=@repair_at,
     l.current_assignment_history_id=h.id,
     l.current_assignment_first_follow_up_at=NULL,
     l.current_assignment_first_follow_up_deadline_at=NULL,
     l.next_follow_up_at=NULL,
     l.qualification_round_no=r.new_round,
     l.qualification_started_at=@repair_at,
     l.qualification_deadline_at=DATE_ADD(@repair_at,INTERVAL r.minutes MINUTE),
     l.qualification_rule_snapshot=JSON_OBJECT('ruleId',r.rule_id,'ruleVersion',r.rule_version,
       'timeoutMinutes',r.minutes,'startedAt',DATE_FORMAT(@repair_at,'%Y-%m-%dT%H:%i:%s'),
       'repairSource','lead-transfer-disabled-owner-repair'),
     l.last_activity_at=@repair_at,
     l.updater='lead-transfer-disabled-owner-repair';

-- A fresh round needs its pending task, matching createQualificationTask.
INSERT INTO zsjos_business_task (task_type,biz_type,biz_id,status,assignee_type,assignee_id,
 title_snapshot,action_code,due_at,payload,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_qualification','lead',r.lead_id,'pending','user',@target_user_id,
 CONCAT('有效性判定：',r.lead_no),'OPEN_LEAD_FOLLOW_UP',
 DATE_ADD(@repair_at,INTERVAL r.minutes MINUTE),
 JSON_OBJECT('ruleId',r.rule_id,'roundNo',r.new_round,'ruleVersion',r.rule_version,
   'timeoutMinutes',r.minutes,'repairSource','lead-transfer-disabled-owner-repair'),
 CONCAT('lead-qualification:',r.lead_id,':',r.new_round),
 'lead-transfer-disabled-owner-repair','lead-transfer-disabled-owner-repair',r.tenant_id
 FROM repair_transfer r
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_task t
   WHERE t.tenant_id=r.tenant_id AND t.deleted=b'0'
   AND t.idempotency_key=CONCAT('lead-qualification:',r.lead_id,':',r.new_round));

-- Record the transfer as a real repair-time event, never backdated.
INSERT INTO zsjos_business_event (event_type,aggregate_type,aggregate_id,operator_user_id,
 from_status,to_status,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_transferred','lead',r.lead_id,@operator_user_id,'suspended','submitted',
 '原责任销售账号已停用，主管改派',
 JSON_OBJECT('fromOwnerUserId',r.from_owner,'toOwnerUserId',@target_user_id,
   'roundNo',r.new_round,'repairSource','lead-transfer-disabled-owner-repair'),
 @repair_at, CONCAT('lead-transferred-repair:',r.lead_id,':',r.new_round),
 'lead-transfer-disabled-owner-repair','lead-transfer-disabled-owner-repair',r.tenant_id
 FROM repair_transfer r
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_event e
   WHERE e.tenant_id=r.tenant_id AND e.aggregate_id=r.lead_id AND e.deleted=b'0'
   AND e.idempotency_key=CONCAT('lead-transferred-repair:',r.lead_id,':',r.new_round));

-- Snapshot the new round for performance attribution under the new owner.
INSERT INTO zsjos_performance_attribution (fact_type,fact_id,user_id,user_name,dept_id,dept_name,
 center_id,center_name,lead_id,assignment_id,received_at,channel_code,channel_label,source_group,
 outcome,completed_at,tenant_id)
 SELECT 'QUALIFICATION',t.id,@target_user_id,u.nickname,
        COALESCE(o.dept_id,u.dept_id),d.name, mo.center_id,cd.name,
        r.lead_id,l.current_assignment_history_id,@repair_at,
        l.source_channel_id,l.source_channel_label_snapshot,
        CASE WHEN l.source_type IN ('internal_new_media','partner') THEN 'inbound'
             WHEN l.source_type='sales_self_sourced' THEN 'self' ELSE 'unknown' END,
        'pending',NULL,r.tenant_id
 FROM repair_transfer r
 JOIN zsjos_lead l ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 JOIN zsjos_business_task t ON t.tenant_id=r.tenant_id AND t.deleted=b'0'
   AND t.idempotency_key=CONCAT('lead-qualification:',r.lead_id,':',r.new_round)
 LEFT JOIN system_users u ON u.id=@target_user_id
 LEFT JOIN system_dept d ON d.id=u.dept_id
 LEFT JOIN zsjos_performance_org o ON o.dept_id=u.dept_id AND o.deleted=b'0'
 LEFT JOIN zsjos_performance_org mo ON mo.id=o.center_id AND mo.deleted=b'0'
 LEFT JOIN system_dept cd ON cd.id=mo.center_id
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_performance_attribution p
   WHERE p.tenant_id=r.tenant_id AND p.fact_type='QUALIFICATION'
   AND p.fact_id=t.id AND p.deleted=b'0');

-- Report what moved and what stayed behind.
SELECT 'transferred' AS section, COUNT(*) AS leads, @target_user_id AS new_owner,
       GROUP_CONCAT(lead_no ORDER BY lead_id) AS lead_nos FROM repair_transfer;
SELECT 'still_suspended' AS section, COUNT(*) AS leads
 FROM zsjos_lead WHERE tenant_id=@repair_tenant AND deleted=b'0' AND status='suspended';

DROP TEMPORARY TABLE IF EXISTS repair_transfer;
DROP TEMPORARY TABLE IF EXISTS repair_target_guard;
DROP TEMPORARY TABLE IF EXISTS repair_target;
DROP TEMPORARY TABLE IF EXISTS repair_operator_guard;
DROP TEMPORARY TABLE IF EXISTS repair_operator;
DROP TEMPORARY TABLE IF EXISTS repair_guard;

-- Verify before COMMIT:
--   SELECT id,status,assignment_status,owner_user_id,qualification_round_no,qualification_deadline_at
--     FROM zsjos_lead WHERE id IN (295,381,3133,5322,5346);
--   SELECT COUNT(*) FROM zsjos_business_task WHERE biz_id IN (295,381,3133,5322,5346)
--     AND task_type='lead_qualification' AND status='pending';
-- Then COMMIT, or ROLLBACK to abandon every write above.
