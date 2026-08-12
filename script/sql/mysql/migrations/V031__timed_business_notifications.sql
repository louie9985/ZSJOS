-- V031: configurable timed lead reminders, delivery-stage idempotency, and order notification defaults.
-- Dependencies: V011 notification rules, V008 business tasks, V023 sales orders, and V029 reviewer filters.
-- Repeatability: guarded columns/tables and NOT EXISTS inserts. No business rows are deleted.
-- Recovery: forward-only; disable seeded rules to stop delivery while retaining notification audit history.

DROP PROCEDURE IF EXISTS `zsjos_v031_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v031_add_column`(IN table_name_value varchar(64), IN column_name_value varchar(64), IN ddl_value text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND table_name=table_name_value AND column_name=column_name_value) THEN
    SET @ddl = ddl_value; PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v031_add_column`('system_notify_rule','timing_stage',
  'ALTER TABLE `system_notify_rule` ADD COLUMN `timing_stage` varchar(16) DEFAULT NULL COMMENT ''提醒阶段：advance/due/overdue'' AFTER `action_type`');
CALL `zsjos_v031_add_column`('system_notify_rule','timing_offset_minutes',
  'ALTER TABLE `system_notify_rule` ADD COLUMN `timing_offset_minutes` int DEFAULT NULL COMMENT ''相对截止时间偏移分钟数'' AFTER `timing_stage`');
DROP PROCEDURE IF EXISTS `zsjos_v031_add_column`;

CREATE TABLE IF NOT EXISTS `zsjos_business_task_notify_stage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号', `task_id` bigint NOT NULL COMMENT '业务任务编号',
  `notify_rule_id` bigint NOT NULL COMMENT '发送时使用的通知规则', `stage` varchar(16) NOT NULL COMMENT '提醒阶段',
  `emitted_at` datetime NOT NULL COMMENT '阶段处理时间',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_task_stage` (`tenant_id`,`task_id`,`stage`),
  KEY `idx_tenant_rule` (`tenant_id`,`notify_rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务任务提醒阶段幂等记录';

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene_code,x.title,x.summary,x.content,2,x.params,0,'V031 系统默认模板','migration-V031',NOW(),'migration-V031',NOW(),b'0'
FROM (
 SELECT '首次跟进时限提醒' name,'ZSJOS_FIRST_FOLLOW_UP_REMINDER' code,'zsjos.lead.first_follow_up_reminder' scene_code,'首次跟进提醒' title,'{{lead.name}}的首次跟进任务{{reminder.stage}}' summary,'客资{{lead.name}}的首次跟进截止时间为{{reminder.dueAt}}，当前阶段：{{reminder.stage}}。' content,'["lead.name","reminder.stage","reminder.dueAt"]' params
 UNION ALL SELECT '下次跟进提醒','ZSJOS_NEXT_FOLLOW_UP_REMINDER','zsjos.lead.next_follow_up_reminder','下次跟进提醒','{{lead.name}}的下次跟进任务{{reminder.stage}}','客资{{lead.name}}的下次跟进时间为{{reminder.dueAt}}，当前阶段：{{reminder.stage}}。','["lead.name","reminder.stage","reminder.dueAt"]'
 UNION ALL SELECT '有效性判定时限提醒','ZSJOS_QUALIFICATION_REMINDER','zsjos.lead.qualification_reminder','有效性判定提醒','{{lead.name}}的有效性判定任务{{reminder.stage}}','客资{{lead.name}}的有效性判定截止时间为{{reminder.dueAt}}，当前阶段：{{reminder.stage}}。','["lead.name","reminder.stage","reminder.dueAt"]'
 UNION ALL SELECT '成交订单待审批','ZSJOS_ORDER_SUBMITTED','zsjos.sales_order.submitted','成交订单待审批','订单{{order.no}}等待审批','订单{{order.no}}（{{order.studentName}}）等待{{order.approvalDepartments}}审批。','["order.no","order.studentName","order.approvalDepartments"]'
 UNION ALL SELECT '成交订单已生效','ZSJOS_ORDER_EFFECTIVE','zsjos.sales_order.effective','成交订单已生效','订单{{order.no}}已生效','订单{{order.no}}已通过审批并生效。','["order.no"]'
 UNION ALL SELECT '成交订单审批拒绝','ZSJOS_ORDER_REJECTED','zsjos.sales_order.rejected','成交订单需补正','订单{{order.no}}审批未通过','订单{{order.no}}审批未通过：{{order.decisionReason}}','["order.no","order.decisionReason"]'
 UNION ALL SELECT '成交订单审批取消','ZSJOS_ORDER_CANCELLED','zsjos.sales_order.cancelled','成交订单审批已取消','订单{{order.no}}审批已取消','订单{{order.no}}审批已取消：{{order.decisionReason}}','["order.no","order.decisionReason"]'
) x WHERE NOT EXISTS (SELECT 1 FROM system_notify_template t WHERE t.code=x.code AND t.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`timing_stage`,`timing_offset_minutes`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,x.roles,'[]','business_detail',x.stage,x.offset_minutes,0,
       'migration-V031',NOW(),'migration-V031',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '首次跟进-提前30分钟' name,'zsjos.lead.first_follow_up_reminder' scene_code,'["owner","direct_leader"]' roles,'advance' stage,30 offset_minutes,'ZSJOS_FIRST_FOLLOW_UP_REMINDER' template_code
 UNION ALL SELECT '首次跟进-到期','zsjos.lead.first_follow_up_reminder','["owner","direct_leader"]','due',0,'ZSJOS_FIRST_FOLLOW_UP_REMINDER'
 UNION ALL SELECT '首次跟进-逾期5分钟','zsjos.lead.first_follow_up_reminder','["owner","direct_leader"]','overdue',5,'ZSJOS_FIRST_FOLLOW_UP_REMINDER'
 UNION ALL SELECT '下次跟进-提前30分钟','zsjos.lead.next_follow_up_reminder','["owner","direct_leader"]','advance',30,'ZSJOS_NEXT_FOLLOW_UP_REMINDER'
 UNION ALL SELECT '下次跟进-到期','zsjos.lead.next_follow_up_reminder','["owner","direct_leader"]','due',0,'ZSJOS_NEXT_FOLLOW_UP_REMINDER'
 UNION ALL SELECT '下次跟进-逾期5分钟','zsjos.lead.next_follow_up_reminder','["owner","direct_leader"]','overdue',5,'ZSJOS_NEXT_FOLLOW_UP_REMINDER'
 UNION ALL SELECT '有效性判定-提前30分钟','zsjos.lead.qualification_reminder','["owner","direct_leader"]','advance',30,'ZSJOS_QUALIFICATION_REMINDER'
 UNION ALL SELECT '有效性判定-到期并挂起','zsjos.lead.qualification_reminder','["owner","direct_leader"]','due',0,'ZSJOS_QUALIFICATION_REMINDER'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
  WHERE r.tenant_id=tenant.id AND r.name=x.name AND r.creator='migration-V031' AND r.deleted=b'0');

