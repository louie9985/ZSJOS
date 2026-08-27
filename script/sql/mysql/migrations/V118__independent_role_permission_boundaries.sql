-- V118: retired migration slot retained for continuous migration history.
-- Scope: version metadata only; no retired Student Operations schema, menu,
-- permission, notification, or business data is created.
-- Repeatability: inserts only when the version is absent.
-- Recovery: no business state to roll back.

INSERT INTO `zsjos_schema_version`
  (`version`, `description`, `checksum`, `installed_at`)
SELECT 'V118', 'Retired Student Operations migration placeholder',
       SHA2('V118__independent_role_permission_boundaries.sql', 256), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V118'
);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
SELECT 'core', 'V118', 'Retired Student Operations migration placeholder',
       SHA2('V118__independent_role_permission_boundaries.sql', 256), 'baseline', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code` = 'core' AND `version` = 'V118'
);
