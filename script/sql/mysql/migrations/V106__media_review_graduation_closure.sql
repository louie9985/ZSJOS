-- V106: retired migration slot retained for continuous migration history.
-- Scope: version metadata only; no retired Student Operations schema, menu,
-- permission, notification, or business data is created.
-- Repeatability: inserts only when the version is absent.
-- Recovery: no business state to roll back.

INSERT INTO `zsjos_schema_version`
  (`version`, `description`, `checksum`, `installed_at`)
SELECT 'V106', 'Retired Student Operations migration placeholder',
       SHA2('V106__media_review_graduation_closure.sql', 256), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V106'
);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
SELECT 'core', 'V106', 'Retired Student Operations migration placeholder',
       SHA2('V106__media_review_graduation_closure.sql', 256), 'baseline', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code` = 'core' AND `version` = 'V106'
);
