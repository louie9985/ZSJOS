-- V146: dictionary-backed media-account maintenance revisions and read-only calendar.
-- Dependencies/order: apply after V145; requires media accounts, System dictionaries, menus and notifications.
-- Data scope: additive account columns/revision table, confirmed dictionary seeds, inherited menu grants and one notify rule per tenant.
-- Repeatability: guarded DDL, natural-key dictionary/menu/template/rule inserts and version upserts.
-- Recovery: forward-only. Disable the calendar/maintenance menus to stop new use; retain snapshots and revisions.
-- Ordered account stage advance/rollback is permanently retired; this migration only disables its legacy menu grants.

DROP PROCEDURE IF EXISTS `zsjos_v146_schema`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v146_schema`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='current_status_value') THEN
    ALTER TABLE `zsjos_media_account`
      ADD COLUMN `current_status_value` varchar(100) DEFAULT NULL COMMENT '当下状态字典值' AFTER `lead_direction`,
      ADD COLUMN `current_status_label_snapshot` varchar(100) DEFAULT NULL COMMENT '当下状态标签快照' AFTER `current_status_value`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='s_stage_label_snapshot') THEN
    ALTER TABLE `zsjos_media_account` ADD COLUMN `s_stage_label_snapshot` varchar(100) DEFAULT NULL COMMENT '阶段标签快照' AFTER `s_stage`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='primary_problems_json') THEN
    ALTER TABLE `zsjos_media_account`
      ADD COLUMN `primary_problems_json` json DEFAULT NULL COMMENT '主要问题值与标签快照' AFTER `s_stage_label_snapshot`,
      ADD COLUMN `execution_measure_value` varchar(100) DEFAULT NULL COMMENT '实行措施字典值' AFTER `primary_problems_json`,
      ADD COLUMN `execution_measure_label_snapshot` varchar(100) DEFAULT NULL COMMENT '实行措施标签快照' AFTER `execution_measure_value`,
      ADD COLUMN `adjustment_direction` varchar(1000) DEFAULT NULL COMMENT '修改方向' AFTER `execution_measure_label_snapshot`,
      ADD COLUMN `maintenance_start_date` date DEFAULT NULL COMMENT '维护区间开始日期' AFTER `adjustment_direction`,
      ADD COLUMN `maintenance_end_date` date DEFAULT NULL COMMENT '维护区间结束日期' AFTER `maintenance_start_date`;
  END IF;
  ALTER TABLE `zsjos_media_account` MODIFY COLUMN `s_stage` varchar(24) DEFAULT NULL,
    MODIFY COLUMN `s_stage_entered_at` datetime DEFAULT NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND index_name='idx_tenant_maintenance_dates') THEN
    ALTER TABLE `zsjos_media_account` ADD KEY `idx_tenant_maintenance_dates` (`tenant_id`,`maintenance_start_date`,`maintenance_end_date`);
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v146_schema`();
DROP PROCEDURE `zsjos_v146_schema`;

CREATE TABLE IF NOT EXISTS `zsjos_media_account_maintenance_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `revision_no` int NOT NULL,
  `current_status_value` varchar(100) DEFAULT NULL,
  `current_status_label_snapshot` varchar(100) DEFAULT NULL,
  `stage_value` varchar(24) DEFAULT NULL,
  `stage_label_snapshot` varchar(100) DEFAULT NULL,
  `primary_problems_json` json DEFAULT NULL,
  `execution_measure_value` varchar(100) DEFAULT NULL,
  `execution_measure_label_snapshot` varchar(100) DEFAULT NULL,
  `adjustment_direction` varchar(1000) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `changed_fields_json` json NOT NULL,
  `operated_by_user_id` bigint NOT NULL,
  `operated_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_account_revision` (`tenant_id`,`account_id`,`revision_no`,`deleted`),
  KEY `idx_tenant_account_operated` (`tenant_id`,`account_id`,`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体账号状态维护版本';

DROP PROCEDURE IF EXISTS `zsjos_v146_assert_menu_parent`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v146_assert_menu_parent`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `system_menu`
      WHERE `id`=7022 AND `type`=2 AND `permission`='zsjos:media-student:query-my'
        AND `status`=0 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V146 blocked: active media-student page menu 7022 is missing or invalid';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v146_assert_menu_parent`();
