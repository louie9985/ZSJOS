-- V147: add tenant-owned Workbench navigation layouts and immutable publish history.
-- Dependencies: V146, system_menu.workbench_render_mode, and the System tenant/menu/role tables.
-- Repeatable and non-destructive: no layout is pre-published, no role-menu grant is created,
-- and no existing application menu hierarchy or business row is changed.
-- Rollback limitation: published layout history is permanent; removal requires a separately
-- reviewed forward migration after confirming that no tenant still uses the projection.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v147_validate_prerequisites`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v147_validate_prerequisites`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V146'
  ) OR NOT EXISTS (
    SELECT 1 FROM `zsjos_module_schema_version`
    WHERE `module_code` = 'core' AND `version` = 'V146'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V147 requires V146 in both schema-version registries';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 79900
      AND (`type` <> 2 OR `parent_id` <> 1 OR `path` <> 'workbench-layout'
        OR NOT (`component` <=> 'system/workbenchLayout/index'))
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Menu ID 79900 is owned by another page';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE (`id` = 79901 AND (`type` <> 3 OR `parent_id` <> 79900
            OR `permission` <> 'system:workbench-layout:query'))
       OR (`id` = 79902 AND (`type` <> 3 OR `parent_id` <> 79900
            OR `permission` <> 'system:workbench-layout:update'))
       OR (`id` = 79903 AND (`type` <> 3 OR `parent_id` <> 79900
            OR `permission` <> 'system:workbench-layout:publish'))
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'A V147 Workbench layout menu ID is owned by another permission';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` NOT IN (79901, 79902, 79903)
      AND `permission` IN (
        'system:workbench-layout:query',
        'system:workbench-layout:update',
        'system:workbench-layout:publish'
      )
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Workbench layout permission already uses another menu ID';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` <> 79900
      AND `parent_id` = 1 AND `path` = 'workbench-layout'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'System Workbench layout route already uses another menu ID';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v147_validate_prerequisites`();
DROP PROCEDURE `zsjos_v147_validate_prerequisites`;

CREATE TABLE IF NOT EXISTS `system_workbench_layout` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '布局编号',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域：GLOBAL、ROLE',
  `scope_id` bigint NOT NULL COMMENT '全局为0，角色为角色编号',
  `draft_snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型校验后的草稿快照JSON',
  `draft_revision` int NOT NULL DEFAULT '0' COMMENT '草稿乐观锁版本',
  `draft_restored_from_version_id` bigint DEFAULT NULL COMMENT '草稿恢复来源版本编号',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布版本编号',
  `published_version_no` int DEFAULT NULL COMMENT '当前发布版本号',
  `published_enabled` bit(1) DEFAULT NULL COMMENT '当前角色覆盖是否启用',
  `published_priority` int DEFAULT NULL COMMENT '当前启用角色覆盖优先级',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_workbench_scope` (`tenant_id`,`scope_type`,`scope_id`),
  UNIQUE KEY `uk_tenant_workbench_priority` (`tenant_id`,`published_priority`),
  KEY `idx_workbench_published_version` (`tenant_id`,`published_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workbench导航布局';

