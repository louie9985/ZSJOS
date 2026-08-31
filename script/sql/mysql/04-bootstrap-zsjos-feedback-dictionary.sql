-- ZSJOS technical-support dictionary approved for V149.
-- Data scope: one dictionary type and five enabled values; no tenant or role data is changed.
-- Repeatability: stable IDs and values are inserted only when absent; identity conflicts stop execution.
-- Rollback limitation: historical feedback keeps its stored type, value, and label snapshots.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_feedback_dictionary_validate`;
DELIMITER $$
CREATE PROCEDURE `zsjos_feedback_dictionary_validate`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `id` = 2301 AND `type` <> 'zsjos_feedback_support_type'
  ) OR EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'zsjos_feedback_support_type' AND `id` <> 2301
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Feedback support dictionary type identity is already occupied';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE (`id` = 230101 AND (`dict_type` <> 'zsjos_feedback_support_type' OR `value` <> 'account_permission'))
       OR (`id` = 230102 AND (`dict_type` <> 'zsjos_feedback_support_type' OR `value` <> 'business_software'))
       OR (`id` = 230103 AND (`dict_type` <> 'zsjos_feedback_support_type' OR `value` <> 'office_equipment'))
       OR (`id` = 230104 AND (`dict_type` <> 'zsjos_feedback_support_type' OR `value` <> 'network_communication'))
       OR (`id` = 230105 AND (`dict_type` <> 'zsjos_feedback_support_type' OR `value` <> 'other'))
  ) OR EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'zsjos_feedback_support_type'
      AND `value` IN (
        'account_permission','business_software','office_equipment','network_communication','other'
      )
      AND `id` NOT BETWEEN 230101 AND 230105
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Feedback support dictionary data identity is already occupied';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_feedback_dictionary_validate`();
DROP PROCEDURE `zsjos_feedback_dictionary_validate`;

INSERT INTO `system_dict_type`
  (`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
SELECT 2301,'技术支持类型','zsjos_feedback_support_type',0,'需求与反馈技术支持类型',
       'bootstrap-V149',NOW(),'bootstrap-V149',NOW(),b'0',NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `id` = 2301);

INSERT INTO `system_dict_data`
  (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.`id`,seed.`sort`,seed.`label`,seed.`value`,'zsjos_feedback_support_type',
       0,seed.`color_type`,'','', 'bootstrap-V149',NOW(),'bootstrap-V149',NOW(),b'0'
FROM (
  SELECT 230101 AS `id`,1 AS `sort`,'账号与权限' AS `label`,
         'account_permission' AS `value`,'primary' AS `color_type`
  UNION ALL SELECT 230102,2,'业务系统/软件','business_software','success'
  UNION ALL SELECT 230103,3,'办公设备','office_equipment','warning'
  UNION ALL SELECT 230104,4,'网络与通信','network_communication','info'
  UNION ALL SELECT 230105,5,'其他','other','default'
) seed
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` existing WHERE existing.`id` = seed.`id`);
