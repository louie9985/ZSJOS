-- V042: normalize legacy converted Lead status values in current inbox filter schemes.
-- Dependencies/order: apply after V041. V037 retired the converted Lead state, but its text-based
-- JSON replacement did not match MySQL-normalized JSON containing spaces.
-- Data scope: active submitter/owner draft and published configurations only. Immutable published
-- version snapshots are preserved; the service normalizes them if an administrator rolls one back.
-- Repeatability: each loop replaces one exact scalar value until none remain; reruns make no data changes.
-- Rollback limitation: forward-only because converted is no longer a valid Lead state. Restore from the
-- migration backup only together with application code that still supports converted.

DROP PROCEDURE IF EXISTS `zsjos_v042_normalize_lead_filter_status`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v042_normalize_lead_filter_status`()
BEGIN
  WHILE EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_scheme`
    WHERE `audience` IN ('submitter','owner') AND `deleted`=b'0'
      AND JSON_SEARCH(`draft_config_json`, 'one', 'converted') IS NOT NULL
  ) DO
    UPDATE `zsjos_lead_inbox_filter_scheme`
    SET `draft_config_json`=JSON_REPLACE(`draft_config_json`,
          JSON_UNQUOTE(JSON_SEARCH(`draft_config_json`, 'one', 'converted')), 'won'),
        `updater`='migration-V042', `update_time`=NOW()
    WHERE `audience` IN ('submitter','owner') AND `deleted`=b'0'
      AND JSON_SEARCH(`draft_config_json`, 'one', 'converted') IS NOT NULL;
  END WHILE;

  WHILE EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_scheme`
    WHERE `audience` IN ('submitter','owner') AND `deleted`=b'0'
      AND JSON_SEARCH(`published_config_json`, 'one', 'converted') IS NOT NULL
  ) DO
    UPDATE `zsjos_lead_inbox_filter_scheme`
    SET `published_config_json`=JSON_REPLACE(`published_config_json`,
          JSON_UNQUOTE(JSON_SEARCH(`published_config_json`, 'one', 'converted')), 'won'),
        `updater`='migration-V042', `update_time`=NOW()
    WHERE `audience` IN ('submitter','owner') AND `deleted`=b'0'
      AND JSON_SEARCH(`published_config_json`, 'one', 'converted') IS NOT NULL;
  END WHILE;

  UPDATE `zsjos_lead_inbox_filter_scheme`
  SET `draft_config_json`=JSON_REPLACE(`draft_config_json`,
        JSON_UNQUOTE(JSON_SEARCH(`draft_config_json`, 'one', '已进入转化')), '已成交'),
      `updater`='migration-V042', `update_time`=NOW()
  WHERE `audience` IN ('submitter','owner') AND `deleted`=b'0'
    AND JSON_SEARCH(`draft_config_json`, 'one', '已进入转化') IS NOT NULL;

  UPDATE `zsjos_lead_inbox_filter_scheme`
  SET `published_config_json`=JSON_REPLACE(`published_config_json`,
        JSON_UNQUOTE(JSON_SEARCH(`published_config_json`, 'one', '已进入转化')), '已成交'),
      `updater`='migration-V042', `update_time`=NOW()
  WHERE `audience` IN ('submitter','owner') AND `deleted`=b'0'
    AND JSON_SEARCH(`published_config_json`, 'one', '已进入转化') IS NOT NULL;
END$$
DELIMITER ;
CALL `zsjos_v042_normalize_lead_filter_status`();
DROP PROCEDURE `zsjos_v042_normalize_lead_filter_status`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V042','Normalize legacy Lead inbox filter status','normalize-legacy-lead-filter-status-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V042','Normalize legacy Lead inbox filter status',
        SHA2('normalize-legacy-lead-filter-status-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
