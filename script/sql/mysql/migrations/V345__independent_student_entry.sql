-- UTF-8. Upgrade/fresh-chain: after V344, before the matching backend and both frontends.
-- Scope: nullable media readiness, empty idempotency command table, one entry page/permission.
-- No historical backfill, business dictionary changes, or role assignments. Replays repair missing DDL.
-- MySQL DDL commits implicitly: retain existing data, inspect both ledgers and replay on partial failure.
-- Rollback limitation: revert applications first; retain column/command audit/menu metadata.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v345_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v345_apply()
BEGIN
 DECLARE root_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V344')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V344') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V345 requires V344 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V345 requires one class-management menu';
 END IF;
 SELECT parent_id INTO root_id FROM system_menu WHERE permission='zsjos:delivery-class:query' AND type=2 AND deleted=0;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_service_relation' AND column_name='media_ready') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN media_ready bit(1) DEFAULT NULL COMMENT '媒体业务就绪，空值沿用历史接收规则';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_student_entry_command (
  id bigint NOT NULL AUTO_INCREMENT,operator_user_id bigint NOT NULL,command_key varchar(64) NOT NULL,
  request_hash varchar(64) NOT NULL,person_id bigint DEFAULT NULL,service_relation_id bigint DEFAULT NULL,
  creator varchar(64) DEFAULT '',create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',tenant_id bigint NOT NULL,
  PRIMARY KEY(id),UNIQUE KEY uk_student_entry_command(tenant_id,operator_user_id,command_key)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员独立录入幂等命令';
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation'
  AND column_name='media_ready' AND column_type='bit(1)' AND is_nullable='YES')
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND column_name IN ('id','operator_user_id','command_key','request_hash','person_id','service_relation_id','creator','create_time','updater','update_time','deleted','tenant_id'))<>12
 OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci')
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND ((column_name IN ('id','operator_user_id','tenant_id') AND data_type='bigint' AND is_nullable='NO')
   OR (column_name IN ('person_id','service_relation_id') AND data_type='bigint' AND is_nullable='YES')
   OR (column_name IN ('command_key','request_hash') AND data_type='varchar' AND character_maximum_length=64 AND is_nullable='NO')))<>7
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND column_name='id' AND extra LIKE '%auto_increment%')
 OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command' AND index_name='PRIMARY') <> 'id'
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND index_name='PRIMARY') <> 1
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND index_name='uk_student_entry_command') <> 3
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_entry_command'
  AND index_name='uk_student_entry_command' AND non_unique=0
  AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='operator_user_id')
   OR (seq_in_index=3 AND column_name='command_key')))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V345 schema postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '学员录入','zsjos:student:create',2,80,root_id,'student-entry','ep:user-filled','zsjos/studentEntry/index','ZsjosStudentEntry',0,b'1',b'1',b'0','V345',NOW(),'V345',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student:create');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:create' AND type=2 AND parent_id=root_id
  AND path='student-entry' AND component='zsjos/studentEntry/index' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V345 student-entry menu conflict';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V345','学员直接新增',SHA2('V345__independent_student_entry.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V345');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V345','学员直接新增',SHA2('V345__independent_student_entry.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V345');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v345_apply();
DROP PROCEDURE zsjos_v345_apply;