DROP PROCEDURE IF EXISTS `zsjos_v146_assert_menu_parent`;

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS `tmp_v146_dict_type`;
CREATE TEMPORARY TABLE `tmp_v146_dict_type` LIKE `system_dict_type`;
ALTER TABLE `tmp_v146_dict_type` DROP COLUMN `id`;
INSERT INTO `tmp_v146_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
('媒体账号当下状态','zsjos_media_account_current_status',0,'媒体账号人工维护的当前状态','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
('媒体账号阶段','zsjos_media_account_stage',0,'媒体账号人工维护阶段，不包含强制流转','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
('媒体账号主要问题','zsjos_media_account_primary_problem',0,'媒体账号人工维护的主要问题，可多选','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
('媒体账号实行措施','zsjos_media_account_execution_measure',0,'媒体账号人工维护的单选实行措施','migration-V146',NOW(),'migration-V146',NOW(),b'0');

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.`name`,seed.`type`,seed.`status`,seed.`remark`,seed.`creator`,seed.`create_time`,seed.`updater`,seed.`update_time`,seed.`deleted`
FROM `tmp_v146_dict_type` seed WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` existing WHERE existing.`type`=seed.`type` AND existing.`deleted`=b'0');
UPDATE `system_dict_type` existing JOIN `tmp_v146_dict_type` seed ON seed.`type`=existing.`type`
SET existing.`name`=seed.`name`,existing.`status`=seed.`status`,existing.`remark`=seed.`remark`,
    existing.`updater`='migration-V146',existing.`update_time`=NOW()
WHERE existing.`deleted`=b'0';
DROP TEMPORARY TABLE `tmp_v146_dict_type`;

DROP TEMPORARY TABLE IF EXISTS `tmp_v146_dict_data`;
CREATE TEMPORARY TABLE `tmp_v146_dict_data` LIKE `system_dict_data`;
ALTER TABLE `tmp_v146_dict_data` DROP COLUMN `id`;
INSERT INTO `tmp_v146_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(1,'A类：活跃增长账号','a_active_growth','zsjos_media_account_current_status',0,'success','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(2,'B类：活跃但不出客资','b_active_no_lead','zsjos_media_account_current_status',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(3,'C类：疑似限流/待挽救','c_limited_rescue','zsjos_media_account_current_status',0,'warning','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(4,'D类：重启/暂停账号','d_restart_paused','zsjos_media_account_current_status',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(1,'S0 待激活','s0','zsjos_media_account_stage',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(2,'S1 定位期','s1','zsjos_media_account_stage',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(3,'S2 冷启动','s2','zsjos_media_account_stage',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(4,'S3 内容验证','s3','zsjos_media_account_stage',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(5,'S4 咨询验证','s4','zsjos_media_account_stage',0,'warning','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(6,'S5 客资验证','s5','zsjos_media_account_stage',0,'success','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(7,'S6 稳定增长','s6','zsjos_media_account_stage',0,'success','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(1,'B1 定位不清','b1','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(2,'B2 学员不执行','b2','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(3,'B3 生产堵塞','b3','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(4,'B4 平台分发弱','b4','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(5,'B5 内容不精确','b5','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(6,'B6 信任不足','b6','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(7,'B7 承接不畅','b7','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(8,'B8 客资质量低','b8','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(9,'B9 销售转化弱','b9','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(10,'B10 合规受限','b10','zsjos_media_account_primary_problem',0,'info','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(1,'冷启动7天','cold_start_7d','zsjos_media_account_execution_measure',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(2,'流量测试7天','traffic_test_7d','zsjos_media_account_execution_measure',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(3,'内容验证7天','content_validation_7d','zsjos_media_account_execution_measure',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(4,'客资验证7天','lead_validation_7d','zsjos_media_account_execution_measure',0,'primary','','','migration-V146',NOW(),'migration-V146',NOW(),b'0');

INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.`sort`,seed.`label`,seed.`value`,seed.`dict_type`,seed.`status`,seed.`color_type`,seed.`css_class`,seed.`remark`,seed.`creator`,seed.`create_time`,seed.`updater`,seed.`update_time`,seed.`deleted`
FROM `tmp_v146_dict_data` seed WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` existing WHERE existing.`dict_type`=seed.`dict_type` AND existing.`value`=seed.`value` AND existing.`deleted`=b'0');
UPDATE `system_dict_data` existing JOIN `tmp_v146_dict_data` seed
  ON seed.`dict_type`=existing.`dict_type` AND seed.`value`=existing.`value`
SET existing.`sort`=seed.`sort`,existing.`label`=seed.`label`,existing.`status`=seed.`status`,
    existing.`color_type`=seed.`color_type`,existing.`css_class`=seed.`css_class`,existing.`remark`=seed.`remark`,
    existing.`updater`='migration-V146',existing.`update_time`=NOW()
WHERE existing.`deleted`=b'0';
DROP TEMPORARY TABLE `tmp_v146_dict_data`;

