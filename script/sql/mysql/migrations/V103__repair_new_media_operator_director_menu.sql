-- V103: keep the content-director student page exclusive to content directors.
-- Scope: removes menu 7022 only from enabled or disabled new_media_operator roles.
-- Repeatability: the delete is idempotent; no business rows or historical audit rows are changed.
-- Recovery: an administrator may re-grant menu 7022 through System role management if the business contract changes.
SET NAMES utf8mb4;

DELETE role_menu
FROM system_role_menu role_menu
JOIN system_role role_row
  ON role_row.id=role_menu.role_id
 AND role_row.tenant_id=role_menu.tenant_id
WHERE role_row.code='new_media_operator'
  AND role_row.deleted=b'0'
  AND role_menu.menu_id=7022
  AND role_menu.deleted=b'0';

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V103','Repair new-media operator director-student menu','repair-new-media-operator-director-menu-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);

INSERT INTO zsjos_module_schema_version
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V103','Repair new-media operator director-student menu',
        SHA2('repair-new-media-operator-director-menu-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
