-- UTF-8. V209 development-baseline correction, requires V208 and the active media-student page.
-- Run from repository root via V209; already-upgraded development databases may run this file alone.
-- Scope: one query button; grants only to roles with active edit/maintenance/query-all grants;
-- packages already containing the parent page gain this button. No business rows or old pages change.
-- Repeatable: resolve by permission and insert only missing active mappings. A revoked grant may be
-- reintroduced on replay while a qualifying write/query-all grant remains; review before replay.
-- Rollback: revoke only mappings introduced by this execution and invalidate their permission cache.
-- Keep the menu if later administrator grants reference it. No deployed historical migration is rewritten.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_menu
 (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,create_time,update_time,deleted)
SELECT '查看账号档案','zsjos:media-account:query',3,12,p.id,'','','',NULL,0,b'1',b'1',b'0','V209-query','V209-query',NOW(),NOW(),b'0'
FROM system_menu p
WHERE p.permission='zsjos:media-student:query-my' AND p.deleted=b'0' AND p.status=0
 AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='zsjos:media-account:query' AND m.deleted=b'0');

INSERT INTO system_role_menu (role_id,menu_id,tenant_id,creator,updater,create_time,update_time,deleted)
SELECT DISTINCT r.id,target.id,r.tenant_id,'V209-query','V209-query',NOW(),NOW(),b'0'
FROM system_role r
JOIN system_role_menu existing ON existing.role_id=r.id AND existing.tenant_id=r.tenant_id AND existing.deleted=b'0'
JOIN system_menu source ON source.id=existing.menu_id AND source.deleted=b'0' AND source.status=0
JOIN system_menu target ON target.permission='zsjos:media-account:query' AND target.deleted=b'0' AND target.status=0
WHERE r.deleted=b'0' AND source.permission IN
 ('zsjos:media-account:edit','zsjos:media-account:maintenance','zsjos:media-account:query-all')
 AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.tenant_id=r.tenant_id AND x.menu_id=target.id AND x.deleted=b'0');

UPDATE system_tenant_package p
JOIN system_menu m ON m.permission='zsjos:media-account:query' AND m.deleted=b'0' AND m.status=0
SET p.menu_ids=JSON_ARRAY_APPEND(p.menu_ids,'$',m.id),p.updater='V209-query',p.update_time=NOW()
WHERE p.deleted=b'0' AND JSON_CONTAINS(p.menu_ids,CAST(m.parent_id AS CHAR),'$')
 AND NOT JSON_CONTAINS(p.menu_ids,CAST(m.id AS CHAR),'$');
COMMIT;
