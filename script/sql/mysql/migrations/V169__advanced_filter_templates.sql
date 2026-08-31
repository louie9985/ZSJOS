-- V169: page-level advanced-filter templates.
-- Depends on V168 and the existing controlled advanced-filter contract.
-- Scope: creates an empty template table and administrator menu permissions only.
-- It does not seed business templates, dictionaries, roles, users, departments, leads or orders.
-- Repeatable: guarded by IF NOT EXISTS and stable menu IDs; records both schema-version registries.
-- Rollback limitation: keep the additive table while older application versions ignore it, or hide the menu in a later migration.

CREATE TABLE IF NOT EXISTS `zsjos_advanced_filter_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '高级筛选模板编号',
  `scene` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '高级筛选场景',
  `page_key` varchar(96) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '页面标识',
  `scope` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板范围：personal 或 system',
  `owner_user_id` bigint DEFAULT NULL COMMENT '个人模板所属用户；系统预置为空',
  `name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `filter_json` json NOT NULL COMMENT '结构化高级筛选条件树',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `default_template` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认模板',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_scene_page_scope` (`tenant_id`,`scene`,`page_key`,`scope`,`deleted`,`sort`),
  KEY `idx_tenant_owner_scene_page` (`tenant_id`,`owner_user_id`,`scene`,`page_key`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 高级筛选模板';

INSERT IGNORE INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (79990,'高级筛选预置','zsjos:advanced-filter-template:query',2,17,1,'zsjos/advanced-filter-template','ep:filter',
   'zsjos/advancedFilterTemplate/index','ZsjosAdvancedFilterTemplate','admin_only',0,b'1',b'1',b'1','V169',NOW(),'V169',NOW(),b'0'),
  (79991,'创建高级筛选预置','zsjos:advanced-filter-template:update',3,1,79990,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V169',NOW(),'V169',NOW(),b'0'),
  (79992,'修改高级筛选预置','zsjos:advanced-filter-template:update',3,2,79990,'','','',NULL,'admin_only',0,b'1',b'1',b'1','V169',NOW(),'V169',NOW(),b'0');

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79990),`updater`='V169',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'1','$') AND NOT JSON_CONTAINS(`menu_ids`,'79990','$');

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79991),`updater`='V169',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'79990','$') AND NOT JSON_CONTAINS(`menu_ids`,'79991','$');

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79992),`updater`='V169',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'79990','$') AND NOT JSON_CONTAINS(`menu_ids`,'79992','$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V169','Add page-level advanced-filter templates',
        SHA2('V169__advanced_filter_templates.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
        (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V169','Add page-level advanced-filter templates',
        SHA2('V169__advanced_filter_templates.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
