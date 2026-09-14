-- UTF-8. V201: copy existing material-create role access to viral decomposition pages.
-- Depends on V198; updates only menu grants and the system-owned default type label.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT DISTINCT source.role_id,target.id,'V201',NOW(),'V201',NOW(),b'0',source.tenant_id
FROM system_role_menu source
JOIN system_menu material ON material.id=80011 AND material.permission='zsjos:material:create' AND material.deleted=b'0'
JOIN system_menu target ON target.id IN (80041,80042) AND target.deleted=b'0'
WHERE source.menu_id=material.id AND source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=source.role_id AND x.menu_id=target.id AND x.tenant_id=source.tenant_id AND x.deleted=b'0');
UPDATE system_dict_type SET name='爆款内容',remark='爆款内容拆解类型；管理员可维护',updater='V201',update_time=NOW() WHERE type='zsjos_viral_content_type' AND deleted=b'0';
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V201','Viral content access reconciliation',SHA2('V201__viral_content_access_reconciliation.sql',256),NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V201','Viral content access reconciliation',SHA2('V201__viral_content_access_reconciliation.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
COMMIT;
