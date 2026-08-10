-- ZSJOS 用户关系场景管理
-- 变更范围：新增场景定义表、初始化派单场景、修正工作台路由、新增通用管理菜单。
-- 可重复执行；不删除或重建 zsjos_user_relation / zsjos_user_relation_log 数据。
-- 回退方式：停用新增菜单和场景。场景若已产生关系或日志，不应物理删除。

CREATE TABLE IF NOT EXISTS `zsjos_user_relation_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '场景编号',
  `name` varchar(100) NOT NULL COMMENT '场景名称',
  `code` varchar(64) NOT NULL COMMENT '场景编码',
  `source_label` varchar(50) NOT NULL COMMENT '来源用户称谓',
  `target_label` varchar(50) NOT NULL COMMENT '目标用户称谓',
  `source_post_code` varchar(64) NOT NULL COMMENT '来源岗位编码',
  `target_post_code` varchar(64) NOT NULL COMMENT '目标岗位编码',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 用户关系场景表';

INSERT INTO `zsjos_user_relation_scene`
  (`name`, `code`, `source_label`, `target_label`, `source_post_code`, `target_post_code`,
   `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '客资指定派单', 'lead_specified_assignment', '新媒体员工', '销售专员',
       'new_media_operator', 'sales_specialist', 0,
       '新媒体员工可将客资指定派给显式绑定的销售专员',
       '1', NOW(), '1', NOW(), b'0', tenant.id
FROM system_tenant tenant
WHERE tenant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM zsjos_user_relation_scene scene
    WHERE scene.tenant_id = tenant.id
      AND scene.code = 'lead_specified_assignment'
  );

-- 工作台菜单同时服务 React Workbench 与 Vue Admin 工作台，子路径必须包含 leads/。
UPDATE system_menu
SET path = 'leads/assignment-relations',
    name = '派单关系配置',
    component = 'zsjos/leadAssignment/index',
    component_name = 'ZsjosLeadAssignment',
    updater = '1',
    update_time = NOW()
WHERE permission = 'zsjos:lead-assignment:query'
  AND deleted = b'0';

SET @operator = '1';
SET @system_parent_id = (
  SELECT id FROM system_menu
  WHERE path = '/system' AND type = 1 AND deleted = b'0' LIMIT 1
);
SET @scene_menu_id = (
  SELECT id FROM system_menu
  WHERE permission = 'zsjos:user-relation-scene:query' AND type = 2 AND deleted = b'0' LIMIT 1
);
SET @scene_menu_id = IFNULL(@scene_menu_id, (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu));

INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @scene_menu_id, '用户关系场景', 'zsjos:user-relation-scene:query', 2, 80,
       @system_parent_id, 'user-relation', 'ep:share',
       'zsjos/userRelation/index', 'ZsjosUserRelationScene',
       0, b'1', b'1', b'1', @operator, NOW(), @operator, NOW(), b'0'
WHERE @system_parent_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM system_menu
    WHERE permission = 'zsjos:user-relation-scene:query' AND deleted = b'0'
  );

SET @permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @permission_id, '新增关系场景', 'zsjos:user-relation-scene:create', 3, 1,
       @scene_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE permission = 'zsjos:user-relation-scene:create' AND deleted = b'0'
);

SET @permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @permission_id, '修改关系场景', 'zsjos:user-relation-scene:update', 3, 2,
       @scene_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE permission = 'zsjos:user-relation-scene:update' AND deleted = b'0'
);

SET @permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @permission_id, '删除关系场景', 'zsjos:user-relation-scene:delete', 3, 3,
       @scene_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE permission = 'zsjos:user-relation-scene:delete' AND deleted = b'0'
);

SET @permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @permission_id, '查询关系数据', 'zsjos:user-relation:query', 3, 4,
       @scene_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE permission = 'zsjos:user-relation:query' AND deleted = b'0'
);

SET @permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @permission_id, '配置关系数据', 'zsjos:user-relation:update', 3, 5,
       @scene_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE permission = 'zsjos:user-relation:update' AND deleted = b'0'
);

SET @permission_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM system_menu);
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT @permission_id, '查询关系日志', 'zsjos:user-relation:log-query', 3, 6,
       @scene_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       @operator, NOW(), @operator, NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE permission = 'zsjos:user-relation:log-query' AND deleted = b'0'
);

-- 超级管理员默认拥有通用管理菜单和全部按钮。
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, @operator, NOW(), @operator, NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu ON menu.permission IN (
  'zsjos:user-relation-scene:query',
  'zsjos:user-relation-scene:create',
  'zsjos:user-relation-scene:update',
  'zsjos:user-relation-scene:delete',
  'zsjos:user-relation:query',
  'zsjos:user-relation:update',
  'zsjos:user-relation:log-query'
) AND menu.deleted = b'0'
WHERE role.code = 'super_admin' AND role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu relation
    WHERE relation.role_id = role.id
      AND relation.menu_id = menu.id
      AND relation.deleted = b'0'
  );
