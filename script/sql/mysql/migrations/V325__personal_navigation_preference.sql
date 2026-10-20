-- UTF-8. Upgrade after Core V324. Adds empty account/client preferences and two
-- permission definitions under System management. No role grants or business seeds.
-- Repeatable; partial DDL is retained and verified before version registration.
-- Rollback: revert application code; retain table/preferences and permission metadata.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v325_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v325_apply()
BEGIN
 DECLARE menu_parent BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V324')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V324') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V325 requires V324 in both ledgers';
 END IF;
 SELECT MIN(parent_id) INTO menu_parent FROM system_menu WHERE permission='system:menu:query' AND deleted=0;
 IF menu_parent IS NULL OR NOT EXISTS(SELECT 1 FROM system_menu WHERE id=menu_parent AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V325 requires system menu configuration parent';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_navigation_preference (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  user_id BIGINT NOT NULL,
  client VARCHAR(16) NOT NULL,
  preference_json MEDIUMTEXT NOT NULL COMMENT '个人导航同级顺序',
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY(id), UNIQUE KEY uk_user_client(tenant_id,user_id,client,deleted)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人导航设置';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_navigation_preference'
   AND column_name IN ('id','tenant_id','user_id','client','preference_json','creator','create_time','updater','update_time','deleted'))<>10
 OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='zsjos_navigation_preference' AND index_name='uk_user_client' AND non_unique=0)<>'tenant_id,user_id,client,deleted'
 OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_navigation_preference' AND index_name='uk_user_client' AND non_unique=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V325 preference schema postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '个人导航设置','zsjos:navigation-preference:query',3,98,menu_parent,'','','',0,1,1,1,'V325','V325',0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:navigation-preference:query');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '保存个人导航','zsjos:navigation-preference:update',3,99,menu_parent,'','','',0,1,1,1,'V325','V325',0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:navigation-preference:update');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:navigation-preference:query','zsjos:navigation-preference:update') AND deleted=0 AND type=3)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V325 permission metadata postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V325','个人导航顺序',SHA2('V325__personal_navigation_preference.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V325');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V325','个人导航顺序',SHA2('V325__personal_navigation_preference.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V325');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v325_apply();
DROP PROCEDURE zsjos_v325_apply;
