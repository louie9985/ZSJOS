-- V060: reconcile the legacy and module migration registries after behavior verification.
-- Dependencies/order: apply after every effective V001-V059 behavior is verified; V058 may have
-- an earlier installed_at timestamp than repaired older versions in an existing environment.
-- Data scope: migration metadata only. No schema, menu, permission, configuration, or business row changes.
-- Repeatability: INSERT IGNORE and stable primary keys make every statement repeatable.
-- Rollback limitation: retain the reconciled registry; deleting version records could re-run old migrations.

START TRANSACTION;

INSERT IGNORE INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
SELECT 'core', legacy.`version`, legacy.`description`,
       SHA2(COALESCE(legacy.`checksum`, legacy.`version`), 256), 'legacy', legacy.`installed_at`
FROM `zsjos_schema_version` legacy
WHERE legacy.`version` REGEXP '^V[0-9]{3}$';

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`, `installed_at`)
VALUES ('V060', 'Reconcile legacy and module migration registries',
        'V060__migration_registry_reconciliation.sql', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES ('core', 'V060', 'Reconcile legacy and module migration registries',
        SHA2('V060__migration_registry_reconciliation.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);

COMMIT;
