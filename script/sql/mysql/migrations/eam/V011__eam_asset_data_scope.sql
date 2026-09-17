-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
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
