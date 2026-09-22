-- UTF-8. Explicit local repair, not a bootstrap seed or a numbered migration.
-- Prerequisites: current Lead/task schema and zsjos_performance_attribution/org.
-- Caller MUST set @repair_tenant and @repair_at (Beijing DATETIME), back up scope,
-- START TRANSACTION before sourcing, then verify and explicitly COMMIT or ROLLBACK.
-- No deletes, permission changes, ownership reconstruction or historical rule invention.
-- Only submitted+owned leads; existing qualification rounds are not reset.
-- Re-run is a no-op after success. Rollback before commit is complete; after commit
-- restore scoped before-images only after checking for intervening business writes.
SET NAMES utf8mb4;
SET time_zone = '+08:00';
CREATE TEMPORARY TABLE repair_guard (ok INT NOT NULL);
INSERT INTO repair_guard VALUES (IF(@repair_tenant > 0 AND @repair_at IS NOT NULL, 1, NULL));
-- Lock leads before deriving evidence, using the same lead-first order as follow-up writes.
UPDATE zsjos_lead SET version=version
 WHERE tenant_id=@repair_tenant AND deleted=b'0' AND status='submitted' AND assignment_status='owned'
 AND (current_assignment_first_follow_up_at IS NULL OR qualification_deadline_at IS NULL);
CREATE TEMPORARY TABLE repair_first AS
 SELECT l.id AS lead_id, l.tenant_id, l.current_assignment_history_id AS assignment_id,
        l.owner_user_id, l.lead_no, f.id AS record_id, f.occurred_at AS first_at
 FROM zsjos_lead l JOIN zsjos_lead_follow_up_record f
 ON f.lead_id=l.id AND f.tenant_id=l.tenant_id AND f.assignment_history_id=l.current_assignment_history_id
 AND f.operator_user_id=l.owner_user_id AND f.deleted=b'0' AND f.occurred_at IS NOT NULL
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0' AND l.status='submitted' AND l.assignment_status='owned'
 AND l.current_assignment_first_follow_up_at IS NULL
 AND NOT EXISTS (SELECT 1 FROM zsjos_lead_follow_up_record p WHERE p.tenant_id=f.tenant_id
   AND p.lead_id=f.lead_id AND p.assignment_history_id=f.assignment_history_id
   AND p.operator_user_id=f.operator_user_id AND p.deleted=b'0'
   AND (p.occurred_at<f.occurred_at OR (p.occurred_at=f.occurred_at AND p.id<f.id)));
UPDATE zsjos_lead l JOIN repair_first r ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 SET l.current_assignment_first_follow_up_at=r.first_at, l.updater='lead-first-recovery';
UPDATE zsjos_lead_follow_up_record f JOIN repair_first r ON f.id=r.record_id AND f.tenant_id=r.tenant_id
 SET f.first_in_assignment=b'1', f.updater='lead-first-recovery';
UPDATE zsjos_business_task t JOIN repair_first r ON t.tenant_id=r.tenant_id
 AND t.biz_id=r.lead_id AND t.idempotency_key=CONCAT('lead-first-follow-up:',r.assignment_id)
 SET t.status='completed',t.completed_at=r.first_at,t.updater='lead-first-recovery'
 WHERE t.deleted=b'0' AND t.biz_type='lead' AND t.task_type='lead_first_follow_up' AND t.status='pending';
-- Missing historical tasks are recorded as completed, without inventing an old due date.
INSERT INTO zsjos_business_task (task_type,biz_type,biz_id,status,assignee_type,assignee_id,
 title_snapshot,action_code,completed_at,payload,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_first_follow_up','lead',r.lead_id,'completed','user',r.owner_user_id,
 CONCAT('首次跟进：',r.lead_no),'OPEN_LEAD_FOLLOW_UP',r.first_at,
 JSON_OBJECT('assignmentHistoryId',r.assignment_id,'repairSource','current-cycle-follow-up','recordId',r.record_id),
 CONCAT('lead-first-follow-up:',r.assignment_id),'lead-first-recovery','lead-first-recovery',r.tenant_id
 FROM repair_first r WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_task t
 WHERE t.tenant_id=r.tenant_id AND t.idempotency_key=CONCAT('lead-first-follow-up:',r.assignment_id));
