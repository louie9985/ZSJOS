SET NAMES utf8mb4;
-- Dependencies: system_menu permissions lead:submit and lead:self-sourced:create.
-- Scope: role-menu grants only; no users, posts, departments, Leads, or other business rows.
-- Repeatability: stable role and permission codes; duplicate active grants are retired.
DROP TEMPORARY TABLE IF EXISTS tmp_v227_grants;
CREATE TEMPORARY TABLE tmp_v227_grants (
  role_code varchar(100) NOT NULL,
  permission varchar(150) NOT NULL,
  PRIMARY KEY (role_code, permission)
) ENGINE=MEMORY;
INSERT INTO tmp_v227_grants VALUES
 ('center_head','zsjos:lead:submit'),
 ('dept_manager','zsjos:lead:submit'),
 ('content_director','zsjos:lead:submit'),
 ('new_media_operator','zsjos:lead:submit'),
 ('sales_specialist','zsjos:lead:submit');

INSERT INTO system_role_menu(role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT r.id,m.id,'migration-V227',NOW(),'migration-V227',NOW(),b'0',r.tenant_id
FROM system_role r JOIN tmp_v227_grants g ON CONVERT(g.role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci=r.code
JOIN system_menu m ON m.permission=CONVERT(g.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AND m.deleted=b'0'
WHERE r.status=0 AND r.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

UPDATE system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
JOIN system_menu m ON m.id=rm.menu_id AND m.permission='zsjos:lead:self-sourced:create' AND m.deleted=b'0'
SET rm.deleted=b'1', rm.updater='migration-V227', rm.update_time=NOW()
WHERE rm.deleted=b'0' AND r.code<>'sales_specialist' AND r.deleted=b'0';

UPDATE system_role_menu dup
JOIN system_role_menu keep ON keep.role_id=dup.role_id AND keep.menu_id=dup.menu_id
 AND keep.tenant_id=dup.tenant_id AND keep.deleted=b'0' AND keep.id<dup.id
JOIN system_role r ON r.id=dup.role_id AND r.tenant_id=dup.tenant_id AND r.code IN
 ('center_head','dept_manager','content_director','new_media_operator','sales_specialist')
SET dup.deleted=b'1', dup.updater='migration-V227', dup.update_time=NOW()
WHERE dup.deleted=b'0';

INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V227','Lead submission role permissions','lead-submission-role-permissions-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
