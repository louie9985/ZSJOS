-- UTF-8. V269: director/operator notification coverage; apply after V268 and matching backend.
-- Scope: missing media and five withdrawal in_app defaults below, plus missing WeCom templates for all business in_app templates
-- and missing WeCom scene rules across existing non-deleted tenants (including post-V177 business scenes).
-- Existing administrator rules (including disabled rules) and templates are preserved. No grants or business rows change.
-- WeCom copies source status, recipients, actions and timing; channel enablement/user preferences stay unchanged.
-- Exception: add diagnosis assignee/due only alongside an untouched enabled V216 supervisor default.
-- Repeatable: template code + tenant/scene/channel guards. Historical messages/outbox are never replayed.
-- Rollback: disable only newly created migration-V269 rules; retain message history. No destructive rollback.
-- Defaults: interview 60 minutes before; review 1440 minutes after live BPM task creation. Administrators may edit/disable.
SET NAMES utf8mb4;

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位访谈已预约','ZSJ_N269_MEDIA_STUDENT_INTERVIEW_SCHEDULED','中仕健消息中心','media.student.interview_scheduled','in_app','定位访谈已预约','定位访谈已预约','定位访谈已预约，时间：{{interviewAt}}。',2,'["interviewAt"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_SCHEDULED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位访谈已预约','media.student.interview_scheduled','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_SCHEDULED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.student.interview_scheduled');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位访谈时间提醒','ZSJ_N269_MEDIA_STUDENT_INTERVIEW_REMINDER','中仕健消息中心','media.student.interview_reminder','in_app','定位访谈时间提醒','定位访谈时间提醒','已预约定位访谈时间：{{interviewAt}}，请按时准备。',2,'["interviewAt"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_REMINDER');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位访谈时间提醒','media.student.interview_reminder','in_app',t.id,'["assignee"]','[]','business_detail','advance',60,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_REMINDER'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.student.interview_reminder');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位访谈已完成','ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED','中仕健消息中心','media.student.interview_completed','in_app','定位访谈已完成','定位访谈已完成','定位访谈已完成。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位访谈已完成','media.student.interview_completed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.student.interview_completed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '学员协作责任已移交','ZSJ_N269_MEDIA_STUDENT_COLLABORATOR_CHANGED','中仕健消息中心','media.student.collaborator_changed','in_app','学员协作责任已移交','学员协作责任已移交','学员协作责任已移交。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_STUDENT_COLLABORATOR_CHANGED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '学员协作责任已移交','media.student.collaborator_changed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_STUDENT_COLLABORATOR_CHANGED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.student.collaborator_changed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位运营复核通过','ZSJ_N269_MEDIA_POSITIONING_OPERATOR_APPROVED','中仕健消息中心','media.positioning.operator_approved','in_app','定位运营复核通过','定位运营复核通过','定位运营复核通过。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_OPERATOR_APPROVED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位运营复核通过','media.positioning.operator_approved','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_OPERATOR_APPROVED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.operator_approved');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '学员确认链接已生成','ZSJ_N269_MEDIA_POSITIONING_CONFIRMATION_READY','中仕健消息中心','media.positioning.confirmation_ready','in_app','学员确认链接已生成','学员确认链接已生成','学员确认链接已生成。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_CONFIRMATION_READY');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '学员确认链接已生成','media.positioning.confirmation_ready','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_CONFIRMATION_READY'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.confirmation_ready');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位确认凭证已完成','ZSJ_N269_MEDIA_POSITIONING_EFFECTIVE','中仕健消息中心','media.positioning.effective','in_app','定位确认凭证已完成','定位确认凭证已完成','定位确认凭证已完成。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_EFFECTIVE');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位确认凭证已完成','media.positioning.effective','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_EFFECTIVE'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.effective');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号应用定位已更新','ZSJ_N269_MEDIA_POSITIONING_APPLIED','中仕健消息中心','media.positioning.applied','in_app','账号应用定位已更新','账号应用定位已更新','账号应用定位已更新。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_APPLIED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号应用定位已更新','media.positioning.applied','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_APPLIED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.applied');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位已开始修订','ZSJ_N269_MEDIA_POSITIONING_REVISION_STARTED','中仕健消息中心','media.positioning.revision_started','in_app','定位已开始修订','定位已开始修订','定位已开始修订。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_REVISION_STARTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位已开始修订','media.positioning.revision_started','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_REVISION_STARTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.revision_started');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '媒体账号已创建','ZSJ_N269_MEDIA_ACCOUNT_CREATED','中仕健消息中心','media.account.created','in_app','媒体账号已创建','媒体账号已创建','媒体账号已创建。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_ACCOUNT_CREATED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '媒体账号已创建','media.account.created','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_ACCOUNT_CREATED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.account.created');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号诊断已提交','ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED','中仕健消息中心','media.account.diagnosis_completed','in_app','账号诊断已提交','账号诊断已提交','账号诊断已提交。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号诊断已提交','media.account.diagnosis_completed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.account.diagnosis_completed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '阶段交付已完成','ZSJ_N269_STUDENT_DELIVERY_COMPLETED','中仕健消息中心','student.delivery.completed','in_app','阶段交付已完成','阶段交付已完成','阶段交付已完成。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_STUDENT_DELIVERY_COMPLETED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '阶段交付已完成','student.delivery.completed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_STUDENT_DELIVERY_COMPLETED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='student.delivery.completed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '内容已登记发布','ZSJ_N269_MEDIA_CONTENT_PUBLISHED','中仕健消息中心','media.content.published','in_app','内容已登记发布','内容已登记发布','内容已登记发布。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_CONTENT_PUBLISHED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '内容已登记发布','media.content.published','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_CONTENT_PUBLISHED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.content.published');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单待接单','ZSJ_N269_MEDIA_TICKET_PENDING_ACCEPT','中仕健消息中心','media.ticket.pending_accept','in_app','工单待接单','工单待接单','工单待接单。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_TICKET_PENDING_ACCEPT');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单待接单','media.ticket.pending_accept','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_TICKET_PENDING_ACCEPT'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.ticket.pending_accept');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单待核对','ZSJ_N269_MEDIA_TICKET_PENDING_CHECK','中仕健消息中心','media.ticket.pending_check','in_app','工单待核对','工单待核对','工单待核对。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_TICKET_PENDING_CHECK');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单待核对','media.ticket.pending_check','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_TICKET_PENDING_CHECK'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.ticket.pending_check');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单核对通过','ZSJ_N269_MEDIA_TICKET_APPROVED','中仕健消息中心','media.ticket.approved','in_app','工单核对通过','工单核对通过','工单核对通过。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_TICKET_APPROVED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单核对通过','media.ticket.approved','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_TICKET_APPROVED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.ticket.approved');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单返工','ZSJ_N269_MEDIA_TICKET_REJECTED','中仕健消息中心','media.ticket.rejected','in_app','工单返工','工单返工','工单返工。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_TICKET_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单返工','media.ticket.rejected','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_TICKET_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.ticket.rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '指定拍剪工单被拒接','ZSJ_N269_MEDIA_TICKET_ASSIGNMENT_REJECTED','中仕健消息中心','media.ticket.assignment_rejected','in_app','指定拍剪工单被拒接','指定拍剪工单被拒接','指定拍剪工单被拒接。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_TICKET_ASSIGNMENT_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '指定拍剪工单被拒接','media.ticket.assignment_rejected','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_TICKET_ASSIGNMENT_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.ticket.assignment_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '公共池拍剪工单已被抢单','ZSJ_N269_MEDIA_TICKET_CLAIMED','中仕健消息中心','media.ticket.claimed','in_app','公共池拍剪工单已被抢单','公共池拍剪工单已被抢单','公共池拍剪工单已被抢单。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_TICKET_CLAIMED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '公共池拍剪工单已被抢单','media.ticket.claimed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_TICKET_CLAIMED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.ticket.claimed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '内容验收通过','ZSJ_N269_MEDIA_CONTENT_APPROVED','中仕健消息中心','media.content.approved','in_app','内容验收通过','内容验收通过','内容验收通过。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_CONTENT_APPROVED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '内容验收通过','media.content.approved','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_CONTENT_APPROVED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.content.approved');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '内容验收退回','ZSJ_N269_MEDIA_CONTENT_REJECTED','中仕健消息中心','media.content.rejected','in_app','内容验收退回','内容验收退回','内容验收退回。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_CONTENT_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '内容验收退回','media.content.rejected','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_CONTENT_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.content.rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号换绑通过','ZSJ_N269_MEDIA_ACCOUNT_REBIND_APPROVED','中仕健消息中心','media.account.rebind_approved','in_app','账号换绑通过','账号换绑通过','账号换绑通过。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_ACCOUNT_REBIND_APPROVED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号换绑通过','media.account.rebind_approved','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_ACCOUNT_REBIND_APPROVED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.account.rebind_approved');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号换绑驳回','ZSJ_N269_MEDIA_ACCOUNT_REBIND_REJECTED','中仕健消息中心','media.account.rebind_rejected','in_app','账号换绑驳回','账号换绑驳回','账号换绑驳回。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_ACCOUNT_REBIND_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号换绑驳回','media.account.rebind_rejected','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_ACCOUNT_REBIND_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.account.rebind_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号状态维护变更','ZSJ_N269_MEDIA_ACCOUNT_MAINTENANCE_CHANGED','中仕健消息中心','media.account.maintenance_changed','in_app','账号状态维护变更','账号状态维护变更','账号状态维护变更。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_ACCOUNT_MAINTENANCE_CHANGED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号状态维护变更','media.account.maintenance_changed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_ACCOUNT_MAINTENANCE_CHANGED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.account.maintenance_changed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号周期诊断提醒','ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS','中仕健消息中心','media.account.diagnosis','in_app','账号周期诊断提醒','账号周期诊断提醒','账号{{accountName}}周期诊断已到期，请及时处理。',2,'["accountName"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号周期诊断提醒','media.account.diagnosis','in_app',t.id,'["assignee"]','[]','business_detail','due',0,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.account.diagnosis' AND NOT (r.creator='migration-V216' AND r.updater='migration-V216' AND r.status=0 AND r.recipient_roles='["supervisor"]' AND r.timing_stage='overdue' AND r.timing_offset_minutes=0));

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '账号交付已延期','ZSJ_N269_STUDENT_DELIVERY_DEFERRED','中仕健消息中心','student.delivery.deferred','in_app','账号交付已延期','账号交付已延期','账号交付已延期。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_STUDENT_DELIVERY_DEFERRED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '账号交付已延期','student.delivery.deferred','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_STUDENT_DELIVERY_DEFERRED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='student.delivery.deferred');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT 'S0-S6交付确认待填写','ZSJ_N269_STUDENT_DELIVERY_CONFIRMATION','中仕健消息中心','student.delivery.confirmation','in_app','S0-S6交付确认待填写','S0-S6交付确认待填写','账号{{accountName}}的{{stageCode}}交付确认待填写。',2,'["accountName", "stageCode"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_STUDENT_DELIVERY_CONFIRMATION');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT 'S0-S6交付确认待填写','student.delivery.confirmation','in_app',t.id,'["assignee"]','[]','business_detail','due',0,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_STUDENT_DELIVERY_CONFIRMATION'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='student.delivery.confirmation');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位待运营复核','ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REVIEW','中仕健消息中心','media.positioning.operator_review','in_app','定位待运营复核','定位待运营复核','定位待运营复核。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REVIEW');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位待运营复核','media.positioning.operator_review','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REVIEW'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.operator_review');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '定位运营退回','ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REJECTED','中仕健消息中心','media.positioning.operator_rejected','in_app','定位运营退回','定位运营退回','定位运营退回。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '定位运营退回','media.positioning.operator_rejected','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.operator_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '学员已确认定位','ZSJ_N269_MEDIA_POSITIONING_STUDENT_CONFIRMED','中仕健消息中心','media.positioning.student_confirmed','in_app','学员已确认定位','学员已确认定位','学员已确认定位。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_STUDENT_CONFIRMED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '学员已确认定位','media.positioning.student_confirmed','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_STUDENT_CONFIRMED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.student_confirmed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '学员已拒绝定位','ZSJ_N269_MEDIA_POSITIONING_STUDENT_REJECTED','中仕健消息中心','media.positioning.student_rejected','in_app','学员已拒绝定位','学员已拒绝定位','学员已拒绝定位。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_POSITIONING_STUDENT_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '学员已拒绝定位','media.positioning.student_rejected','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_POSITIONING_STUDENT_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.positioning.student_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '学员运营指派通知','ZSJ_N269_ZSJOS_STUDENT_OPERATOR_ASSIGNED','中仕健消息中心','zsjos.student.operator_assigned','in_app','学员运营指派通知','学员运营指派通知','已为你指派学员运营工作，请查看关联学员。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_STUDENT_OPERATOR_ASSIGNED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '学员运营指派通知','zsjos.student.operator_assigned','in_app',t.id,'["operator"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_STUDENT_OPERATOR_ASSIGNED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.student.operator_assigned');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '内容批次已提交','ZSJ_N269_ZSJOS_CONTENT_REVIEW_BATCH_SUBMITTED','中仕健消息中心','zsjos.content_review.batch_submitted','in_app','内容批次已提交','内容批次已提交','内容审核批次{{batchNo}}：内容批次已提交。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_BATCH_SUBMITTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '内容批次已提交','zsjos.content_review.batch_submitted','in_app',t.id,'["director"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_BATCH_SUBMITTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.batch_submitted');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '编导已审核内容','ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_ITEM_DECISION','中仕健消息中心','zsjos.content_review.director_item_decision','in_app','编导已审核内容','编导已审核内容','内容审核批次{{batchNo}}：编导已审核内容。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_ITEM_DECISION');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '编导已审核内容','zsjos.content_review.director_item_decision','in_app',t.id,'["operator"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_ITEM_DECISION'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.director_item_decision');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '编导审核通过','ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_COMPLETED','中仕健消息中心','zsjos.content_review.director_completed','in_app','编导审核通过','编导审核通过','内容审核批次{{batchNo}}：编导审核通过。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_COMPLETED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '编导审核通过','zsjos.content_review.director_completed','in_app',t.id,'["operator", "final_reviewer"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_COMPLETED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.director_completed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '编导审核驳回','ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_REJECTED','中仕健消息中心','zsjos.content_review.director_rejected','in_app','编导审核驳回','编导审核驳回','内容审核批次{{batchNo}}：编导审核驳回。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '编导审核驳回','zsjos.content_review.director_rejected','in_app',t.id,'["operator", "submitter"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.director_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '终审已审核内容','ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_ITEM_DECISION','中仕健消息中心','zsjos.content_review.final_item_decision','in_app','终审已审核内容','终审已审核内容','内容审核批次{{batchNo}}：终审已审核内容。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_ITEM_DECISION');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '终审已审核内容','zsjos.content_review.final_item_decision','in_app',t.id,'["operator", "director"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_ITEM_DECISION'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.final_item_decision');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '终审通过','ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_COMPLETED','中仕健消息中心','zsjos.content_review.final_completed','in_app','终审通过','终审通过','内容审核批次{{batchNo}}：终审通过。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_COMPLETED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '终审通过','zsjos.content_review.final_completed','in_app',t.id,'["operator", "director", "submitter"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_COMPLETED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.final_completed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '终审驳回','ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_REJECTED','中仕健消息中心','zsjos.content_review.final_rejected','in_app','终审驳回','终审驳回','内容审核批次{{batchNo}}：终审驳回。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '终审驳回','zsjos.content_review.final_rejected','in_app',t.id,'["operator", "director", "submitter"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.final_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '审核超时提醒','ZSJ_N269_ZSJOS_CONTENT_REVIEW_REVIEW_TIMEOUT_REMINDER','中仕健消息中心','zsjos.content_review.review_timeout_reminder','in_app','审核超时提醒','审核超时提醒','内容审核批次{{batchNo}}：审核超时提醒。',2,'["batchNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_REVIEW_TIMEOUT_REMINDER');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '审核超时提醒','zsjos.content_review.review_timeout_reminder','in_app',t.id,'["director", "final_reviewer"]','[]','business_detail','overdue',1440,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_REVIEW_TIMEOUT_REMINDER'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.content_review.review_timeout_reminder');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '收到指定工单','ZSJ_N269_ZSJOS_WORK_ORDER_ASSIGNED','中仕健消息中心','zsjos.work_order.assigned','in_app','收到指定工单','收到指定工单','工单{{orderNo}}：收到指定工单。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_ASSIGNED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '收到指定工单','zsjos.work_order.assigned','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_ASSIGNED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.assigned');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '候选池有新工单','ZSJ_N269_ZSJOS_WORK_ORDER_POOL_AVAILABLE','中仕健消息中心','zsjos.work_order.pool_available','in_app','候选池有新工单','候选池有新工单','工单{{orderNo}}：候选池有新工单。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_POOL_AVAILABLE');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '候选池有新工单','zsjos.work_order.pool_available','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_POOL_AVAILABLE'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.pool_available');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单已接单','ZSJ_N269_ZSJOS_WORK_ORDER_TAKEN','中仕健消息中心','zsjos.work_order.taken','in_app','工单已接单','工单已接单','工单{{orderNo}}：工单已接单。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_TAKEN');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单已接单','zsjos.work_order.taken','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_TAKEN'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.taken');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单被拒绝','ZSJ_N269_ZSJOS_WORK_ORDER_REJECTED','中仕健消息中心','zsjos.work_order.rejected','in_app','工单被拒绝','工单被拒绝','工单{{orderNo}}：工单被拒绝。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单被拒绝','zsjos.work_order.rejected','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单待验收','ZSJ_N269_ZSJOS_WORK_ORDER_REVIEW_REQUESTED','中仕健消息中心','zsjos.work_order.review_requested','in_app','工单待验收','工单待验收','工单{{orderNo}}：工单待验收。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_REVIEW_REQUESTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单待验收','zsjos.work_order.review_requested','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_REVIEW_REQUESTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.review_requested');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单已打回重做','ZSJ_N269_ZSJOS_WORK_ORDER_REWORKED','中仕健消息中心','zsjos.work_order.reworked','in_app','工单已打回重做','工单已打回重做','工单{{orderNo}}：工单已打回重做。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_REWORKED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单已打回重做','zsjos.work_order.reworked','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_REWORKED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.reworked');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单已验收通过','ZSJ_N269_ZSJOS_WORK_ORDER_COMPLETED','中仕健消息中心','zsjos.work_order.completed','in_app','工单已验收通过','工单已验收通过','工单{{orderNo}}：工单已验收通过。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_COMPLETED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单已验收通过','zsjos.work_order.completed','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_COMPLETED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.completed');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单不合格终止','ZSJ_N269_ZSJOS_WORK_ORDER_TERMINATED','中仕健消息中心','zsjos.work_order.terminated','in_app','工单不合格终止','工单不合格终止','工单{{orderNo}}：工单不合格终止。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_TERMINATED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单不合格终止','zsjos.work_order.terminated','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_TERMINATED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.terminated');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工单已撤回','ZSJ_N269_ZSJOS_WORK_ORDER_WITHDRAWN','中仕健消息中心','zsjos.work_order.withdrawn','in_app','工单已撤回','工单已撤回','工单{{orderNo}}：工单已撤回。',2,'["orderNo"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_ZSJOS_WORK_ORDER_WITHDRAWN');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工单已撤回','zsjos.work_order.withdrawn','in_app',t.id,'["recipient"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_ZSJOS_WORK_ORDER_WITHDRAWN'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.work_order.withdrawn');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工作任务分派','ZSJ_N269_WORK_TASK_ASSIGNED','中仕健消息中心','work_task_assigned','in_app','工作任务分派','工作任务分派','工作任务{{task.title}}：工作任务分派。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_ASSIGNED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工作任务分派','work_task_assigned','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_ASSIGNED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_assigned');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工作任务提醒','ZSJ_N269_WORK_TASK_REMINDER','中仕健消息中心','work_task_reminder','in_app','工作任务提醒','工作任务提醒','工作任务{{task.title}}：工作任务提醒。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_REMINDER');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工作任务提醒','work_task_reminder','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_REMINDER'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_reminder');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工作任务首次逾期','ZSJ_N269_WORK_TASK_OVERDUE','中仕健消息中心','work_task_overdue','in_app','工作任务首次逾期','工作任务首次逾期','工作任务{{task.title}}：工作任务首次逾期。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_OVERDUE');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工作任务首次逾期','work_task_overdue','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_OVERDUE'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_overdue');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '完成汇报待确认','ZSJ_N269_WORK_TASK_REPORT_SUBMITTED','中仕健消息中心','work_task_report_submitted','in_app','完成汇报待确认','完成汇报待确认','工作任务{{task.title}}：完成汇报待确认。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_REPORT_SUBMITTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '完成汇报待确认','work_task_report_submitted','in_app',t.id,'["confirmer"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_REPORT_SUBMITTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_report_submitted');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '任务确认完成','ZSJ_N269_WORK_TASK_CONFIRM_APPROVED','中仕健消息中心','work_task_confirm_approved','in_app','任务确认完成','任务确认完成','工作任务{{task.title}}：任务确认完成。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_CONFIRM_APPROVED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '任务确认完成','work_task_confirm_approved','in_app',t.id,'["assignee", "assigner"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_CONFIRM_APPROVED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_confirm_approved');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '完成汇报退回','ZSJ_N269_WORK_TASK_CONFIRM_REJECTED','中仕健消息中心','work_task_confirm_rejected','in_app','完成汇报退回','完成汇报退回','工作任务{{task.title}}：完成汇报退回。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_CONFIRM_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '完成汇报退回','work_task_confirm_rejected','in_app',t.id,'["assignee", "assigner"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_CONFIRM_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_confirm_rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工作任务调整','ZSJ_N269_WORK_TASK_ADJUSTED','中仕健消息中心','work_task_adjusted','in_app','工作任务调整','工作任务调整','工作任务{{task.title}}：工作任务调整。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_ADJUSTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工作任务调整','work_task_adjusted','in_app',t.id,'["assignee", "confirmer"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_ADJUSTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_adjusted');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '工作任务取消','ZSJ_N269_WORK_TASK_CANCELLED','中仕健消息中心','work_task_cancelled','in_app','工作任务取消','工作任务取消','工作任务{{task.title}}：工作任务取消。',2,'["task.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_TASK_CANCELLED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '工作任务取消','work_task_cancelled','in_app',t.id,'["assignee", "confirmer"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_TASK_CANCELLED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_task_cancelled');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '计划待总结','ZSJ_N269_WORK_PLAN_SUMMARY_READY','中仕健消息中心','work_plan_summary_ready','in_app','计划待总结','计划待总结','计划{{plan.title}}：计划待总结。',2,'["plan.title"]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WORK_PLAN_SUMMARY_READY');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '计划待总结','work_plan_summary_ready','in_app',t.id,'["plan_owner"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WORK_PLAN_SUMMARY_READY'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='work_plan_summary_ready');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '内容已待验收组批','ZSJ_N269_MEDIA_CONTENT_PENDING_ACCEPTANCE','中仕健消息中心','media.content.pending_acceptance','in_app','内容已待验收组批','内容已待验收组批','内容已待验收组批。请查看关联业务。',2,'[]',0,'V269 notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_MEDIA_CONTENT_PENDING_ACCEPTANCE');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '内容已待验收组批','media.content.pending_acceptance','in_app',t.id,'["assignee"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_MEDIA_CONTENT_PENDING_ACCEPTANCE'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='media.content.pending_acceptance');

-- Five existing withdrawal scenes; no new workflow or cancellation/settlement policy.
INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '提现申请待审核','ZSJ_N269_WITHDRAWAL_SUBMITTED','中世健消息中心','zsjos.withdrawal.submitted','in_app','提现申请待审核','提现申请待审核','提现单{{withdrawal.no}}，金额{{withdrawal.amount}}元，请审核。',2,'["withdrawal.no","withdrawal.amount"]',0,'V269 withdrawal notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WITHDRAWAL_SUBMITTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '提现申请待审核','zsjos.withdrawal.submitted','in_app',t.id,'["finance"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WITHDRAWAL_SUBMITTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.withdrawal.submitted');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '提现审批通过待打款','ZSJ_N269_WITHDRAWAL_APPROVED','中世健消息中心','zsjos.withdrawal.approved','in_app','提现审批通过待打款','提现审批通过待打款','提现单{{withdrawal.no}}已通过审批，金额{{withdrawal.amount}}元，等待打款。',2,'["withdrawal.no","withdrawal.amount"]',0,'V269 withdrawal notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WITHDRAWAL_APPROVED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '提现审批通过待打款','zsjos.withdrawal.approved','in_app',t.id,'["applicant","finance"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WITHDRAWAL_APPROVED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.withdrawal.approved');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '提现申请已驳回','ZSJ_N269_WITHDRAWAL_REJECTED','中世健消息中心','zsjos.withdrawal.rejected','in_app','提现申请已驳回','提现申请已驳回','提现单{{withdrawal.no}}已驳回，金额{{withdrawal.amount}}元。原因：{{withdrawal.rejectionReason}}。',2,'["withdrawal.no","withdrawal.amount","withdrawal.rejectionReason"]',0,'V269 withdrawal notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WITHDRAWAL_REJECTED');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '提现申请已驳回','zsjos.withdrawal.rejected','in_app',t.id,'["applicant","finance"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WITHDRAWAL_REJECTED'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.withdrawal.rejected');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '提现已记录打款','ZSJ_N269_WITHDRAWAL_PAID','中世健消息中心','zsjos.withdrawal.paid','in_app','提现已记录打款','提现已记录打款','提现单{{withdrawal.no}}已记录打款，金额{{withdrawal.amount}}元，请核对到账情况。',2,'["withdrawal.no","withdrawal.amount"]',0,'V269 withdrawal notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WITHDRAWAL_PAID');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '提现已记录打款','zsjos.withdrawal.paid','in_app',t.id,'["applicant","finance"]','[]','business_detail',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WITHDRAWAL_PAID'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.withdrawal.paid');

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
SELECT '财务提现周期提醒','ZSJ_N269_WITHDRAWAL_FINANCE_REMINDER','中世健消息中心','zsjos.withdrawal.finance_reminder','in_app','财务提现周期提醒','财务提现周期提醒','待审核{{pendingCount}}笔，待打款{{approvedCount}}笔、{{approvedAmount}}元，超时未完成{{overdueCount}}笔。',2,'["pendingCount","approvedCount","approvedAmount","overdueCount"]',0,'V269 withdrawal notification defaults','migration-V269','migration-V269'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE deleted=0 AND code='ZSJ_N269_WITHDRAWAL_FINANCE_REMINDER');
INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,creator,updater,tenant_id)
SELECT '财务提现周期提醒','zsjos.withdrawal.finance_reminder','in_app',t.id,'["finance"]','[]','none',NULL,NULL,0,'migration-V269','migration-V269',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.deleted=0 AND t.code='ZSJ_N269_WITHDRAWAL_FINANCE_REMINDER'
WHERE tenant.deleted=0 AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.deleted=0 AND r.tenant_id=tenant.id AND r.channel_code='in_app' AND r.scene_code='zsjos.withdrawal.finance_reminder');

