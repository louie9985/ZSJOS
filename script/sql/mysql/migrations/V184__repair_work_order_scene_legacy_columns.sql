-- V184: make legacy post-based work-order scene columns nullable.
-- Dependency: V183 student-information schema and the existing zsjos_work_order_scene table.
-- Scope: column nullability only; no template, work-order, permission, or dictionary rows change.
-- Repeatability: each column is altered only while it is still NOT NULL.
-- Rollback: forward-only compatibility repair; restoring NOT NULL would break the current
-- role/department-based template contract and is not an automatic rollback operation.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v184_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v184_apply`()
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE() AND table_name IN
        ('zsjos_student_info_form','zsjos_student_info_form_value','zsjos_student_info_form_config')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V184 requires V183 student-information schema';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_scene') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V184 requires zsjos_work_order_scene';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_scene'
        AND ((column_name='source_post_code' AND data_type='varchar' AND character_maximum_length=64)
          OR (column_name='target_post_code' AND data_type='varchar' AND character_maximum_length=64)
          OR (column_name='assignment_mode' AND data_type='varchar' AND character_maximum_length=32))) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V184 found incompatible work-order scene legacy columns';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_scene' AND column_name='source_post_code' AND is_nullable='NO') THEN
    ALTER TABLE `zsjos_work_order_scene` MODIFY COLUMN `source_post_code` varchar(64) DEFAULT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_scene' AND column_name='target_post_code' AND is_nullable='NO') THEN
    ALTER TABLE `zsjos_work_order_scene` MODIFY COLUMN `target_post_code` varchar(64) DEFAULT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_scene' AND column_name='assignment_mode' AND is_nullable='NO') THEN
    ALTER TABLE `zsjos_work_order_scene` MODIFY COLUMN `assignment_mode` varchar(32) DEFAULT NULL;
  END IF;
  INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V183','Student information collection', '2C0FFDAF33A11CC4FB72D7C7F283D1B6593F55AFFC14EDE30DEB9FF0A8E3D9EF',NOW());
  INSERT IGNORE INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V183','Student information collection','2C0FFDAF33A11CC4FB72D7C7F283D1B6593F55AFFC14EDE30DEB9FF0A8E3D9EF','baseline',NOW());
  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V184','Repair legacy work-order scene column nullability',SHA2('V184__repair_work_order_scene_legacy_columns.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V184','Repair legacy work-order scene column nullability',SHA2('V184__repair_work_order_scene_legacy_columns.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v184_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v184_apply`;
