-- Adds configurable, tenant-scoped business notification rules and durable message snapshots.
-- Dependencies: V010, system_notify_template, system_notify_message and existing tenant users.
-- Execution order: additive columns/table/indexes, legacy snapshot backfill, default global template,
-- menu metadata/grants, then the version record.
-- Repeatability: information_schema guards, stable menu IDs and NOT EXISTS inserts permit reruns.
-- Data scope: schema metadata, legacy message title/summary backfill, one disabled-by-default global
-- template, and inherited notification-management menu grants. No business rows are deleted.
-- Recovery: forward-only. Existing message bodies are preserved; unused rules/templates can be disabled.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

DROP PROCEDURE IF EXISTS `zsjos_v011_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v011_add_column`(IN table_name_value varchar(64), IN column_name_value varchar(64), IN ddl_value text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND table_name=table_name_value AND column_name=column_name_value) THEN
    SET @ddl = ddl_value; PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v011_add_column`('system_notify_template','scene_code',
  'ALTER TABLE `system_notify_template` ADD COLUMN `scene_code` varchar(64) DEFAULT NULL COMMENT ''业务场景编码'' AFTER `nickname`');
CALL `zsjos_v011_add_column`('system_notify_template','title',
  'ALTER TABLE `system_notify_template` ADD COLUMN `title` varchar(128) DEFAULT NULL COMMENT ''标题模板'' AFTER `scene_code`');
CALL `zsjos_v011_add_column`('system_notify_template','summary',
  'ALTER TABLE `system_notify_template` ADD COLUMN `summary` varchar(512) DEFAULT NULL COMMENT ''摘要模板'' AFTER `title`');

CALL `zsjos_v011_add_column`('system_notify_message','template_title',
  'ALTER TABLE `system_notify_message` ADD COLUMN `template_title` varchar(128) DEFAULT NULL COMMENT ''标题快照'' AFTER `template_nickname`');
CALL `zsjos_v011_add_column`('system_notify_message','template_summary',
  'ALTER TABLE `system_notify_message` ADD COLUMN `template_summary` varchar(512) DEFAULT NULL COMMENT ''摘要快照'' AFTER `template_title`');
CALL `zsjos_v011_add_column`('system_notify_message','notify_rule_id',
  'ALTER TABLE `system_notify_message` ADD COLUMN `notify_rule_id` bigint DEFAULT NULL COMMENT ''通知规则编号'' AFTER `template_params`');
CALL `zsjos_v011_add_column`('system_notify_message','scene_code',
  'ALTER TABLE `system_notify_message` ADD COLUMN `scene_code` varchar(64) DEFAULT NULL COMMENT ''业务场景编码'' AFTER `notify_rule_id`');
CALL `zsjos_v011_add_column`('system_notify_message','source_event_key',
  'ALTER TABLE `system_notify_message` ADD COLUMN `source_event_key` varchar(128) DEFAULT NULL COMMENT ''来源事件键'' AFTER `scene_code`');
CALL `zsjos_v011_add_column`('system_notify_message','action_type',
  'ALTER TABLE `system_notify_message` ADD COLUMN `action_type` varchar(32) DEFAULT NULL COMMENT ''受控点击动作'' AFTER `source_event_key`');
CALL `zsjos_v011_add_column`('system_notify_message','biz_type',
  'ALTER TABLE `system_notify_message` ADD COLUMN `biz_type` varchar(64) DEFAULT NULL COMMENT ''业务类型'' AFTER `action_type`');
CALL `zsjos_v011_add_column`('system_notify_message','biz_id',
  'ALTER TABLE `system_notify_message` ADD COLUMN `biz_id` bigint DEFAULT NULL COMMENT ''业务编号'' AFTER `biz_type`');

DROP PROCEDURE IF EXISTS `zsjos_v011_add_column`;

ALTER TABLE `system_notify_template` MODIFY COLUMN `content` text NOT NULL COMMENT '正文模板';
ALTER TABLE `system_notify_template` MODIFY COLUMN `params` text DEFAULT NULL COMMENT '参数数组';
ALTER TABLE `system_notify_message` MODIFY COLUMN `template_content` text NOT NULL COMMENT '正文快照';
ALTER TABLE `system_notify_message` MODIFY COLUMN `template_params` text NOT NULL COMMENT '模板参数';

UPDATE `system_notify_template`
SET `title`=COALESCE(NULLIF(`title`,''),`name`),
    `summary`=COALESCE(NULLIF(`summary`,''),LEFT(`content`,512))
WHERE `title` IS NULL OR `title`='' OR `summary` IS NULL OR `summary`='';

UPDATE `system_notify_message` message
LEFT JOIN `system_notify_template` template ON template.id=message.template_id
SET message.template_title=COALESCE(NULLIF(message.template_title,''),template.name,message.template_nickname),
    message.template_summary=COALESCE(NULLIF(message.template_summary,''),LEFT(message.template_content,512))
WHERE message.template_title IS NULL OR message.template_title=''
   OR message.template_summary IS NULL OR message.template_summary='';

ALTER TABLE `system_notify_template` MODIFY COLUMN `title` varchar(128) NOT NULL COMMENT '标题模板';
ALTER TABLE `system_notify_template` MODIFY COLUMN `summary` varchar(512) NOT NULL COMMENT '摘要模板';
ALTER TABLE `system_notify_message` MODIFY COLUMN `template_title` varchar(128) NOT NULL COMMENT '标题快照';
ALTER TABLE `system_notify_message` MODIFY COLUMN `template_summary` varchar(512) NOT NULL COMMENT '摘要快照';

CREATE TABLE IF NOT EXISTS `system_notify_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `scene_code` varchar(64) NOT NULL,
  `template_id` bigint NOT NULL,
  `recipient_roles` text NOT NULL,
  `specified_user_ids` text NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `status` tinyint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_scene_status` (`tenant_id`,`scene_code`,`status`),
  KEY `idx_tenant_template` (`tenant_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户业务通知规则';

