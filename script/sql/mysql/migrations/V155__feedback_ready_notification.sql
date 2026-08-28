-- V155: add the notification emitted when feedback becomes ready for handling.
-- Dependencies/order: apply after V149; fresh bootstrap runs this after V154.
-- Data scope: one System notification template and one missing default in-app rule per enabled tenant.
-- Existing feedback, notification messages, and administrator-maintained rules are not changed or replayed.
-- Repeatability: inserts are guarded by the stable template code and tenant-scoped scene code.
-- Recovery: forward-only. Disable the scene rules to retire delivery; keep historical messages and version rows.

DROP PROCEDURE IF EXISTS `zsjos_v155_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v155_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V149') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V155 requires V149';
  END IF;
  IF EXISTS (
    SELECT 1 FROM `system_notify_template`
    WHERE `code`='ZSJOS_FEEDBACK_READY_FOR_HANDLING' AND `deleted`=b'0'
      AND (`scene_code`<>'zsjos.feedback.ready_for_handling' OR `type`<>2)
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V155 blocked: feedback ready notification template code is already used';
  END IF;

  START TRANSACTION;
  INSERT INTO `system_notify_template`
    (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,
     `status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT '新反馈待处理','ZSJOS_FEEDBACK_READY_FOR_HANDLING','中世健消息中心',
         'zsjos.feedback.ready_for_handling','有新的反馈待处理','员工提交的反馈已进入待处理',
         '反馈 {{feedbackNo}}「{{feedbackTitle}}」已进入待处理，请及时查看并分派。',
         2,'["feedbackNo","feedbackTitle","deepLink"]',0,
         'V155 反馈进入待处理站内通知','migration-V155',NOW(),'migration-V155',NOW(),b'0'
  WHERE NOT EXISTS (
    SELECT 1 FROM `system_notify_template`
    WHERE `code`='ZSJOS_FEEDBACK_READY_FOR_HANDLING' AND `deleted`=b'0'
  );

  INSERT INTO `system_notify_rule`
    (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,
     `action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT '新反馈待处理通知','zsjos.feedback.ready_for_handling','in_app',template.`id`,
         '["dispatcher"]','[]','business_detail',0,
         'migration-V155',NOW(),'migration-V155',NOW(),b'0',tenant.`id`
  FROM `system_tenant` tenant
  INNER JOIN `system_notify_template` template
    ON template.`code`='ZSJOS_FEEDBACK_READY_FOR_HANDLING' AND template.`deleted`=b'0'
  WHERE tenant.`deleted`=b'0' AND tenant.`status`=0
    AND NOT EXISTS (
      SELECT 1 FROM `system_notify_rule` existing
      WHERE existing.`tenant_id`=tenant.`id`
        AND existing.`scene_code`='zsjos.feedback.ready_for_handling'
        AND existing.`deleted`=b'0'
    );

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V155','Feedback ready-for-handling notification',
          SHA2('V155__feedback_ready_notification.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V155','Feedback ready-for-handling notification',
          SHA2('V155__feedback_ready_notification.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v155_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v155_apply`;
