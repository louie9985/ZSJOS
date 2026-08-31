-- V107: complete local new-media role operation permissions.
-- Scope: tenant-scoped role_menu rows for existing roles only; no role/user/dept creation,
-- no permission removals, and no business-data changes. Repeatable and forward-only.
-- Apply after V105. Object authorization and data scope remain enforced by ZSJOS services.

SET NAMES utf8mb4;

INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V107',NOW(),'migration-V107',NOW(),b'0',r.tenant_id
FROM system_role r
JOIN system_menu m ON m.permission IN (
  -- study planner: owned students
  'zsjos:student:query-my','zsjos:student:accept','zsjos:student:update-basic-info',
  -- content director: director-owned students, accounts, content and positioning
  'zsjos:media-student:query-my','zsjos:media-account:query','zsjos:media-account:edit',
  'zsjos:media-account:bind-student','zsjos:media-account:stage-advance','zsjos:media-account:stage-rollback',
  'zsjos:content:query','zsjos:content:create','zsjos:content:complete-topic','zsjos:content:submit-production',
  'zsjos:content:submit-acceptance','zsjos:content:revise','zsjos:content:resubmit-production','zsjos:content:edit',
  'zsjos:positioning-card:query','zsjos:positioning-card:create','zsjos:positioning-card:submit-review',
  'zsjos:positioning-card:edit','zsjos:positioning-card:sign',
  -- operator: production responsibility
  'zsjos:media-account:query','zsjos:media-account:create','zsjos:media-account:edit','zsjos:media-account:bind-student',
  'zsjos:media-account:stage-advance','zsjos:media-account:stage-rollback',
  'zsjos:media-account:rescue','zsjos:media-account:rebind',
  'zsjos:content:query','zsjos:content:create','zsjos:content:complete-topic','zsjos:content:submit-production',
  'zsjos:content:submit-acceptance','zsjos:content:acceptance-review','zsjos:content:revise','zsjos:content:resubmit-production','zsjos:content:edit',
  'zsjos:production-ticket:query','zsjos:production-ticket:create','zsjos:production-ticket:accept',
  'zsjos:production-ticket:produce','zsjos:production-ticket:submit','zsjos:production-ticket:check',
  'zsjos:production-ticket:edit','zsjos:production-ticket:over-entitlement',
  'zsjos:positioning-card:query','zsjos:positioning-card:create','zsjos:positioning-card:submit-review',
  'zsjos:positioning-card:feasibility-review','zsjos:positioning-card:confirm-trial','zsjos:positioning-card:archive',
  'zsjos:positioning-card:sign',
  -- filming editor: assigned-ticket execution only
  'zsjos:production-ticket:query','zsjos:production-ticket:accept','zsjos:production-ticket:produce',
  'zsjos:production-ticket:submit','zsjos:production-ticket:edit'
)
WHERE r.tenant_id=1 AND r.status=0 AND r.deleted=b'0' AND m.deleted=b'0'
  AND r.code IN ('study_planner','content_director','new_media_operator','filming_editor')
  AND (
    (r.code='study_planner' AND m.permission IN ('zsjos:student:query-my','zsjos:student:accept','zsjos:student:update-basic-info'))
    OR (r.code='content_director' AND m.permission IN ('zsjos:media-student:query-my','zsjos:media-account:query','zsjos:media-account:edit','zsjos:media-account:bind-student','zsjos:media-account:stage-advance','zsjos:media-account:stage-rollback','zsjos:content:query','zsjos:content:create','zsjos:content:complete-topic','zsjos:content:submit-production','zsjos:content:submit-acceptance','zsjos:content:revise','zsjos:content:resubmit-production','zsjos:content:edit','zsjos:positioning-card:query','zsjos:positioning-card:create','zsjos:positioning-card:submit-review','zsjos:positioning-card:edit','zsjos:positioning-card:sign'))
    OR (r.code='new_media_operator' AND m.permission IN ('zsjos:media-account:query','zsjos:media-account:create','zsjos:media-account:edit','zsjos:media-account:bind-student','zsjos:media-account:stage-advance','zsjos:media-account:stage-rollback','zsjos:media-account:rescue','zsjos:media-account:rebind','zsjos:content:query','zsjos:content:create','zsjos:content:complete-topic','zsjos:content:submit-production','zsjos:content:submit-acceptance','zsjos:content:acceptance-review','zsjos:content:revise','zsjos:content:resubmit-production','zsjos:content:edit','zsjos:production-ticket:query','zsjos:production-ticket:create','zsjos:production-ticket:accept','zsjos:production-ticket:produce','zsjos:production-ticket:submit','zsjos:production-ticket:check','zsjos:production-ticket:edit','zsjos:production-ticket:over-entitlement','zsjos:positioning-card:query','zsjos:positioning-card:create','zsjos:positioning-card:submit-review','zsjos:positioning-card:feasibility-review','zsjos:positioning-card:confirm-trial','zsjos:positioning-card:archive','zsjos:positioning-card:sign'))
    OR (r.code='filming_editor' AND m.permission IN ('zsjos:production-ticket:query','zsjos:production-ticket:accept','zsjos:production-ticket:produce','zsjos:production-ticket:submit','zsjos:production-ticket:edit'))
  )
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V107','Complete new-media role operation permissions without review or diagnosis grants','new-media-role-operation-permissions-v2')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V107','Complete new-media role operation permissions without review or diagnosis grants',SHA2('new-media-role-operation-permissions-v2',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
