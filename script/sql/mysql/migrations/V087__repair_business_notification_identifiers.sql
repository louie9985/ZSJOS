-- V087: forward repair for the already-applied V085 notification identifier migration.
-- Dependencies/order: apply after V085 and the occupied V086 Lead-detail permission migration.
-- Data scope: ZSJOS Lead, sales-order and registration notification templates and snapshots,
-- including logically deleted messages and business records. No account or permission data changes.
-- Repeatability: templates are rebuilt with unique params; messages update only when keys differ.
-- Recovery: forward-only. Back up notification and related business tables before real execution.
-- Limitation: rows whose V085 source name key is already gone cannot be checked against that old
-- name without a pre-V085 backup; this migration never guesses names or overwrites message bodies.

DELIMITER $$
DROP PROCEDURE IF EXISTS `zsjos_apply_v087_notification_repairs`$$
CREATE PROCEDURE `zsjos_apply_v087_notification_repairs`()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE invalid_count BIGINT DEFAULT 0;
  DECLARE template_id BIGINT;
  DECLARE template_scene VARCHAR(64);
  DECLARE template_title VARCHAR(512);
  DECLARE template_summary TEXT;
  DECLARE template_content LONGTEXT;
  DECLARE template_params LONGTEXT;
  DECLARE next_title VARCHAR(512);
  DECLARE next_summary TEXT;
  DECLARE next_content LONGTEXT;
  DECLARE normalized_params JSON;
  DECLARE param_index INT DEFAULT 0;
  DECLARE param_count INT DEFAULT 0;
  DECLARE param_type VARCHAR(16);
  DECLARE param_value VARCHAR(255);
  DECLARE mapped_param VARCHAR(255);

  DECLARE template_cursor CURSOR FOR
    SELECT `id`,`scene_code`,`title`,`summary`,`content`,`params`
    FROM `system_notify_template`
    WHERE `scene_code` LIKE 'zsjos.lead.%'
       OR `scene_code` LIKE 'zsjos.sales_order.%'
       OR `scene_code` LIKE 'zsjos.registration.%'
    ORDER BY `id`;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS `tmp_v087_notify_message_repair`;
    DROP TEMPORARY TABLE IF EXISTS `tmp_v087_target_message`;
    RESIGNAL;
  END;

  DROP TEMPORARY TABLE IF EXISTS `tmp_v087_notify_message_repair`;
  DROP TEMPORARY TABLE IF EXISTS `tmp_v087_target_message`;
  CREATE TEMPORARY TABLE `tmp_v087_target_message` (
    `message_id` bigint NOT NULL,
    PRIMARY KEY (`message_id`)
  ) ENGINE=InnoDB;
  CREATE TEMPORARY TABLE `tmp_v087_notify_message_repair` (
    `message_id` bigint NOT NULL,
    `old_param_key` varchar(64) NOT NULL,
    `old_param_value` text NOT NULL,
    `new_param_key` varchar(64) NOT NULL,
    `new_param_value` varchar(128) NOT NULL,
    PRIMARY KEY (`message_id`)
  ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

  -- Malformed legacy snapshots are left unchanged unless they still expose a forbidden parameter key.
  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  WHERE ((message_row.scene_code LIKE 'zsjos.lead.%'
          AND COALESCE(message_row.template_params,'') LIKE '%lead.name%')
      OR (message_row.scene_code LIKE 'zsjos.sales_order.%'
          AND COALESCE(message_row.template_params,'') LIKE '%order.studentName%')
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND COALESCE(message_row.template_params,'') LIKE '%student.name%'))
    AND (message_row.template_params IS NULL OR JSON_VALID(message_row.template_params)=0
         OR IF(JSON_VALID(message_row.template_params)=1,
               JSON_TYPE(message_row.template_params),'INVALID')<>'OBJECT');
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: malformed notification params still contain a legacy name key';
  END IF;

  INSERT INTO `tmp_v087_target_message` (`message_id`)
  SELECT message_row.id
  FROM `system_notify_message` message_row
  WHERE (message_row.scene_code LIKE 'zsjos.lead.%'
      OR message_row.scene_code LIKE 'zsjos.sales_order.%'
      OR message_row.scene_code LIKE 'zsjos.registration.%')
    AND IF(JSON_VALID(message_row.template_params)=1,
           JSON_TYPE(message_row.template_params),'INVALID')='OBJECT';

  -- Scene, business type, tenant relation and user-visible identifier must all be deterministic.
  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  LEFT JOIN `zsjos_lead` lead_row
    ON message_row.scene_code LIKE 'zsjos.lead.%'
   AND message_row.biz_type='lead' AND lead_row.id=message_row.biz_id
   AND lead_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` order_row
    ON message_row.scene_code LIKE 'zsjos.sales_order.%'
   AND message_row.biz_type='sales_order' AND order_row.id=message_row.biz_id
   AND order_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_registration_case` registration_row
    ON message_row.scene_code LIKE 'zsjos.registration.%'
   AND message_row.biz_type='registration_case' AND registration_row.id=message_row.biz_id
   AND registration_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` registration_order
    ON registration_order.id=registration_row.order_id
   AND registration_order.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_lead` registration_lead
    ON registration_lead.id=registration_order.lead_id
   AND registration_lead.tenant_id=message_row.tenant_id
  WHERE (message_row.scene_code LIKE 'zsjos.lead.%'
      OR message_row.scene_code LIKE 'zsjos.sales_order.%'
      OR message_row.scene_code LIKE 'zsjos.registration.%')
    AND ((message_row.scene_code LIKE 'zsjos.lead.%'
          AND (message_row.biz_type<>'lead' OR lead_row.id IS NULL
               OR lead_row.lead_no IS NULL OR lead_row.lead_no='')
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"')=1
               OR JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=0))
      OR (message_row.scene_code LIKE 'zsjos.sales_order.%'
          AND (message_row.biz_type<>'sales_order' OR order_row.id IS NULL
               OR order_row.order_no IS NULL OR order_row.order_no='')
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"')=1
               OR JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=0))
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND (message_row.biz_type<>'registration_case' OR registration_row.id IS NULL
               OR registration_order.id IS NULL OR registration_order.order_no IS NULL
               OR registration_order.order_no=''
               OR (message_row.scene_code<>'zsjos.registration.task_created'
                   AND registration_order.lead_id IS NOT NULL
                   AND (registration_lead.id IS NULL OR registration_lead.lead_no IS NULL
                        OR registration_lead.lead_no='')))
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"')=1
               OR (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=0
                   AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=0))));
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: targeted notifications lack a safe business identifier relation';
  END IF;

  -- Old structured name values must be non-empty strings before exact replacement is attempted.
  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  WHERE ((message_row.scene_code LIKE 'zsjos.lead.%'
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"')=1
          AND (JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."lead.name"'))<>'STRING'
               OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.name"')))=''))
      OR (message_row.scene_code LIKE 'zsjos.sales_order.%'
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"')=1
          AND (JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."order.studentName"'))<>'STRING'
               OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.studentName"')))=''))
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"')=1
          AND (JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."student.name"'))<>'STRING'
               OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."student.name"')))='')));
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: a legacy customer-name parameter is not a non-empty string';
  END IF;

  -- Existing user-visible identifiers must be strings. Do not rely on JSON/SQL coercion here.
  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  LEFT JOIN `zsjos_registration_case` registration_row
    ON message_row.scene_code LIKE 'zsjos.registration.%'
   AND registration_row.id=message_row.biz_id
   AND registration_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` registration_order
    ON registration_order.id=registration_row.order_id
   AND registration_order.tenant_id=message_row.tenant_id
  WHERE (message_row.scene_code LIKE 'zsjos.lead.%'
         AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=1
         AND JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."lead.no"'))<>'STRING')
     OR (message_row.scene_code LIKE 'zsjos.sales_order.%'
         AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=1
         AND JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."order.no"'))<>'STRING')
     OR (message_row.scene_code LIKE 'zsjos.registration.%'
         AND (message_row.scene_code='zsjos.registration.task_created'
              OR registration_order.lead_id IS NULL)
         AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=1
         AND JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."order.no"'))<>'STRING')
     OR (message_row.scene_code LIKE 'zsjos.registration.%'
         AND message_row.scene_code<>'zsjos.registration.task_created'
         AND registration_order.lead_id IS NOT NULL
         AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=1
         AND JSON_TYPE(JSON_EXTRACT(message_row.template_params,'$."lead.no"'))<>'STRING');
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: a stored business number is not a string';
  END IF;

  -- A pre-existing business-number key may not disagree with the authoritative relation.
  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  LEFT JOIN `zsjos_lead` lead_row
    ON message_row.scene_code LIKE 'zsjos.lead.%' AND lead_row.id=message_row.biz_id
   AND lead_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` order_row
    ON message_row.scene_code LIKE 'zsjos.sales_order.%' AND order_row.id=message_row.biz_id
   AND order_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_registration_case` registration_row
    ON message_row.scene_code LIKE 'zsjos.registration.%' AND registration_row.id=message_row.biz_id
   AND registration_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` registration_order
    ON registration_order.id=registration_row.order_id
   AND registration_order.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_lead` registration_lead
    ON registration_lead.id=registration_order.lead_id
   AND registration_lead.tenant_id=message_row.tenant_id
  WHERE (message_row.scene_code LIKE 'zsjos.lead.%'
      OR message_row.scene_code LIKE 'zsjos.sales_order.%'
      OR message_row.scene_code LIKE 'zsjos.registration.%')
    AND ((message_row.scene_code LIKE 'zsjos.lead.%'
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=1
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.no"')) AS BINARY)
              <>CAST(lead_row.lead_no AS BINARY))
      OR (message_row.scene_code LIKE 'zsjos.sales_order.%'
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=1
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.no"')) AS BINARY)
              <>CAST(order_row.order_no AS BINARY))
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND (message_row.scene_code='zsjos.registration.task_created'
               OR registration_order.lead_id IS NULL)
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=1
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.no"')) AS BINARY)
              <>CAST(registration_order.order_no AS BINARY))
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND message_row.scene_code<>'zsjos.registration.task_created'
          AND registration_order.lead_id IS NOT NULL
          AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=1
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.no"')) AS BINARY)
              <>CAST(registration_lead.lead_no AS BINARY)));
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: a stored business number conflicts with its relation';
  END IF;

  INSERT INTO `tmp_v087_notify_message_repair`
    (`message_id`,`old_param_key`,`old_param_value`,`new_param_key`,`new_param_value`)
  SELECT message_row.id,'lead.name',
         JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.name"')),
         'lead.no',lead_row.lead_no
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  JOIN `zsjos_lead` lead_row ON lead_row.id=message_row.biz_id
   AND lead_row.tenant_id=message_row.tenant_id
  WHERE message_row.scene_code LIKE 'zsjos.lead.%' AND message_row.biz_type='lead'
    AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"')=1;

  INSERT INTO `tmp_v087_notify_message_repair`
    (`message_id`,`old_param_key`,`old_param_value`,`new_param_key`,`new_param_value`)
  SELECT message_row.id,'order.studentName',
         JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.studentName"')),
         'order.no',order_row.order_no
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  JOIN `zsjos_order` order_row ON order_row.id=message_row.biz_id
   AND order_row.tenant_id=message_row.tenant_id
  WHERE message_row.scene_code LIKE 'zsjos.sales_order.%' AND message_row.biz_type='sales_order'
    AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"')=1;

  INSERT INTO `tmp_v087_notify_message_repair`
    (`message_id`,`old_param_key`,`old_param_value`,`new_param_key`,`new_param_value`)
  SELECT message_row.id,'student.name',
         JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."student.name"')),
         CASE WHEN message_row.scene_code='zsjos.registration.task_created'
                   OR registration_order.lead_id IS NULL THEN 'order.no' ELSE 'lead.no' END,
         CASE WHEN message_row.scene_code='zsjos.registration.task_created'
                   OR registration_order.lead_id IS NULL THEN registration_order.order_no
              ELSE registration_lead.lead_no END
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  JOIN `zsjos_registration_case` registration_row ON registration_row.id=message_row.biz_id
   AND registration_row.tenant_id=message_row.tenant_id
  JOIN `zsjos_order` registration_order ON registration_order.id=registration_row.order_id
   AND registration_order.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_lead` registration_lead ON registration_lead.id=registration_order.lead_id
   AND registration_lead.tenant_id=message_row.tenant_id
  WHERE message_row.scene_code LIKE 'zsjos.registration.%'
    AND message_row.biz_type='registration_case'
    AND JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"')=1;

  START TRANSACTION;

  -- Rebuild every targeted template parameter array so token replacement is also deduplicated.
  OPEN template_cursor;
  template_loop: LOOP
    FETCH template_cursor INTO template_id,template_scene,template_title,template_summary,
                               template_content,template_params;
    IF done=1 THEN LEAVE template_loop; END IF;
    IF IF(JSON_VALID(template_params)=1,JSON_TYPE(template_params),'INVALID')<>'ARRAY' THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT='V087 blocked: targeted notification template params are not a JSON array';
    END IF;

    SET next_title=template_title;
    SET next_summary=template_summary;
    SET next_content=template_content;
    IF template_scene LIKE 'zsjos.lead.%' THEN
      SET next_title=REPLACE(REPLACE(next_title,'{{lead.name}}','{{lead.no}}'),'{lead.name}','{lead.no}');
      SET next_summary=REPLACE(REPLACE(next_summary,'{{lead.name}}','{{lead.no}}'),'{lead.name}','{lead.no}');
      SET next_content=REPLACE(REPLACE(next_content,'{{lead.name}}','{{lead.no}}'),'{lead.name}','{lead.no}');
    ELSEIF template_scene LIKE 'zsjos.sales_order.%' THEN
      SET next_title=REPLACE(REPLACE(next_title,'{{order.studentName}}','{{order.no}}'),'{order.studentName}','{order.no}');
      SET next_summary=REPLACE(REPLACE(next_summary,'{{order.studentName}}','{{order.no}}'),'{order.studentName}','{order.no}');
      SET next_content=REPLACE(REPLACE(next_content,'{{order.studentName}}','{{order.no}}'),'{order.studentName}','{order.no}');
    ELSEIF template_scene='zsjos.registration.task_created' THEN
      SET next_title=REPLACE(REPLACE(next_title,'{{student.name}}','{{order.no}}'),'{student.name}','{order.no}');
      SET next_summary=REPLACE(REPLACE(next_summary,'{{student.name}}','{{order.no}}'),'{student.name}','{order.no}');
      SET next_content=REPLACE(REPLACE(next_content,'{{student.name}}','{{order.no}}'),'{student.name}','{order.no}');
    ELSE
      SET next_title=REPLACE(REPLACE(next_title,'{{student.name}}','{{lead.no}}'),'{student.name}','{lead.no}');
      SET next_summary=REPLACE(REPLACE(next_summary,'{{student.name}}','{{lead.no}}'),'{student.name}','{lead.no}');
      SET next_content=REPLACE(REPLACE(next_content,'{{student.name}}','{{lead.no}}'),'{student.name}','{lead.no}');
    END IF;

    SET normalized_params=JSON_ARRAY();
    SET param_index=0;
    SET param_count=JSON_LENGTH(template_params);
    WHILE param_index<param_count DO
      SET param_type=JSON_TYPE(JSON_EXTRACT(template_params,CONCAT('$[',param_index,']')));
      IF param_type<>'STRING' THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT='V087 blocked: a targeted template parameter is not a string';
      END IF;
      SET param_value=JSON_UNQUOTE(JSON_EXTRACT(template_params,CONCAT('$[',param_index,']')));
      SET mapped_param=param_value;
      IF template_scene LIKE 'zsjos.lead.%' AND param_value='lead.name' THEN
        SET mapped_param='lead.no';
      ELSEIF template_scene LIKE 'zsjos.sales_order.%' AND param_value='order.studentName' THEN
        SET mapped_param='order.no';
      ELSEIF template_scene='zsjos.registration.task_created' AND param_value='student.name' THEN
        SET mapped_param='order.no';
      ELSEIF template_scene LIKE 'zsjos.registration.%' AND param_value='student.name' THEN
        SET mapped_param='lead.no';
      END IF;
      IF JSON_CONTAINS(normalized_params,JSON_QUOTE(mapped_param),'$')=0 THEN
        SET normalized_params=JSON_ARRAY_APPEND(normalized_params,'$',mapped_param);
      END IF;
      SET param_index=param_index+1;
    END WHILE;

    UPDATE `system_notify_template`
    SET `title`=next_title,`summary`=next_summary,`content`=next_content,
        `params`=normalized_params,`updater`='migration-V087',`update_time`=NOW()
    WHERE `id`=template_id
      AND NOT (CAST(`title` AS BINARY)<=>CAST(next_title AS BINARY)
               AND CAST(`summary` AS BINARY)<=>CAST(next_summary AS BINARY)
               AND CAST(`content` AS BINARY)<=>CAST(next_content AS BINARY)
               AND CAST(`params` AS BINARY)<=>CAST(normalized_params AS BINARY));
  END LOOP;
  CLOSE template_cursor;

  UPDATE `system_notify_message` message_row
  JOIN `tmp_v087_notify_message_repair` repair_row ON repair_row.message_id=message_row.id
  SET message_row.template_title=REPLACE(message_row.template_title,repair_row.old_param_value,
                                         repair_row.new_param_value),
      message_row.template_summary=REPLACE(message_row.template_summary,repair_row.old_param_value,
                                           repair_row.new_param_value),
      message_row.template_content=REPLACE(message_row.template_content,repair_row.old_param_value,
                                           repair_row.new_param_value),
       message_row.template_params=JSON_SET(
         JSON_REMOVE(message_row.template_params,CONCAT('$."',repair_row.old_param_key,'"')),
         CONCAT('$."',repair_row.new_param_key,'"'),repair_row.new_param_value),
       message_row.updater='migration-V087',message_row.update_time=NOW()
  WHERE JSON_CONTAINS_PATH(message_row.template_params,'one',
                           CONCAT('$."',repair_row.old_param_key,'"'))=1;

  -- Backfill or correct business-number params even when V085 already removed the old name key.
  UPDATE `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  JOIN `zsjos_lead` lead_row ON lead_row.id=message_row.biz_id
   AND lead_row.tenant_id=message_row.tenant_id
  SET message_row.template_params=JSON_SET(JSON_REMOVE(message_row.template_params,'$."lead.name"'),
                                           '$."lead.no"',lead_row.lead_no),
      message_row.updater='migration-V087',message_row.update_time=NOW()
  WHERE message_row.scene_code LIKE 'zsjos.lead.%' AND message_row.biz_type='lead'
    AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"')=1
      OR JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=0
      OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.no"')) AS BINARY)
         <>CAST(lead_row.lead_no AS BINARY));

  UPDATE `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  JOIN `zsjos_order` order_row ON order_row.id=message_row.biz_id
   AND order_row.tenant_id=message_row.tenant_id
  SET message_row.template_params=JSON_SET(JSON_REMOVE(message_row.template_params,'$."order.studentName"'),
                                           '$."order.no"',order_row.order_no),
      message_row.updater='migration-V087',message_row.update_time=NOW()
  WHERE message_row.scene_code LIKE 'zsjos.sales_order.%' AND message_row.biz_type='sales_order'
    AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"')=1
      OR JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=0
      OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.no"')) AS BINARY)
         <>CAST(order_row.order_no AS BINARY));

  UPDATE `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  JOIN `zsjos_registration_case` registration_row ON registration_row.id=message_row.biz_id
   AND registration_row.tenant_id=message_row.tenant_id
  JOIN `zsjos_order` order_row ON order_row.id=registration_row.order_id
   AND order_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_lead` lead_row ON lead_row.id=order_row.lead_id
   AND lead_row.tenant_id=message_row.tenant_id
  SET message_row.template_params=JSON_SET(
        JSON_REMOVE(message_row.template_params,'$."student.name"'),
        CASE WHEN message_row.scene_code='zsjos.registration.task_created' OR order_row.lead_id IS NULL
             THEN '$."order.no"' ELSE '$."lead.no"' END,
        CASE WHEN message_row.scene_code='zsjos.registration.task_created' OR order_row.lead_id IS NULL
             THEN order_row.order_no ELSE lead_row.lead_no END),
      message_row.updater='migration-V087',message_row.update_time=NOW()
  WHERE message_row.scene_code LIKE 'zsjos.registration.%' AND message_row.biz_type='registration_case'
    AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"')=1
      OR ((message_row.scene_code='zsjos.registration.task_created' OR order_row.lead_id IS NULL)
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=0
            OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.no"')) AS BINARY)
               <>CAST(order_row.order_no AS BINARY)))
      OR ((message_row.scene_code<>'zsjos.registration.task_created' AND order_row.lead_id IS NOT NULL)
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=0
            OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.no"')) AS BINARY)
               <>CAST(lead_row.lead_no AS BINARY))));

  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_notify_message_repair` repair_row ON repair_row.message_id=message_row.id
  WHERE repair_row.old_param_value<>repair_row.new_param_value
    AND (LOCATE(repair_row.old_param_value,message_row.template_title)>0
      OR LOCATE(repair_row.old_param_value,message_row.template_summary)>0
      OR LOCATE(repair_row.old_param_value,message_row.template_content)>0);
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: an exact legacy customer-name replacement was incomplete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  WHERE (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.name"')=1
      OR JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.studentName"')=1
      OR JSON_CONTAINS_PATH(message_row.template_params,'one','$."student.name"')=1);
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: a legacy customer-name parameter remains';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_message` message_row
  JOIN `tmp_v087_target_message` target_row ON target_row.message_id=message_row.id
  LEFT JOIN `zsjos_lead` lead_row
    ON message_row.scene_code LIKE 'zsjos.lead.%' AND lead_row.id=message_row.biz_id
   AND lead_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` order_row
    ON message_row.scene_code LIKE 'zsjos.sales_order.%' AND order_row.id=message_row.biz_id
   AND order_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_registration_case` registration_row
    ON message_row.scene_code LIKE 'zsjos.registration.%' AND registration_row.id=message_row.biz_id
   AND registration_row.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_order` registration_order
    ON registration_order.id=registration_row.order_id
   AND registration_order.tenant_id=message_row.tenant_id
  LEFT JOIN `zsjos_lead` registration_lead
    ON registration_lead.id=registration_order.lead_id
   AND registration_lead.tenant_id=message_row.tenant_id
  WHERE (message_row.scene_code LIKE 'zsjos.lead.%'
      OR message_row.scene_code LIKE 'zsjos.sales_order.%'
      OR message_row.scene_code LIKE 'zsjos.registration.%')
    AND ((message_row.scene_code LIKE 'zsjos.lead.%'
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=0
               OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.no"')) AS BINARY)
                  <>CAST(lead_row.lead_no AS BINARY)))
      OR (message_row.scene_code LIKE 'zsjos.sales_order.%'
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=0
               OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.no"')) AS BINARY)
                  <>CAST(order_row.order_no AS BINARY)))
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND (message_row.scene_code='zsjos.registration.task_created'
               OR registration_order.lead_id IS NULL)
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."order.no"')=0
               OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."order.no"')) AS BINARY)
                  <>CAST(registration_order.order_no AS BINARY)))
      OR (message_row.scene_code LIKE 'zsjos.registration.%'
          AND message_row.scene_code<>'zsjos.registration.task_created'
          AND registration_order.lead_id IS NOT NULL
          AND (JSON_CONTAINS_PATH(message_row.template_params,'one','$."lead.no"')=0
               OR CAST(JSON_UNQUOTE(JSON_EXTRACT(message_row.template_params,'$."lead.no"')) AS BINARY)
                  <>CAST(registration_lead.lead_no AS BINARY))));
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: a business-number parameter repair was incomplete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM `system_notify_template`
  WHERE (`scene_code` LIKE 'zsjos.lead.%'
      OR `scene_code` LIKE 'zsjos.sales_order.%'
      OR `scene_code` LIKE 'zsjos.registration.%')
    AND (`title` LIKE '%lead.name%' OR `summary` LIKE '%lead.name%' OR `content` LIKE '%lead.name%'
      OR `title` LIKE '%order.studentName%' OR `summary` LIKE '%order.studentName%'
      OR `content` LIKE '%order.studentName%' OR `title` LIKE '%student.name%'
      OR `summary` LIKE '%student.name%' OR `content` LIKE '%student.name%'
      OR CAST(`params` AS CHAR) LIKE '%lead.name%'
      OR CAST(`params` AS CHAR) LIKE '%order.studentName%'
      OR CAST(`params` AS CHAR) LIKE '%student.name%');
  IF invalid_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V087 blocked: a legacy customer-name template variable remains';
  END IF;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V087','Repair business notification identifiers after V085',
          'V087__repair_business_notification_identifiers.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V087','Repair business notification identifiers after V085',
          SHA2('V087__repair_business_notification_identifiers.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
  DROP TEMPORARY TABLE IF EXISTS `tmp_v087_notify_message_repair`;
  DROP TEMPORARY TABLE IF EXISTS `tmp_v087_target_message`;
END$$
DELIMITER ;

CALL `zsjos_apply_v087_notification_repairs`();
DROP PROCEDURE IF EXISTS `zsjos_apply_v087_notification_repairs`;
