-- UTF-8. V319: homepage shared manual rest dates and management button only.
-- Prerequisites: V318 in both core ledgers; canonical homepage menu 6780.
-- Run after V318 through core migrations. No business rows or role grants are seeded.
-- Replay validates actual structure, even with version markers. Partial DDL persists;
-- repair failed prerequisites/structure and replay. Both ledgers commit only after checks.
-- Rollback: disable management in System; retain dates/table for forward recovery.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v319_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v319_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V318')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V318') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 requires Core V318';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB' AND table_name IN ('system_menu','zsjos_schema_version','zsjos_module_schema_version'))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 requires transactional menu and ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE id=6780 AND deleted=0 AND type=2 AND permission='zsjos:business-task:query') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 requires canonical homepage';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu WHERE (id=73650 AND (permission<>'zsjos:calendar-rest-day:manage' OR parent_id<>6780 OR type<>3 OR deleted<>0)) OR (permission='zsjos:calendar-rest-day:manage' AND id<>73650)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 rest day menu identity conflict';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_calendar_rest_day (
 id bigint NOT NULL AUTO_INCREMENT,
 tenant_id bigint NOT NULL,
 date date NOT NULL,
 rest bit(1) NOT NULL DEFAULT b'0',
 version bigint NOT NULL DEFAULT 0,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id), UNIQUE KEY uk_calendar_rest_day_date(tenant_id,date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页共享休息日';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' AND (
 (column_name IN ('id','tenant_id','version') AND data_type='bigint' AND is_nullable='NO') OR
 (column_name='date' AND data_type='date' AND is_nullable='NO') OR
 (column_name IN ('rest','deleted') AND column_type='bit(1)' AND is_nullable='NO' AND column_default="b'0'") OR
 (column_name IN ('creator','updater') AND data_type='varchar' AND character_maximum_length=64 AND character_set_name='utf8mb4') OR
 (column_name IN ('create_time','update_time') AND data_type='datetime' AND is_nullable='NO' AND LOWER(column_default)='current_timestamp')))<>10
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' AND column_name='id' AND extra='auto_increment')
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' AND column_name='version' AND column_default='0')
 OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci')
 OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' AND index_name='uk_calendar_rest_day_date' AND non_unique=0),'')<>'tenant_id,date'
 OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' AND index_name='PRIMARY'),'')<>'id' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 rest day structure mismatch';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT 73650,'管理休息日','zsjos:calendar-rest-day:manage',3,20,6780,'','','','native',0,b'1',b'1',b'0','V319','V319',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=73650);
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE id=73650 AND deleted=0 AND type=3 AND parent_id=6780 AND permission='zsjos:calendar-rest-day:manage') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V319 menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V319','Homepage shared rest dates',SHA2('V319__home_calendar_rest_days.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V319');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V319','Homepage shared rest dates',SHA2('V319__home_calendar_rest_days.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V319');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v319_apply();
DROP PROCEDURE zsjos_v319_apply;
