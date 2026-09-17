-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Retired authorization migration; version slot retained for migration order.
SET NAMES utf8mb4;

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V107','Complete new-media role operation permissions without review or diagnosis grants','new-media-role-operation-permissions-v2')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V107','Complete new-media role operation permissions without review or diagnosis grants',SHA2('new-media-role-operation-permissions-v2',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
