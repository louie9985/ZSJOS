-- UTF-8. V199: reconcile order-management permissions and tenant-scoped filter templates.
-- Depends on V195; metadata only, no business rows. Repeatable and forward-only.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT DISTINCT old_rm.role_id,new_menu.id,'V199',NOW(),'V199',NOW(),b'0',old_rm.tenant_id
FROM system_role_menu old_rm
JOIN system_menu old_menu ON old_menu.id=old_rm.menu_id AND old_menu.permission IN ('zsjos:sales-order:query-own','zsjos:sales-order:query-team') AND old_menu.deleted=b'0'
JOIN system_menu new_menu ON new_menu.permission='zsjos:sales-order:query-management' AND new_menu.deleted=b'0'
WHERE old_rm.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=old_rm.role_id AND x.menu_id=new_menu.id AND x.tenant_id=old_rm.tenant_id AND x.deleted=b'0');
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V199','Review compatibility reconciliation',SHA2('V199__review_compatibility_reconciliation.sql',256),NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V199','Review compatibility reconciliation',SHA2('V199__review_compatibility_reconciliation.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
COMMIT;
