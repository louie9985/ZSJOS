-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- V192: expose one 班级管理 menu while retaining independent data-scope permissions.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE system_menu
SET name='班级管理',
    permission='zsjos:delivery-class:query',
    path='class-management',
    component='zsjos/class-management',
    component_name='ZsjosClassManagement',
    visible=b'1',
    updater='V192', update_time=NOW()
WHERE id=73620 AND deleted=b'0';

UPDATE system_menu
SET parent_id=73620, updater='V192', update_time=NOW()
WHERE id IN (73626,73627) AND deleted=b'0';

INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(73628,'管理范围班级数据','zsjos:delivery-class:query-managed',3,90,73620,'','','',NULL,'native',0,b'0',b'1',b'0','V192',NOW(),'V192',NOW(),b'0'),
(73629,'本人班级数据','zsjos:delivery-class:query-my',3,91,73620,'','','',NULL,'native',0,b'0',b'1',b'0','V192',NOW(),'V192',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),visible=b'0',deleted=b'0',updater='V192',update_time=NOW();

UPDATE system_menu
SET name='班级管理（旧路径）', visible=b'0', updater='V192', update_time=NOW()
WHERE id=73624 AND deleted=b'0';

UPDATE system_tenant_package
SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',73620), updater='V192', update_time=NOW()
WHERE deleted=b'0' AND JSON_CONTAINS(menu_ids,'73624','$')
  AND NOT JSON_CONTAINS(menu_ids,'73620','$');
UPDATE system_tenant_package
SET menu_ids=JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(menu_ids,'$',73628),'$',73629), updater='V192', update_time=NOW()
WHERE deleted=b'0' AND JSON_CONTAINS(menu_ids,'73620','$')
  AND (NOT JSON_CONTAINS(menu_ids,'73628','$') OR NOT JSON_CONTAINS(menu_ids,'73629','$'));

INSERT INTO zsjos_schema_version (version,description,checksum,installed_at)
VALUES ('V192','Unify delivery class menu',SHA2('V192__unify_delivery_class_menu.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version (module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V192','Unify delivery class menu',SHA2('V192__unify_delivery_class_menu.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);

COMMIT;
