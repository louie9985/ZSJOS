-- UTF-8. Tenant-independent nullable service origin; no business row changes.
-- Requires V309 in both ledgers. Historical student enrollment is not a sale.
-- Deploy schema, then null-safe backend, then the separately reviewed cleanup data plan.
-- DDL implicitly commits. Repeat/partial recovery always inspect actual nullability;
-- failure never registers a success marker. Application rollback must retain nullable columns.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS zsjos_v310_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v310_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V310 requires V309 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation'
   AND column_name IN ('order_id','order_item_id','registration_case_id') AND data_type='bigint')<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V310 service reference prerequisite mismatch';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='order_id' AND is_nullable='NO') THEN
  ALTER TABLE zsjos_service_relation MODIFY order_id BIGINT DEFAULT NULL COMMENT '来源有效订单编号；历史独立服务可为空';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='order_item_id' AND is_nullable='NO') THEN
  ALTER TABLE zsjos_service_relation MODIFY order_item_id BIGINT DEFAULT NULL COMMENT '来源订单项编号；历史独立服务可为空';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='registration_case_id' AND is_nullable='NO') THEN
  ALTER TABLE zsjos_service_relation MODIFY registration_case_id BIGINT DEFAULT NULL COMMENT '激活报名服务单编号；历史独立服务可为空';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation'
   AND column_name IN ('order_id','order_item_id','registration_case_id') AND is_nullable='YES')<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V310 postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V310','Allow independent historical student service',SHA2('V310__student_service_without_virtual_order.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V310');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V310','Allow independent historical student service',SHA2('V310__student_service_without_virtual_order.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V310');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v310_apply();
DROP PROCEDURE zsjos_v310_apply;
