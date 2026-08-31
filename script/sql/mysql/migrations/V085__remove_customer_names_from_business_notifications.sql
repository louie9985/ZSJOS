-- V085: replace customer/student names in ZSJOS notification contracts and snapshots.
-- Dependencies/order: apply after V084; requires V054 Lead numbers and the order/registration tables.
-- Data scope: ZSJOS Lead, sales-order and registration notification templates/messages only.
-- Repeatability: token replacements, JSON key updates and version upserts are idempotent.
-- Safety: aborts before mutation when a targeted structured message has invalid JSON or no
-- determinable business identifier. It never guesses names in arbitrary free text.
-- Recovery: forward-only; take a database backup before applying because rendered snapshots change.

DELIMITER $$
DROP PROCEDURE IF EXISTS `zsjos_assert_v085_notification_snapshots`$$
CREATE PROCEDURE `zsjos_assert_v085_notification_snapshots`()
BEGIN
  DECLARE invalid_count BIGINT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  LEFT JOIN `zsjos_lead` lead_row
    ON message_row.biz_type='lead' AND lead_row.id=message_row.biz_id
       AND lead_row.deleted=b'0' AND lead_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` order_row
    ON message_row.biz_type='sales_order' AND order_row.id=message_row.biz_id
       AND order_row.deleted=b'0' AND order_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_registration_case` registration_row
    ON message_row.biz_type='registration_case' AND registration_row.id=message_row.biz_id
       AND registration_row.deleted=b'0' AND registration_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` registration_order
    ON registration_order.id=registration_row.order_id
       AND registration_order.deleted=b'0' AND registration_order.tenant_id=message_row.tenant_id
  WHERE message_row.deleted=b'0'
    AND ((message_row.scene_code LIKE 'zsjos.lead.%' AND message_row.biz_type='lead'
          AND ((JSON_VALID(message_row.template_params)=0 AND message_row.template_params LIKE '%lead.name%')
               OR (JSON_VALID(message_row.template_params)=1
                   AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"')=1
                   AND (lead_row.lead_no IS NULL OR lead_row.lead_no=''))))
      OR (message_row.scene_code LIKE 'zsjos.sales_order.%' AND message_row.biz_type='sales_order'
          AND ((JSON_VALID(message_row.template_params)=0 AND message_row.template_params LIKE '%order.studentName%')
               OR (JSON_VALID(message_row.template_params)=1
                   AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"')=1
                   AND (order_row.order_no IS NULL OR order_row.order_no=''))))
      OR (message_row.scene_code LIKE 'zsjos.registration.%' AND message_row.biz_type='registration_case'
          AND ((JSON_VALID(message_row.template_params)=0 AND message_row.template_params LIKE '%student.name%')
               OR (JSON_VALID(message_row.template_params)=1
                   AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"')=1
                   AND (registration_order.order_no IS NULL OR registration_order.order_no='')))));

  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V085 blocked: targeted notification snapshots are not safely resolvable';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_assert_v085_notification_snapshots`();
DROP PROCEDURE IF EXISTS `zsjos_assert_v085_notification_snapshots`;

UPDATE `system_notify_template`
SET `title`=REPLACE(REPLACE(`title`,'{{lead.name}}','{{lead.no}}'),'{lead.name}','{lead.no}'),
    `summary`=REPLACE(REPLACE(`summary`,'{{lead.name}}','{{lead.no}}'),'{lead.name}','{lead.no}'),
    `content`=REPLACE(REPLACE(`content`,'{{lead.name}}','{{lead.no}}'),'{lead.name}','{lead.no}'),
    `params`=REPLACE(`params`,'"lead.name"','"lead.no"'),
    `updater`='migration-V085',`update_time`=NOW()
WHERE `deleted`=b'0' AND `scene_code` LIKE 'zsjos.lead.%'
  AND (`title` LIKE '%lead.name%' OR `summary` LIKE '%lead.name%'
       OR `content` LIKE '%lead.name%' OR `params` LIKE '%lead.name%');

UPDATE `system_notify_template`
SET `title`=REPLACE(REPLACE(`title`,'{{order.studentName}}','{{order.no}}'),'{order.studentName}','{order.no}'),
    `summary`=REPLACE(REPLACE(`summary`,'{{order.studentName}}','{{order.no}}'),'{order.studentName}','{order.no}'),
    `content`=REPLACE(REPLACE(`content`,'{{order.studentName}}','{{order.no}}'),'{order.studentName}','{order.no}'),
    `params`=REPLACE(`params`,'"order.studentName"','"order.no"'),
    `updater`='migration-V085',`update_time`=NOW()
WHERE `deleted`=b'0' AND `scene_code` LIKE 'zsjos.sales_order.%'
  AND (`title` LIKE '%order.studentName%' OR `summary` LIKE '%order.studentName%'
       OR `content` LIKE '%order.studentName%' OR `params` LIKE '%order.studentName%');

UPDATE `system_notify_template`
SET `title`=REPLACE(REPLACE(`title`,'{{student.name}}','{{order.no}}'),'{student.name}','{order.no}'),
    `summary`=REPLACE(REPLACE(`summary`,'{{student.name}}','{{order.no}}'),'{student.name}','{order.no}'),
    `content`=REPLACE(REPLACE(`content`,'{{student.name}}','{{order.no}}'),'{student.name}','{order.no}'),
    `params`=REPLACE(`params`,'"student.name"','"order.no"'),
    `updater`='migration-V085',`update_time`=NOW()
