-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V069: remove the invalid admin route introduced by V063/V068.
-- The partner portal is an app-api/H5 capability and has no admin Vue route.
-- Keep the existing V063/V068 rows for audit; logical deletion is repeatable.
-- Rollback is forward-only and must retain the deleted menu if any replacement
-- administrator-owned menu references it.

UPDATE `system_menu`
SET `deleted`=b'1', `updater`='migration-V069', `update_time`=NOW()
WHERE `path`='partner-portal'
  AND `component_name`='ZsjosPartnerPortal'
  AND `deleted`=b'0';

INSERT INTO `zsjos_schema_version`
(`version`,`description`,`checksum`,`installed_at`)
VALUES ('V069','Remove invalid partner admin route','V069__remove_invalid_partner_admin_route.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V069','Remove invalid partner admin route',SHA2('V069__remove_invalid_partner_admin_route.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
