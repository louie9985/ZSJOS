-- V004: EAM category configuration import, asset quantity snapshots, and idempotent import tracking.
-- Depends on: EAM V001-V003 and Core system_menu.
-- Data scope: schema metadata plus default snapshots for existing EAM categories/assets; no business rows are deleted.
-- Repeatability: every column/index/table/menu addition is guarded. Existing administrator data is preserved.
-- Rollback limitation: imported batch/row audit data and quantity snapshots must be retained once used; rollback is manual.

DROP PROCEDURE IF EXISTS `eam_v004_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v004_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DO RELEASE_LOCK('eam:migration:V004');
    RESIGNAL;
  END;

  SELECT GET_LOCK('eam:migration:V004', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V004 blocked: could not acquire migration lock';
  END IF;

  START TRANSACTION;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category' AND column_name='management_mode') THEN
    ALTER TABLE `eam_category` ADD COLUMN `management_mode` tinyint NOT NULL DEFAULT 1 COMMENT '管理模式：1 单件 2 批量' AFTER `status`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category' AND column_name='unit') THEN
    ALTER TABLE `eam_category` ADD COLUMN `unit` varchar(20) NOT NULL DEFAULT '个' COMMENT '默认计量单位' AFTER `management_mode`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category_field' AND column_name='admin_visible') THEN
    ALTER TABLE `eam_category_field` ADD COLUMN `admin_visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '管理端是否显示' AFTER `required`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category_field' AND column_name='collection_visible') THEN
    ALTER TABLE `eam_category_field` ADD COLUMN `collection_visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '员工收集表是否显示' AFTER `admin_visible`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category_field' AND column_name='collection_required') THEN
    ALTER TABLE `eam_category_field` ADD COLUMN `collection_required` bit(1) NOT NULL DEFAULT b'0' COMMENT '员工收集表是否必填' AFTER `collection_visible`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category_field' AND column_name='condition_rule') THEN
    ALTER TABLE `eam_category_field` ADD COLUMN `condition_rule` json DEFAULT NULL COMMENT '员工收集表条件规则' AFTER `collection_required`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='management_mode') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `management_mode` tinyint NOT NULL DEFAULT 1 COMMENT '管理模式快照：1 单件 2 批量' AFTER `category_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='quantity') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `quantity` int NOT NULL DEFAULT 1 COMMENT '资产数量' AFTER `management_mode`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='unit') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `unit` varchar(20) NOT NULL DEFAULT '个' COMMENT '计量单位快照' AFTER `quantity`;
  END IF;

  CREATE TABLE IF NOT EXISTS `eam_asset_import_batch` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '导入批次编号',
    `file_hash`     char(64)     NOT NULL COMMENT '文件 SHA-256',
    `file_name`     varchar(255) NOT NULL COMMENT '原始文件名',
    `sheet_name`    varchar(100) NOT NULL COMMENT '工作表名称',
    `total_rows`    int          NOT NULL DEFAULT 0 COMMENT '有效数据行数',
    `create_count`  int          NOT NULL DEFAULT 0 COMMENT '新增数量',
    `update_count`  int          NOT NULL DEFAULT 0 COMMENT '更新数量',
    `skip_count`    int          NOT NULL DEFAULT 0 COMMENT '跳过数量',
    `warning_count` int          NOT NULL DEFAULT 0 COMMENT '警告数量',
    `operator_id`   bigint                DEFAULT NULL COMMENT '导入操作人',
    `creator`       varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_eam_asset_import_batch_hash` (`tenant_id`, `file_hash`, `create_time`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产导入批次';

  CREATE TABLE IF NOT EXISTS `eam_asset_import_row` (
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '导入行编号',
    `batch_id`      bigint      NOT NULL COMMENT '导入批次编号',
    `file_hash`     char(64)    NOT NULL COMMENT '文件 SHA-256',
    `sheet_name`    varchar(100) NOT NULL COMMENT '工作表名称',
    `row_num`       int         NOT NULL COMMENT 'Excel 行号',
    `asset_id`      bigint      NOT NULL COMMENT '资产主键',
    `asset_code`    varchar(64) NOT NULL COMMENT '资产业务编号快照',
    `import_action` tinyint     NOT NULL COMMENT '导入动作：1 新增 2 更新 3 跳过',
    `creator`       varchar(64)          DEFAULT '' COMMENT '创建者',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)          DEFAULT '' COMMENT '更新者',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eam_asset_import_source` (`tenant_id`, `file_hash`, `sheet_name`, `row_num`, `deleted`),
    KEY `idx_eam_asset_import_row_batch` (`tenant_id`, `batch_id`),
    KEY `idx_eam_asset_import_row_asset` (`tenant_id`, `asset_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产导入行来源';

  INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT '分类配置导入','eam:category:import',3,9,7101,'','','',NULL,
         0,b'1',b'1',b'1','migration-eam-V004',NOW(),'migration-eam-V004',NOW(),b'0'
  WHERE EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=7101 AND `deleted`=b'0')
    AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='eam:category:import' AND `deleted`=b'0');

  COMMIT;
  DO RELEASE_LOCK('eam:migration:V004');
END$$
DELIMITER ;

CALL `eam_v004_apply`();
DROP PROCEDURE IF EXISTS `eam_v004_apply`;