SET @ddl = (SELECT IF(EXISTS (
  SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='system_notify_message' AND index_name='uk_notify_rule_user_event'),
  'SELECT 1',
  'ALTER TABLE `system_notify_message` ADD UNIQUE KEY `uk_notify_rule_user_event` (`tenant_id`,`notify_rule_id`,`user_id`,`source_event_key`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资待接单','ZSJOS_LEAD_PENDING_ASSIGNMENT','中世健消息中心','zsjos.lead.assigned',
       '新客资待接单','客资编号{{lead.id}}已经派单给你，请尽快接受',
       '客资编号{{lead.id}}已经派单给你，请在{{lead.pendingExpiresAt}}前完成接单。',
       2,'["lead.id","lead.pendingExpiresAt"]',0,'全局默认模板；租户需自行创建并启用通知规则',
       'migration-V011',NOW(),'migration-V011',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code`='ZSJOS_LEAD_PENDING_ASSIGNMENT' AND `deleted`=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6785,'业务通知规则','',2,1,2144,'notify-rule','ep:bell-filled','system/notify/rule/index','SystemNotifyRule',0,b'1',b'1',b'1','migration-V011',NOW(),'migration-V011',NOW(),b'0'),
(6786,'业务通知规则查询','system:notify-rule:query',3,1,6785,'','','',NULL,0,b'1',b'1',b'1','migration-V011',NOW(),'migration-V011',NOW(),b'0'),
(6787,'业务通知规则创建','system:notify-rule:create',3,2,6785,'','','',NULL,0,b'1',b'1',b'1','migration-V011',NOW(),'migration-V011',NOW(),b'0'),
(6788,'业务通知规则更新','system:notify-rule:update',3,3,6785,'','','',NULL,0,b'1',b'1',b'1','migration-V011',NOW(),'migration-V011',NOW(),b'0'),
(6789,'业务通知规则删除','system:notify-rule:delete',3,4,6785,'','','',NULL,0,b'1',b'1',b'1','migration-V011',NOW(),'migration-V011',NOW(),b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,target.menu_id,'migration-V011',NOW(),'migration-V011',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source
CROSS JOIN (SELECT 6785 menu_id UNION ALL SELECT 6786 UNION ALL SELECT 6787 UNION ALL SELECT 6788 UNION ALL SELECT 6789) target
WHERE source.menu_id=2145 AND source.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V011','Add configurable business notifications and durable message snapshots','configurable-business-notifications-v1');
