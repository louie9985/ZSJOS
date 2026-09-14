-- UTF-8. V200: ensure the unified class menu is granted once per role and tenant.
-- Depends on V193. No class/business rows are changed.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT DISTINCT source.role_id,73020,'V200',NOW(),'V200',NOW(),b'0',source.tenant_id
FROM system_role_menu source
WHERE source.menu_id IN (73620,73624) AND source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id=source.role_id AND existing.menu_id=73020 AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V200','Delivery class access reconciliation',SHA2('V200__delivery_class_access_reconciliation.sql',256),NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V200','Delivery class access reconciliation',SHA2('V200__delivery_class_access_reconciliation.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
COMMIT;
