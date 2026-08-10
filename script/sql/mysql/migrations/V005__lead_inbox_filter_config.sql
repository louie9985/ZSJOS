-- Adds tenant-owned, versioned lead-inbox filter schemes and their administration menu.
-- Dependencies: zsjos_lead, system_tenant, system_menu and system_role_menu must already exist; V004 must be applied first.
-- Execution order: create scheme/version tables, seed two published schemes per active tenant, register menu permissions,
-- grant them to roles that already manage lead-dispatch rules, then record V005.
-- Repeatability: tables, tenant/audience rows, versions, menus and grants use stable unique keys or NOT EXISTS guards.
-- Data scope: inserts configuration, immutable version snapshots and permission metadata only. Existing leads are not updated.
-- Rollback limitation: forward-only; disable the menu and publish a replacement scheme instead of deleting audit history.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

CREATE TABLE IF NOT EXISTS `zsjos_lead_inbox_filter_scheme` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '筛选方案编号',
  `audience` varchar(32) NOT NULL COMMENT '适用视角：submitter 或 owner',
  `name` varchar(100) NOT NULL COMMENT '方案名称',
  `draft_config_json` json NOT NULL COMMENT '当前草稿配置',
  `published_config_json` json DEFAULT NULL COMMENT '当前已发布配置',
  `published_version` int NOT NULL DEFAULT '0' COMMENT '当前发布版本',
  `published_by` bigint DEFAULT NULL COMMENT '最近发布人用户编号',
  `published_at` datetime DEFAULT NULL COMMENT '最近发布时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_audience` (`tenant_id`,`audience`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资收件箱筛选方案';

CREATE TABLE IF NOT EXISTS `zsjos_lead_inbox_filter_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '筛选方案版本编号',
  `scheme_id` bigint NOT NULL COMMENT '筛选方案编号', `version_no` int NOT NULL COMMENT '版本号',
  `config_json` json NOT NULL COMMENT '不可变发布配置快照',
  `published_by` bigint NOT NULL COMMENT '发布人用户编号', `published_at` datetime NOT NULL COMMENT '发布时间',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_scheme_version` (`tenant_id`,`scheme_id`,`version_no`),
  KEY `idx_tenant_scheme_published_at` (`tenant_id`,`scheme_id`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资收件箱筛选发布版本';

SET @submitter_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["submitted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"unassigned","label":"待分配","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["unassigned"]}]},{"key":"pending_acceptance","label":"待接单","sort":20,"enabled":true,"conditions":[{"field":"assignment_status","values":["pending_acceptance"]}]},{"key":"public_pool","label":"抢单池","sort":30,"enabled":true,"conditions":[{"field":"assignment_status","values":["public_pool"]}]},{"key":"owned","label":"已归属","sort":40,"enabled":true,"conditions":[{"field":"assignment_status","values":["owned"]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["valid","converted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"valid","label":"已判有效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["valid"]}]},{"key":"converted","label":"已进入转化","sort":20,"enabled":true,"conditions":[{"field":"status","values":["converted"]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["invalid"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["closed"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]}]}';
SET @owner_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["submitted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"owned","label":"已接单","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["owned"]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["valid","converted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"valid","label":"已判有效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["valid"]}]},{"key":"converted","label":"已进入转化","sort":20,"enabled":true,"conditions":[{"field":"status","values":["converted"]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["invalid"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["closed"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]}]}';

INSERT INTO `zsjos_lead_inbox_filter_scheme`
(`audience`,`name`,`draft_config_json`,`published_config_json`,`published_version`,`published_by`,`published_at`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'submitter','提交人视角',@submitter_filter,@submitter_filter,1,0,NOW(),0,'migration-V005',NOW(),'migration-V005',NOW(),b'0',t.id
FROM `system_tenant` t WHERE t.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_inbox_filter_scheme` s WHERE s.tenant_id=t.id AND s.audience='submitter' AND s.deleted=b'0');
INSERT INTO `zsjos_lead_inbox_filter_scheme`
(`audience`,`name`,`draft_config_json`,`published_config_json`,`published_version`,`published_by`,`published_at`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'owner','负责人视角',@owner_filter,@owner_filter,1,0,NOW(),0,'migration-V005',NOW(),'migration-V005',NOW(),b'0',t.id
FROM `system_tenant` t WHERE t.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_inbox_filter_scheme` s WHERE s.tenant_id=t.id AND s.audience='owner' AND s.deleted=b'0');

INSERT INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`,`version_no`,`config_json`,`published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT s.id,1,s.published_config_json,COALESCE(s.published_by,0),COALESCE(s.published_at,NOW()),
       'migration-V005',NOW(),'migration-V005',NOW(),b'0',s.tenant_id
FROM `zsjos_lead_inbox_filter_scheme` s WHERE s.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_inbox_filter_version` v
  WHERE v.tenant_id=s.tenant_id AND v.scheme_id=s.id AND v.version_no=1 AND v.deleted=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6773,'客资筛选方案','zsjos:lead-filter:query',2,95,6735,'lead-filter','ep:filter','zsjos/leadFilter/index','ZsjosLeadFilter',0,b'1',b'1',b'1','migration-V005',NOW(),'migration-V005',NOW(),b'0'),
(6774,'修改客资筛选方案','zsjos:lead-filter:update',3,1,6773,'','','',NULL,0,b'1',b'1',b'1','migration-V005',NOW(),'migration-V005',NOW(),b'0'),
(6775,'发布客资筛选方案','zsjos:lead-filter:publish',3,2,6773,'','','',NULL,0,b'1',b'1',b'1','migration-V005',NOW(),'migration-V005',NOW(),b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source.role_id, target.menu_id, 'migration-V005', NOW(), 'migration-V005', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead-rule:update' AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6773 menu_id UNION ALL SELECT 6774 UNION ALL SELECT 6775) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V005','Add configurable lead inbox filter schemes','lead-inbox-filter-config-v1');
