-- UTF-8. Sales notification configuration alignment, included by the development V274 source.
-- Prerequisites: V269 notification schema/configuration and V274 reply in_app defaults.
-- Order: seed payment template/rules, then missing sales WeCom templates/rules.
-- Scope: global templates for zsjos.lead.*, zsjos.sales_order.*, zsjos.payment.paid;
-- rules only for non-deleted tenants. No business data, permissions or historical messages.
-- Repeatable: insert missing codes/scenes; align the exact untouched V177 source-linked clone.
-- Preserve all other existing templates/rules,
-- including disabled/custom WeCom scenes; never enable channels or personal preferences.
-- Back up both notification tables before synchronization. Rollback may disable only rows
-- created by sales-notify-alignment after review; already delivered messages cannot be recalled.
SET NAMES utf8mb4;

-- Exact untouched V177 clone of the V080 default only. V257 updated the in_app
-- counterpart; do not replace administrator-edited wording, status or recipient rules.
UPDATE system_notify_template w JOIN system_notify_template a
 ON a.code='ZSJOS_LEAD_SOURCE_LINKED' AND a.deleted=0 AND a.scene_code='zsjos.lead.created'
SET w.summary=a.summary,w.content=a.content,w.params=a.params,
 w.updater='sales-notify-alignment',w.update_time=NOW()
WHERE w.code='ZSJOS_LEAD_SOURCE_LINKED_WECOM' AND w.deleted=0
 AND w.channel_code='wecom' AND w.scene_code=a.scene_code
 AND w.creator='migration-V177' AND w.updater='migration-V177'
 AND w.content='{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。'
 AND w.summary=w.content AND CAST(w.params AS JSON)=JSON_ARRAY('operator.name','lead.no')
 AND a.content='{{operator.name}}（{{lead.submitterIdentityLabel}}）提交客资{{lead.no}}（客资编号），已关联你为客资来源。'
 AND a.summary=a.content
 AND CAST(a.params AS JSON)=JSON_ARRAY('operator.name','lead.submitterIdentityLabel','lead.no');

INSERT INTO system_notify_template
(name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,creator,updater)
SELECT '在线收款到账待补录','ZSJOS_PAYMENT_PAID','中仕健消息中心','zsjos.payment.paid','in_app',
'在线收款到账待补录','支付单{{payment.no}}已到账',
'购买意向{{purchase.no}}，支付单{{payment.no}}已到账{{payment.amount}}元，请在成交录单中核对并补录订单。',
2,'["purchase.no","payment.no","payment.amount"]',0,'sales-notify-alignment','sales-notify-alignment'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_PAYMENT_PAID' AND deleted=0);

INSERT INTO system_notify_rule
(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,updater,tenant_id)
SELECT '在线收款到账待补录','zsjos.payment.paid','in_app',t.id,'["owner"]','[]','message_detail',0,
'sales-notify-alignment','sales-notify-alignment',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.code='ZSJOS_PAYMENT_PAID' AND t.deleted=0
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r
 WHERE r.tenant_id=tenant.id AND r.scene_code='zsjos.payment.paid' AND r.channel_code='in_app' AND r.deleted=0);

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,
 `channel_code`,`sms_template_id`,`wecom_message_type`,`status`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT LEFT(CONCAT(source.`name`,'（企微）'),63),
       CASE WHEN CHAR_LENGTH(source.`code`) <= 58 THEN CONCAT(source.`code`,'_WECOM')
            ELSE CONCAT(LEFT(source.`code`,47),'_',LEFT(MD5(source.`code`),8),'_WECOM') END,
       source.`nickname`, source.`scene_code`,
       source.`title`, source.`summary`, source.`content`, source.`type`, source.`params`,
       'wecom', NULL, COALESCE(NULLIF(source.`wecom_message_type`,''),'textcard'), source.`status`,
       LEFT(CONCAT(COALESCE(source.`remark`,''),'；销售通知补全：由业务站内信模板生成企微模板'),255),
       'sales-notify-alignment', NOW(), 'sales-notify-alignment', NOW(), b'0'
FROM `system_notify_template` source
WHERE (source.`scene_code` LIKE 'zsjos.lead.%' OR source.`scene_code` LIKE 'zsjos.sales_order.%' OR source.`scene_code`='zsjos.payment.paid')
  AND source.`deleted`=b'0'
  AND source.`scene_code` IS NOT NULL AND TRIM(source.`scene_code`) <> ''
  AND (source.`channel_code` IS NULL OR source.`channel_code`='' OR source.`channel_code`='in_app')
  AND NOT EXISTS (
    SELECT 1 FROM `system_notify_template` target
    WHERE target.`code`=(CASE WHEN CHAR_LENGTH(source.`code`) <= 58 THEN CONCAT(source.`code`,'_WECOM')
                              ELSE CONCAT(LEFT(source.`code`,47),'_',LEFT(MD5(source.`code`),8),'_WECOM') END)
      AND target.`deleted`=b'0'
  );
INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,
 `action_type`,`timing_stage`,`timing_offset_minutes`,`status`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT LEFT(CONCAT(source_rule.`name`,'（企微）'),64), source_rule.`scene_code`, 'wecom', wecom_template.`id`,
       source_rule.`recipient_roles`, source_rule.`specified_user_ids`, source_rule.`action_type`,
       source_rule.`timing_stage`, source_rule.`timing_offset_minutes`, source_rule.`status`,
       'sales-notify-alignment', NOW(), 'sales-notify-alignment', NOW(), b'0', source_rule.`tenant_id`
FROM `system_notify_rule` source_rule
JOIN `system_notify_template` source_template ON source_template.`id`=source_rule.`template_id`
  AND source_template.`deleted`=b'0'
JOIN `system_notify_template` wecom_template
  ON wecom_template.`code`=(CASE WHEN CHAR_LENGTH(source_template.`code`) <= 58
                                 THEN CONCAT(source_template.`code`,'_WECOM')
                                 ELSE CONCAT(LEFT(source_template.`code`,47),'_',LEFT(MD5(source_template.`code`),8),'_WECOM') END)
 AND wecom_template.`scene_code`=source_template.`scene_code`
 AND wecom_template.`channel_code`='wecom' AND wecom_template.`deleted`=b'0'
WHERE (source_rule.`scene_code` LIKE 'zsjos.lead.%' OR source_rule.`scene_code` LIKE 'zsjos.sales_order.%' OR source_rule.`scene_code`='zsjos.payment.paid')
  AND source_rule.`deleted`=b'0'
  AND (source_rule.`channel_code` IS NULL OR source_rule.`channel_code`='' OR source_rule.`channel_code`='in_app')
  AND source_rule.`scene_code` IS NOT NULL AND TRIM(source_rule.`scene_code`) <> ''
  AND EXISTS (SELECT 1 FROM system_tenant tenant WHERE tenant.id=source_rule.tenant_id AND tenant.deleted=0)
  AND source_template.scene_code=source_rule.scene_code
  AND (source_template.channel_code IS NULL OR source_template.channel_code='' OR source_template.channel_code='in_app')
  AND NOT EXISTS (
    SELECT 1 FROM system_notify_rule existing_rule
    WHERE existing_rule.tenant_id=source_rule.tenant_id
      AND existing_rule.scene_code=source_rule.scene_code
      AND existing_rule.channel_code='wecom' AND existing_rule.deleted=0
  );
