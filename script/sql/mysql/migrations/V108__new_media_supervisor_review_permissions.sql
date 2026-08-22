-- V108: supervisor actions for new-media closure flows.
-- Existing manager roles only; additive, repeatable, no role/user creation or removals.

SET NAMES utf8mb4;
INSERT INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V108',NOW(),'migration-V108',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.permission IN ('zsjos:handover:arbitrate','zsjos:review:approve','zsjos:student-ops:graduate')
WHERE r.tenant_id=1 AND r.status=0 AND r.deleted=b'0' AND m.deleted=b'0'
  AND ((r.code='dept_manager' AND m.permission='zsjos:handover:arbitrate')
    OR (r.code='delivery_manager' AND m.permission IN ('zsjos:review:approve','zsjos:student-ops:graduate')))
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V108','New-media supervisor review permissions','new-media-supervisor-review-permissions-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V108','New-media supervisor review permissions',SHA2('new-media-supervisor-review-permissions-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
