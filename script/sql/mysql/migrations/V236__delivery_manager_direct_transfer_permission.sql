-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Retired authorization migration; version slot retained for migration order.
SET NAMES utf8mb4;

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES ('V236','Delivery manager direct transfer permission',SHA2('V236__delivery_manager_direct_transfer_permission.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V236','Delivery manager direct transfer permission',SHA2('V236__delivery_manager_direct_transfer_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
