-- ZSJOS 产品/课程配置增量脚本
-- 仅新增表、菜单和权限定义；不执行历史 Lead 迁移。

CREATE TABLE IF NOT EXISTS `zsjos_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品编号',
  `product_ref` varchar(128) NOT NULL COMMENT '稳定产品引用，创建后不可修改',
  `name` varchar(200) NOT NULL COMMENT '产品/课程名称',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_product_ref` (`tenant_id`, `product_ref`),
  KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 产品配置';

SET @zsjos_product_parent_id = (
  SELECT parent_id FROM system_menu
  WHERE permission = 'zsjos:lead:submit' AND deleted = b'0' LIMIT 1
);
SET @zsjos_product_menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible,
 keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @zsjos_product_menu_id, '产品配置', 'zsjos:product:query', 2, 25,
       COALESCE(@zsjos_product_parent_id, 0), 'product', 'ep:goods', 'zsjos/product/index', 'ZsjosProduct',
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product:query' AND deleted = b'0');

SET @zsjos_product_menu_id = (SELECT id FROM system_menu WHERE permission = 'zsjos:product:query' AND deleted = b'0' LIMIT 1);
SET @zsjos_product_button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible,
 keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @zsjos_product_button_id, '新增产品', 'zsjos:product:create', 3, 1, @zsjos_product_menu_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @zsjos_product_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product:create' AND deleted = b'0');

SET @zsjos_product_button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible,
 keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @zsjos_product_button_id, '编辑产品', 'zsjos:product:update', 3, 2, @zsjos_product_menu_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @zsjos_product_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product:update' AND deleted = b'0');

SET @zsjos_product_button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible,
 keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @zsjos_product_button_id, '删除产品', 'zsjos:product:delete', 3, 3, @zsjos_product_menu_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @zsjos_product_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product:delete' AND deleted = b'0');

SET @zsjos_product_button_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible,
 keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @zsjos_product_button_id, '修改产品状态', 'zsjos:product:status', 3, 4, @zsjos_product_menu_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @zsjos_product_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'zsjos:product:status' AND deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, '1', NOW(), '1', NOW(), b'0', role.tenant_id
FROM system_role role JOIN system_menu menu
  ON menu.permission IN ('zsjos:product:query', 'zsjos:product:create', 'zsjos:product:update',
                         'zsjos:product:delete', 'zsjos:product:status') AND menu.deleted = b'0'
WHERE role.code = 'super_admin' AND role.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
                  WHERE rm.role_id = role.id AND rm.menu_id = menu.id AND rm.deleted = b'0');
