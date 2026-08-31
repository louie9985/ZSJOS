-- ZSJOS 课程 SPU/SKU 与客资价格快照增量脚本。
-- 依赖：zsjos_product_configuration.sql、zsjos_product_hierarchy.sql。
-- 本脚本不初始化业务课程，不删除历史数据，可重复执行；上线前需先备份并在测试库验证。

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `subtitle` varchar(200) DEFAULT NULL COMMENT ''课程副标题''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'subtitle'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `description` longtext DEFAULT NULL COMMENT ''课程详情''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'description'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `target_audience` varchar(500) DEFAULT NULL COMMENT ''适用人群''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'target_audience'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `study_duration` varchar(100) DEFAULT NULL COMMENT ''学习时长''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'study_duration'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `study_mode` varchar(100) DEFAULT NULL COMMENT ''学习方式''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'study_mode'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `cover_image` varchar(1024) DEFAULT NULL COMMENT ''课程封面''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'cover_image'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_product_attr` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL COMMENT '课程SPU编号',
  `attr_key` varchar(64) NOT NULL COMMENT '稳定属性键',
  `attr_name` varchar(50) NOT NULL COMMENT '属性名称',
  `required` bit(1) NOT NULL DEFAULT b'1',
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_spu_attr_key` (`tenant_id`, `spu_id`, `attr_key`, `deleted`),
  KEY `idx_tenant_spu_attr_name` (`tenant_id`, `spu_id`, `attr_name`, `deleted`),
  KEY `idx_tenant_spu_sort` (`tenant_id`, `spu_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS SPU销售属性';

CREATE TABLE IF NOT EXISTS `zsjos_product_attr_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attr_id` bigint NOT NULL,
  `value` varchar(100) NOT NULL,
  `label` varchar(100) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_attr_value` (`tenant_id`, `attr_id`, `value`, `deleted`),
  KEY `idx_tenant_attr_sort` (`tenant_id`, `attr_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS SPU销售属性值';

CREATE TABLE IF NOT EXISTS `zsjos_product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL,
  `sku_ref` varchar(64) NOT NULL,
  `sku_name` varchar(200) NOT NULL,
  `attr_values_json` json NOT NULL,
  `attr_values_hash` varchar(64) NOT NULL,
  `price` decimal(10,2) NOT NULL DEFAULT 0.00,
  `status` tinyint NOT NULL DEFAULT 0,
  `sort` int NOT NULL DEFAULT 0,
  `remark` varchar(1000) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_sku_ref` (`tenant_id`, `sku_ref`),
  KEY `idx_tenant_spu_attr_hash` (`tenant_id`, `spu_id`, `attr_values_hash`, `deleted`),
  KEY `idx_tenant_spu_status_sort` (`tenant_id`, `spu_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 课程SKU';

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `spu_ref` varchar(64) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'spu_ref'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `spu_name_snapshot` varchar(200) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'spu_name_snapshot'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `sku_ref` varchar(64) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'sku_ref'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `sku_name_snapshot` varchar(200) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'sku_name_snapshot'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `selected_attr_values_json` json DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'selected_attr_values_json'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `price_snapshot` decimal(10,2) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'price_snapshot'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `spu_unknown` bit(1) NOT NULL DEFAULT b''0''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'spu_unknown'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `sku_unknown` bit(1) NOT NULL DEFAULT b''0''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'sku_unknown'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @menu_id = (SELECT id FROM system_menu WHERE permission = 'zsjos:product:query' AND deleted = b'0' LIMIT 1);
SET @button_id = (SELECT COALESCE(MAX(id), 0) FROM system_menu);
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id + ROW_NUMBER() OVER (ORDER BY permission_sort), permission_name, permission_code, 3, permission_sort, @menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM (
  SELECT 'SKU查询' permission_name, 'zsjos:product:sku-query' permission_code, 20 permission_sort UNION ALL
  SELECT 'SKU新增', 'zsjos:product:sku-create', 21 UNION ALL SELECT 'SKU编辑', 'zsjos:product:sku-update', 22 UNION ALL
  SELECT 'SKU删除', 'zsjos:product:sku-delete', 23 UNION ALL SELECT 'SKU状态', 'zsjos:product:sku-status', 24 UNION ALL
  SELECT '属性查询', 'zsjos:product:attr-query', 25 UNION ALL SELECT '属性编辑', 'zsjos:product:attr-update', 26
) permission_seed
WHERE @menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = permission_seed.permission_code AND deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, '1', NOW(), '1', NOW(), b'0', role.tenant_id
FROM system_role role JOIN system_menu menu ON menu.permission IN ('zsjos:product:sku-query','zsjos:product:sku-create','zsjos:product:sku-update','zsjos:product:sku-delete','zsjos:product:sku-status','zsjos:product:attr-query','zsjos:product:attr-update') AND menu.deleted = b'0'
WHERE role.code = 'super_admin' AND role.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = role.id AND rm.menu_id = menu.id AND rm.deleted = b'0');
