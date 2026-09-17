-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- V209 query-button metadata; requires V208 and the active media-student page.
-- Scope: one query button and packages already containing its parent; no role grants.
-- Repeatable by permission identity; preserve retired menu rows and administrator assignments.
-- Rollback disables the new menu through a reviewed change, preserving historical references.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_menu
 (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,create_time,update_time,deleted)
SELECT '查看账号档案','zsjos:media-account:query',3,12,p.id,'','','',NULL,0,b'1',b'1',b'0','V209-query','V209-query',NOW(),NOW(),b'0'
FROM system_menu p
WHERE p.permission='zsjos:media-student:query-my' AND p.deleted=b'0' AND p.status=0
 AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='zsjos:media-account:query' AND m.deleted=b'0');

UPDATE system_tenant_package p
JOIN system_menu m ON m.permission='zsjos:media-account:query' AND m.deleted=b'0' AND m.status=0
SET p.menu_ids=JSON_ARRAY_APPEND(p.menu_ids,'$',m.id),p.updater='V209-query',p.update_time=NOW()
WHERE p.deleted=b'0' AND JSON_CONTAINS(p.menu_ids,CAST(m.parent_id AS CHAR),'$')
 AND NOT JSON_CONTAINS(p.menu_ids,CAST(m.id AS CHAR),'$');
COMMIT;
