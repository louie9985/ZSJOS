-- UTF-8. V314: personal/course event colors and account-owned palettes.
-- Prerequisite: Core V313 in both ledgers; bootstrap then ordered migrations for fresh installs.
-- Scope: two nullable columns, empty palette table, two menu/button metadata rows. No role grants or backfill.
-- Repeat/recovery: validate actual structure even with markers; fail closed, replay after repairing prerequisites.
-- DDL commits implicitly. Back up event tables/menu/ledgers before upgrade; rollback application while retaining additions.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v314_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v314_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V313') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V313') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 requires Core V313';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB' AND table_name IN ('zsjos_personal_calendar_event','zsjos_course_calendar_event','system_menu','zsjos_schema_version','zsjos_module_schema_version'))<>5 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 requires calendar/menu tables and transactional ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND type=2 AND ((id=73605 AND permission='zsjos:personal-calendar:query') OR (id=73630 AND permission='zsjos:course-calendar:query')))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 calendar menu prerequisite mismatch';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu WHERE (id=73635 AND (permission<>'zsjos:personal-calendar:color-manage' OR parent_id<>73605 OR type<>3 OR deleted<>0)) OR (id=73636 AND (permission<>'zsjos:course-calendar:color-manage' OR parent_id<>73630 OR type<>3 OR deleted<>0)) OR (permission='zsjos:personal-calendar:color-manage' AND id<>73635) OR (permission='zsjos:course-calendar:color-manage' AND id<>73636)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 color permission metadata conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_personal_calendar_event' AND column_name='background_color') THEN
  ALTER TABLE zsjos_personal_calendar_event ADD background_color varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '日程自选底色';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_course_calendar_event' AND column_name='background_color') THEN
  ALTER TABLE zsjos_course_calendar_event ADD background_color varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程自选底色';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_calendar_palette (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, user_id bigint NOT NULL,
  calendar_type varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  colors_json json NOT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY(id), UNIQUE KEY uk_calendar_palette_owner(tenant_id,user_id,calendar_type)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工日历快捷色';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_personal_calendar_event','zsjos_course_calendar_event') AND column_name='background_color' AND data_type='varchar' AND character_maximum_length=7 AND is_nullable='YES' AND column_default IS NULL AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci')<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 background color structure mismatch';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_palette' AND ((column_name IN ('id','tenant_id','user_id') AND data_type='bigint' AND is_nullable='NO') OR (column_name='calendar_type' AND data_type='varchar' AND character_maximum_length=16 AND is_nullable='NO') OR (column_name='colors_json' AND data_type='json' AND is_nullable='NO') OR (column_name IN ('creator','updater') AND data_type='varchar' AND character_maximum_length=64) OR (column_name IN ('create_time','update_time') AND data_type='datetime' AND is_nullable='NO') OR (column_name='deleted' AND data_type='bit' AND is_nullable='NO')))<>10
 OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_palette' AND engine='InnoDB')
 OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_palette' AND index_name='uk_calendar_palette_owner' AND non_unique=0),'')<>'tenant_id,user_id,calendar_type'
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_palette' AND column_name='id' AND extra='auto_increment')
 OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_palette' AND index_name='PRIMARY'),'')<>'id' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 palette structure mismatch';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater)
 SELECT 73635,'管理个人日历快捷色','zsjos:personal-calendar:color-manage',3,5,73605,'','','',0,1,1,0,'V314','V314' WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=73635);
 INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater)
 SELECT 73636,'管理课程日历快捷色','zsjos:course-calendar:color-manage',3,5,73630,'','','',0,1,1,0,'V314','V314' WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=73636);
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND type=3 AND ((id=73635 AND parent_id=73605 AND permission='zsjos:personal-calendar:color-manage') OR (id=73636 AND parent_id=73630 AND permission='zsjos:course-calendar:color-manage')))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V314 permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V314','Calendar event colors and personal palettes',SHA2('V314__calendar_colors.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V314');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V314','Calendar event colors and personal palettes',SHA2('V314__calendar_colors.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V314');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v314_apply();
DROP PROCEDURE zsjos_v314_apply;
