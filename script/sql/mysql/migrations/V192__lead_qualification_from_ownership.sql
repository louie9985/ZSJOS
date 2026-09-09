-- UTF-8. V192: start lead qualification timing at ownership time and backfill active owned leads.
-- Dependencies: V014 qualification columns, V006 ownership timestamps, and the enabled tenant rule.
-- Data scope: only submitted + owned leads with ownership_started_at and no qualification deadline;
-- public-pool, suspended, valid, invalid, and closed historical leads are untouched.
-- Repeatability: guarded temporary snapshot plus qualification task/event idempotency keys.
-- Rollback limitation: no automatic rollback; generated task/event and round facts are audit history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v192_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v192_apply()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V191') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V192 requires V191';
  END IF;

  DROP TEMPORARY TABLE IF EXISTS zsjos_v192_backfill;
  CREATE TEMPORARY TABLE zsjos_v192_backfill AS
  SELECT l.id, l.tenant_id, l.owner_user_id, l.lead_no, l.submitted_name,
         l.ownership_started_at AS started_at,
         COALESCE(l.qualification_round_no, 0) + 1 AS round_no,
         r.id AS rule_id, COALESCE(r.version, 0) AS rule_version,
         r.qualification_timeout_minutes AS timeout_minutes,
         DATE_ADD(l.ownership_started_at, INTERVAL r.qualification_timeout_minutes MINUTE) AS due_at
    FROM zsjos_lead l
    JOIN zsjos_lead_follow_up_rule r
      ON r.tenant_id = l.tenant_id AND r.code = 'default' AND r.status = 0 AND r.deleted = b'0'
   WHERE l.status = 'submitted' AND l.assignment_status = 'owned'
     AND l.owner_user_id IS NOT NULL AND l.ownership_started_at IS NOT NULL
     AND l.qualification_deadline_at IS NULL AND l.deleted = b'0';

  UPDATE zsjos_lead l JOIN zsjos_v192_backfill b ON b.id = l.id
     SET l.qualification_round_no = b.round_no,
         l.qualification_started_at = b.started_at,
         l.qualification_deadline_at = b.due_at,
         l.qualification_rule_snapshot = JSON_OBJECT('ruleId', b.rule_id,
             'ruleVersion', b.rule_version, 'timeoutMinutes', b.timeout_minutes,
             'startedAt', DATE_FORMAT(b.started_at, '%Y-%m-%dT%H:%i:%s'));

  INSERT INTO zsjos_business_task
    (task_type, biz_type, biz_id, status, assignee_type, assignee_id, title_snapshot,
     action_code, due_at, payload, idempotency_key, version, creator, updater, tenant_id)
  SELECT 'lead_qualification', 'lead', b.id, 'pending', 'user', b.owner_user_id,
         CONCAT('有效性判定：', COALESCE(NULLIF(b.lead_no, ''), b.submitted_name)),
         'OPEN_LEAD_FOLLOW_UP', b.due_at,
         JSON_OBJECT('roundNo', b.round_no, 'ruleId', b.rule_id,
             'ruleVersion', b.rule_version, 'timeoutMinutes', b.timeout_minutes),
         CONCAT('lead-qualification:', b.id, ':', b.round_no), 0, 'V192', 'V192', b.tenant_id
    FROM zsjos_v192_backfill b
   WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_task t
                      WHERE t.tenant_id = b.tenant_id
                        AND t.idempotency_key = CONCAT('lead-qualification:', b.id, ':', b.round_no));

  INSERT INTO zsjos_business_event
    (event_type, aggregate_type, aggregate_id, operator_user_id, from_status, to_status,
     related_object_refs, occurred_at, idempotency_key, creator, updater, tenant_id)
  SELECT 'lead_qualification_started', 'lead', b.id, b.owner_user_id, 'submitted', 'submitted',
         JSON_OBJECT('roundNo', b.round_no, 'dueAt', DATE_FORMAT(b.due_at, '%Y-%m-%dT%H:%i:%s')),
         b.started_at, CONCAT('lead-qualification-started:', b.id, ':', b.round_no), 'V192', 'V192', b.tenant_id
    FROM zsjos_v192_backfill b
   WHERE NOT EXISTS (SELECT 1 FROM zsjos_business_event e
                      WHERE e.tenant_id = b.tenant_id
                        AND e.idempotency_key = CONCAT('lead-qualification-started:', b.id, ':', b.round_no));

  DROP TEMPORARY TABLE IF EXISTS zsjos_v192_backfill;
  INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
  VALUES ('V192','Lead qualification timing from ownership',SHA2('V192__lead_qualification_from_ownership.sql',256),NOW())
  ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
  INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
  VALUES ('core','V192','Lead qualification timing from ownership',SHA2('V192__lead_qualification_from_ownership.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
END$$
DELIMITER ;
CALL zsjos_v192_apply();
DROP PROCEDURE IF EXISTS zsjos_v192_apply;