UPDATE `zsjos_media_account` account
JOIN `system_dict_data` dict ON dict.dict_type='zsjos_media_account_stage' AND dict.value=account.s_stage AND dict.status=0 AND dict.deleted=b'0'
SET account.s_stage_label_snapshot=dict.label,account.updater='migration-V146',account.update_time=NOW()
WHERE account.s_stage IS NOT NULL AND account.s_stage_label_snapshot IS NULL AND account.deleted=b'0';

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(73600,'日历','',1,6,0,'/calendar','ep:calendar','','',0,b'1',b'1',b'1','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(73601,'账号日历','zsjos:media-calendar:query',2,1,73600,'overview','ep:calendar','zsjos/mediaCalendar/index','ZsjosMediaCalendar',0,b'1',b'1',b'1','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(73602,'账号日历查看全部','zsjos:media-calendar:query-all',3,1,73601,'','','',NULL,0,b'1',b'1',b'0','migration-V146',NOW(),'migration-V146',NOW(),b'0'),
(73603,'维护账号状态','zsjos:media-account:maintenance',3,11,7022,'','','',NULL,0,b'1',b'1',b'0','migration-V146',NOW(),'migration-V146',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),`visible`=VALUES(`visible`),`updater`='migration-V146',`update_time`=NOW(),`deleted`=b'0';

DROP TEMPORARY TABLE IF EXISTS `tmp_v146_grants`;
CREATE TEMPORARY TABLE `tmp_v146_grants` (`role_id` bigint NOT NULL,`tenant_id` bigint NOT NULL,`grant_type` varchar(16) NOT NULL,PRIMARY KEY (`role_id`,`grant_type`));
INSERT IGNORE INTO `tmp_v146_grants` (`role_id`,`tenant_id`,`grant_type`)
SELECT rm.role_id,rm.tenant_id,CASE menu.permission WHEN 'zsjos:media-account:query' THEN 'query' WHEN 'zsjos:media-account:edit' THEN 'maintain' ELSE 'query-all' END
FROM `system_role_menu` rm JOIN `system_menu` menu ON menu.id=rm.menu_id AND menu.deleted=b'0'
WHERE rm.deleted=b'0' AND menu.permission IN ('zsjos:media-account:query','zsjos:media-account:edit','zsjos:media-account:query-all');
INSERT IGNORE INTO `tmp_v146_grants` (`role_id`,`tenant_id`,`grant_type`)
SELECT rm.role_id,rm.tenant_id,'query'
FROM `system_role_menu` rm JOIN `system_menu` menu ON menu.id=rm.menu_id AND menu.deleted=b'0'
WHERE rm.deleted=b'0' AND menu.permission='zsjos:media-account:query-all';

UPDATE `system_menu` SET `status`=1,`updater`='migration-V146',`update_time`=NOW()
WHERE `permission` IN ('zsjos:media-account:stage-advance','zsjos:media-account:stage-rollback') AND `deleted`=b'0';
UPDATE `system_role_menu` rm JOIN `system_menu` menu ON menu.id=rm.menu_id
SET rm.deleted=b'1',rm.updater='migration-V146',rm.update_time=NOW()
WHERE menu.permission IN ('zsjos:media-account:stage-advance','zsjos:media-account:stage-rollback') AND rm.deleted=b'0';

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT grant_row.role_id,menu_id.id,'migration-V146',NOW(),'migration-V146',NOW(),b'0',grant_row.tenant_id
FROM `tmp_v146_grants` grant_row JOIN (
  SELECT 73600 id,'query' grant_type UNION ALL SELECT 73601,'query'
  UNION ALL SELECT 73603,'maintain' UNION ALL SELECT 73602,'query-all'
) menu_id ON menu_id.grant_type=grant_row.grant_type
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` existing WHERE existing.role_id=grant_row.role_id AND existing.menu_id=menu_id.id AND existing.tenant_id=grant_row.tenant_id AND existing.deleted=b'0');
DROP TEMPORARY TABLE `tmp_v146_grants`;

INSERT INTO `system_notify_template` (`name`,`code`,`nickname`,`scene_code`,`channel_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '账号状态维护变更','ZSJOS_MEDIA_ACCOUNT_MAINTENANCE_CHANGED','中世健消息中心','media.account.maintenance_changed','in_app','账号状态已更新','{{operatorName}} 更新了账号 {{bizNo}}','{{operatorName}} 更新了账号 {{bizNo}}（{{accountName}}）：{{changeSummary}}',2,'["bizNo","accountName","operatorName","changeSummary"]',0,'V146 账号状态维护通知','migration-V146',NOW(),'migration-V146',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing WHERE existing.code='ZSJOS_MEDIA_ACCOUNT_MAINTENANCE_CHANGED' AND existing.deleted=b'0');

INSERT INTO `system_notify_rule` (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '账号状态维护变更通知','media.account.maintenance_changed','in_app',template.id,'["assignee"]','[]','business_detail',0,'migration-V146',NOW(),'migration-V146',NOW(),b'0',tenant.id
FROM `system_tenant` tenant JOIN `system_notify_template` template ON template.code='ZSJOS_MEDIA_ACCOUNT_MAINTENANCE_CHANGED' AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing WHERE existing.tenant_id=tenant.id AND existing.scene_code='media.account.maintenance_changed' AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`) VALUES ('V146','Media-account maintenance revisions and calendar','V146__media_account_maintenance_calendar.sql',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V146','Media-account maintenance revisions and calendar',SHA2('V146__media_account_maintenance_calendar.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