-- Mirror business in_app templates, then all source rules for tenant/scenes without any WeCom rule.
-- This also covers business scenes introduced after V177 outside the 59 media and five withdrawal defaults above.
-- A single INSERT preserves multiple source recipients/timing rules for each missing scene.
-- Existing WeCom scenes (even disabled/customized) are never supplemented or overwritten.
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
       LEFT(CONCAT(COALESCE(source.`remark`,''),'；V269由业务站内信模板生成企微模板'),255),
       'migration-V269', NOW(), 'migration-V269', NOW(), b'0'
FROM `system_notify_template` source
WHERE source.`deleted`=b'0'
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
       'migration-V269', NOW(), 'migration-V269', NOW(), b'0', source_rule.`tenant_id`
FROM `system_notify_rule` source_rule
JOIN `system_notify_template` source_template ON source_template.`id`=source_rule.`template_id`
  AND source_template.`deleted`=b'0'
JOIN `system_notify_template` wecom_template
  ON wecom_template.`code`=(CASE WHEN CHAR_LENGTH(source_template.`code`) <= 58
                                 THEN CONCAT(source_template.`code`,'_WECOM')
                                 ELSE CONCAT(LEFT(source_template.`code`,47),'_',LEFT(MD5(source_template.`code`),8),'_WECOM') END)
 AND wecom_template.`scene_code`=source_template.`scene_code`
 AND wecom_template.`channel_code`='wecom' AND wecom_template.`deleted`=b'0'
WHERE source_rule.`deleted`=b'0'
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

INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V269','Complete director/operator notifications',SHA2('V269__director_operator_notifications.sql',256)) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V269','Complete director/operator notifications',SHA2('V269__director_operator_notifications.sql',256),'baseline') ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
