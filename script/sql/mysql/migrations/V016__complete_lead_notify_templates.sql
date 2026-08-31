-- V016 补齐全部已注册客资通知场景的默认站内信模板。
-- Dependencies: V015、system_notify_template、zsjos_schema_version。
-- Data scope: only missing global templates for the 20 LeadNotifySceneProvider scene codes.
-- Repeatability: templates are inserted only when no active row with the same template code exists;
-- administrator-created or modified templates are never overwritten.
-- Rollback limitation: inserted templates may already be referenced by tenant rules; disable unused
-- templates instead of deleting them, and keep any generated message snapshots.

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.code,'中世健消息中心',seed.scene_code,seed.title,seed.summary,seed.content,
       2,seed.params,0,'全局默认模板；租户需自行创建并启用通知规则',
       'migration-V016',NOW(),'migration-V016',NOW(),b'0'
FROM (
  SELECT '客资新建' name,'ZSJOS_LEAD_CREATED' code,'zsjos.lead.created' scene_code,
         '客资提交成功' title,'客资{{lead.id}}已提交' summary,
         '客资{{lead.id}}已于{{event.time}}提交，主要意向产品为{{product.primaryName}}。' content,
         '["lead.id","event.time","product.primaryName"]' params
  UNION ALL SELECT '重复客资激活','ZSJOS_LEAD_ACTIVATED','zsjos.lead.activated',
         '重复客资已激活','客资{{lead.id}}收到新的重复提交',
         '客资{{lead.id}}已于{{event.time}}被再次激活，请当前负责人关注后续处理。',
         '["lead.id","event.time"]'
  UNION ALL SELECT '客资待接单','ZSJOS_LEAD_PENDING_ASSIGNMENT','zsjos.lead.assigned',
         '新客资待接单','客资{{lead.id}}已经派单给你，请尽快接受',
         '客资{{lead.id}}已经派单给你，请在{{lead.pendingExpiresAt}}前完成接单。',
         '["lead.id","lead.pendingExpiresAt"]'
  UNION ALL SELECT '客资重新派单','ZSJOS_LEAD_REASSIGNED','zsjos.lead.reassigned',
         '客资重新派单','客资{{lead.id}}已重新派单，请尽快处理',
         '客资{{lead.id}}已重新派单给你，请在{{lead.pendingExpiresAt}}前完成接单。',
         '["lead.id","lead.pendingExpiresAt"]'
  UNION ALL SELECT '客资接单成功','ZSJOS_LEAD_ACCEPTED','zsjos.lead.accepted',
         '客资已接单','客资{{lead.id}}已由{{owner.name}}接单',
         '客资{{lead.id}}已于{{event.time}}接单，当前负责人为{{owner.name}}。',
         '["lead.id","owner.name","event.time"]'
  UNION ALL SELECT '客资拒绝接单','ZSJOS_LEAD_REJECTED','zsjos.lead.rejected',
         '客资接单被拒绝','客资{{lead.id}}的本次派单已被拒绝',
         '客资{{lead.id}}的本次派单已于{{event.time}}被拒绝，系统将按派单规则继续处理。',
         '["lead.id","event.time"]'
  UNION ALL SELECT '客资接单超时','ZSJOS_LEAD_EXPIRED','zsjos.lead.expired',
         '客资接单超时','客资{{lead.id}}未在截止时间前完成接单',
         '客资{{lead.id}}的接单截止时间为{{lead.pendingExpiresAt}}，当前已超时。',
         '["lead.id","lead.pendingExpiresAt"]'
  UNION ALL SELECT '客资进入抢单池','ZSJOS_LEAD_PUBLIC_POOL','zsjos.lead.public_pool',
         '客资进入抢单池','客资{{lead.id}}已进入抢单池',
         '客资{{lead.id}}已于{{lead.publicPoolAt}}进入抢单池，原因：{{assignment.reason}}。',
         '["lead.id","lead.publicPoolAt","assignment.reason"]'
  UNION ALL SELECT '客资抢单成功','ZSJOS_LEAD_CLAIMED','zsjos.lead.claimed',
         '客资已被认领','客资{{lead.id}}已由{{owner.name}}认领',
         '客资{{lead.id}}已于{{event.time}}被{{owner.name}}认领，请及时开展首次跟进。',
         '["lead.id","owner.name","event.time"]'
  UNION ALL SELECT '客资管理员转派','ZSJOS_LEAD_TRANSFERRED','zsjos.lead.transferred',
         '客资负责人已变更','客资{{lead.id}}已转派给{{owner.name}}',
         '客资{{lead.id}}已于{{event.time}}完成管理员转派，当前负责人为{{owner.name}}。',
         '["lead.id","owner.name","event.time"]'
  UNION ALL SELECT '客资新增跟进','ZSJOS_LEAD_FOLLOW_UP_RECORDED','zsjos.lead.follow_up_recorded',
         '客资新增跟进','客资{{lead.id}}新增了一条{{followUp.method}}跟进记录',
         '客资{{lead.id}}的跟进结果为{{followUp.result}}，下次跟进时间为{{followUp.nextAt}}。',
         '["lead.id","followUp.method","followUp.result","followUp.nextAt"]'
  UNION ALL SELECT '客资分类变化','ZSJOS_LEAD_CATEGORY_CHANGED','zsjos.lead.category_changed',
         '客资分类已变更','客资{{lead.id}}的分类由{{category.before}}变更为{{category.after}}',
         '客资{{lead.id}}的分类已于{{event.time}}由{{category.before}}变更为{{category.after}}。',
         '["lead.id","category.before","category.after","event.time"]'
  UNION ALL SELECT '客资判定超时挂起','ZSJOS_LEAD_QUALIFICATION_SUSPENDED','zsjos.lead.qualification_suspended',
         '客资判定超时挂起','客资{{lead.id}}已因判定超时挂起',
         '客资{{lead.id}}未在{{lead.qualificationDeadlineAt}}前完成有效性判定，现已挂起，请主管处理。',
         '["lead.id","lead.qualificationDeadlineAt"]'
  UNION ALL SELECT '挂起客资恢复','ZSJOS_LEAD_QUALIFICATION_RESTORED','zsjos.lead.qualification_restored',
         '挂起客资已恢复','客资{{lead.id}}已恢复处理',
         '客资{{lead.id}}已于{{event.time}}恢复，处置理由：{{qualification.reason}}。',
         '["lead.id","event.time","qualification.reason"]'
  UNION ALL SELECT '异常客资转派','ZSJOS_LEAD_QUALIFICATION_TRANSFERRED','zsjos.lead.qualification_transferred',
         '异常客资已转派','客资{{lead.id}}已转派给{{owner.name}}',
         '客资{{lead.id}}已转派给{{owner.name}}，处置理由：{{qualification.reason}}。',
         '["lead.id","owner.name","qualification.reason"]'
  UNION ALL SELECT '挂起客资回收','ZSJOS_LEAD_QUALIFICATION_RECYCLED','zsjos.lead.qualification_recycled',
         '挂起客资已回收','客资{{lead.id}}已进入回收待处理',
         '客资{{lead.id}}已于{{event.time}}回收，处置理由：{{qualification.reason}}。',
         '["lead.id","event.time","qualification.reason"]'
  UNION ALL SELECT '异常客资释放到抢单池','ZSJOS_LEAD_QUALIFICATION_RELEASED','zsjos.lead.qualification_released',
         '异常客资已释放','客资{{lead.id}}已释放到抢单池',
         '客资{{lead.id}}已释放到抢单池，处置理由：{{qualification.reason}}。',
         '["lead.id","qualification.reason"]'
  UNION ALL SELECT '客资申诉待处理','ZSJOS_LEAD_APPEAL_SUBMITTED','zsjos.lead.appeal_submitted',
         '客资申诉待处理','客资{{lead.id}}有新的第{{appeal.roundNo}}次申诉待处理',
         '客资{{lead.id}}有新的申诉，请进入申诉处理页面完成审核。',
         '["lead.id","appeal.roundNo"]'
  UNION ALL SELECT '客资申诉改判有效','ZSJOS_LEAD_APPEAL_OVERTURNED','zsjos.lead.appeal_overturned',
         '客资申诉已改判有效','客资{{lead.id}}的第{{appeal.roundNo}}次申诉已改判有效',
         '客资{{lead.id}}已恢复为有效客资，裁决理由：{{appeal.decisionReason}}。',
         '["lead.id","appeal.roundNo","appeal.decisionReason"]'
  UNION ALL SELECT '客资申诉维持无效','ZSJOS_LEAD_APPEAL_UPHELD','zsjos.lead.appeal_upheld',
         '客资申诉维持无效','客资{{lead.id}}的第{{appeal.roundNo}}次申诉维持无效',
         '客资{{lead.id}}本轮申诉维持无效，裁决理由：{{appeal.decisionReason}}。',
         '["lead.id","appeal.roundNo","appeal.decisionReason"]'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template` existing
  WHERE existing.code=seed.code AND existing.deleted=b'0'
);

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V016','Complete default templates for registered lead notification scenes','complete-lead-notify-templates-v1');
