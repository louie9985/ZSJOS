-- UTF-8. V290: tenant-shared exam calendar rich-text note and image bindings.
-- Prerequisite: Core V287 in both ledgers; independent of V288 notice sharing.
-- Fresh: baseline then the manifest migration chain. Upgrade: V287 -> V290 -> backend -> Workbench.
-- Scope: two empty tables only. No account grants, business options, data deletion or history rewrite.
-- Repeatable and recoverable after partial DDL; inspect tables and both ledgers before retry.
-- DDL implicitly commits. Invalid existing columns/indexes fail explicitly; retain additive schema on rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v290_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v290_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V287')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V290 requires Core V287 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
      AND table_name IN ('zsjos_schema_version','zsjos_module_schema_version')) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V290 requires transactional version ledgers';
  END IF;
CREATE TABLE IF NOT EXISTS `zsjos_exam_calendar_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` mediumtext NOT NULL COMMENT '富文本说明，图片使用稳定文件引用',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '并发编辑版本',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_note_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考期日历共享说明';
CREATE TABLE IF NOT EXISTS `zsjos_exam_calendar_note_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL COMMENT 'Infra文件编号',
  `uploaded_by` bigint NOT NULL COMMENT '上传人',
  `bound` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否被当前说明引用',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_note_image` (`tenant_id`,`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考期说明图片归属';
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_exam_calendar_note'
      AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci') <> 1
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_calendar_note' AND (
      (column_name='id' AND data_type='bigint' AND is_nullable='NO' AND extra LIKE '%auto_increment%') OR (column_name='content' AND data_type='mediumtext' AND is_nullable='NO') OR (column_name='version' AND data_type='bigint' AND is_nullable='NO' AND column_default='0') OR (column_name='creator' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=64) OR (column_name='create_time' AND data_type='datetime' AND is_nullable='NO') OR (column_name='updater' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=64) OR (column_name='update_time' AND data_type='datetime' AND is_nullable='NO') OR (column_name='deleted' AND data_type='bit' AND is_nullable='NO' AND column_default='b''0''') OR (column_name='tenant_id' AND data_type='bigint' AND is_nullable='NO' AND column_default='0'))) <> 9 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V290 zsjos_exam_calendar_note column contract mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM (SELECT index_name FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_exam_calendar_note' AND non_unique=0 GROUP BY index_name
      HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) IN ('id','tenant_id')) required_keys) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V290 zsjos_exam_calendar_note unique index mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_exam_calendar_note_image'
      AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci') <> 1
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_calendar_note_image' AND (
      (column_name='id' AND data_type='bigint' AND is_nullable='NO' AND extra LIKE '%auto_increment%') OR (column_name='file_id' AND data_type='bigint' AND is_nullable='NO') OR (column_name='uploaded_by' AND data_type='bigint' AND is_nullable='NO') OR (column_name='bound' AND data_type='bit' AND is_nullable='NO' AND column_default='b''0''') OR (column_name='creator' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=64) OR (column_name='create_time' AND data_type='datetime' AND is_nullable='NO') OR (column_name='updater' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=64) OR (column_name='update_time' AND data_type='datetime' AND is_nullable='NO') OR (column_name='deleted' AND data_type='bit' AND is_nullable='NO' AND column_default='b''0''') OR (column_name='tenant_id' AND data_type='bigint' AND is_nullable='NO' AND column_default='0'))) <> 10 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V290 zsjos_exam_calendar_note_image column contract mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM (SELECT index_name FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_exam_calendar_note_image' AND non_unique=0 GROUP BY index_name
      HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) IN ('id','tenant_id,file_id')) required_keys) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V290 zsjos_exam_calendar_note_image unique index mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V290','Exam calendar rich-text note',SHA2('V290__exam_calendar_note.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V290');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V290','Exam calendar rich-text note',SHA2('V290__exam_calendar_note.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V290');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v290_apply();
DROP PROCEDURE zsjos_v290_apply;