-- Event defaults from the confirmed recipient matrix. Follow-up/category-change remain configurable without defaults.
INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,x.roles,'[]','business_detail',0,'migration-V031',NOW(),'migration-V031',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '重复客资激活通知' name,'zsjos.lead.activated' scene_code,'["owner"]' roles,'ZSJOS_LEAD_ACTIVATED' template_code
 UNION ALL SELECT '首次派单通知','zsjos.lead.assigned','["pending_sales"]','ZSJOS_LEAD_PENDING_ASSIGNMENT'
 UNION ALL SELECT '重新派单通知','zsjos.lead.reassigned','["pending_sales"]','ZSJOS_LEAD_REASSIGNED'
 UNION ALL SELECT '销售接单通知','zsjos.lead.accepted','["owner"]','ZSJOS_LEAD_ACCEPTED'
 UNION ALL SELECT '销售拒单通知','zsjos.lead.rejected','["pending_sales"]','ZSJOS_LEAD_REJECTED'
 UNION ALL SELECT '接单超时通知','zsjos.lead.expired','["pending_sales","direct_leader"]','ZSJOS_LEAD_EXPIRED'
 UNION ALL SELECT '抢单池可认领通知','zsjos.lead.public_pool','["all_eligible_sales"]','ZSJOS_LEAD_PUBLIC_POOL'
 UNION ALL SELECT '抢单成功通知','zsjos.lead.claimed','["owner"]','ZSJOS_LEAD_CLAIMED'
 UNION ALL SELECT '管理员转派通知','zsjos.lead.transferred','["previous_owner","new_owner"]','ZSJOS_LEAD_TRANSFERRED'
 UNION ALL SELECT '挂起恢复通知','zsjos.lead.qualification_restored','["owner"]','ZSJOS_LEAD_QUALIFICATION_RESTORED'
 UNION ALL SELECT '异常转派通知','zsjos.lead.qualification_transferred','["previous_owner","new_owner"]','ZSJOS_LEAD_QUALIFICATION_TRANSFERRED'
 UNION ALL SELECT '异常回收通知','zsjos.lead.qualification_recycled','["previous_owner"]','ZSJOS_LEAD_QUALIFICATION_RECYCLED'
 UNION ALL SELECT '释放抢单池通知','zsjos.lead.qualification_released','["previous_owner","all_eligible_sales"]','ZSJOS_LEAD_QUALIFICATION_RELEASED'
 UNION ALL SELECT '申诉提交通知','zsjos.lead.appeal_submitted','["appeal_reviewers","owner"]','ZSJOS_LEAD_APPEAL_SUBMITTED'
 UNION ALL SELECT '申诉改判通知','zsjos.lead.appeal_overturned','["submitter","owner"]','ZSJOS_LEAD_APPEAL_OVERTURNED'
 UNION ALL SELECT '申诉维持通知','zsjos.lead.appeal_upheld','["submitter","owner"]','ZSJOS_LEAD_APPEAL_UPHELD'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
  WHERE r.tenant_id=tenant.id AND r.name=x.name AND r.creator='migration-V031' AND r.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,x.roles,'[]','business_detail',0,'migration-V031',NOW(),'migration-V031',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '成交订单提交审批' name,'zsjos.sales_order.submitted' scene_code,'["reviewers"]' roles,'ZSJOS_ORDER_SUBMITTED' template_code
 UNION ALL SELECT '成交订单最终通过','zsjos.sales_order.effective','["submitter"]','ZSJOS_ORDER_EFFECTIVE'
 UNION ALL SELECT '成交订单最终拒绝','zsjos.sales_order.rejected','["submitter"]','ZSJOS_ORDER_REJECTED'
 UNION ALL SELECT '成交订单审批取消','zsjos.sales_order.cancelled','["submitter"]','ZSJOS_ORDER_CANCELLED'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
  WHERE r.tenant_id=tenant.id AND r.name=x.name AND r.creator='migration-V031' AND r.deleted=b'0');

