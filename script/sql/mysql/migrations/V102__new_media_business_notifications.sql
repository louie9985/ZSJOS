-- V102: new-media pending, return, and final-result in-app notifications.
-- Dependencies/order: apply after V101, V056 reliable outbox, and V096 new-media schema.
-- Data scope: global system-owned templates and one default in-app rule per active tenant/scene when absent.
-- Repeatability: stable template codes, scene existence checks, and schema-version upserts prevent duplicates.
-- Recovery: forward-only; disable V102-created rules and preserve delivered messages and outbox history.

START TRANSACTION;

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`channel_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene,'in_app',x.title,x.summary,x.content,2,'["bizNo"]',0,
       'V102 新媒体业务消息','migration-V102',NOW(),'migration-V102',NOW(),b'0'
FROM (
 SELECT '工单待接单' name,'ZSJOS_MEDIA_TICKET_PENDING_ACCEPT' code,'media.ticket.pending_accept' scene,'工单待接单' title,'你有新的拍剪工单待接单' summary,'拍剪工单 {{bizNo}} 等待你接单。' content
 UNION ALL SELECT '工单待核对','ZSJOS_MEDIA_TICKET_PENDING_CHECK','media.ticket.pending_check','工单待核对','你有拍剪成品待核对','拍剪工单 {{bizNo}} 已提交，等待你核对。'
 UNION ALL SELECT '工单核对通过','ZSJOS_MEDIA_TICKET_APPROVED','media.ticket.approved','工单核对通过','拍剪工单已完成','拍剪工单 {{bizNo}} 已通过核对。'
 UNION ALL SELECT '工单返工','ZSJOS_MEDIA_TICKET_REJECTED','media.ticket.rejected','工单需要返工','拍剪工单已退回','拍剪工单 {{bizNo}} 已退回，请按要求返工。'
 UNION ALL SELECT '内容待验收','ZSJOS_MEDIA_CONTENT_PENDING_ACCEPTANCE','media.content.pending_acceptance','内容待验收','你有内容成品待验收','内容 {{bizNo}} 已提交，等待你验收。'
 UNION ALL SELECT '内容验收通过','ZSJOS_MEDIA_CONTENT_APPROVED','media.content.approved','内容验收通过','内容已完成验收','内容 {{bizNo}} 已通过验收。'
 UNION ALL SELECT '内容验收退回','ZSJOS_MEDIA_CONTENT_REJECTED','media.content.rejected','内容验收退回','内容需要修改','内容 {{bizNo}} 已退回，请修改后重新提交。'
 UNION ALL SELECT '定位待运营复核','ZSJOS_MEDIA_POSITIONING_OPERATOR_REVIEW','media.positioning.operator_review','定位待运营复核','你有定位方案待复核','定位卡 {{bizNo}} 等待运营可行性复核。'
 UNION ALL SELECT '定位运营退回','ZSJOS_MEDIA_POSITIONING_OPERATOR_REJECTED','media.positioning.operator_rejected','定位运营退回','定位方案需要修改','定位卡 {{bizNo}} 已由运营退回，请修改后重新提交。'
 UNION ALL SELECT 'IP审核通过','ZSJOS_MEDIA_POSITIONING_IP_APPROVED','media.positioning.ip_approved','IP审核通过','专业风险定位已通过IP审核','定位卡 {{bizNo}} 已通过IP审核。'
 UNION ALL SELECT 'IP审核驳回','ZSJOS_MEDIA_POSITIONING_IP_REJECTED','media.positioning.ip_rejected','IP审核驳回','专业风险定位需要修改','定位卡 {{bizNo}} 已被IP审核驳回，请修改后重新提交。'
 UNION ALL SELECT '定位待学员确认','ZSJOS_MEDIA_POSITIONING_STUDENT_CONFIRMATION','media.positioning.student_confirmation','定位方案待确认','你有新的定位方案待确认','定位卡 {{bizNo}} 等待你的确认。'
 UNION ALL SELECT '学员已确认定位','ZSJOS_MEDIA_POSITIONING_STUDENT_CONFIRMED','media.positioning.student_confirmed','学员已确认定位','定位方案已获学员确认','定位卡 {{bizNo}} 已由学员确认。'
 UNION ALL SELECT '学员已拒绝定位','ZSJOS_MEDIA_POSITIONING_STUDENT_REJECTED','media.positioning.student_rejected','学员已拒绝定位','定位方案需重新共创','定位卡 {{bizNo}} 已被学员拒绝，请重新共创。'
) x
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing
                  WHERE existing.code=x.code AND existing.deleted=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT x.name,x.scene,'in_app',template.id,'["assignee"]','[]','business_detail',0,
       'migration-V102',NOW(),'migration-V102',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
JOIN (
 SELECT '工单待接单' name,'media.ticket.pending_accept' scene,'ZSJOS_MEDIA_TICKET_PENDING_ACCEPT' code
 UNION ALL SELECT '工单待核对','media.ticket.pending_check','ZSJOS_MEDIA_TICKET_PENDING_CHECK'
 UNION ALL SELECT '工单核对通过','media.ticket.approved','ZSJOS_MEDIA_TICKET_APPROVED'
 UNION ALL SELECT '工单返工','media.ticket.rejected','ZSJOS_MEDIA_TICKET_REJECTED'
 UNION ALL SELECT '内容待验收','media.content.pending_acceptance','ZSJOS_MEDIA_CONTENT_PENDING_ACCEPTANCE'
 UNION ALL SELECT '内容验收通过','media.content.approved','ZSJOS_MEDIA_CONTENT_APPROVED'
 UNION ALL SELECT '内容验收退回','media.content.rejected','ZSJOS_MEDIA_CONTENT_REJECTED'
 UNION ALL SELECT '定位待运营复核','media.positioning.operator_review','ZSJOS_MEDIA_POSITIONING_OPERATOR_REVIEW'
 UNION ALL SELECT '定位运营退回','media.positioning.operator_rejected','ZSJOS_MEDIA_POSITIONING_OPERATOR_REJECTED'
 UNION ALL SELECT 'IP审核通过','media.positioning.ip_approved','ZSJOS_MEDIA_POSITIONING_IP_APPROVED'
 UNION ALL SELECT 'IP审核驳回','media.positioning.ip_rejected','ZSJOS_MEDIA_POSITIONING_IP_REJECTED'
 UNION ALL SELECT '定位待学员确认','media.positioning.student_confirmation','ZSJOS_MEDIA_POSITIONING_STUDENT_CONFIRMATION'
 UNION ALL SELECT '学员已确认定位','media.positioning.student_confirmed','ZSJOS_MEDIA_POSITIONING_STUDENT_CONFIRMED'
 UNION ALL SELECT '学员已拒绝定位','media.positioning.student_rejected','ZSJOS_MEDIA_POSITIONING_STUDENT_REJECTED'
) x
JOIN `system_notify_template` template ON template.code=x.code AND template.scene_code=x.scene AND template.deleted=b'0'
WHERE tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing
                  WHERE existing.tenant_id=tenant.id AND existing.scene_code=x.scene AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V102','New-media business notifications','V102__new_media_business_notifications.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V102','New-media business notifications',SHA2('V102__new_media_business_notifications.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