-- With no first-follow evidence, keep the lead pending and start a real reminder now.
-- This is remediation timing, never a reconstructed historical ownership start.
CREATE TEMPORARY TABLE repair_pending_first AS
 SELECT l.id AS lead_id,l.tenant_id,l.lead_no,l.owner_user_id,l.current_assignment_history_id AS assignment_id,
 r.id AS rule_id,r.version AS rule_version,r.first_follow_up_timeout_minutes AS minutes,
 DATE_ADD(@repair_at,INTERVAL r.first_follow_up_timeout_minutes MINUTE) AS due_at
 FROM zsjos_lead l JOIN zsjos_lead_follow_up_rule r ON r.tenant_id=l.tenant_id
 AND r.code='default' AND r.status=0 AND r.deleted=b'0'
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0' AND l.status='submitted' AND l.assignment_status='owned'
 AND l.owner_user_id IS NOT NULL AND l.current_assignment_history_id IS NOT NULL
 AND l.current_assignment_first_follow_up_at IS NULL AND l.current_assignment_first_follow_up_deadline_at IS NULL
 AND l.qualification_deadline_at IS NULL
 AND NOT EXISTS (SELECT 1 FROM zsjos_business_task t WHERE t.tenant_id=l.tenant_id
 AND t.idempotency_key=CONCAT('lead-first-follow-up:',l.current_assignment_history_id));
