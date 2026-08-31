-- V156: repair feedback daily counters that can lag existing feedback numbers.
-- Dependencies/order: apply after V149 and V155; fresh bootstrap runs this after V155.
-- Data scope: feedback daily-counter rows only. Existing feedback, work orders, messages, and numbers are unchanged.
-- Repeatability: counters are inserted or raised to the greatest valid persisted suffix and are never lowered.
-- Recovery: forward-only. Lowering a repaired counter can reissue a historical number and is not supported.
-- Compatibility: business numbers and derived types use binary comparison because legacy tables can have different utf8mb4 collations.

DROP PROCEDURE IF EXISTS `zsjos_v156_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v156_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V149')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V155') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V156 requires V149 and V155';
  END IF;

  START TRANSACTION;
  INSERT INTO `zsjos_feedback_no_daily_counter`
    (`sequence_date`,`feedback_type`,`current_value`,`creator`,`create_time`,`updater`,`update_time`,
     `deleted`,`deleted_time`,`tenant_id`)
  SELECT parsed.`sequence_date`,parsed.`feedback_type`,MAX(parsed.`sequence_value`),
         'migration-V156',NOW(),'migration-V156',NOW(),b'0',NULL,parsed.`tenant_id`
  FROM (
    SELECT source_row.`tenant_id`,
           STR_TO_DATE(SUBSTRING(source_row.`number_value`,5,8),'%Y%m%d') AS `sequence_date`,
           CASE SUBSTRING(source_row.`number_value`,1,3)
             WHEN BINARY 'REQ' THEN BINARY 'REQUIREMENT'
             WHEN BINARY 'BUG' THEN BINARY 'BUG'
             WHEN BINARY 'SUP' THEN BINARY 'SUPPORT'
           END AS `feedback_type`,
           CAST(SUBSTRING(source_row.`number_value`,14) AS UNSIGNED) AS `sequence_value`,
           source_row.`number_value`
    FROM (
      SELECT feedback.`tenant_id`,CAST(feedback.`feedback_no` AS BINARY) AS `number_value`
      FROM `zsjos_feedback` feedback
      UNION ALL
      SELECT work_order.`tenant_id`,CAST(work_order.`order_no` AS BINARY) AS `number_value`
      FROM `zsjos_work_order` work_order
      WHERE CAST(work_order.`business_type` AS BINARY)=BINARY 'FEEDBACK'
    ) source_row
    WHERE source_row.`number_value` REGEXP BINARY '^(REQ|BUG|SUP)-[0-9]{8}-[0-9]+$'
  ) parsed
  WHERE parsed.`sequence_date` IS NOT NULL
    AND CAST(DATE_FORMAT(parsed.`sequence_date`,'%Y%m%d') AS BINARY)=SUBSTRING(parsed.`number_value`,5,8)
  GROUP BY parsed.`tenant_id`,parsed.`sequence_date`,parsed.`feedback_type`
  ON DUPLICATE KEY UPDATE
    `current_value`=GREATEST(`current_value`,VALUES(`current_value`)),
    `deleted`=b'0',`deleted_time`=NULL;

  IF EXISTS (
    SELECT 1
    FROM (
      SELECT parsed.`tenant_id`,parsed.`sequence_date`,parsed.`feedback_type`,
             MAX(parsed.`sequence_value`) AS `required_value`
      FROM (
        SELECT source_row.`tenant_id`,
               STR_TO_DATE(SUBSTRING(source_row.`number_value`,5,8),'%Y%m%d') AS `sequence_date`,
               CASE SUBSTRING(source_row.`number_value`,1,3)
                 WHEN BINARY 'REQ' THEN BINARY 'REQUIREMENT'
                 WHEN BINARY 'BUG' THEN BINARY 'BUG'
                 WHEN BINARY 'SUP' THEN BINARY 'SUPPORT'
               END AS `feedback_type`,
               CAST(SUBSTRING(source_row.`number_value`,14) AS UNSIGNED) AS `sequence_value`,
               source_row.`number_value`
        FROM (
          SELECT feedback.`tenant_id`,CAST(feedback.`feedback_no` AS BINARY) AS `number_value`
          FROM `zsjos_feedback` feedback
          UNION ALL
          SELECT work_order.`tenant_id`,CAST(work_order.`order_no` AS BINARY) AS `number_value`
          FROM `zsjos_work_order` work_order
          WHERE CAST(work_order.`business_type` AS BINARY)=BINARY 'FEEDBACK'
        ) source_row
        WHERE source_row.`number_value` REGEXP BINARY '^(REQ|BUG|SUP)-[0-9]{8}-[0-9]+$'
      ) parsed
      WHERE parsed.`sequence_date` IS NOT NULL
        AND CAST(DATE_FORMAT(parsed.`sequence_date`,'%Y%m%d') AS BINARY)=SUBSTRING(parsed.`number_value`,5,8)
      GROUP BY parsed.`tenant_id`,parsed.`sequence_date`,parsed.`feedback_type`
    ) required_counter
    LEFT JOIN `zsjos_feedback_no_daily_counter` counter_row
      ON counter_row.`tenant_id`=required_counter.`tenant_id`
     AND counter_row.`sequence_date`=required_counter.`sequence_date`
     AND CAST(counter_row.`feedback_type` AS BINARY)=required_counter.`feedback_type`
    WHERE counter_row.`id` IS NULL OR counter_row.`deleted`=b'1'
       OR counter_row.`current_value`<required_counter.`required_value`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V156 failed to align feedback daily counters';
  END IF;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V156','Repair feedback number counter',
          SHA2('V156__repair_feedback_number_counter.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V156','Repair feedback number counter',
          SHA2('V156__repair_feedback_number_counter.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v156_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v156_apply`;
