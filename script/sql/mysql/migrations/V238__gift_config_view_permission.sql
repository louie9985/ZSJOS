-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES ('V238','Gift config view permission and management grant',SHA2('V238__gift_config_view_permission.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V238','Gift config view permission and management grant',SHA2('V238__gift_config_view_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
