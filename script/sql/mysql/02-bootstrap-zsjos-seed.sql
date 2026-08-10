-- ZSJOS baseline seed. No products, SKUs, leads, orders or other business rows.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_intended_product' AND column_name='product_ref' AND is_nullable='NO'),
  'ALTER TABLE `zsjos_lead_intended_product` MODIFY COLUMN `product_ref` varchar(128) NULL COMMENT ''兼容 SPU 稳定引用，未明确课程时为空''',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `zsjos_lead_assignment_rule`
(`code`, `name`, `strategy_type`, `config_json`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'default', '全公司轮询', 'global_round_robin', JSON_OBJECT('acceptTimeoutSeconds', 120, 'maxAttempts', 5),
       0, '1', NOW(), '1', NOW(), b'0', t.id
FROM `system_tenant` t
WHERE t.id = 1 AND t.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_assignment_rule` r
    WHERE r.tenant_id = t.id AND r.code = 'default' AND r.deleted = b'0'
  );

INSERT IGNORE INTO `zsjos_lead_assignment_cursor`
(`rule_id`, `last_sales_user_id`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, NULL, 0, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM `zsjos_lead_assignment_rule` r
WHERE r.tenant_id = 1 AND r.code = 'default' AND r.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_assignment_cursor` c
    WHERE c.rule_id = r.id AND c.tenant_id = r.tenant_id AND c.deleted = b'0'
  );

INSERT IGNORE INTO `zsjos_lead_follow_up_rule`
(`code`, `name`, `first_follow_up_timeout_minutes`, `qualification_timeout_minutes`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'default', '默认客资跟进与判定规则', 1440, 4320, 0, 0, '1', NOW(), '1', NOW(), b'0', t.id
FROM `system_tenant` t
WHERE t.id = 1 AND t.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_follow_up_rule` r
    WHERE r.tenant_id = t.id AND r.code = 'default' AND r.deleted = b'0'
  );

SET @submitter_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["submitted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"unassigned","label":"待分配","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["unassigned"]}]},{"key":"pending_acceptance","label":"待接单","sort":20,"enabled":true,"conditions":[{"field":"assignment_status","values":["pending_acceptance"]}]},{"key":"public_pool","label":"抢单池","sort":30,"enabled":true,"conditions":[{"field":"assignment_status","values":["public_pool"]}]},{"key":"owned","label":"已归属","sort":40,"enabled":true,"conditions":[{"field":"assignment_status","values":["owned"]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["valid","converted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"valid","label":"已判有效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["valid"]}]},{"key":"converted","label":"已进入转化","sort":20,"enabled":true,"conditions":[{"field":"status","values":["converted"]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["invalid"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["closed"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]}]}';
SET @owner_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["submitted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"owned","label":"已接单","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["owned"]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["valid","converted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"valid","label":"已判有效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["valid"]}]},{"key":"converted","label":"已进入转化","sort":20,"enabled":true,"conditions":[{"field":"status","values":["converted"]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["invalid"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["closed"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]}]}';

INSERT IGNORE INTO `zsjos_lead_inbox_filter_scheme`
(`audience`, `name`, `draft_config_json`, `published_config_json`, `published_version`, `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'submitter', '提交人视角', @submitter_filter, @submitter_filter, 1, 1, NOW(), 0, '1', NOW(), '1', NOW(), b'0', t.id
FROM `system_tenant` t WHERE t.id = 1 AND t.deleted = b'0';
INSERT IGNORE INTO `zsjos_lead_inbox_filter_scheme`
(`audience`, `name`, `draft_config_json`, `published_config_json`, `published_version`, `published_by`, `published_at`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'owner', '负责人视角', @owner_filter, @owner_filter, 1, 1, NOW(), 0, '1', NOW(), '1', NOW(), b'0', t.id
FROM `system_tenant` t WHERE t.id = 1 AND t.deleted = b'0';

INSERT IGNORE INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`, `version_no`, `config_json`, `published_by`, `published_at`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.id, 1, s.published_config_json, 1, s.published_at, '1', NOW(), '1', NOW(), b'0', s.tenant_id
FROM `zsjos_lead_inbox_filter_scheme` s WHERE s.tenant_id = 1 AND s.deleted = b'0';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source.role_id, target.menu_id, '1', NOW(), '1', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead-rule:update' AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6773 menu_id UNION ALL SELECT 6774 UNION ALL SELECT 6775) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, '1', NOW(), '1', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead:submit' AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6770 menu_id UNION ALL SELECT 6778) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, '1', NOW(), '1', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission IN ('zsjos:lead:claim','zsjos:lead:accept') AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6770 menu_id UNION ALL SELECT 6779) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, '1', NOW(), '1', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN `system_menu` source_menu ON source_menu.id=source.menu_id
  AND source_menu.permission='zsjos:lead:query-all' AND source_menu.deleted=b'0'
CROSS JOIN (SELECT 6770 menu_id UNION ALL SELECT 6778 UNION ALL SELECT 6779) target
WHERE source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V000_BASELINE', 'ZSJOS empty database baseline', 'bootstrap-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V001', 'Allow lead intended product without a product reference', 'lead-product-ref-nullable-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V002', 'Add read-only lead management menu and permissions', 'lead-management-menu-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V003', 'Split claim-pool menu and action permissions', 'claim-pool-dual-frontend-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V004', 'Reference Infra files for private-read lead attachments', 'lead-attachment-private-read-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V005', 'Add configurable lead inbox filter schemes', 'lead-inbox-filter-config-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V006', 'Add lead acceptance tasks and follow-up rule', 'lead-acceptance-follow-up-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V007', 'Split lead inbox into fixed submitter and owner routes', 'lead-inbox-fixed-audiences-v1');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6781,'1',NOW(),'1',NOW(),b'0',source.tenant_id FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission='zsjos:lead:query' AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6781 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6780,'1',NOW(),'1',NOW(),b'0',source.tenant_id FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission IN ('zsjos:lead:submit','zsjos:lead:query-submitted','zsjos:lead:query-owned','zsjos:lead:claim','zsjos:lead:accept') AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6780 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,target.menu_id,'1',NOW(),'1',NOW(),b'0',source.tenant_id FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission IN ('zsjos:lead:claim','zsjos:lead:accept') AND m.deleted=b'0'
CROSS JOIN (SELECT 6781 menu_id UNION ALL SELECT 6782) target WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=target.menu_id AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6809,'1',NOW(),'1',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission='zsjos:lead-follow-up:create' AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6809 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V008', 'Add lead follow-up records and today tasks', 'lead-follow-up-today-tasks-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V009', 'Add online round-robin dispatch preference', 'online-round-robin-dispatch-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V010', 'Add personal all and unread message-center menus', 'personal-message-center-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V011', 'Add configurable business notifications and durable message snapshots', 'configurable-business-notifications-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V014', 'Add lead qualification timing and suspension workflow', 'lead-qualification-suspension-v1');
INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V015', 'Add three-level lead appeal workflow', 'lead-three-level-appeal-v1');
INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V016', 'Complete default templates for registered lead notification scenes', 'complete-lead-notify-templates-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V017', 'Add lead-invalid quick remark template dictionary', 'lead-invalid-remark-template-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V018', 'Add lead actions and opportunity follow-ups', 'lead-actions-opportunity-followups-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V019', 'Normalize historical valid leads with initial opportunities', 'normalize-historical-valid-leads-v1');

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V020', 'Add unified schema migration metadata and missing CRM tables', 'unified-schema-migration-v1');
