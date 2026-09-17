-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V079: repair the unified Lead management page after V078 visibility drift.
-- Dependency/order: apply after V078.
-- Data scope: menu metadata and role-menu relations only; no Lead or account data changes.
-- Repeatability: the visibility update and grant upserts are safe to rerun.

START TRANSACTION;

UPDATE `system_menu`
SET `visible`=b'1',`type`=2,`parent_id`=6735,`path`='leads/manage',
    `component`='zsjos/lead/index',`component_name`='ZsjosLeadManagement',
    `updater`='migration-V079',`update_time`=NOW()
WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V079','Repair unified Lead management visibility','V079__repair_lead_management_visibility.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V079','Repair unified Lead management visibility',
        SHA2('V079__repair_lead_management_visibility.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
