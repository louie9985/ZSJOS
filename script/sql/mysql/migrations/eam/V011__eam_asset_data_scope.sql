-- EAM read-scope and all-management permissions. Repeatable and non-destructive.
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 7210,'本人资产只读','eam:asset:query-self',3,8,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V011',NOW(),'migration-eam-V011',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:asset:query-self' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 7211,'部门资产只读','eam:asset:query-dept',3,9,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V011',NOW(),'migration-eam-V011',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:asset:query-dept' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 7212,'全量资产管理','eam:manage-all',3,10,7102,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V011',NOW(),'migration-eam-V011',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:manage-all' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 7213,'本人流转只读','eam:transfer:query-self',3,6,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V011',NOW(),'migration-eam-V011',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:transfer:query-self' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 7214,'部门流转只读','eam:transfer:query-dept',3,7,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V011',NOW(),'migration-eam-V011',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:transfer:query-dept' AND deleted=b'0');

-- Preserve existing administrator access: roles that already had the legacy
-- asset/transfer query permission receive the explicit all-management grant.
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT DISTINCT rm.role_id, m_all.id, 'migration-eam-V011', NOW(), 'migration-eam-V011', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
JOIN system_menu old_menu ON old_menu.id=rm.menu_id
JOIN system_menu m_all ON m_all.permission='eam:manage-all' AND m_all.deleted=b'0'
WHERE old_menu.permission IN ('eam:asset:query','eam:transfer:query')
  AND old_menu.deleted=b'0' AND rm.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=m_all.id AND x.tenant_id=rm.tenant_id AND x.deleted=b'0');
