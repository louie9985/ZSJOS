-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- Adds sales acceptance permission, first-follow-up rules and administration metadata.
-- Dependencies: V005, system menu/role grants, business task/event tables and active tenants.
-- Execution order: create the rule table, seed one enabled rule per tenant, register menus,
-- grant follow-up management to existing lead-rule managers, grant acceptance to claim roles, record V006.
-- Repeatability: stable table/menu IDs, tenant rule code and NOT EXISTS guards make re-execution safe.
-- Data scope: configuration and permission metadata only; no lead, task or account rows are deleted or rewritten.
-- Recovery: forward-only. Disable permissions or update the rule; do not delete generated task/event history.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

CREATE TABLE IF NOT EXISTS `zsjos_lead_follow_up_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客资跟进规则编号',
  `code` varchar(64) NOT NULL COMMENT '规则编码', `name` varchar(100) NOT NULL COMMENT '规则名称',
  `first_follow_up_timeout_minutes` int NOT NULL COMMENT '接单后首次跟进时限（分钟）',
  `status` tinyint NOT NULL DEFAULT '0', `version` int NOT NULL DEFAULT '0',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资跟进时效规则';

INSERT INTO `zsjos_lead_follow_up_rule`
(`code`,`name`,`first_follow_up_timeout_minutes`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'default','默认首次跟进规则',1440,0,0,'migration-V006',NOW(),'migration-V006',NOW(),b'0',t.id
FROM `system_tenant` t WHERE t.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_lead_follow_up_rule` r
  WHERE r.tenant_id=t.id AND r.code='default' AND r.deleted=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6776,'客资跟进规则','zsjos:lead-follow-up-rule:query',2,92,6735,'lead-follow-up-rule','ep:timer','zsjos/leadFollowUpRule/index','ZsjosLeadFollowUpRule',0,b'1',b'1',b'1','migration-V006',NOW(),'migration-V006',NOW(),b'0'),
(6777,'修改客资跟进规则','zsjos:lead-follow-up-rule:update',3,1,6776,'','','',NULL,0,b'1',b'1',b'1','migration-V006',NOW(),'migration-V006',NOW(),b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V006','Add lead acceptance tasks and follow-up rule','lead-acceptance-follow-up-v1');
