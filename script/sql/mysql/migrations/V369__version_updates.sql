-- UTF-8. Independent version-update feature. Upgrade after V368, before the new runtime.
-- Adds two empty tenant-owned tables and menu/button metadata only. No role grants or business seeds.
-- Repeatable, including recovery after partial DDL. DDL commits implicitly.
-- Rollback uses the preceding runtime while retaining tables and acknowledgement history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v369_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v369_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE read_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V368')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V368') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 requires V368 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 requires unique Workbench root';
 END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;
 CREATE TABLE IF NOT EXISTS zsjos_version_update (
  id bigint NOT NULL AUTO_INCREMENT,
  title varchar(100) NOT NULL,
  content mediumtext NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'DRAFT',
  target_user_ids mediumtext NOT NULL,
  attachment_ids text NOT NULL,
  inline_file_ids text NOT NULL,
  publisher_id bigint DEFAULT NULL,
  publisher_name varchar(100) DEFAULT NULL,
  publish_time datetime DEFAULT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 1,
  PRIMARY KEY(id), KEY idx_version_update_page(tenant_id,deleted,status,publish_time,id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 CREATE TABLE IF NOT EXISTS zsjos_version_update_recipient (
  id bigint NOT NULL AUTO_INCREMENT,
  update_id bigint NOT NULL, user_id bigint NOT NULL,
  user_name varchar(100) NOT NULL, dept_id bigint DEFAULT NULL, dept_name varchar(100) DEFAULT NULL,
  acknowledged_time datetime DEFAULT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 1,
  PRIMARY KEY(id), UNIQUE KEY uk_version_update_recipient(tenant_id,update_id,user_id),
  KEY idx_version_update_pending(tenant_id,user_id,deleted,acknowledged_time,update_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_version_update')<>16
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_version_update_recipient')<>13
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_version_update' AND column_name='content' AND data_type='mediumtext' AND character_set_name='utf8mb4')
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_version_update_recipient' AND column_name='acknowledged_time' AND data_type='datetime')
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_version_update_recipient' AND index_name='uk_version_update_recipient' AND non_unique=0)<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 incompatible version-update structure';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission LIKE 'zsjos:version-update:%' GROUP BY permission HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 duplicate permission identity';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:query' AND (deleted<>0 OR parent_id<>root_id OR type<>2 OR path<>'version-updates' OR component<>'zsjos-workbench'))
 OR EXISTS(SELECT 1 FROM system_menu WHERE parent_id=root_id AND path='version-updates' AND deleted=0 AND permission<>'zsjos:version-update:query') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 page identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '版本更新发布','zsjos:version-update:query',2,49,root_id,'version-updates','ep:document','zsjos-workbench','native',0,b'1',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:query');
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:version-update:query';
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:read' AND (deleted<>0 OR parent_id<>root_id OR type<>2 OR path<>'version-update-history' OR component<>'zsjos-workbench'))
 OR EXISTS(SELECT 1 FROM system_menu WHERE parent_id=root_id AND path='version-update-history' AND deleted=0 AND permission<>'zsjos:version-update:read') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 page identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '更新记录','zsjos:version-update:read',2,49,root_id,'version-update-history','ep:document','zsjos-workbench','native',0,b'0',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:read');
 SELECT id INTO read_id FROM system_menu WHERE permission='zsjos:version-update:read';
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:create' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '新建版本更新','zsjos:version-update:create',3,1,page_id,'',0,b'1',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:create');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:update' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '编辑版本更新','zsjos:version-update:update',3,2,page_id,'',0,b'1',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:update');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:publish' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '发布版本更新','zsjos:version-update:publish',3,3,page_id,'',0,b'1',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:publish');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:offline' AND (deleted<>0 OR type<>3 OR parent_id<>page_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '下线版本更新','zsjos:version-update:offline',3,4,page_id,'',0,b'1',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:offline');
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:acknowledge' AND (deleted<>0 OR type<>3 OR parent_id<>read_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 button identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '知悉版本更新','zsjos:version-update:acknowledge',3,5,read_id,'',0,b'1',b'1',b'1','V369','V369',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:version-update:acknowledge');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:version-update:%' AND deleted=0)<>7 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V369 permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V369','Independent version updates',SHA2('V369__version_updates.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V369');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V369','Independent version updates',SHA2('V369__version_updates.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V369');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v369_apply();
DROP PROCEDURE zsjos_v369_apply;
