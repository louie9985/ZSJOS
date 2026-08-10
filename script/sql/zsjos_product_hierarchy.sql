-- ZSJOS 产品三级选择链增量脚本。
-- 上一版 zsjos_product_configuration.sql 已执行；本脚本只补充分类和快照字段，不迁移产品数据。

CREATE TABLE IF NOT EXISTS `zsjos_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父分类编号，一级为0',
  `level` tinyint NOT NULL COMMENT '分类层级，仅1或2',
  `name` varchar(100) NOT NULL COMMENT '分类名称',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_parent_name` (`tenant_id`, `parent_id`, `name`),
  KEY `idx_tenant_parent_level` (`tenant_id`, `parent_id`, `level`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 产品分类';

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_product` ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT ''所属二级分类''', 'SELECT 1')
           FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_product' AND column_name = 'category_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `level1_category_id` bigint DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'level1_category_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `level1_category_name_snapshot` varchar(100) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'level1_category_name_snapshot');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `level2_category_id` bigint DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'level2_category_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `level2_category_name_snapshot` varchar(100) DEFAULT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'level2_category_name_snapshot');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP PROCEDURE IF EXISTS `zsjos_product_add_index_if_absent`;
DELIMITER $$
CREATE PROCEDURE `zsjos_product_add_index_if_absent`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
                AND table_name = 'zsjos_product' AND index_name = 'idx_tenant_product_category') THEN
    ALTER TABLE `zsjos_product` ADD INDEX `idx_tenant_product_category` (`tenant_id`, `category_id`, `status`);
  END IF;
END$$
DELIMITER ;
CALL `zsjos_product_add_index_if_absent`();
DROP PROCEDURE IF EXISTS `zsjos_product_add_index_if_absent`;

SET @menu_id = (SELECT id FROM system_menu WHERE permission = 'zsjos:product:query' AND deleted = b'0' LIMIT 1);
SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '分类查询', 'zsjos:product-category:query', 3, 10, @menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product-category:query' AND deleted = b'0');
SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '分类新增', 'zsjos:product-category:create', 3, 11, @menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product-category:create' AND deleted = b'0');
SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '分类编辑', 'zsjos:product-category:update', 3, 12, @menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product-category:update' AND deleted = b'0');
SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '分类删除', 'zsjos:product-category:delete', 3, 13, @menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product-category:delete' AND deleted = b'0');
SET @button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @button_id, '分类状态', 'zsjos:product-category:status', 3, 14, @menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product-category:status' AND deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, '1', NOW(), '1', NOW(), b'0', role.tenant_id
FROM system_role role JOIN system_menu menu
  ON menu.permission IN ('zsjos:product-category:query', 'zsjos:product-category:create',
                         'zsjos:product-category:update', 'zsjos:product-category:delete', 'zsjos:product-category:status')
 AND menu.deleted = b'0'
WHERE role.code = 'super_admin' AND role.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
                  WHERE rm.role_id = role.id AND rm.menu_id = menu.id AND rm.deleted = b'0');
