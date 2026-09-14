-- V193: repair delivery-class access after the unified-menu migration.
-- The visible pages are shared; query-managed/query-my remain independent data-scope capabilities.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE system_menu
SET name='学员管理', visible=b'1', updater='V193', update_time=NOW()
WHERE id=73020 AND deleted=b'0';

-- A role that retained the old personal-class menu must not inherit the managed scope
-- merely because V192 copied the unified parent menu.
UPDATE system_role_menu managed
JOIN system_role_menu personal ON personal.role_id=managed.role_id
    AND personal.tenant_id=managed.tenant_id AND personal.menu_id=73624
    AND personal.deleted=b'0'
SET managed.deleted=b'1', managed.updater='V193', managed.update_time=NOW()
WHERE managed.menu_id=73628 AND managed.deleted=b'0';

-- Anyone who can enter class management needs the student-management route in the
-- authorized menu tree so class-card navigation cannot be rejected by the route guard.
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT source.role_id,73020,'V193',NOW(),'V193',NOW(),b'0',source.tenant_id
FROM system_role_menu source
WHERE source.menu_id IN (73620,73624) AND source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
                  WHERE existing.role_id=source.role_id AND existing.menu_id=73020
                    AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');

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
