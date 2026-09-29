SET NAMES utf8mb4;
-- V284: additive System notice reading statistics, after Core V283 (including V181).
-- Scope: five columns only; no historical roster/profile backfill, role grants or business-row changes.
-- Fresh and deployed environments use the same order. Repeatable; partial DDL resumes forward.
-- DDL commits implicitly. Rollback application first; retain columns and historical records.
-- Shared DB execution requires authorization. Existing ledger checksums are never rewritten.
DROP PROCEDURE IF EXISTS zsjos_v284_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v284_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V283')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V283') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V284 requires Core V283';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_type='BASE TABLE' AND table_name IN ('system_notice','system_notice_recipient','system_notice_read')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V284 requires notice, recipient and read tables';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='recipient_snapshot_complete') THEN
    ALTER TABLE system_notice ADD COLUMN recipient_snapshot_complete bit(1) NOT NULL DEFAULT b'0' COMMENT '发布名单快照已完成';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='user_name_snapshot') THEN
    ALTER TABLE system_notice_recipient ADD COLUMN user_name_snapshot varchar(64) DEFAULT NULL COMMENT '发布时用户姓名';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='dept_id_snapshot') THEN
    ALTER TABLE system_notice_recipient ADD COLUMN dept_id_snapshot bigint DEFAULT NULL COMMENT '发布时部门编号';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='dept_name_snapshot') THEN
    ALTER TABLE system_notice_recipient ADD COLUMN dept_name_snapshot varchar(64) DEFAULT NULL COMMENT '发布时部门名称';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='profile_snapshot_complete') THEN
    ALTER TABLE system_notice_recipient ADD COLUMN profile_snapshot_complete bit(1) NOT NULL DEFAULT b'0' COMMENT '发布时资料快照已完成';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='recipient_snapshot_complete' AND column_type='bit(1)' AND is_nullable='NO') + (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='user_name_snapshot' AND column_type='varchar(64)' AND is_nullable='YES') + (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='dept_id_snapshot' AND column_type='bigint' AND is_nullable='YES') + (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='dept_name_snapshot' AND column_type='varchar(64)' AND is_nullable='YES') + (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_recipient' AND column_name='profile_snapshot_complete' AND column_type='bit(1)' AND is_nullable='NO')) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V284 snapshot column contract mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
       AND table_name IN ('zsjos_schema_version','zsjos_module_schema_version')) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V284 requires transactional version ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name='system_notice' AND column_name='recipient_snapshot_complete')
        OR (table_name='system_notice_recipient' AND column_name='profile_snapshot_complete'))
      AND column_default='b''0''') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V284 completion defaults must remain false';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V284','Notice recipient snapshots and reading statistics',SHA2('V284__notice_read_statistics.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V284');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V284','Notice recipient snapshots and reading statistics',SHA2('V284__notice_read_statistics.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V284');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v284_apply();
DROP PROCEDURE zsjos_v284_apply;
