-- V003: seed EAM dictionary types and options, plus the default asset-code rule.
-- Module: eam. Depends on: V001 (EAM tables) and Core system_dict_type / system_dict_data.
-- Data scope: 7 dictionary types, 36 dictionary options, 1 global asset-code rule.
--             Options are seeded as a starting point; administrators may add, edit or delete them.
-- Repeatability: guarded by NOT EXISTS on the dictionary type / value pair; re-running inserts nothing.
--                Existing administrator edits are never overwritten.
-- Rollback limitation: deleting a dictionary option that assets already reference leaves those
--                      assets showing a raw code. Disable the option instead of deleting it.

DROP PROCEDURE IF EXISTS `eam_v003_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v003_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DO RELEASE_LOCK('eam:migration:V003');
    RESIGNAL;
  END;

  SELECT GET_LOCK('eam:migration:V003', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V003 blocked: could not acquire migration lock';
  END IF;

  START TRANSACTION;

  -- ========== 字典类型 ==========
  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 资产状态','eam_asset_status',0,'资产生命周期状态','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_asset_status' AND `deleted`=b'0');

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 资产来源','eam_asset_source',0,'资产取得方式','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_asset_source' AND `deleted`=b'0');

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 流转类型','eam_transfer_type',0,'资产流转单类型','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_transfer_type' AND `deleted`=b'0');

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 流转单状态','eam_transfer_status',0,'资产流转单状态','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_transfer_status' AND `deleted`=b'0');

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 自定义字段类型','eam_field_type',0,'分类自定义字段的控件类型','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_field_type' AND `deleted`=b'0');

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 盘点结果','eam_inventory_result',0,'盘点明细结果','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_inventory_result' AND `deleted`=b'0');

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 报废原因','eam_scrap_reason',0,'资产报废原因分类','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',NULL
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='eam_scrap_reason' AND `deleted`=b'0');

  -- ========== 资产状态 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'闲置','0','eam_asset_status',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='0' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'在用','1','eam_asset_status',0,'success','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'借出','2','eam_asset_status',0,'warning','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'维修中','3','eam_asset_status',0,'warning','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='3' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 5,'待报废','4','eam_asset_status',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='4' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 6,'已报废','5','eam_asset_status',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='5' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7,'已丢失','6','eam_asset_status',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='6' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 8,'已冻结','7','eam_asset_status',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_status' AND `value`='7' AND `deleted`=b'0');

  -- ========== 资产来源 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'采购','1','eam_asset_source',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_source' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'自建','2','eam_asset_source',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_source' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'受赠','3','eam_asset_source',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_source' AND `value`='3' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'调入','4','eam_asset_source',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_source' AND `value`='4' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 5,'其他','5','eam_asset_source',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_asset_source' AND `value`='5' AND `deleted`=b'0');

  -- ========== 流转类型 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'领用','1','eam_transfer_type',0,'success','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_type' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'退还','2','eam_transfer_type',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_type' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'借用','3','eam_transfer_type',0,'warning','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_type' AND `value`='3' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'归还','4','eam_transfer_type',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_type' AND `value`='4' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 5,'调拨','5','eam_transfer_type',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_type' AND `value`='5' AND `deleted`=b'0');

  -- ========== 流转单状态 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'审批中','0','eam_transfer_status',0,'warning','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_status' AND `value`='0' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'已生效','1','eam_transfer_status',0,'success','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_status' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'已驳回','2','eam_transfer_status',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_status' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'已取消','3','eam_transfer_status',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_transfer_status' AND `value`='3' AND `deleted`=b'0');

  -- ========== 自定义字段类型 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'单行文本','1','eam_field_type',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_field_type' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'多行文本','2','eam_field_type',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_field_type' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'数字','3','eam_field_type',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_field_type' AND `value`='3' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'日期','4','eam_field_type',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_field_type' AND `value`='4' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 5,'下拉选择','5','eam_field_type',0,'primary','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_field_type' AND `value`='5' AND `deleted`=b'0');

  -- ========== 盘点结果 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'未盘','0','eam_inventory_result',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_inventory_result' AND `value`='0' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'正常','1','eam_inventory_result',0,'success','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_inventory_result' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'位置不符','2','eam_inventory_result',0,'warning','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_inventory_result' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'未找到','3','eam_inventory_result',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_inventory_result' AND `value`='3' AND `deleted`=b'0');

  -- ========== 报废原因 ==========
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 1,'损坏无法修复','1','eam_scrap_reason',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_scrap_reason' AND `value`='1' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 2,'老旧淘汰','2','eam_scrap_reason',0,'warning','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_scrap_reason' AND `value`='2' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 3,'丢失','3','eam_scrap_reason',0,'danger','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_scrap_reason' AND `value`='3' AND `deleted`=b'0');
  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'其他','4','eam_scrap_reason',0,'info','','','migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='eam_scrap_reason' AND `value`='4' AND `deleted`=b'0');

  -- ========== 默认资产编号规则 ==========
  -- 无此行时资产创建会因找不到规则而失败，故随模块一并安装一条全局兜底规则。
  INSERT INTO `eam_code_rule`
  (`category_id`,`prefix`,`use_category_code`,`date_format`,`serial_length`,`separator`,`current_serial`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT NULL,'AS',b'1','yyyy',4,'-',0,'migration-eam-V003',NOW(),'migration-eam-V003',NOW(),b'0',1
  WHERE NOT EXISTS (SELECT 1 FROM `eam_code_rule` WHERE `category_id` IS NULL AND `tenant_id` = 1 AND `deleted` = b'0');

  COMMIT;
  DO RELEASE_LOCK('eam:migration:V003');
END$$
DELIMITER ;

CALL `eam_v003_apply`();
DROP PROCEDURE IF EXISTS `eam_v003_apply`;
