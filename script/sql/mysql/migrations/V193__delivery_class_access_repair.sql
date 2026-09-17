-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V193: repair delivery-class access after the unified-menu migration.
-- The visible pages are shared; query-managed/query-my remain independent data-scope capabilities.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE system_menu
SET name='学员管理', visible=b'1', updater='V193', update_time=NOW()
WHERE id=73020 AND deleted=b'0';

UPDATE system_tenant_package
SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',73020), updater='V193', update_time=NOW()
WHERE deleted=b'0' AND (JSON_CONTAINS(menu_ids,'73620','$') OR JSON_CONTAINS(menu_ids,'73624','$'))
  AND NOT JSON_CONTAINS(menu_ids,'73020','$');

INSERT INTO zsjos_schema_version (version,description,checksum,installed_at)
VALUES ('V193','Repair delivery class access',SHA2('V193__delivery_class_access_repair.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version (module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V193','Repair delivery class access',SHA2('V193__delivery_class_access_repair.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);

COMMIT;
