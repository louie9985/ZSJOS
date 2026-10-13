-- UTF-8. V313: expand Zhongshijian notice content from TEXT to MEDIUMTEXT.
-- Upgrade: after Core V312 in both ledgers; fresh: bootstrap then ordered migrations.
-- Renumbered from the test-only notice V295; never relabel existing database ledgers.
-- Scope: system_notice.content across all tenants, and this migration's two ledger rows.
-- Preserve content, charset, collation, nullability, default, comment and all other columns.
-- Repeat/recovery: inspect actual structure even when a V313 marker exists; skip ALTER
-- only when already MEDIUMTEXT. Failure propagates; both ledger writes commit together
-- after schema verification. A partially committed ALTER is recovered by replay.
-- Back up system_notice and both ledgers first. DDL commits implicitly and may briefly
-- block writes. Retain the wider column on rollback; narrowing can truncate new content.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v313_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v313_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND engine='InnoDB' AND table_name IN
      ('system_notice','zsjos_schema_version','zsjos_module_schema_version')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V313 requires notice table and transactional ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V312')
      OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V312') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V313 requires Core V312 in both ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='system_notice' AND column_name='content' AND data_type IN ('text','mediumtext')
      AND is_nullable='NO' AND column_default IS NULL AND extra=''
      AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci'
      AND HEX(column_comment)='E585ACE5918AE58685E5AEB9') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V313 notice content contract mismatch';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='system_notice' AND column_name='content' AND data_type='text') THEN
    ALTER TABLE system_notice MODIFY COLUMN content MEDIUMTEXT
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告内容';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='system_notice' AND column_name='content' AND data_type='mediumtext'
      AND character_octet_length=16777215 AND is_nullable='NO' AND column_default IS NULL
      AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci'
      AND HEX(column_comment)='E585ACE5918AE58685E5AEB9') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V313 content expansion postcondition failed';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V313','Notice content capacity',SHA2('V313__notice_content_capacity.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V313');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V313','Notice content capacity',SHA2('V313__notice_content_capacity.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V313');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v313_apply();
DROP PROCEDURE zsjos_v313_apply;
