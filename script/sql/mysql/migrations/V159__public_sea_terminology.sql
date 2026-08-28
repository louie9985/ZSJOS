-- V159: unify user-visible ZSJOS public-sea terminology from "超期公海/商机公海/人工公海" to "公海".
-- Dependencies: V158 and the existing V034/V036 public-sea metadata.
-- Repeatable and non-destructive: only menu names, filter scheme names, notification template/rule text,
-- and table/column comments are updated. No business Lead, order, assignment, public-sea or notification
-- delivery records are changed.
-- Rollback limitation: reverting the wording requires a reviewed forward migration.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v159_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v159_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V158')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V158') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V159 requires V158 in both schema-version registries';
  END IF;

  START TRANSACTION;

  UPDATE `system_menu`
  SET `name`=CASE `id`
      WHEN 6794 THEN '公海'
      WHEN 6795 THEN '管理部门公海'
      WHEN 6796 THEN '管理全部公海'
      ELSE `name` END,
      `updater`='V159',`update_time`=NOW()
  WHERE `id` IN (6794,6795,6796) AND `deleted`=b'0';

  UPDATE `zsjos_lead_inbox_filter_scheme`
  SET `name`='公海视角',`updater`='V159',`update_time`=NOW()
  WHERE `audience`='agingPool' AND `deleted`=b'0'
    AND `name` IN ('超期公海视角','商机公海视角','人工公海视角');

  UPDATE `system_notify_template`
  SET `name`='公海提前提醒',
      `title`='客资即将进入公海',
      `summary`='{{lead.name}}将在{{agingPool.dueAt}}进入公海',
      `content`='客资{{lead.name}}将在{{agingPool.dueAt}}进入公海，请及时推进成交。',
      `remark`='V159 公海模板',`updater`='V159',`update_time`=NOW()
  WHERE `code`='ZSJOS_AGING_POOL_REMINDER' AND `deleted`=b'0';

  UPDATE `system_notify_template`
  SET `name`='公海到期',
      `title`='客资已进入公海',
      `summary`='{{lead.name}}已进入公海',
      `content`='客资{{lead.name}}已到期进入公海，等待主管指派协同销售。',
      `remark`='V159 公海模板',`updater`='V159',`update_time`=NOW()
  WHERE `code`='ZSJOS_AGING_POOL_DUE' AND `deleted`=b'0';

  UPDATE `system_notify_template`
  SET `name`='公海指派',
      `title`='公海协同指派',
      `summary`='{{lead.name}}已指派协同销售',
      `content`='客资{{lead.name}}已完成协同销售指派。',
      `remark`='V159 公海模板',`updater`='V159',`update_time`=NOW()
  WHERE `code`='ZSJOS_AGING_POOL_ASSIGNED' AND `deleted`=b'0';

  UPDATE `system_notify_template`
  SET `name`='公海换派',
      `title`='公海协同换派',
      `summary`='{{lead.name}}已更换协同销售',
      `content`='客资{{lead.name}}的协同销售已变更。',
      `remark`='V159 公海模板',`updater`='V159',`update_time`=NOW()
  WHERE `code`='ZSJOS_AGING_POOL_REASSIGNED' AND `deleted`=b'0';

  UPDATE `system_notify_template`
  SET `name`='公海待重派',
      `title`='公海待重新指派',
      `summary`='{{lead.name}}需要重新指派协同销售',
      `content`='客资{{lead.name}}的原协同销售已失效，请主管重新指派。',
      `remark`='V159 公海模板',`updater`='V159',`update_time`=NOW()
  WHERE `code`='ZSJOS_AGING_POOL_REASSIGN_REQUIRED' AND `deleted`=b'0';

  UPDATE `system_notify_template`
  SET `name`='公海退出',
      `title`='客资退出公海',
      `summary`='{{lead.name}}已退出公海',
      `content`='客资{{lead.name}}已由主管退出公海，恢复原销售独占推进。',
      `remark`='V159 公海模板',`updater`='V159',`update_time`=NOW()
  WHERE `code`='ZSJOS_AGING_POOL_EXITED' AND `deleted`=b'0';

  UPDATE `system_notify_rule`
  SET `name`=CASE `scene_code`
      WHEN 'zsjos.lead.aging_pool_reminder' THEN '公海-提前7天'
      WHEN 'zsjos.lead.aging_pool_due' THEN '公海到期通知'
      WHEN 'zsjos.lead.aging_pool_assigned' THEN '公海指派通知'
      WHEN 'zsjos.lead.aging_pool_reassigned' THEN '公海换派通知'
      WHEN 'zsjos.lead.aging_pool_reassign_required' THEN '公海待重派通知'
      WHEN 'zsjos.lead.aging_pool_exited' THEN '公海退出通知'
      ELSE `name` END,
      `updater`='V159',`update_time`=NOW()
  WHERE `scene_code` IN (
      'zsjos.lead.aging_pool_reminder','zsjos.lead.aging_pool_due',
      'zsjos.lead.aging_pool_assigned','zsjos.lead.aging_pool_reassigned',
      'zsjos.lead.aging_pool_reassign_required','zsjos.lead.aging_pool_exited')
    AND `deleted`=b'0';

  ALTER TABLE `zsjos_lead_follow_up_rule`
    MODIFY COLUMN `aging_pool_timeout_days` int NOT NULL DEFAULT 90 COMMENT '公海期限（自然日）';
  ALTER TABLE `zsjos_lead_aging_pool_cycle` COMMENT='ZSJOS 客资公海周期';
  ALTER TABLE `zsjos_lead_aging_pool_event` COMMENT='ZSJOS 客资公海事件';
  ALTER TABLE `zsjos_lead_aging_pool_notify_stage` COMMENT='ZSJOS 公海提前通知幂等阶段';
  ALTER TABLE `zsjos_lead_public_sea_record` COMMENT='ZSJOS 公海协作记录';

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  SELECT 'V159','Unify public sea user-visible terminology',SHA2('V159__public_sea_terminology.sql',256),NOW()
  WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V159');
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  SELECT 'core','V159','Unify public sea user-visible terminology',SHA2('V159__public_sea_terminology.sql',256),'baseline',NOW()
  WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V159');

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v159_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v159_apply`;
