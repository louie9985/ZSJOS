-- UTF-8. 教务团队独立菜单与操作权限；不写角色授权和业务数据。
-- 前置：既有下属员工菜单、交付组织 business_domain 字段及两本版本账本。
-- 顺序：交付域结构升级完成后运行本文件。重复执行保留管理员名称/排序/启停。
-- 部分执行可重放；冲突或前置失败不登记版本。回滚通过菜单管理停用新入口，保留数据。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v326_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v326_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_org' AND column_name='business_domain') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 requires delivery organization domain schema';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:subordinate-sales:query' AND path='subordinate-sales' AND component='zsjos/subordinateSales/index' AND type=2)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 requires canonical subordinate employee menu';
 END IF;
 SELECT parent_id INTO root_id FROM system_menu WHERE deleted=0 AND permission='zsjos:subordinate-sales:query' AND path='subordinate-sales' AND component='zsjos/subordinateSales/index' AND type=2;
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE id=root_id AND path='/zsjos' AND type=1 AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 requires workbench menu root';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT 1 FROM system_menu WHERE deleted=0 AND (permission='zsjos:education-team:query' OR (parent_id=root_id AND path='education-team'))
  AND NOT(permission='zsjos:education-team:query' AND parent_id=root_id AND path='education-team' AND COALESCE(component,'')='zsjos/educationTeam/index' AND type=2)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 education team page conflicts with existing metadata';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '教务团队','zsjos:education-team:query',2,40,root_id,'education-team','ep:user','zsjos/educationTeam/index',0,1,1,1,'V326','V326',0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:education-team:query' AND deleted=0);
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:education-team:query' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 education team page is ambiguous';
 END IF;
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:education-team:query' AND deleted=0;
 IF EXISTS(SELECT 1 FROM system_menu WHERE deleted=0 AND permission IN('zsjos:education-team:account-status','zsjos:education-team:lead-transfer','zsjos:education-team:lead-restore','zsjos:education-team:lead-recycle','zsjos:education-team:lead-overturn-valid') AND (parent_id<>page_id OR type<>3)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 education operation conflicts with existing metadata';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT x.name,CONCAT('zsjos:education-team:',x.action),3,x.sort,page_id,'','','',0,1,1,1,'V326','V326',0
 FROM (SELECT '账号启停' name,'account-status' action,1 sort UNION ALL SELECT '教务客资转派','lead-transfer',2 UNION ALL SELECT '教务客资恢复','lead-restore',3 UNION ALL SELECT '教务客资回收','lead-recycle',4 UNION ALL SELECT '教务客资改判有效','lead-overturn-valid',5) x
 WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=CONCAT('zsjos:education-team:',x.action) AND m.deleted=0);
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND parent_id=page_id AND type=3 AND permission IN('zsjos:education-team:account-status','zsjos:education-team:lead-transfer','zsjos:education-team:lead-restore','zsjos:education-team:lead-recycle','zsjos:education-team:lead-overturn-valid'))<>5 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V326 operation postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V326','教务团队独立菜单',SHA2('V326__education_team_menu.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V326');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V326','教务团队独立菜单',SHA2('V326__education_team_menu.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V326');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v326_apply();
DROP PROCEDURE zsjos_v326_apply;
