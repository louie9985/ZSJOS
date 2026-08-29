-- V163: notify submitters when sales first follow-up is unreachable.
-- Dependencies: V162 in both schema-version registries, V008 follow-up dictionaries, and System business notifications.
-- Scope: dictionary presence, default notification template/rules, and schema-version registries only.
-- Repeatability: guarded inserts and upserts make reruns safe; administrator-edited rules/templates are not overwritten.
-- Rollback: disable the seeded notification rule/template in a later reviewed migration; keep historical message snapshots.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v163_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v163_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V162')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version` WHERE `module_code`='core' AND `version`='V162') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V163 requires V162 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_lead_follow_up_result' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V163 requires zsjos_lead_follow_up_result dictionary type';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code`='ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED'
             AND `deleted`=b'0' AND `scene_code`<>'zsjos.lead.submitter_assist_requested') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Template code ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED is owned by another scene';
  END IF;

  START TRANSACTION;

  INSERT INTO `system_dict_data`
    (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 4,'未联系上','unreachable','zsjos_lead_follow_up_result',0,'warning','',
         '销售首次跟进未联系上；提交后提醒来源提交人协助处理','migration-V163',NOW(),'migration-V163',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data`
                    WHERE `dict_type`='zsjos_lead_follow_up_result' AND `value`='unreachable' AND `deleted`=b'0');

  INSERT INTO `system_notify_template`
    (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT '提交人协助处理','ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED','中世健消息中心',
         'zsjos.lead.submitter_assist_requested','请协助处理客资',
         '客资{{lead.no}}首次跟进未联系上，请协助确认信息',
         '客资{{lead.no}}由{{owner.name}}首次跟进后标记为{{followUp.result}}，备注：{{followUp.remark}}。请补充或确认客资信息，协助后续处理。',
         2,'["lead.no","owner.name","followUp.result","followUp.remark"]',0,
         'V163 系统默认模板；使用客资编号 lead.no，不展示内部客资ID',
         'migration-V163',NOW(),'migration-V163',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template`
                    WHERE `code`='ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED' AND `deleted`=b'0');

  INSERT INTO `system_notify_rule`
    (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT '提交人协助处理','zsjos.lead.submitter_assist_requested','in_app',template.id,
         '["submitter"]','[]','business_detail',0,'migration-V163',NOW(),'migration-V163',NOW(),b'0',tenant.id
  FROM `system_tenant` tenant
  JOIN `system_notify_template` template ON template.code='ZSJOS_LEAD_SUBMITTER_ASSIST_REQUESTED' AND template.deleted=b'0'
  WHERE tenant.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
                    WHERE rule_row.tenant_id=tenant.id
                      AND rule_row.scene_code='zsjos.lead.submitter_assist_requested'
                      AND rule_row.channel_code='in_app'
                      AND rule_row.creator='migration-V163'
                      AND rule_row.deleted=b'0');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V163','Lead unreachable submitter assist notification','V163__lead_unreachable_submitter_assist.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V163','Lead unreachable submitter assist notification',SHA2('V163__lead_unreachable_submitter_assist.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v163_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v163_apply`;
