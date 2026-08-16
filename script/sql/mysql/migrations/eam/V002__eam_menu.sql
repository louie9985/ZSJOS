-- V002: register EAM menus and permissions.
-- Module: eam. Depends on: V001 (EAM tables) and Core system_menu.
-- Data scope: inserts 1 directory, 8 menus and 33 button permissions into system_menu.
--             Grants nothing to any role; administrators assign these menus themselves.
-- ID range: 7100-7199 is reserved for EAM. The script aborts if an ID in that range belongs
--           to a non-EAM menu, so a collision fails loudly instead of corrupting navigation.
-- Repeatability: every insert is guarded by NOT EXISTS on both id and permission.
-- Rollback limitation: if administrators already granted these menus to roles, deleting the rows
--                      also drops those grants. Prefer disabling (status = 1) after a rollback.

DROP PROCEDURE IF EXISTS `eam_v002_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v002_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DO RELEASE_LOCK('eam:migration:V002');
    RESIGNAL;
  END;

  SELECT GET_LOCK('eam:migration:V002', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V002 blocked: could not acquire migration lock';
  END IF;

  START TRANSACTION;

  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE `id` BETWEEN 7100 AND 7199
               AND (`permission` IS NULL OR `permission` NOT LIKE 'eam:%')
               AND `name` NOT IN ('资产管理','资产分类','资产台账','资产流转','资产盘点',
                                  '维修记录','报废管理','编号规则','统计报表')
               AND `deleted` = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V002 blocked: menu ID range 7100-7199 is used by another module';
  END IF;

  -- ========== 目录 ==========
  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7100,'资产管理','',1,80,0,'/eam','fa-solid:boxes-stacked','',NULL,
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7100);

  -- ========== 菜单 ==========
  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7101,'资产分类','',2,1,7100,'category','fa:sitemap','eam/category/index','EamCategory',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7101);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7102,'资产台账','',2,2,7100,'asset','fa-solid:laptop','eam/asset/index','EamAsset',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7102);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7103,'资产流转','',2,3,7100,'transfer','fa:exchange','eam/transfer/index','EamTransfer',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7103);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7104,'资产盘点','',2,4,7100,'inventory','fa:list-alt','eam/inventory/index','EamInventory',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7104);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7105,'维修记录','',2,5,7100,'repair','fa:wrench','eam/repair/index','EamRepair',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7105);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7106,'报废管理','',2,6,7100,'scrap','fa:trash','eam/scrap/index','EamScrap',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7106);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7107,'编号规则','',2,7,7100,'code-rule','fa:barcode','eam/codeRule/index','EamCodeRule',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7107);

  INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7108,'统计报表','',2,8,7100,'statistics','fa:bar-chart','eam/statistics/index','EamStatistics',
         0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7108);

  -- ========== 按钮权限：资产分类 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7110,'分类查询','eam:category:query',3,1,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7110) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7111,'分类创建','eam:category:create',3,2,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7111) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7112,'分类更新','eam:category:update',3,3,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7112) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category:update' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7113,'分类删除','eam:category:delete',3,4,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7113) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category:delete' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7114,'自定义字段查询','eam:category-field:query',3,5,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7114) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category-field:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7115,'自定义字段创建','eam:category-field:create',3,6,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7115) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category-field:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7116,'自定义字段更新','eam:category-field:update',3,7,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7116) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category-field:update' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7117,'自定义字段删除','eam:category-field:delete',3,8,7101,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7117) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category-field:delete' AND `deleted`=b'0');

  -- ========== 按钮权限：资产台账 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7120,'资产查询','eam:asset:query',3,1,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7120) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7121,'资产创建','eam:asset:create',3,2,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7121) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7122,'资产更新','eam:asset:update',3,3,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7122) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:update' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7123,'资产删除','eam:asset:delete',3,4,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7123) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:delete' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7124,'资产导出','eam:asset:export',3,5,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7124) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:export' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7125,'资产导入','eam:asset:import',3,6,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7125) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:import' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7126,'生成二维码','eam:asset:qrcode',3,7,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7126) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:asset:qrcode' AND `deleted`=b'0');

  -- ========== 按钮权限：资产流转 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7130,'流转查询','eam:transfer:query',3,1,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7130) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:transfer:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7131,'流转创建','eam:transfer:create',3,2,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7131) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:transfer:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7132,'流转审批','eam:transfer:update',3,3,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7132) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:transfer:update' AND `deleted`=b'0');

  -- ========== 按钮权限：资产盘点 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7140,'盘点查询','eam:inventory:query',3,1,7104,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7140) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:inventory:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7141,'盘点创建','eam:inventory:create',3,2,7104,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7141) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:inventory:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7142,'盘点录入','eam:inventory:update',3,3,7104,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7142) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:inventory:update' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7143,'盘点删除','eam:inventory:delete',3,4,7104,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7143) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:inventory:delete' AND `deleted`=b'0');

  -- ========== 按钮权限：维修记录 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7150,'维修查询','eam:repair:query',3,1,7105,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7150) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:repair:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7151,'送修','eam:repair:create',3,2,7105,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7151) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:repair:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7152,'维修完成','eam:repair:update',3,3,7105,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7152) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:repair:update' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7153,'维修删除','eam:repair:delete',3,4,7105,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7153) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:repair:delete' AND `deleted`=b'0');

  -- ========== 按钮权限：报废管理 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7160,'报废查询','eam:scrap:query',3,1,7106,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7160) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:scrap:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7161,'申请报废','eam:scrap:create',3,2,7106,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7161) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:scrap:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7162,'报废审批','eam:scrap:update',3,3,7106,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7162) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:scrap:update' AND `deleted`=b'0');

  -- ========== 按钮权限：编号规则 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7170,'规则查询','eam:code-rule:query',3,1,7107,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7170) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:code-rule:query' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7171,'规则创建','eam:code-rule:create',3,2,7107,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7171) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:code-rule:create' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7172,'规则更新','eam:code-rule:update',3,3,7107,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7172) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:code-rule:update' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7173,'规则删除','eam:code-rule:delete',3,4,7107,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7173) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:code-rule:delete' AND `deleted`=b'0');

  -- ========== 按钮权限：统计报表 ==========
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7180,'统计查询','eam:statistics:query',3,1,7108,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V002',NOW(),'migration-eam-V002',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7180) AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:statistics:query' AND `deleted`=b'0');

  COMMIT;
  DO RELEASE_LOCK('eam:migration:V002');
END$$
DELIMITER ;

CALL `eam_v002_apply`();
DROP PROCEDURE IF EXISTS `eam_v002_apply`;
