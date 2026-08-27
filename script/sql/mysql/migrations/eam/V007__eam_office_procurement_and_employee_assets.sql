-- V007: lightweight office procurement, shared inventory, employee holdings and HRM lifecycle tasks.
-- Depends on EAM V006 plus Core system_menu. All business rows remain empty.
-- Repeatability: columns, tables and menu rows are guarded; CREATE TABLE uses IF NOT EXISTS.
-- Rollback limitation: new business tables and category policy columns must be retained once data exists.
-- Creates the empty eam_purchase_payment_mode dictionary type. Business options are intentionally not seeded.

DROP PROCEDURE IF EXISTS `eam_v007_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v007_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE zsjos_root_id BIGINT DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    DO RELEASE_LOCK('eam:migration:V007');
    RESIGNAL;
  END;

  SELECT GET_LOCK('eam:migration:V007', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V007 lock unavailable'; END IF;

  IF EXISTS (
    SELECT 1
    FROM `system_menu` m
    JOIN (
      SELECT 7181 id,'eam:demand:menu' permission UNION ALL
      SELECT 7182,'eam:purchase:menu' UNION ALL
      SELECT 7183,'eam:stock:menu' UNION ALL
      SELECT 7184,'eam:demand:query' UNION ALL
      SELECT 7185,'eam:demand:create' UNION ALL
      SELECT 7186,'eam:purchase:query' UNION ALL
      SELECT 7187,'eam:purchase:create' UNION ALL
      SELECT 7188,'eam:purchase:receive' UNION ALL
      SELECT 7189,'eam:purchase:return' UNION ALL
      SELECT 7190,'eam:purchase:close' UNION ALL
      SELECT 7191,'eam:purchase:expense' UNION ALL
      SELECT 7192,'eam:stock:query' UNION ALL
      SELECT 7193,'eam:stock:allocate' UNION ALL
      SELECT 7194,'eam:stock:update' UNION ALL
      SELECT 7195,'eam:employee-asset:query' UNION ALL
      SELECT 7196,'eam:employee-asset:task' UNION ALL
      SELECT 7197,'eam:employee-asset:inspect' UNION ALL
      SELECT 7200,'eam:workbench:asset:query' UNION ALL
      SELECT 7201,'eam:workbench:demand:query' UNION ALL
      SELECT 7202,'eam:workbench:asset:sign' UNION ALL
      SELECT 7203,'eam:workbench:asset:return' UNION ALL
      SELECT 7204,'eam:workbench:asset:repair' UNION ALL
      SELECT 7205,'eam:workbench:demand:create'
    ) expected ON expected.id = m.id
    WHERE m.deleted = b'0' AND COALESCE(m.permission, '') <> expected.permission
  ) OR EXISTS (
    SELECT 1
    FROM `system_menu` m
    JOIN (
      SELECT 7181 id,'eam:demand:menu' permission UNION ALL
      SELECT 7182,'eam:purchase:menu' UNION ALL
      SELECT 7183,'eam:stock:menu' UNION ALL
      SELECT 7184,'eam:demand:query' UNION ALL
      SELECT 7185,'eam:demand:create' UNION ALL
      SELECT 7186,'eam:purchase:query' UNION ALL
      SELECT 7187,'eam:purchase:create' UNION ALL
      SELECT 7188,'eam:purchase:receive' UNION ALL
      SELECT 7189,'eam:purchase:return' UNION ALL
      SELECT 7190,'eam:purchase:close' UNION ALL
      SELECT 7191,'eam:purchase:expense' UNION ALL
      SELECT 7192,'eam:stock:query' UNION ALL
      SELECT 7193,'eam:stock:allocate' UNION ALL
      SELECT 7194,'eam:stock:update' UNION ALL
      SELECT 7195,'eam:employee-asset:query' UNION ALL
      SELECT 7196,'eam:employee-asset:task' UNION ALL
      SELECT 7197,'eam:employee-asset:inspect' UNION ALL
      SELECT 7200,'eam:workbench:asset:query' UNION ALL
      SELECT 7201,'eam:workbench:demand:query' UNION ALL
      SELECT 7202,'eam:workbench:asset:sign' UNION ALL
      SELECT 7203,'eam:workbench:asset:return' UNION ALL
      SELECT 7204,'eam:workbench:asset:repair' UNION ALL
      SELECT 7205,'eam:workbench:demand:create'
    ) expected ON expected.permission = m.permission
    WHERE m.deleted = b'0' AND m.id <> expected.id
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V007 blocked: menu ID or permission mapping conflicts with existing data';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='eam_category' AND column_name='delivery_mode') THEN
    ALTER TABLE `eam_category` ADD COLUMN `delivery_mode` tinyint DEFAULT NULL
      COMMENT '本级交付模式：1 实物入库 2 数字交付，NULL 继承父级' AFTER `management_mode`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='eam_category' AND column_name='custody_mode') THEN
    ALTER TABLE `eam_category` ADD COLUMN `custody_mode` tinyint DEFAULT NULL
      COMMENT '本级持有模式：1 消耗型 2 需归还型，NULL 继承父级' AFTER `delivery_mode`;
  END IF;

  CREATE TABLE IF NOT EXISTS `eam_demand` (
    `id` bigint NOT NULL AUTO_INCREMENT, `no` varchar(64) NOT NULL, `employee_id` bigint NOT NULL,
    `applicant_user_id` bigint NOT NULL, `applicant_dept_id` bigint DEFAULT NULL, `status` tinyint NOT NULL,
    `process_instance_id` varchar(64) DEFAULT NULL, `reason` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_demand_no` (`tenant_id`,`no`,`deleted`),
    KEY `idx_eam_demand_employee` (`tenant_id`,`employee_id`,`status`),
    KEY `idx_eam_demand_applicant` (`tenant_id`,`applicant_user_id`,`create_time`),
    KEY `idx_eam_demand_process` (`process_instance_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 办公资产需求';

  CREATE TABLE IF NOT EXISTS `eam_demand_item` (
    `id` bigint NOT NULL AUTO_INCREMENT, `demand_id` bigint NOT NULL, `name` varchar(200) NOT NULL,
    `category_id` bigint NOT NULL, `management_mode` tinyint NOT NULL, `delivery_mode` tinyint NOT NULL,
    `delivery_mode_label_snapshot` varchar(50) NOT NULL, `custody_mode` tinyint NOT NULL,
    `custody_mode_label_snapshot` varchar(50) NOT NULL, `quantity` int NOT NULL, `unit` varchar(20) NOT NULL,
    `ext_fields` json DEFAULT NULL, `ext_field_labels` json DEFAULT NULL,
    `ext_field_dict_types` json DEFAULT NULL,
    `reserved_quantity` int NOT NULL DEFAULT 0, `purchased_quantity` int NOT NULL DEFAULT 0,
    `fulfilled_quantity` int NOT NULL DEFAULT 0, `closed_quantity` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_demand_item_demand` (`tenant_id`,`demand_id`),
    KEY `idx_eam_demand_item_category` (`tenant_id`,`category_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 办公资产需求明细';

  CREATE TABLE IF NOT EXISTS `eam_purchase` (
    `id` bigint NOT NULL AUTO_INCREMENT, `no` varchar(64) NOT NULL, `status` tinyint NOT NULL,
    `payment_mode` int NOT NULL, `payment_mode_label_snapshot` varchar(100) NOT NULL,
    `supplier_name_snapshot` varchar(200) DEFAULT NULL, `supplier_contact_snapshot` varchar(200) DEFAULT NULL,
    `estimated_amount` decimal(14,2) DEFAULT NULL, `actual_amount` decimal(14,2) DEFAULT NULL,
    `expected_arrival_date` date DEFAULT NULL, `process_instance_id` varchar(64) DEFAULT NULL,
    `expense_status` tinyint NOT NULL DEFAULT 0, `expense_process_instance_id` varchar(64) DEFAULT NULL,
    `applicant_user_id` bigint NOT NULL, `file_urls` json DEFAULT NULL, `remark` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_purchase_no` (`tenant_id`,`no`,`deleted`),
    KEY `idx_eam_purchase_status` (`tenant_id`,`status`,`create_time`),
    KEY `idx_eam_purchase_process` (`process_instance_id`), KEY `idx_eam_purchase_expense_process` (`expense_process_instance_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 轻量办公采购单';

  CREATE TABLE IF NOT EXISTS `eam_purchase_item` (
    `id` bigint NOT NULL AUTO_INCREMENT, `purchase_id` bigint NOT NULL, `name` varchar(200) NOT NULL,
    `category_id` bigint NOT NULL, `management_mode` tinyint NOT NULL, `delivery_mode` tinyint NOT NULL,
    `delivery_mode_label_snapshot` varchar(50) NOT NULL, `custody_mode` tinyint NOT NULL,
    `custody_mode_label_snapshot` varchar(50) NOT NULL, `quantity` int NOT NULL,
    `received_quantity` int NOT NULL DEFAULT 0, `returned_quantity` int NOT NULL DEFAULT 0,
    `short_closed_quantity` int NOT NULL DEFAULT 0, `short_close_remark` varchar(500) DEFAULT NULL,
    `unit` varchar(20) NOT NULL, `unit_price` decimal(14,2) DEFAULT NULL,
    `ext_fields` json DEFAULT NULL, `ext_field_labels` json DEFAULT NULL,
    `ext_field_dict_types` json DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_purchase_item_purchase` (`tenant_id`,`purchase_id`),
    KEY `idx_eam_purchase_item_category` (`tenant_id`,`category_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 采购明细';

  CREATE TABLE IF NOT EXISTS `eam_purchase_source` (
    `id` bigint NOT NULL AUTO_INCREMENT, `purchase_item_id` bigint NOT NULL, `demand_item_id` bigint DEFAULT NULL,
    `quantity` int NOT NULL, `fulfilled_quantity` int NOT NULL DEFAULT 0, `closed_quantity` int NOT NULL DEFAULT 0,
    `target_employee_id` bigint DEFAULT NULL, `target_user_id` bigint DEFAULT NULL, `target_dept_id` bigint DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_purchase_source_item` (`tenant_id`,`purchase_item_id`),
    KEY `idx_eam_purchase_source_demand` (`tenant_id`,`demand_item_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 采购来源与分配映射';

  CREATE TABLE IF NOT EXISTS `eam_receipt` (
    `id` bigint NOT NULL AUTO_INCREMENT, `no` varchar(64) NOT NULL, `purchase_id` bigint NOT NULL,
    `type` tinyint NOT NULL COMMENT '1 入库/交付 2 供应商退货', `operator_user_id` bigint DEFAULT NULL,
    `operate_time` datetime NOT NULL, `file_urls` json DEFAULT NULL, `remark` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_receipt_no` (`tenant_id`,`no`,`deleted`),
    KEY `idx_eam_receipt_purchase` (`tenant_id`,`purchase_id`,`operate_time`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 入库与退货批次';

  CREATE TABLE IF NOT EXISTS `eam_receipt_item` (
    `id` bigint NOT NULL AUTO_INCREMENT, `receipt_id` bigint NOT NULL, `purchase_item_id` bigint NOT NULL,
    `stock_balance_id` bigint DEFAULT NULL, `quantity` int NOT NULL, `unit_price` decimal(14,2) DEFAULT NULL,
    `serial_numbers` json DEFAULT NULL, `actual_ext_fields` json DEFAULT NULL,
    `actual_ext_field_labels` json DEFAULT NULL, `actual_ext_field_dict_types` json DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_receipt_item_receipt` (`tenant_id`,`receipt_id`),
    KEY `idx_eam_receipt_item_purchase_item` (`tenant_id`,`purchase_item_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 入库与退货批次明细';

  CREATE TABLE IF NOT EXISTS `eam_stock_balance` (
    `id` bigint NOT NULL AUTO_INCREMENT, `name` varchar(200) NOT NULL, `category_id` bigint NOT NULL,
    `management_mode` tinyint NOT NULL, `delivery_mode` tinyint NOT NULL, `custody_mode` tinyint NOT NULL,
    `unit` varchar(20) NOT NULL, `attribute_signature` char(64) NOT NULL,
    `ext_fields` json DEFAULT NULL, `ext_field_labels` json DEFAULT NULL,
    `ext_field_dict_types` json DEFAULT NULL,
    `on_hand_quantity` int NOT NULL DEFAULT 0, `reserved_quantity` int NOT NULL DEFAULT 0,
    `frozen_quantity` int NOT NULL DEFAULT 0, `minimum_quantity` int NOT NULL DEFAULT 0,
    `next_expiry_date` date DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_stock_balance_match`
      (`tenant_id`,`category_id`,`unit`,`management_mode`,`delivery_mode`,`custody_mode`,`attribute_signature`,`deleted`),
    KEY `idx_eam_stock_balance_alert` (`tenant_id`,`minimum_quantity`,`next_expiry_date`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 全公司库存余额';

  CREATE TABLE IF NOT EXISTS `eam_stock_movement` (
    `id` bigint NOT NULL AUTO_INCREMENT, `stock_balance_id` bigint NOT NULL, `type` tinyint NOT NULL,
    `quantity` int NOT NULL, `before_quantity` int NOT NULL, `after_quantity` int NOT NULL,
    `business_type` varchar(50) NOT NULL, `business_id` bigint DEFAULT NULL, `operator_user_id` bigint DEFAULT NULL,
    `operate_time` datetime NOT NULL, `remark` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_stock_movement_balance` (`tenant_id`,`stock_balance_id`,`operate_time`),
    KEY `idx_eam_stock_movement_business` (`tenant_id`,`business_type`,`business_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 库存流水';

  CREATE TABLE IF NOT EXISTS `eam_stock_reservation` (
    `id` bigint NOT NULL AUTO_INCREMENT, `demand_item_id` bigint NOT NULL, `stock_balance_id` bigint DEFAULT NULL,
    `asset_id` bigint DEFAULT NULL, `target_employee_id` bigint NOT NULL, `target_user_id` bigint DEFAULT NULL,
    `quantity` int NOT NULL, `status` tinyint NOT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_stock_reservation_demand` (`tenant_id`,`demand_item_id`,`status`),
    KEY `idx_eam_stock_reservation_balance` (`tenant_id`,`stock_balance_id`,`status`),
    KEY `idx_eam_stock_reservation_asset` (`tenant_id`,`asset_id`,`status`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 库存预留';

  CREATE TABLE IF NOT EXISTS `eam_stock_holding` (
    `id` bigint NOT NULL AUTO_INCREMENT, `employee_id` bigint NOT NULL, `user_id` bigint DEFAULT NULL,
    `asset_id` bigint DEFAULT NULL, `stock_balance_id` bigint DEFAULT NULL, `name_snapshot` varchar(220) NOT NULL,
    `quantity` int NOT NULL, `custody_mode` tinyint NOT NULL, `status` tinyint NOT NULL,
    `signed_at` datetime DEFAULT NULL, `return_applied_at` datetime DEFAULT NULL,
    `return_inspected_at` datetime DEFAULT NULL, `return_result` tinyint DEFAULT NULL,
    `return_remark` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_stock_holding_employee` (`tenant_id`,`employee_id`,`status`),
    KEY `idx_eam_stock_holding_user` (`tenant_id`,`user_id`,`status`),
    KEY `idx_eam_stock_holding_asset` (`tenant_id`,`asset_id`,`status`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 员工批量或待签收持有明细';

  CREATE TABLE IF NOT EXISTS `eam_stock_reminder` (
    `id` bigint NOT NULL AUTO_INCREMENT, `scene` varchar(40) NOT NULL, `business_type` varchar(40) NOT NULL,
    `business_id` bigint NOT NULL, `due_date` date DEFAULT NULL, `reminder_date` date NOT NULL,
    `status` tinyint NOT NULL DEFAULT 0, `content` varchar(500) NOT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_stock_reminder`
      (`tenant_id`,`scene`,`business_type`,`business_id`,`reminder_date`,`deleted`),
    KEY `idx_eam_stock_reminder_status` (`tenant_id`,`status`,`due_date`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 低库存与数字资产到期提醒投影';

  CREATE TABLE IF NOT EXISTS `eam_employee_asset_task` (
    `id` bigint NOT NULL AUTO_INCREMENT, `event_key` varchar(160) NOT NULL, `latest_event_key` varchar(160) NOT NULL,
    `type` tinyint NOT NULL,
    `status` tinyint NOT NULL, `employee_id` bigint NOT NULL, `user_id` bigint DEFAULT NULL,
    `leader_user_id` bigint DEFAULT NULL, `employee_name_snapshot` varchar(100) NOT NULL,
    `dept_id_snapshot` bigint DEFAULT NULL, `process_instance_id` varchar(64) DEFAULT NULL,
    `demand_id` bigint DEFAULT NULL, `planned_leave_time` datetime DEFAULT NULL, `remark` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_employee_asset_task_event` (`tenant_id`,`event_key`,`deleted`),
    UNIQUE KEY `uk_eam_employee_asset_task_latest_event` (`tenant_id`,`latest_event_key`,`deleted`),
    KEY `idx_eam_employee_asset_task_employee` (`tenant_id`,`employee_id`,`type`,`status`),
    KEY `idx_eam_employee_asset_task_process` (`process_instance_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 员工配资、异动复核与离职结清任务';

  CREATE TABLE IF NOT EXISTS `eam_employee_asset_task_item` (
    `id` bigint NOT NULL AUTO_INCREMENT, `task_id` bigint NOT NULL, `asset_id` bigint DEFAULT NULL,
    `holding_id` bigint DEFAULT NULL, `asset_name_snapshot` varchar(220) NOT NULL, `action` tinyint DEFAULT NULL,
    `transfer_to_user_id` bigint DEFAULT NULL, `status` tinyint NOT NULL DEFAULT 0, `remark` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_eam_employee_asset_task_item_task` (`tenant_id`,`task_id`),
    KEY `idx_eam_employee_asset_task_item_asset` (`tenant_id`,`asset_id`),
    KEY `idx_eam_employee_asset_task_item_holding` (`tenant_id`,`holding_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 员工资产任务明细';

  INSERT INTO `system_dict_type`
    (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
  SELECT 'EAM 采购付款方式','eam_purchase_payment_mode',0,
         '办公采购付款方式；选项由管理员维护','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0',NULL
  WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type`='eam_purchase_payment_mode' AND `deleted`=b'0'
  );

  INSERT INTO `system_dict_data`
    (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 9,'已退供应商','8','eam_asset_status',0,'info','','采购退货后的终态',
         'migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
  WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type`='eam_asset_status' AND `value`='8' AND `deleted`=b'0'
  );

  -- Admin menus and buttons. No role grants are created.
  INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT 7181,'办公需求','eam:demand:menu',2,9,7100,'demand','fa-solid:clipboard-list','eam/demand/index','EamDemand',0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
  WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=7181)
    AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='eam:demand:menu' AND deleted=b'0');
  INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT 7182,'办公采购','eam:purchase:menu',2,10,7100,'purchase','fa-solid:cart-shopping','eam/purchase/index','EamPurchase',0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
  WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=7182)
    AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='eam:purchase:menu' AND deleted=b'0');
  INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT 7183,'库存余额','eam:stock:menu',2,11,7100,'stock','fa-solid:boxes','eam/stock/index','EamStock',0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
  WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=7183)
    AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='eam:stock:menu' AND deleted=b'0');

  INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT ids.id,ids.name,ids.permission,3,ids.sort,ids.parent_id,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
  FROM (
    SELECT 7184 id,'需求查询' name,'eam:demand:query' permission,1 sort,7181 parent_id UNION ALL
    SELECT 7185,'需求创建','eam:demand:create',2,7181 UNION ALL
    SELECT 7186,'采购查询','eam:purchase:query',1,7182 UNION ALL
    SELECT 7187,'采购创建','eam:purchase:create',2,7182 UNION ALL
    SELECT 7188,'采购入库','eam:purchase:receive',3,7182 UNION ALL
    SELECT 7189,'供应商退货','eam:purchase:return',4,7182 UNION ALL
    SELECT 7190,'少到关闭','eam:purchase:close',5,7182 UNION ALL
    SELECT 7191,'费用审批','eam:purchase:expense',6,7182 UNION ALL
    SELECT 7192,'库存查询','eam:stock:query',1,7183 UNION ALL
    SELECT 7193,'库存分配','eam:stock:allocate',2,7183 UNION ALL
    SELECT 7194,'最低库存维护','eam:stock:update',3,7183 UNION ALL
    SELECT 7195,'个人资产查询','eam:employee-asset:query',7,7181 UNION ALL
    SELECT 7196,'员工资产任务','eam:employee-asset:task',8,7181 UNION ALL
    SELECT 7197,'退还验收','eam:employee-asset:inspect',9,7181
  ) ids WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.id=ids.id)
    AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=ids.permission AND m.deleted=b'0');

  SELECT id INTO zsjos_root_id FROM system_menu WHERE parent_id=0 AND path='/zsjos' AND deleted=b'0' LIMIT 1;
  IF zsjos_root_id IS NOT NULL THEN
    INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
    SELECT 7200,'我的资产','eam:workbench:asset:query',2,90,zsjos_root_id,'my-assets','fa-solid:laptop','EamMyAssets','EamMyAssets',0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=7200) AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='eam:workbench:asset:query' AND deleted=b'0');
    INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
    SELECT 7201,'采购申请','eam:workbench:demand:query',2,91,zsjos_root_id,'asset-demands','fa-solid:clipboard-list','EamAssetDemands','EamAssetDemands',0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=7201) AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='eam:workbench:demand:query' AND deleted=b'0');
    INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
    SELECT ids.id,ids.name,ids.permission,3,ids.sort,ids.parent_id,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V007',NOW(),'migration-eam-V007',NOW(),b'0'
    FROM (
      SELECT 7202 id,'资产签收' name,'eam:workbench:asset:sign' permission,1 sort,7200 parent_id UNION ALL
      SELECT 7203,'资产退还','eam:workbench:asset:return',2,7200 UNION ALL
      SELECT 7204,'资产报修','eam:workbench:asset:repair',3,7200 UNION ALL
      SELECT 7205,'提交采购申请','eam:workbench:demand:create',1,7201
    ) ids WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.id=ids.id)
      AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=ids.permission AND m.deleted=b'0');
  END IF;

  DO RELEASE_LOCK('eam:migration:V007');
END$$
DELIMITER ;
CALL `eam_v007_apply`();
DROP PROCEDURE IF EXISTS `eam_v007_apply`;
