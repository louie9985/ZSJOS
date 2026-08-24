-- V093: add default in-app templates and rules for sales-order supervisor confirmation.
-- Dependencies/order: apply after V092 and the System business-notification tables.
-- Data scope: two System notification templates and missing per-tenant default rules only.
-- Repeatability: stable template codes and scene/template rule checks; administrator rules are not overwritten.
-- Recovery: forward-only; administrators may disable the inserted defaults without deleting message history.

START TRANSACTION;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene_code,x.title,x.summary,x.content,2,x.params,0,
       'V093 系统默认模板','migration-V093',NOW(),'migration-V093',NOW(),b'0'
FROM (
 SELECT '成交订单主管确认申请' name,'ZSJOS_ORDER_SUPERVISOR_REQUESTED' code,
        'zsjos.sales_order.supervisor_requested' scene_code,'成交订单待主管确认' title,
        '订单{{order.no}}需要销售主管确认' summary,
        '订单{{order.no}}由{{order.supervisorCenter}}申请主管确认：{{order.supervisorReason}}' content,
        '["order.no","order.supervisorCenter","order.supervisorReason"]' params
 UNION ALL
 SELECT '成交订单主管确认结果','ZSJOS_ORDER_SUPERVISOR_DECIDED',
        'zsjos.sales_order.supervisor_decided','销售主管确认结果',
        '订单{{order.no}}主管确认已处理',
        '订单{{order.no}}主管确认结果：{{order.supervisorDecision}}；意见：{{order.supervisorReason}}',
        '["order.no","order.supervisorDecision","order.supervisorReason"]'
) x
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` t WHERE t.code=x.code AND t.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene_code,'in_app',t.id,x.roles,'[]','business_detail',0,
       'migration-V093',NOW(),'migration-V093',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN (
 SELECT '成交订单主管确认申请' name,'zsjos.sales_order.supervisor_requested' scene_code,
        '["supervisor"]' roles,'ZSJOS_ORDER_SUPERVISOR_REQUESTED' template_code
 UNION ALL
 SELECT '成交订单主管确认结果','zsjos.sales_order.supervisor_decided',
        '["requester"]','ZSJOS_ORDER_SUPERVISOR_DECIDED'
) x
JOIN `system_notify_template` t ON t.code=x.template_code AND t.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` r
                  WHERE r.tenant_id=tenant.id AND r.scene_code=x.scene_code
                    AND r.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V093','Sales order supervisor notifications','V093__sales_order_supervisor_notifications.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V093','Sales order supervisor notifications',SHA2('V093__sales_order_supervisor_notifications.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