CREATE TABLE IF NOT EXISTS `system_workbench_layout_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发布版本编号',
  `layout_id` bigint NOT NULL COMMENT '布局编号',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域：GLOBAL、ROLE',
  `scope_id` bigint NOT NULL COMMENT '全局为0，角色为角色编号',
  `version_no` int NOT NULL COMMENT '作用域内发布版本号',
  `snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不可变发布快照JSON',
  `enabled` bit(1) NOT NULL COMMENT '该版本角色覆盖是否启用',
  `priority` int DEFAULT NULL COMMENT '该版本角色覆盖优先级',
  `publish_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布说明',
  `restored_from_version_id` bigint DEFAULT NULL COMMENT '恢复来源版本编号',
  `publisher_user_id` bigint NOT NULL COMMENT '发布人用户编号',
  `publish_time` datetime NOT NULL COMMENT '发布时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_layout_version` (`tenant_id`,`layout_id`,`version_no`),
  KEY `idx_workbench_version_scope` (`tenant_id`,`scope_type`,`scope_id`,`version_no`),
  KEY `idx_workbench_version_published` (`tenant_id`,`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workbench导航布局发布历史';

DROP PROCEDURE IF EXISTS `zsjos_v147_validate_schema`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v147_validate_schema`()
BEGIN
  IF (
    SELECT COUNT(*) FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE() AND `table_name` = 'system_workbench_layout'
      AND `column_name` IN (
        'id','scope_type','scope_id','draft_snapshot_json','draft_revision',
        'draft_restored_from_version_id','published_version_id','published_version_no',
        'published_enabled','published_priority','creator','create_time','updater',
        'update_time','deleted','tenant_id'
      )
  ) <> 16 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'system_workbench_layout exists with an incompatible shape';
  END IF;

  IF (
    SELECT COUNT(*) FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE() AND `table_name` = 'system_workbench_layout_version'
      AND `column_name` IN (
        'id','layout_id','scope_type','scope_id','version_no','snapshot_json','enabled',
        'priority','publish_remark','restored_from_version_id','publisher_user_id',
        'publish_time','creator','create_time','updater','update_time','deleted','tenant_id'
      )
  ) <> 18 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'system_workbench_layout_version exists with an incompatible shape';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v147_validate_schema`();
DROP PROCEDURE `zsjos_v147_validate_schema`;

START TRANSACTION;

INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,
   `updater`,`update_time`,`deleted`)
SELECT 79900,'Workbench 菜单编排','',2,15,1,'workbench-layout','ep:operation',
       'system/workbenchLayout/index','SystemWorkbenchLayout','admin_only',0,b'1',b'1',b'1',
       'V147',NOW(),'V147',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 79900);

INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,
   `updater`,`update_time`,`deleted`)
SELECT seed.`id`,seed.`name`,seed.`permission`,3,seed.`sort`,79900,'','','',NULL,
       'admin_only',0,b'1',b'1',b'1','V147',NOW(),'V147',NOW(),b'0'
FROM (
  SELECT 79901 AS `id`,'查询 Workbench 菜单编排' AS `name`,
         'system:workbench-layout:query' AS `permission`,1 AS `sort`
  UNION ALL
  SELECT 79902,'更新 Workbench 菜单编排','system:workbench-layout:update',2
  UNION ALL
  SELECT 79903,'发布 Workbench 菜单编排','system:workbench-layout:publish',3
) seed
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing WHERE existing.`id` = seed.`id`);

UPDATE `system_menu`
SET `name`='Workbench 菜单编排',`permission`='',`type`=2,`sort`=15,`parent_id`=1,
    `path`='workbench-layout',`icon`='ep:operation',`component`='system/workbenchLayout/index',
    `component_name`='SystemWorkbenchLayout',`workbench_render_mode`='admin_only',`status`=0,
    `visible`=b'1',`keep_alive`=b'1',`always_show`=b'1',`updater`='V147',
    `update_time`=NOW(),`deleted`=b'0'
WHERE `id`=79900;

UPDATE `system_menu` menu
INNER JOIN (
  SELECT 79901 AS `id`,'查询 Workbench 菜单编排' AS `name`,
         'system:workbench-layout:query' AS `permission`,1 AS `sort`
  UNION ALL
  SELECT 79902,'更新 Workbench 菜单编排','system:workbench-layout:update',2
  UNION ALL
  SELECT 79903,'发布 Workbench 菜单编排','system:workbench-layout:publish',3
) seed ON seed.`id`=menu.`id`
SET menu.`name`=seed.`name`,menu.`permission`=seed.`permission`,menu.`type`=3,
    menu.`sort`=seed.`sort`,menu.`parent_id`=79900,menu.`path`='',menu.`icon`='',
    menu.`component`='',menu.`component_name`=NULL,menu.`workbench_render_mode`='admin_only',
    menu.`status`=0,menu.`visible`=b'1',menu.`keep_alive`=b'1',menu.`always_show`=b'1',
    menu.`updater`='V147',menu.`update_time`=NOW(),menu.`deleted`=b'0';

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79900),`updater`='V147',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'1','$')
  AND NOT JSON_CONTAINS(`menu_ids`,'79900','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79901),`updater`='V147',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'1','$')
  AND NOT JSON_CONTAINS(`menu_ids`,'79901','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79902),`updater`='V147',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'1','$')
  AND NOT JSON_CONTAINS(`menu_ids`,'79902','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79903),`updater`='V147',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'1','$')
  AND NOT JSON_CONTAINS(`menu_ids`,'79903','$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V147','Workbench navigation layout',
       SHA2('V147__workbench_navigation_layout.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V147');

INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V147','Workbench navigation layout',
       SHA2('V147__workbench_navigation_layout.sql',256),'baseline',NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code`='core' AND `version`='V147'
);

COMMIT;
