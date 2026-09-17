-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- UTF-8. V201: copy existing material-create role access to viral decomposition pages.
-- Depends on V198; updates only menu grants and the system-owned default type label.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE system_dict_type SET name='爆款内容',remark='爆款内容拆解类型；管理员可维护',updater='V201',update_time=NOW() WHERE type='zsjos_viral_content_type' AND deleted=b'0';
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V201','Viral content access reconciliation',SHA2('V201__viral_content_access_reconciliation.sql',256),NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V201','Viral content access reconciliation',SHA2('V201__viral_content_access_reconciliation.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
COMMIT;