WHERE `deleted`=b'0' AND `scene_code`='zsjos.registration.task_created'
  AND (`title` LIKE '%student.name%' OR `summary` LIKE '%student.name%'
       OR `content` LIKE '%student.name%' OR `params` LIKE '%student.name%');

UPDATE `system_notify_template`
SET `title`=REPLACE(REPLACE(`title`,'{{student.name}}','{{lead.no}}'),'{student.name}','{lead.no}'),
    `summary`=REPLACE(REPLACE(`summary`,'{{student.name}}','{{lead.no}}'),'{student.name}','{lead.no}'),
    `content`=REPLACE(REPLACE(`content`,'{{student.name}}','{{lead.no}}'),'{student.name}','{lead.no}'),
    `params`=REPLACE(`params`,'"student.name"','"lead.no"'),
    `updater`='migration-V085',`update_time`=NOW()
WHERE `deleted`=b'0' AND `scene_code` LIKE 'zsjos.registration.%'
  AND `scene_code`<>'zsjos.registration.task_created'
  AND (`title` LIKE '%student.name%' OR `summary` LIKE '%student.name%'
       OR `content` LIKE '%student.name%' OR `params` LIKE '%student.name%');

UPDATE `system_notify_message` message_row
JOIN `zsjos_lead` lead_row
  ON lead_row.id=message_row.biz_id AND lead_row.deleted=b'0'
 AND lead_row.tenant_id=message_row.tenant_id
SET message_row.template_title=REPLACE(message_row.template_title,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.name"')),lead_row.lead_no),
    message_row.template_summary=REPLACE(message_row.template_summary,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.name"')),lead_row.lead_no),
    message_row.template_content=REPLACE(message_row.template_content,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.name"')),lead_row.lead_no),
    message_row.template_params=JSON_SET(JSON_REMOVE(message_row.template_params,'$."lead.name"'),
      '$."lead.no"',lead_row.lead_no),
    message_row.updater='migration-V085',message_row.update_time=NOW()
WHERE message_row.deleted=b'0' AND message_row.scene_code LIKE 'zsjos.lead.%'
  AND message_row.biz_type='lead' AND JSON_VALID(message_row.template_params)
  AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"');

UPDATE `system_notify_message` message_row
JOIN `zsjos_order` order_row
  ON order_row.id=message_row.biz_id AND order_row.deleted=b'0'
 AND order_row.tenant_id=message_row.tenant_id
SET message_row.template_title=REPLACE(message_row.template_title,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.studentName"')),order_row.order_no),
    message_row.template_summary=REPLACE(message_row.template_summary,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.studentName"')),order_row.order_no),
    message_row.template_content=REPLACE(message_row.template_content,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.studentName"')),order_row.order_no),
    message_row.template_params=JSON_SET(JSON_REMOVE(message_row.template_params,'$."order.studentName"'),
      '$."order.no"',order_row.order_no),
    message_row.updater='migration-V085',message_row.update_time=NOW()
WHERE message_row.deleted=b'0' AND message_row.scene_code LIKE 'zsjos.sales_order.%'
  AND message_row.biz_type='sales_order' AND JSON_VALID(message_row.template_params)
  AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"');

UPDATE `system_notify_message` message_row
JOIN `zsjos_registration_case` registration_row
  ON registration_row.id=message_row.biz_id AND registration_row.deleted=b'0'
 AND registration_row.tenant_id=message_row.tenant_id
JOIN `zsjos_order` order_row
  ON order_row.id=registration_row.order_id AND order_row.deleted=b'0'
 AND order_row.tenant_id=message_row.tenant_id
LEFT JOIN `zsjos_lead` lead_row
  ON lead_row.id=order_row.lead_id AND lead_row.deleted=b'0'
 AND lead_row.tenant_id=message_row.tenant_id
SET message_row.template_title=REPLACE(message_row.template_title,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."student.name"')),
      CASE WHEN message_row.scene_code='zsjos.registration.task_created' THEN order_row.order_no
           ELSE COALESCE(NULLIF(lead_row.lead_no,''),order_row.order_no) END),
    message_row.template_summary=REPLACE(message_row.template_summary,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."student.name"')),
      CASE WHEN message_row.scene_code='zsjos.registration.task_created' THEN order_row.order_no
           ELSE COALESCE(NULLIF(lead_row.lead_no,''),order_row.order_no) END),
    message_row.template_content=REPLACE(message_row.template_content,
      JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."student.name"')),
      CASE WHEN message_row.scene_code='zsjos.registration.task_created' THEN order_row.order_no
           ELSE COALESCE(NULLIF(lead_row.lead_no,''),order_row.order_no) END),
    message_row.template_params=JSON_REMOVE(message_row.template_params,'$."student.name"'),
    message_row.updater='migration-V085',message_row.update_time=NOW()
WHERE message_row.deleted=b'0' AND message_row.scene_code LIKE 'zsjos.registration.%'
  AND message_row.biz_type='registration_case' AND JSON_VALID(message_row.template_params)
  AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V085','Remove customer names from business notifications',
        'V085__remove_customer_names_from_business_notifications.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V085','Remove customer names from business notifications',
        SHA2('V085__remove_customer_names_from_business_notifications.sql',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
