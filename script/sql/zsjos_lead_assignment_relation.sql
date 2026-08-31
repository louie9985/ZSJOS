-- 客资指定派单：用户对用户关系、审计日志与菜单权限

CREATE TABLE IF NOT EXISTS `zsjos_user_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关系编号',
  `scene` varchar(64) NOT NULL COMMENT '业务场景',
  `source_user_id` bigint NOT NULL COMMENT '来源用户编号',
  `target_user_id` bigint NOT NULL COMMENT '目标用户编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_scene_source_target` (`tenant_id`, `scene`, `source_user_id`, `target_user_id`),
  KEY `idx_tenant_scene_source_status` (`tenant_id`, `scene`, `source_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 用户业务关系表';

CREATE TABLE IF NOT EXISTS `zsjos_user_relation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  `scene` varchar(64) NOT NULL COMMENT '业务场景',
  `source_user_ids` varchar(2000) NOT NULL COMMENT '来源用户编号集合',
  `target_user_ids` varchar(2000) NOT NULL DEFAULT '' COMMENT '目标用户编号集合',
  `action_type` varchar(32) NOT NULL COMMENT '操作类型',
  `operator_user_id` bigint NOT NULL COMMENT '操作人用户编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_scene_create_time` (`tenant_id`, `scene`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 用户业务关系日志表';

-- 在“客资管理”目录下增加派单关系页面。脚本可重复执行。
SET @operator = '1';
SET @lead_parent_id = (
  SELECT parent_id FROM system_menu
  WHERE permission = 'zsjos:lead:submit' AND deleted = b'0' LIMIT 1
);
SET @relation_menu_id = (
  SELECT id FROM system_menu
  WHERE permission = 'zsjos:lead-assignment:query' AND type = 2 AND deleted = b'0' LIMIT 1
);
SET @relation_menu_id = IFNULL(@relation_menu_id, (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu));

INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @relation_menu_id, '派单关系配置', 'zsjos:lead-assignment:query', 2, 20,
       @lead_parent_id, 'assignment-relations', 'ep:connection',
       'zsjos/leadAssignment/index', 'ZsjosLeadAssignment',
       0, b'1', b'1', b'1', @operator, NOW(), @operator, NOW(), b'0'
WHERE @lead_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE id = @relation_menu_id AND deleted = b'0');

SET @update_menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @update_menu_id, '派单关系配置', 'zsjos:lead-assignment:update', 3, 1,
       @relation_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead-assignment:update' AND deleted = b'0'
);

SET @log_menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @log_menu_id, '派单关系日志', 'zsjos:lead-assignment:log-query', 3, 2,
       @relation_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead-assignment:log-query' AND deleted = b'0'
);

SET @all_menu_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @all_menu_id, '管理全部派单关系', 'zsjos:lead-assignment:manage-all', 3, 3,
       @relation_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE permission = 'zsjos:lead-assignment:manage-all' AND deleted = b'0'
);

-- 超级管理员默认拥有页面及全部按钮；部门负责人由管理员按需授权页面、配置和日志权限。
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, @operator, NOW(), @operator, NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu ON menu.permission IN (
  'zsjos:lead-assignment:query',
  'zsjos:lead-assignment:update',
  'zsjos:lead-assignment:log-query',
  'zsjos:lead-assignment:manage-all'
) AND menu.deleted = b'0'
WHERE role.code = 'super_admin' AND role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id = role.id AND rm.menu_id = menu.id AND rm.deleted = b'0'
  );
