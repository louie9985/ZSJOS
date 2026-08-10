-- Adds unified lead actions, valid notes, and opportunity-owned follow-ups.
-- Dependencies: V017, System dictionaries/menus, existing Lead and Opportunity tables.
-- Data scope: schema metadata, one empty dictionary type, one button permission, and derived role grants.
-- Repeatability: DDL and inserts are guarded; no lead, opportunity, order, or dictionary options are seeded.
-- Rollback limitation: new business records must be retained; disable the permission/dictionary on rollback.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='valid_description'),
  'SELECT 1','ALTER TABLE `zsjos_lead` ADD COLUMN `valid_description` varchar(2000) DEFAULT NULL COMMENT ''有效判定备注'' AFTER `qualified_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='category_after_label_snapshot' AND is_nullable='NO'),
  'ALTER TABLE `zsjos_lead_follow_up_record` MODIFY COLUMN `category_after_label_snapshot` varchar(100) NULL COMMENT ''跟进后客资分类标签快照，可清空''','SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='category_after' AND is_nullable='NO'),
  'ALTER TABLE `zsjos_lead_follow_up_record` MODIFY COLUMN `category_after` varchar(100) NULL COMMENT ''跟进后客资分类，可清空''','SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_opportunity_follow_up_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `opportunity_id` bigint NOT NULL, `lead_id` bigint NOT NULL,
  `operator_user_id` bigint NOT NULL, `owner_user_id_snapshot` bigint NOT NULL,
  `owner_dept_id_snapshot` bigint DEFAULT NULL, `method_value` varchar(100) NOT NULL,
  `method_label_snapshot` varchar(100) NOT NULL, `result_value` varchar(100) NOT NULL,
  `result_label_snapshot` varchar(100) NOT NULL, `category_before` varchar(100) DEFAULT NULL,
  `category_before_label_snapshot` varchar(100) DEFAULT NULL, `category_after` varchar(100) DEFAULT NULL,
  `category_after_label_snapshot` varchar(100) DEFAULT NULL, `remark` varchar(2000) DEFAULT NULL,
  `next_follow_up_at` datetime DEFAULT NULL, `occurred_at` datetime NOT NULL,
  `idempotency_key` varchar(64) NOT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_opportunity_follow_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_tenant_opportunity_occurred` (`tenant_id`,`opportunity_id`,`occurred_at`,`id`),
  KEY `idx_tenant_lead_occurred` (`tenant_id`,`lead_id`,`occurred_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售机会只追加跟进记录';

CREATE TABLE IF NOT EXISTS `zsjos_opportunity_follow_up_image` (
  `id` bigint NOT NULL AUTO_INCREMENT, `follow_up_record_id` bigint NOT NULL, `infra_file_id` bigint NOT NULL,
  `original_name` varchar(255) NOT NULL, `content_type` varchar(100) NOT NULL, `file_size` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT '0', `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_opportunity_record_file` (`tenant_id`,`follow_up_record_id`,`infra_file_id`),
  KEY `idx_tenant_opportunity_record_sort` (`tenant_id`,`follow_up_record_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售机会跟进图片快照';

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资有效快捷备注','zsjos_lead_valid_remark_template',0,'管理员维护；初始化不提供业务选项','migration-V018',NOW(),'migration-V018',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_valid_remark_template' AND `deleted`=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6809,'修改客资基础信息','zsjos:lead:update',3,14,6770,'','','',NULL,0,b'1',b'1',b'1','migration-V018',NOW(),'migration-V018',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6809,'migration-V018',NOW(),'migration-V018',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission='zsjos:lead-follow-up:create' AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6809 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V018','Add lead actions and opportunity follow-ups','lead-actions-opportunity-followups-v1');
