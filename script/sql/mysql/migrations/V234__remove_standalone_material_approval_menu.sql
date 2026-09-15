-- V234: Remove standalone material approval navigation; approval lives in material management detail.
-- Scope: soft-disable only V233 navigation page and buttons; no business rows or BPM tasks.
SET NAMES utf8mb4;
UPDATE system_menu SET deleted=b'1',visible=b'0',updater='V234',update_time=NOW()
WHERE permission IN ('zsjos:material-approval:query','zsjos:material-approval:approve','zsjos:material-approval:reject') AND deleted=b'0';
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V234','Remove standalone material approval menu',SHA2('V234__remove_standalone_material_approval_menu.sql',256),NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V234','Remove standalone material approval menu',SHA2('V234__remove_standalone_material_approval_menu.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
