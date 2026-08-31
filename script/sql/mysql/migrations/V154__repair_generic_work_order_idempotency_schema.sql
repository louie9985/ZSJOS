-- V154: repair generic work-order idempotency columns missing from an already-created legacy table.
-- Dependencies/order: apply after V115. Fresh bootstrap runs this after V153.
-- Data scope: zsjos_work_order.command_user_id/request_fingerprint and
-- zsjos_work_order_history.operation/request_fingerprint only; no rows are deleted.
-- Compatibility: command_user_id comes from the persisted source_user_id invariant. Missing legacy
-- fingerprints and operation names receive stable legacy markers so old keys fail closed on replay.
-- Repeatability: each ADD COLUMN and nullability repair is guarded; legacy backfills update only missing values.
-- Recovery: forward-only. Dropping these columns after runtime use loses the idempotency audit contract.

DROP PROCEDURE IF EXISTS `zsjos_v154_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v154_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V115') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V154 requires V115';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `information_schema`.`tables`
                 WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order')
     OR NOT EXISTS (SELECT 1 FROM `information_schema`.`tables`
                    WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order_history') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V154 requires the V115 work-order tables';
  END IF;
  IF (SELECT COUNT(*) FROM `information_schema`.`columns`
      WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order'
        AND `column_name` IN ('id','tenant_id','source_user_id','idempotency_key'))<>4
     OR (SELECT COUNT(*) FROM `information_schema`.`columns`
         WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order_history'
           AND `column_name` IN ('id','tenant_id','idempotency_key'))<>3 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V154 blocked: the legacy work-order tables lack required base columns';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM `information_schema`.`columns`
                 WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order'
                   AND `column_name`='command_user_id') THEN
    ALTER TABLE `zsjos_work_order`
      ADD COLUMN `command_user_id` bigint DEFAULT NULL AFTER `idempotency_key`;
  END IF;
  UPDATE `zsjos_work_order`
  SET `command_user_id`=`source_user_id`
  WHERE `command_user_id` IS NULL AND `source_user_id` IS NOT NULL;
  IF EXISTS (SELECT 1 FROM `zsjos_work_order` WHERE `command_user_id` IS NULL LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V154 blocked: a work order has no authoritative source user for command_user_id';
  END IF;
  IF EXISTS (SELECT 1 FROM `information_schema`.`columns`
             WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order'
               AND `column_name`='command_user_id' AND `is_nullable`='YES') THEN
    ALTER TABLE `zsjos_work_order`
      MODIFY COLUMN `command_user_id` bigint NOT NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM `information_schema`.`columns`
                 WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order'
                   AND `column_name`='request_fingerprint') THEN
    ALTER TABLE `zsjos_work_order`
      ADD COLUMN `request_fingerprint` varchar(64) DEFAULT NULL AFTER `command_user_id`;
  END IF;
  UPDATE `zsjos_work_order`
  SET `request_fingerprint`=SHA2(CONCAT('legacy-work-order:',`tenant_id`,':',`id`,':',
      COALESCE(`idempotency_key`,'')),256)
  WHERE `request_fingerprint` IS NULL OR `request_fingerprint`='';
  IF EXISTS (SELECT 1 FROM `information_schema`.`columns`
             WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order'
               AND `column_name`='request_fingerprint' AND `is_nullable`='YES') THEN
    ALTER TABLE `zsjos_work_order`
      MODIFY COLUMN `request_fingerprint` varchar(64) NOT NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM `information_schema`.`columns`
                 WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order_history'
                   AND `column_name`='operation') THEN
    ALTER TABLE `zsjos_work_order_history`
      ADD COLUMN `operation` varchar(32) DEFAULT NULL AFTER `idempotency_key`;
  END IF;
  UPDATE `zsjos_work_order_history`
  SET `operation`='legacy'
  WHERE `operation` IS NULL OR `operation`='';
  IF EXISTS (SELECT 1 FROM `information_schema`.`columns`
             WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order_history'
               AND `column_name`='operation' AND `is_nullable`='YES') THEN
    ALTER TABLE `zsjos_work_order_history`
      MODIFY COLUMN `operation` varchar(32) NOT NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM `information_schema`.`columns`
                 WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order_history'
                   AND `column_name`='request_fingerprint') THEN
    ALTER TABLE `zsjos_work_order_history`
      ADD COLUMN `request_fingerprint` varchar(64) DEFAULT NULL AFTER `operation`;
  END IF;
  UPDATE `zsjos_work_order_history`
  SET `request_fingerprint`=SHA2(CONCAT('legacy-work-order-history:',`tenant_id`,':',`id`,':',
      COALESCE(`idempotency_key`,'')),256)
  WHERE `request_fingerprint` IS NULL OR `request_fingerprint`='';
  IF EXISTS (SELECT 1 FROM `information_schema`.`columns`
             WHERE `table_schema`=DATABASE() AND `table_name`='zsjos_work_order_history'
               AND `column_name`='request_fingerprint' AND `is_nullable`='YES') THEN
    ALTER TABLE `zsjos_work_order_history`
      MODIFY COLUMN `request_fingerprint` varchar(64) NOT NULL;
  END IF;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V154','Repair generic work-order idempotency schema',
          'V154__repair_generic_work_order_idempotency_schema.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V154','Repair generic work-order idempotency schema',
          SHA2('V154__repair_generic_work_order_idempotency_schema.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v154_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v154_apply`;
