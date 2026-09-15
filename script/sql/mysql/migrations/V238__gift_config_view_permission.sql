-- UTF-8. V238: split gift-config read into a standalone view permission for sales specialists,
--   and finish wiring the gift management actions (create/update/delete) that V212 created but never granted.
-- Dependency: V212 gift-config menus (8900 query, 8901 create, 8902 update, 8903 delete),
--   V002 sales_specialist role, bootstrap system_administrator role.
-- Scope: adds the view button menu (8904), backfills the management + view menus into any tenant package
--   already holding the gift-config menu, grants view to sales_specialist, and grants view+create/update/delete
--   to system_administrator. No users, tables or BPM rows touched.
-- Repeatability: every insert/update is guarded by permission / role-menu / JSON_CONTAINS existence checks.
SET NAMES utf8mb4;

-- 1) Add the standalone view permission as a button under the gift-config menu.
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8904,'查看礼品','zsjos:gift-config:view',3,0,8900,0,b'1',b'1',b'1','migration-V238',NOW(),'migration-V238',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-config:view' AND deleted=b'0');

-- 2) Backfill the view + management button menus into every tenant package that already contains the gift-config menu (8900).
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',8904),`updater`='migration-V238',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'8900','$') AND NOT JSON_CONTAINS(`menu_ids`,'8904','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',8901),`updater`='migration-V238',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'8900','$') AND NOT JSON_CONTAINS(`menu_ids`,'8901','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',8902),`updater`='migration-V238',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'8900','$') AND NOT JSON_CONTAINS(`menu_ids`,'8902','$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',8903),`updater`='migration-V238',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'8900','$') AND NOT JSON_CONTAINS(`menu_ids`,'8903','$');

-- 3) Grant the view permission to every active sales_specialist role.
INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_row.id, menu_row.id, 'migration-V238', NOW(), 'migration-V238', NOW(), b'0', role_row.tenant_id
FROM system_role role_row JOIN system_menu menu_row
  ON menu_row.permission='zsjos:gift-config:view' AND menu_row.deleted=b'0'
WHERE role_row.code='sales_specialist' AND role_row.status=0 AND role_row.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id=role_row.id
    AND existing.menu_id=menu_row.id AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');

-- 4) Grant view + create/update/delete to every active system_administrator role.
INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_row.id, menu_row.id, 'migration-V238', NOW(), 'migration-V238', NOW(), b'0', role_row.tenant_id
FROM system_role role_row JOIN system_menu menu_row
  ON menu_row.permission IN ('zsjos:gift-config:view','zsjos:gift-config:create','zsjos:gift-config:update','zsjos:gift-config:delete')
     AND menu_row.deleted=b'0'
WHERE role_row.code='system_administrator' AND role_row.status=0 AND role_row.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing WHERE existing.role_id=role_row.id
    AND existing.menu_id=menu_row.id AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES ('V238','Gift config view permission and management grant',SHA2('V238__gift_config_view_permission.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V238','Gift config view permission and management grant',SHA2('V238__gift_config_view_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