UPDATE zsjos_lead l JOIN repair_pending_first r ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 SET l.current_assignment_first_follow_up_deadline_at=r.due_at,l.updater='lead-first-recovery';
INSERT INTO zsjos_business_task (task_type,biz_type,biz_id,status,assignee_type,assignee_id,
 title_snapshot,action_code,due_at,payload,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_first_follow_up','lead',r.lead_id,'pending','user',r.owner_user_id,
 CONCAT('首次跟进：',r.lead_no),'OPEN_LEAD_FOLLOW_UP',r.due_at,
 JSON_OBJECT('assignmentHistoryId',r.assignment_id,'ruleId',r.rule_id,'ruleVersion',COALESCE(r.rule_version,0),
 'timeoutMinutes',r.minutes,'repairSource','migration-missing-first-task','repairStartedAt',@repair_at),
 CONCAT('lead-first-follow-up:',r.assignment_id),'lead-first-recovery','lead-first-recovery',r.tenant_id
 FROM repair_pending_first r;
-- Only wholly absent qualification rounds. Partially populated rounds need a separate audit.
CREATE TEMPORARY TABLE repair_qualification AS
 SELECT l.id AS lead_id,l.tenant_id,l.lead_no,l.owner_user_id,l.current_assignment_history_id AS assignment_id,
 r.id AS rule_id,r.version AS rule_version,r.qualification_timeout_minutes AS minutes,
 DATE_ADD(@repair_at,INTERVAL r.qualification_timeout_minutes MINUTE) AS due_at
 FROM zsjos_lead l JOIN zsjos_lead_follow_up_rule r ON r.tenant_id=l.tenant_id
 AND r.code='default' AND r.status=0 AND r.deleted=b'0'
 WHERE l.tenant_id=@repair_tenant AND l.deleted=b'0' AND l.status='submitted' AND l.assignment_status='owned'
 AND l.owner_user_id IS NOT NULL AND l.current_assignment_history_id IS NOT NULL
 AND COALESCE(l.qualification_round_no,0)=0 AND l.qualification_started_at IS NULL
 AND l.qualification_deadline_at IS NULL AND l.qualification_rule_snapshot IS NULL
 AND NOT EXISTS (SELECT 1 FROM zsjos_business_task t WHERE t.tenant_id=l.tenant_id
 AND t.biz_id=l.id AND t.biz_type='lead' AND t.task_type='lead_qualification');
UPDATE zsjos_lead l JOIN repair_qualification r ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 SET l.qualification_round_no=1,l.qualification_started_at=@repair_at,l.qualification_deadline_at=r.due_at,
 l.qualification_rule_snapshot=JSON_OBJECT('ruleId',r.rule_id,'ruleVersion',COALESCE(r.rule_version,0),
 'timeoutMinutes',r.minutes,'startedAt',DATE_FORMAT(@repair_at,'%Y-%m-%dT%H:%i:%s'),
 'repairSource','migration-missing-round-restart'),l.updater='lead-first-recovery';
INSERT INTO zsjos_business_task (task_type,biz_type,biz_id,status,assignee_type,assignee_id,
 title_snapshot,action_code,due_at,payload,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_qualification','lead',r.lead_id,'pending','user',r.owner_user_id,
 CONCAT('有效性判定：',r.lead_no),'OPEN_LEAD_FOLLOW_UP',r.due_at,
 JSON_OBJECT('roundNo',1,'ruleId',r.rule_id,'ruleVersion',COALESCE(r.rule_version,0),'timeoutMinutes',r.minutes,
 'repairSource','migration-missing-round-restart'),CONCAT('lead-qualification:',r.lead_id,':1'),
 'lead-first-recovery','lead-first-recovery',r.tenant_id FROM repair_qualification r;
INSERT INTO zsjos_business_event (event_type,aggregate_type,aggregate_id,from_status,to_status,
 related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
 SELECT 'lead_qualification_started','lead',r.lead_id,'submitted','submitted',
 JSON_OBJECT('roundNo',1,'dueAt',DATE_FORMAT(r.due_at,'%Y-%m-%dT%H:%i:%s'),
 'repairSource','migration-missing-round-restart'),@repair_at,
 CONCAT('lead-qualification-started:',r.lead_id,':1'),'lead-first-recovery','lead-first-recovery',r.tenant_id
 FROM repair_qualification r WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_event e
 WHERE e.tenant_id=r.tenant_id AND e.idempotency_key=CONCAT('lead-qualification-started:',r.lead_id,':1'));
-- New qualification facts use current authoritative user/org data, not fabricated historical identities.
INSERT INTO zsjos_performance_attribution (fact_type,fact_id,outcome,user_id,user_name,dept_id,dept_name,
 center_id,center_name,lead_id,assignment_id,received_at,source_group,channel_code,channel_label,
 creator,updater,tenant_id)
 SELECT 'QUALIFICATION',t.id,'pending',l.owner_user_id,u.nickname,u.dept_id,d.name,
 o.center_id,c.name,l.id,l.current_assignment_history_id,l.ownership_started_at,
 CASE WHEN l.source_type IN ('internal_new_media','partner') THEN 'inbound'
 WHEN l.source_type='sales_self_sourced' THEN 'self' ELSE 'unknown' END,l.source_channel_id,l.source_channel_label_snapshot,
 'lead-first-recovery','lead-first-recovery',l.tenant_id
 FROM repair_qualification r JOIN zsjos_lead l ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 JOIN zsjos_business_task t ON t.tenant_id=r.tenant_id AND t.idempotency_key=CONCAT('lead-qualification:',r.lead_id,':1')
 LEFT JOIN system_users u ON u.id=l.owner_user_id AND u.tenant_id=l.tenant_id AND u.deleted=b'0'
 LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=l.tenant_id AND d.deleted=b'0'
 LEFT JOIN zsjos_performance_org o ON o.dept_id=u.dept_id AND o.tenant_id=l.tenant_id AND o.deleted=b'0'
 LEFT JOIN system_dept c ON c.id=o.center_id AND c.tenant_id=l.tenant_id AND c.deleted=b'0'
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_performance_attribution a WHERE a.tenant_id=l.tenant_id
 AND a.fact_type='QUALIFICATION' AND a.fact_id=t.id);
SELECT (SELECT COUNT(*) FROM repair_first) AS recovered_first_facts,
       (SELECT COUNT(*) FROM repair_pending_first) AS pending_first_tasks,
       (SELECT COUNT(*) FROM repair_qualification) AS restarted_qualification_rounds;
SELECT COUNT(*) AS mismatched_first FROM repair_first r JOIN zsjos_lead l ON l.id=r.lead_id AND l.tenant_id=r.tenant_id
 WHERE NOT(l.current_assignment_first_follow_up_at <=> r.first_at);
SELECT COUNT(*) AS missing_or_wrong_qualification_task FROM repair_qualification r
 LEFT JOIN zsjos_business_task t ON t.tenant_id=r.tenant_id AND t.idempotency_key=CONCAT('lead-qualification:',r.lead_id,':1')
 WHERE t.id IS NULL OR t.status<>'pending' OR NOT(t.due_at <=> r.due_at) OR t.assignee_id<>r.owner_user_id;
SELECT HEX(LEFT(title_snapshot,6)) AS qualification_title_hex,COUNT(*) AS tasks
 FROM zsjos_business_task WHERE tenant_id=@repair_tenant AND creator='lead-first-recovery'
 AND task_type='lead_qualification' GROUP BY HEX(LEFT(title_snapshot,6));
DROP TEMPORARY TABLE repair_first,repair_pending_first,repair_qualification,repair_guard;
