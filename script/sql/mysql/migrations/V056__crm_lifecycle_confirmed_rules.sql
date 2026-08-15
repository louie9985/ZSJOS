-- V056: confirmed CRM lifecycle rules.
-- Additive and repeatable. No business rows are deleted or rewritten.
-- Apply after V055. Rollback is limited to preserving a backup before dropping
-- the newly-created tables/indexes.

CREATE TABLE IF NOT EXISTS `zsjos_order_no_daily_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sequence_date` date NOT NULL COMMENT '北京时间自然日',
  `current_value` int NOT NULL DEFAULT 0 COMMENT '当日已分配流水，范围 0-9999',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_order_no_sequence_date` (`tenant_id`,`sequence_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单号每日流水';

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead_transfer_request' AND column_name='transfer_reviewer_user_id'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead_transfer_request` ADD COLUMN `transfer_reviewer_user_id` bigint DEFAULT NULL COMMENT ''提交时主管快照'' AFTER `owner_dept_id_snapshot`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP PROCEDURE IF EXISTS `zsjos_v056_assert_account_uniqueness`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v056_assert_account_uniqueness`()
BEGIN
  DECLARE v_username_duplicates bigint DEFAULT 0;
  DECLARE v_mobile_duplicates bigint DEFAULT 0;
  DECLARE v_cross_conflicts bigint DEFAULT 0;
  DECLARE v_message varchar(255);

  SELECT COUNT(*) INTO v_username_duplicates FROM (
    SELECT tenant_id, BINARY username FROM system_users
    GROUP BY tenant_id, BINARY username HAVING COUNT(*) > 1
  ) duplicate_usernames;
  SELECT COUNT(*) INTO v_mobile_duplicates FROM (
    SELECT tenant_id, mobile FROM system_users WHERE mobile <> ''
    GROUP BY tenant_id, mobile HAVING COUNT(*) > 1
  ) duplicate_mobiles;
  SELECT COUNT(*) INTO v_cross_conflicts
  FROM system_users username_user
  JOIN system_users mobile_user
    ON mobile_user.tenant_id = username_user.tenant_id
   AND mobile_user.id <> username_user.id
   AND mobile_user.mobile <> ''
   AND BINARY mobile_user.mobile = BINARY username_user.username;

  IF v_username_duplicates > 0 OR v_mobile_duplicates > 0 OR v_cross_conflicts > 0 THEN
    SET v_message = CONCAT('V056 account conflicts: username_groups=', v_username_duplicates,
      ', mobile_groups=', v_mobile_duplicates, ', username_mobile_pairs=', v_cross_conflicts);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v056_assert_account_uniqueness`();
DROP PROCEDURE `zsjos_v056_assert_account_uniqueness`;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='system_users' AND column_name='unique_username'), 'SELECT 1',
  'ALTER TABLE `system_users` ADD COLUMN `unique_username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (`username`) STORED COMMENT ''租户内大小写敏感用户名唯一值'' AFTER `mobile`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='system_users' AND index_name='uk_tenant_username_binary'), 'SELECT 1',
  'ALTER TABLE `system_users` ADD UNIQUE KEY `uk_tenant_username_binary` (`tenant_id`,`unique_username`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='system_users' AND column_name='unique_mobile'), 'SELECT 1',
  'ALTER TABLE `system_users` ADD COLUMN `unique_mobile` varchar(11) GENERATED ALWAYS AS (NULLIF(`mobile`,'''')) STORED'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='system_users' AND index_name='uk_tenant_mobile_active'), 'SELECT 1',
  'ALTER TABLE `system_users` ADD UNIQUE KEY `uk_tenant_mobile_active` (`tenant_id`,`unique_mobile`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `system_notify_business_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `scene_code` varchar(64) NOT NULL,
  `source_event_key` varchar(128) NOT NULL,
  `target_rule_id` bigint DEFAULT NULL,
  `biz_type` varchar(64) DEFAULT NULL, `biz_id` bigint DEFAULT NULL,
  `operator_user_id` bigint DEFAULT NULL, `occurred_at` datetime NOT NULL,
  `payload` json DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/succeeded/failed',
  `attempt_count` int NOT NULL DEFAULT 0, `next_attempt_at` datetime NOT NULL,
  `lease_until` datetime DEFAULT NULL, `claim_token` varchar(64) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `succeeded_at` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_notify_outbox_event_rule` (`tenant_id`,`source_event_key`,`target_rule_id`),
  KEY `idx_notify_outbox_due` (`status`,`next_attempt_at`,`lease_until`),
  KEY `idx_notify_outbox_retention` (`status`,`succeeded_at`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务通知持久化 Outbox';

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='system_notify_business_outbox' AND column_name='claim_token'), 'SELECT 1',
  'ALTER TABLE `system_notify_business_outbox` ADD COLUMN `claim_token` varchar(64) DEFAULT NULL AFTER `lease_until`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene,x.title,x.summary,x.content,2,x.params,0,
       'V056 已确认业务通知','migration-V056',NOW(),'migration-V056',NOW(),b'0'
FROM (
 SELECT '客资进入成交审批' name,'ZSJOS_ORDER_SUBMITTER_PENDING' code,'zsjos.sales_order.submitter_pending' scene,'客资进入成交审批' title,'你提交的客资已进入成交审批阶段' summary,'你提交的{{order.studentName}}客资已经进入成交审批阶段。' content,'["order.studentName"]' params
 UNION ALL SELECT '客资成交结果','ZSJOS_ORDER_SUBMITTER_EFFECTIVE','zsjos.sales_order.submitter_effective','客资已成交','你提交的客资已完成成交审批','你提交的{{order.studentName}}客资已通过成交审批，订单返现合计{{order.cashbackTotal}}。','["order.studentName","order.cashbackTotal"]'
 UNION ALL SELECT '重复客资重新激活','ZSJOS_DUPLICATE_REACTIVATED','zsjos.lead.duplicate_reactivated','客资重新激活','重复客资已重新激活','客资{{lead.id}}已重新激活并分配。','["lead.id"]'
 UNION ALL SELECT '重复客资提醒负责人','ZSJOS_DUPLICATE_OWNER_REMINDER','zsjos.lead.duplicate_owner_reminder','重复客资提醒','收到重复客资提醒','客资{{lead.id}}收到一条重复提交提醒。','["lead.id"]'
 UNION ALL SELECT '公海转派待审批','ZSJOS_TRANSFER_REQUESTED','zsjos.lead.transfer_requested','转派申请待审批','收到客资转派申请','客资{{lead.id}}有新的正式转派申请。','["lead.id"]'
 UNION ALL SELECT '公海转派已同意','ZSJOS_TRANSFER_APPROVED','zsjos.lead.transfer_approved','转派申请已同意','客资转派申请已同意','客资{{lead.id}}的正式转派申请已同意。','["lead.id"]'
 UNION ALL SELECT '公海转派已拒绝','ZSJOS_TRANSFER_REJECTED','zsjos.lead.transfer_rejected','转派申请已拒绝','客资转派申请已拒绝','客资{{lead.id}}的正式转派申请已拒绝。','["lead.id"]'
 UNION ALL SELECT '公海转派已失效','ZSJOS_TRANSFER_INVALIDATED','zsjos.lead.transfer_invalidated','转派申请已失效','客资转派申请已失效','客资{{lead.id}}的正式转派申请已失效。','["lead.id"]'
) x WHERE NOT EXISTS (SELECT 1 FROM system_notify_template t WHERE t.code=x.code AND t.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene,'in_app',t.id,x.roles,'[]','business_detail',0,'migration-V056',NOW(),'migration-V056',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '客资进入成交审批' name,'zsjos.sales_order.submitter_pending' scene,'["lead_submitter"]' roles,'ZSJOS_ORDER_SUBMITTER_PENDING' template_code
 UNION ALL SELECT '客资成交最终结果','zsjos.sales_order.submitter_effective','["lead_submitter"]','ZSJOS_ORDER_SUBMITTER_EFFECTIVE'
 UNION ALL SELECT '重复客资重新激活','zsjos.lead.duplicate_reactivated','["previous_owner","new_owner"]','ZSJOS_DUPLICATE_REACTIVATED'
 UNION ALL SELECT '重复客资提醒负责人','zsjos.lead.duplicate_owner_reminder','["owner"]','ZSJOS_DUPLICATE_OWNER_REMINDER'
 UNION ALL SELECT '公海转派待审批','zsjos.lead.transfer_requested','["transfer_reviewer"]','ZSJOS_TRANSFER_REQUESTED'
 UNION ALL SELECT '公海转派已同意','zsjos.lead.transfer_approved','["requester","previous_owner"]','ZSJOS_TRANSFER_APPROVED'
 UNION ALL SELECT '公海转派已拒绝','zsjos.lead.transfer_rejected','["requester"]','ZSJOS_TRANSFER_REJECTED'
 UNION ALL SELECT '公海转派已失效','zsjos.lead.transfer_invalidated','["requester"]','ZSJOS_TRANSFER_INVALIDATED'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
 WHERE r.tenant_id=tenant.id AND r.scene_code=x.scene AND r.creator='migration-V056' AND r.deleted=b'0');

UPDATE system_notify_rule SET recipient_roles='["result_actors"]', updater='migration-V056', update_time=NOW()
WHERE scene_code IN ('zsjos.sales_order.effective','zsjos.sales_order.rejected') AND deleted=b'0';
UPDATE system_notify_rule SET status=1, updater='migration-V056', update_time=NOW()
WHERE scene_code='zsjos.sales_order.cancelled' AND deleted=b'0';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V056','Confirmed CRM lifecycle rules','crm-lifecycle-confirmed-rules-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V056','Confirmed CRM lifecycle rules',SHA2('crm-lifecycle-confirmed-rules-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
