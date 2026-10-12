-- UTF-8. Independent appeal cycles for each immutable invalid-decision business event.
-- Prerequisite: V026 appeal snapshots, Lead/BusinessEvent tables and transactional ledgers.
-- Deployment: stop appeal writers, apply this upgrade, then deploy all appeal clients/backend together.
-- Scope: one appeal cycle key, one unique index replacement, evidence-only historical correlation.
-- No permissions/dictionaries/BPM instances are modified. Legacy rows without exact submit events retain 0.
-- Repeat/partial recovery: inspect real structure even with version marker; RESIGNAL all errors.
-- DDL commits implicitly; a failed run can leave columns/indexes. Rerun after correcting the cause.
-- Rollback: retain schema/history; do not roll back to lead-wide-round writers after new cycles exist.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v311_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v311_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V026') OR
     NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V026') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V311 requires V026 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
      AND table_name IN ('zsjos_lead_appeal','zsjos_business_event','zsjos_schema_version','zsjos_module_schema_version')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V311 prerequisite tables missing';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_appeal' AND column_name='invalid_event_id') THEN
    ALTER TABLE zsjos_lead_appeal ADD COLUMN invalid_event_id bigint NOT NULL DEFAULT 0 COMMENT '申诉所属无效判定事件，0为未关联历史';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_appeal' AND column_name='invalid_event_id' AND data_type='bigint'
      AND is_nullable='NO' AND column_default='0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V311 cycle column incompatible';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_lead_cycle_round') THEN
    ALTER TABLE zsjos_lead_appeal ADD UNIQUE KEY uk_tenant_lead_cycle_round(tenant_id,lead_id,invalid_event_id,round_no);
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal'
      AND index_name='uk_tenant_lead_cycle_round' AND non_unique=0 AND sub_part IS NULL),'')
      <> 'tenant_id,lead_id,invalid_event_id,round_no' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V311 cycle index incompatible';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_lead_round') THEN
    IF COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_lead_round'
        AND non_unique=0 AND sub_part IS NULL),'') <> 'tenant_id,lead_id,round_no' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V311 legacy index incompatible';
    END IF;
    ALTER TABLE zsjos_lead_appeal DROP INDEX uk_tenant_lead_round;
  END IF;
  START TRANSACTION;
  -- Event IDs order writes made under the same Lead lock, including decisions/submissions in one second.
  -- Require exactly one matching submission event; no timestamp-only historical inference.
  UPDATE zsjos_lead_appeal a
  JOIN (SELECT tenant_id,aggregate_id,JSON_UNQUOTE(JSON_EXTRACT(related_object_refs,'$.appealId')) appeal_id,
               MIN(id) submit_id FROM zsjos_business_event
        WHERE aggregate_type='lead' AND event_type='lead_appeal_submitted' AND deleted=0
        GROUP BY tenant_id,aggregate_id,JSON_UNQUOTE(JSON_EXTRACT(related_object_refs,'$.appealId')) HAVING COUNT(*)=1) s
    ON s.tenant_id=a.tenant_id AND s.aggregate_id=a.lead_id AND s.appeal_id=CAST(a.id AS CHAR)
  SET a.invalid_event_id=COALESCE((SELECT MAX(e.id) FROM zsjos_business_event e
      WHERE e.tenant_id=a.tenant_id AND e.aggregate_type='lead' AND e.aggregate_id=a.lead_id
      AND e.event_type='lead_qualified_invalid' AND e.deleted=0 AND e.id<s.submit_id),0), a.update_time=a.update_time
  WHERE a.invalid_event_id=0;
  IF EXISTS(SELECT 1 FROM zsjos_lead_appeal a LEFT JOIN zsjos_business_event e
      ON e.id=a.invalid_event_id AND e.tenant_id=a.tenant_id AND e.aggregate_type='lead'
      AND e.aggregate_id=a.lead_id AND e.event_type='lead_qualified_invalid'
      WHERE a.invalid_event_id<>0 AND e.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V311 cycle event integrity failed';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V311','Lead appeal cycles',SHA2('V311__lead_appeal_cycles.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V311');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V311','Lead appeal cycles',SHA2('V311__lead_appeal_cycles.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V311');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v311_apply();
DROP PROCEDURE zsjos_v311_apply;
