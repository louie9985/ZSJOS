-- UTF-8. V236: grant delivery managers the explicitly scoped direct-transfer action.
-- Dependency: V188 delivery-class menu (73625) and V205 delivery-manager read scope.
-- Scope: active delivery_manager role-menu rows only; no users, classes, relations or BPM rows.
-- Repeatability: guarded insert; recovery is a later reviewed permission-retirement migration.
SET NAMES utf8mb4;
INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_row.id, menu_row.id, 'migration-V236', NOW(), 'migration-V236', NOW(), b'0', role_row.tenant_id
FROM system_role role_row JOIN system_menu menu_row
  ON menu_row.id=73625 AND menu_row.permission='zsjos:delivery-class:direct-transfer' AND menu_row.deleted=b'0'
WHERE role_row.code='delivery_manager' AND role_row.status=0 AND role_row.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id=role_row.id
    AND existing.menu_id=menu_row.id AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES ('V236','Delivery manager direct transfer permission',SHA2('V236__delivery_manager_direct_transfer_permission.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V236','Delivery manager direct transfer permission',SHA2('V236__delivery_manager_direct_transfer_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
