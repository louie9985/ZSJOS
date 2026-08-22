-- V109: allow the existing local dept_manager role to import and deploy BPM models.
-- Additive and repeatable; no account creation, permission removal, or business-row changes.

SET NAMES utf8mb4;
INSERT INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V109',NOW(),'migration-V109',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.permission IN ('bpm:model:create','bpm:model:import','bpm:model:deploy','bpm:model:update','bpm:category:create','bpm:category:query')
WHERE r.tenant_id=1 AND r.code='dept_manager' AND r.status=0 AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V109','Local media BPM publisher permission','local-media-bpm-publisher-permission-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V109','Local media BPM publisher permission',SHA2('local-media-bpm-publisher-permission-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
