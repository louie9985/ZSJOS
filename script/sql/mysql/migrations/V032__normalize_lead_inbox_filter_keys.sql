-- V032: normalize reviewer filter option keys without changing BPM task-definition values.
-- Dependency/order: apply after V029 has created reviewer schemes.
-- Data scope: current draft_config_json and published_config_json for active reviewer schemes only.
-- Repeatability: exact legacy key fragments are replaced, so reruns make no further data changes.
-- Rollback limitation: restoring camel-case option keys would reintroduce the validation conflict; use a
-- controlled forward migration if rollback is required. Historical version snapshots remain unchanged.
UPDATE `zsjos_lead_inbox_filter_scheme`
SET `draft_config_json` = REPLACE(
        REPLACE(
          REPLACE(
            REPLACE(`draft_config_json`, '"key":"registrationReview"', '"key":"registration_review"'),
            '"key": "registrationReview"', '"key": "registration_review"'),
          '"key":"financeReview"', '"key":"finance_review"'),
        '"key": "financeReview"', '"key": "finance_review"'),
    `published_config_json` = REPLACE(
        REPLACE(
          REPLACE(
            REPLACE(`published_config_json`, '"key":"registrationReview"', '"key":"registration_review"'),
            '"key": "registrationReview"', '"key": "registration_review"'),
          '"key":"financeReview"', '"key":"finance_review"'),
        '"key": "financeReview"', '"key": "finance_review"')
WHERE `audience` = 'reviewer'
  AND `deleted` = b'0'
  AND (`draft_config_json` LIKE '%"key":"registrationReview"%'
    OR `draft_config_json` LIKE '%"key": "registrationReview"%'
    OR `draft_config_json` LIKE '%"key":"financeReview"%'
    OR `draft_config_json` LIKE '%"key": "financeReview"%'
    OR `published_config_json` LIKE '%"key":"registrationReview"%'
    OR `published_config_json` LIKE '%"key": "registrationReview"%'
    OR `published_config_json` LIKE '%"key":"financeReview"%'
    OR `published_config_json` LIKE '%"key": "financeReview"%');

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V032', 'Normalize lead inbox reviewer filter option keys', NULL)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
