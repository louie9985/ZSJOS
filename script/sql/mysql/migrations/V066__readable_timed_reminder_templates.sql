-- V066: readable Chinese copy for advance, due, and overdue lead reminders.
-- Dependency/order: apply after V065; V031 must already provide timed reminder scenes and default rules.
-- Data scope: inserts nine global System notification templates and repoints only untouched V031 default
-- rules whose rule and current template still have migration-V031 as both creator and updater.
-- Repeatability: template codes are existence-guarded; already-repointed or administrator-edited rows are unchanged.
-- Rollback limitation: existing messages keep their rendered text. Disabling the new templates or manually restoring
-- untouched default rules to the V031 templates is possible, but administrator-customized mappings must be preserved.

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT x.name,x.code,'中世健消息中心',x.scene_code,x.title,x.summary,x.content,2,
       '["lead.name","reminder.dueAt"]',0,'V066 分阶段中文提醒模板',
       'migration-V066',NOW(),'migration-V066',NOW(),b'0'
FROM (
 SELECT '首次跟进即将到期' name,'ZSJOS_FIRST_FOLLOW_UP_ADVANCE' code,'zsjos.lead.first_follow_up_reminder' scene_code,
        '首次跟进即将到期' title,'客资{{lead.name}}的首次跟进即将到期' summary,
        '客资{{lead.name}}的首次跟进截止时间为{{reminder.dueAt}}，请在截止前完成首次跟进。' content
 UNION ALL SELECT '首次跟进已到期','ZSJOS_FIRST_FOLLOW_UP_DUE','zsjos.lead.first_follow_up_reminder',
        '首次跟进已到期','客资{{lead.name}}的首次跟进已到截止时间',
        '客资{{lead.name}}的首次跟进已到截止时间{{reminder.dueAt}}，请立即完成首次跟进。'
 UNION ALL SELECT '首次跟进已逾期','ZSJOS_FIRST_FOLLOW_UP_OVERDUE','zsjos.lead.first_follow_up_reminder',
        '首次跟进已逾期','客资{{lead.name}}的首次跟进已逾期',
        '客资{{lead.name}}的首次跟进已超过截止时间{{reminder.dueAt}}，请尽快补充首次跟进记录。'
 UNION ALL SELECT '下次跟进即将到期','ZSJOS_NEXT_FOLLOW_UP_ADVANCE','zsjos.lead.next_follow_up_reminder',
        '下次跟进即将到期','客资{{lead.name}}的下次跟进即将到期',
        '客资{{lead.name}}的下次跟进时间为{{reminder.dueAt}}，请在截止前完成本次跟进。'
 UNION ALL SELECT '下次跟进已到期','ZSJOS_NEXT_FOLLOW_UP_DUE','zsjos.lead.next_follow_up_reminder',
        '下次跟进已到期','客资{{lead.name}}的下次跟进已到截止时间',
        '客资{{lead.name}}的下次跟进已到截止时间{{reminder.dueAt}}，请立即跟进并记录结果。'
 UNION ALL SELECT '下次跟进已逾期','ZSJOS_NEXT_FOLLOW_UP_OVERDUE','zsjos.lead.next_follow_up_reminder',
        '下次跟进已逾期','客资{{lead.name}}的下次跟进已逾期',
        '客资{{lead.name}}的下次跟进已超过截止时间{{reminder.dueAt}}，请尽快补充跟进记录并安排后续动作。'
 UNION ALL SELECT '有效性判定即将到期','ZSJOS_QUALIFICATION_ADVANCE','zsjos.lead.qualification_reminder',
        '有效性判定即将到期','客资{{lead.name}}的有效性判定即将到期',
        '客资{{lead.name}}的有效性判定截止时间为{{reminder.dueAt}}，请在截止前完成判定。'
 UNION ALL SELECT '有效性判定已到期','ZSJOS_QUALIFICATION_DUE','zsjos.lead.qualification_reminder',
        '有效性判定已到期','客资{{lead.name}}的有效性判定已到截止时间',
        '客资{{lead.name}}的有效性判定已到截止时间{{reminder.dueAt}}，请立即完成判定，避免客资继续滞留。'
 UNION ALL SELECT '有效性判定已逾期','ZSJOS_QUALIFICATION_OVERDUE','zsjos.lead.qualification_reminder',
        '有效性判定已逾期','客资{{lead.name}}的有效性判定已逾期',
        '客资{{lead.name}}的有效性判定已超过截止时间{{reminder.dueAt}}，请尽快完成判定并处理异常。'
) x
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` t WHERE t.`code`=x.code);

UPDATE `system_notify_rule` r
JOIN `system_notify_template` old_template ON old_template.`id`=r.`template_id`
JOIN `system_notify_template` new_template
  ON new_template.`code`=CASE
    WHEN r.`scene_code`='zsjos.lead.first_follow_up_reminder' AND r.`timing_stage`='advance' THEN 'ZSJOS_FIRST_FOLLOW_UP_ADVANCE'
    WHEN r.`scene_code`='zsjos.lead.first_follow_up_reminder' AND r.`timing_stage`='due' THEN 'ZSJOS_FIRST_FOLLOW_UP_DUE'
    WHEN r.`scene_code`='zsjos.lead.first_follow_up_reminder' AND r.`timing_stage`='overdue' THEN 'ZSJOS_FIRST_FOLLOW_UP_OVERDUE'
    WHEN r.`scene_code`='zsjos.lead.next_follow_up_reminder' AND r.`timing_stage`='advance' THEN 'ZSJOS_NEXT_FOLLOW_UP_ADVANCE'
    WHEN r.`scene_code`='zsjos.lead.next_follow_up_reminder' AND r.`timing_stage`='due' THEN 'ZSJOS_NEXT_FOLLOW_UP_DUE'
    WHEN r.`scene_code`='zsjos.lead.next_follow_up_reminder' AND r.`timing_stage`='overdue' THEN 'ZSJOS_NEXT_FOLLOW_UP_OVERDUE'
    WHEN r.`scene_code`='zsjos.lead.qualification_reminder' AND r.`timing_stage`='advance' THEN 'ZSJOS_QUALIFICATION_ADVANCE'
    WHEN r.`scene_code`='zsjos.lead.qualification_reminder' AND r.`timing_stage`='due' THEN 'ZSJOS_QUALIFICATION_DUE'
    WHEN r.`scene_code`='zsjos.lead.qualification_reminder' AND r.`timing_stage`='overdue' THEN 'ZSJOS_QUALIFICATION_OVERDUE'
  END
 AND new_template.`deleted`=b'0'
SET r.`template_id`=new_template.`id`,r.`updater`='migration-V066',r.`update_time`=NOW()
WHERE r.`creator`='migration-V031' AND r.`updater`='migration-V031' AND r.`deleted`=b'0'
  AND old_template.`creator`='migration-V031' AND old_template.`updater`='migration-V031'
  AND old_template.`code` IN ('ZSJOS_FIRST_FOLLOW_UP_REMINDER','ZSJOS_NEXT_FOLLOW_UP_REMINDER','ZSJOS_QUALIFICATION_REMINDER')
  AND r.`timing_stage` IN ('advance','due','overdue');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V066','readable timed reminder templates','V066__readable_timed_reminder_templates.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V066','readable timed reminder templates',
        SHA2('V066__readable_timed_reminder_templates.sql',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
