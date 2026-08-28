-- V153: synchronize media-account operator ownership from accepted service relations.
-- Dependencies/order: apply after V128 and V146; V151 is already owned by Partner permission repair.
-- Data scope: only non-deleted media accounts whose student has exactly one distinct active,
-- accepted, non-null service-relation operator. No menu, role, content, ticket, or history rows.
-- Repeatability: guarded temporary snapshot and version markers make reruns no-ops after alignment.
-- Recovery: forward-only; take a database backup before execution if restoration of prior owner IDs is required.

DROP PROCEDURE IF EXISTS `zsjos_v153_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v153_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V146') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V153 requires V146';
  END IF;
  IF EXISTS (
    SELECT 1 FROM `zsjos_service_relation`
    WHERE `status`='active' AND `acceptance_status`='accepted' AND `operator_user_id` IS NOT NULL AND `deleted`=b'0'
    GROUP BY `tenant_id`,`person_id` HAVING COUNT(DISTINCT `operator_user_id`)>1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V153 blocked: a student has multiple active operators';
  END IF;

  START TRANSACTION;
  DROP TEMPORARY TABLE IF EXISTS `tmp_v153_account_owner`;
  CREATE TEMPORARY TABLE `tmp_v153_account_owner` (
    `account_id` bigint NOT NULL PRIMARY KEY,
    `operator_user_id` bigint NOT NULL
  );
  INSERT INTO `tmp_v153_account_owner` (`account_id`,`operator_user_id`)
  SELECT ma.`id`, MAX(sr.`operator_user_id`)
  FROM `zsjos_media_account` ma
  JOIN `zsjos_service_relation` sr ON sr.`tenant_id`=ma.`tenant_id` AND sr.`person_id`=ma.`student_person_id`
    AND sr.`status`='active' AND sr.`acceptance_status`='accepted' AND sr.`operator_user_id` IS NOT NULL AND sr.`deleted`=b'0'
  WHERE ma.`deleted`=b'0'
  GROUP BY ma.`id`;
  UPDATE `zsjos_media_account` ma JOIN `tmp_v153_account_owner` fix ON fix.`account_id`=ma.`id`
  SET ma.`owner_operator_user_id`=fix.`operator_user_id`, ma.`version`=ma.`version`+1,
      ma.`updater`='V153', ma.`update_time`=NOW()
  WHERE ma.`owner_operator_user_id`<>fix.`operator_user_id` OR ma.`owner_operator_user_id` IS NULL;
  DROP TEMPORARY TABLE `tmp_v153_account_owner`;
  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V153','Synchronize media-account operator ownership','V153__sync_media_account_operator_owner.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V153','Synchronize media-account operator ownership',SHA2('V153__sync_media_account_operator_owner.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v153_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v153_apply`;
