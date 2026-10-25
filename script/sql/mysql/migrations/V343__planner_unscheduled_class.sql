-- UTF-8. Requires V339 in both ledgers; independent of V340-V342.
-- Add owned unscheduled containers, without moving services or assigning role permissions.
-- Apply before the new backend. Planner rows are created by System-qualified application code.
-- Repeatable and recoverable after partial DDL; DDL commits implicitly. Rollback requires
-- retaining new class rows and constraints while any service references them; do not drop data.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v343_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v343_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V339')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V339') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V343 requires V339 in both ledgers';
 END IF;
 ALTER TABLE zsjos_delivery_class MODIFY COLUMN class_kind varchar(24) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/PENDING/UNSCHEDULED/EXCEPTION';
 ALTER TABLE zsjos_delivery_class MODIFY COLUMN pending_guard bigint GENERATED ALWAYS AS
  (CASE WHEN (class_kind='PENDING' AND deleted=b'0') THEN tenant_id ELSE NULL END) STORED;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='unscheduled_guard') THEN
  ALTER TABLE zsjos_delivery_class ADD COLUMN unscheduled_guard bigint GENERATED ALWAYS AS
   (CASE WHEN (class_kind='UNSCHEDULED' AND deleted=b'0') THEN homeroom_user_id ELSE NULL END) STORED;
 END IF;
 -- Normalize an already-present column after partial application as well.
 ALTER TABLE zsjos_delivery_class MODIFY COLUMN unscheduled_guard bigint GENERATED ALWAYS AS
  (CASE WHEN (class_kind='UNSCHEDULED' AND deleted=b'0') THEN homeroom_user_id ELSE NULL END) STORED;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_unscheduled_guard') THEN
  ALTER TABLE zsjos_delivery_class ADD UNIQUE KEY uk_delivery_unscheduled_guard(tenant_id,unscheduled_guard);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class'
  AND column_name='unscheduled_guard' AND data_type='bigint' AND extra LIKE '%STORED GENERATED%')
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class'
  AND index_name='uk_delivery_unscheduled_guard' AND non_unique=0
  AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='unscheduled_guard')))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V343 unscheduled uniqueness postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V343','规划师未确定考期默认班',SHA2('V343__planner_unscheduled_class.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V343');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V343','规划师未确定考期默认班',SHA2('V343__planner_unscheduled_class.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V343');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v343_apply();
DROP PROCEDURE zsjos_v343_apply;
