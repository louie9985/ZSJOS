-- V059: repair V047 filter options that MySQL serialized as JSON strings instead of arrays.
-- Dependencies/order: apply after V047. Only active submitter/owner schemes at the V047 version-2
-- state with string-typed pending-stage options are eligible.
-- Data scope: append one corrected immutable version per eligible scheme and publish/draft that array form.
-- Repeatability: eligible rows advance from version 2 and no longer have string-typed options.
-- Rollback limitation: publish a later configuration version; retain V047 and V059 snapshots for audit.

START TRANSACTION;

INSERT INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`, `version_no`, `config_json`, `published_by`, `published_at`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT scheme.`id`, scheme.`published_version` + 1,
       JSON_SET(scheme.`published_config_json`, '$.groups[1].options',
         CAST(JSON_UNQUOTE(JSON_EXTRACT(scheme.`published_config_json`, '$.groups[1].options')) AS JSON)),
       COALESCE(scheme.`published_by`, 0), NOW(),
       'migration-V059', NOW(), 'migration-V059', NOW(), b'0', scheme.`tenant_id`
FROM `zsjos_lead_inbox_filter_scheme` scheme
WHERE scheme.`deleted` = b'0' AND scheme.`audience` IN ('submitter', 'owner')
  AND scheme.`published_version` = 2 AND scheme.`updater` = 'migration-V047'
  AND JSON_TYPE(JSON_EXTRACT(scheme.`published_config_json`, '$.groups[1].options')) = 'STRING'
  AND JSON_VALID(JSON_UNQUOTE(JSON_EXTRACT(scheme.`published_config_json`, '$.groups[1].options')))
  AND JSON_TYPE(CAST(JSON_UNQUOTE(JSON_EXTRACT(
        scheme.`published_config_json`, '$.groups[1].options')) AS JSON)) = 'ARRAY'
  AND JSON_TYPE(JSON_EXTRACT(scheme.`draft_config_json`, '$.groups[1].options')) = 'STRING'
  AND JSON_VALID(JSON_UNQUOTE(JSON_EXTRACT(scheme.`draft_config_json`, '$.groups[1].options')))
  AND JSON_TYPE(CAST(JSON_UNQUOTE(JSON_EXTRACT(
        scheme.`draft_config_json`, '$.groups[1].options')) AS JSON)) = 'ARRAY'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_version` version_row
    WHERE version_row.`tenant_id` = scheme.`tenant_id`
      AND version_row.`scheme_id` = scheme.`id`
      AND version_row.`version_no` = scheme.`published_version` + 1
      AND version_row.`deleted` = b'0'
  );

UPDATE `zsjos_lead_inbox_filter_scheme`
SET `published_config_json` = JSON_SET(`published_config_json`, '$.groups[1].options',
      CAST(JSON_UNQUOTE(JSON_EXTRACT(`published_config_json`, '$.groups[1].options')) AS JSON)),
    `draft_config_json` = JSON_SET(`draft_config_json`, '$.groups[1].options',
      CAST(JSON_UNQUOTE(JSON_EXTRACT(`draft_config_json`, '$.groups[1].options')) AS JSON)),
    `published_version` = `published_version` + 1,
    `published_at` = NOW(), `updater` = 'migration-V059', `update_time` = NOW()
WHERE `deleted` = b'0' AND `audience` IN ('submitter', 'owner')
  AND `published_version` = 2 AND `updater` = 'migration-V047'
  AND JSON_TYPE(JSON_EXTRACT(`published_config_json`, '$.groups[1].options')) = 'STRING'
  AND JSON_VALID(JSON_UNQUOTE(JSON_EXTRACT(`published_config_json`, '$.groups[1].options')))
  AND JSON_TYPE(CAST(JSON_UNQUOTE(JSON_EXTRACT(
        `published_config_json`, '$.groups[1].options')) AS JSON)) = 'ARRAY'
  AND JSON_TYPE(JSON_EXTRACT(`draft_config_json`, '$.groups[1].options')) = 'STRING'
  AND JSON_VALID(JSON_UNQUOTE(JSON_EXTRACT(`draft_config_json`, '$.groups[1].options')))
  AND JSON_TYPE(CAST(JSON_UNQUOTE(JSON_EXTRACT(
        `draft_config_json`, '$.groups[1].options')) AS JSON)) = 'ARRAY';

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`, `installed_at`)
VALUES ('V059', 'Repair serialized pending-stage filter options',
        'V059__split_filter_json_array_repair.sql', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES ('core', 'V059', 'Repair serialized pending-stage filter options',
        SHA2('V059__split_filter_json_array_repair.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);

COMMIT;