UPDATE zsjos_lead_inbox_filter_scheme
SET draft_config_json=REPLACE(draft_config_json,'"label":"教务审批"','"label":"报名履约中心审批"'),
    published_config_json=REPLACE(published_config_json,'"label":"教务审批"','"label":"报名履约中心审批"')
WHERE audience='reviewer' AND deleted=b'0'
  AND (draft_config_json LIKE '%"label":"教务审批"%' OR published_config_json LIKE '%"label":"教务审批"%');
UPDATE zsjos_lead_inbox_filter_version
SET config_json=REPLACE(config_json,'"label":"教务审批"','"label":"报名履约中心审批"')
WHERE deleted=b'0' AND config_json LIKE '%"label":"教务审批"%';

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene_code,x.title,x.summary,x.content,2,x.params,0,'V031 系统默认模板','migration-V031',NOW(),'migration-V031',NOW(),b'0'
FROM (
 SELECT '客资判定有效' name,'ZSJOS_LEAD_QUALIFIED_VALID' code,'zsjos.lead.qualified_valid' scene_code,'客资已判定有效' title,'客资{{lead.id}}已判定有效' summary,'客资{{lead.id}}已由{{owner.name}}判定有效。' content,'["lead.id","owner.name"]' params
 UNION ALL SELECT '客资判定无效','ZSJOS_LEAD_QUALIFIED_INVALID','zsjos.lead.qualified_invalid','客资已判定无效','客资{{lead.id}}已判定无效','客资{{lead.id}}已由{{owner.name}}判定无效。','["lead.id","owner.name"]'
) x WHERE NOT EXISTS (SELECT 1 FROM system_notify_template t WHERE t.code=x.code AND t.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,'["submitter"]','[]','business_detail',0,'migration-V031',NOW(),'migration-V031',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN (
 SELECT '客资判定有效结果' name,'zsjos.lead.qualified_valid' scene_code,'ZSJOS_LEAD_QUALIFIED_VALID' template_code
 UNION ALL SELECT '客资判定无效结果','zsjos.lead.qualified_invalid','ZSJOS_LEAD_QUALIFIED_INVALID'
) x JOIN system_notify_template t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
  WHERE r.tenant_id=tenant.id AND r.name=x.name AND r.creator='migration-V031' AND r.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V031','Add configurable timed business notifications and order notification defaults','timed-business-notifications-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
