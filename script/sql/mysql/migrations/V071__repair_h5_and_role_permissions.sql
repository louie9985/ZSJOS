-- V071: repair partner H5 and role permission assignments.
-- Dependencies/order: apply after V070 and the V063/V068/V069 partner permission repairs.
-- Data scope: System menu metadata and role-menu relations only; no users, BPM, or business rows.
-- Repeatability: target grants are resolved by stable role/permission code; active duplicates are retired.
-- Recovery: restore reviewed role-menu snapshots with a forward migration. Do not rewrite V071 after rollout.
-- This file must not be executed against an existing environment without separate approval.

DROP TEMPORARY TABLE IF EXISTS `tmp_v071_permission_grant`;
CREATE TEMPORARY TABLE `tmp_v071_permission_grant` (
  `role_code` varchar(100) NOT NULL,
  `permission` varchar(100) NOT NULL,
  PRIMARY KEY (`role_code`, `permission`)
) ENGINE=MEMORY;

INSERT INTO `tmp_v071_permission_grant` (`role_code`,`permission`) VALUES
('part_time_partner','zsjos:partner:self-query'),
('part_time_partner','zsjos:lead:submit'),
('part_time_partner','zsjos:lead:query-submitted'),
('part_time_partner','zsjos:lead:submitter-supplement'),
('part_time_partner','zsjos:lead:urge'),
('part_time_partner','zsjos:lead-complaint:create'),
('part_time_partner','zsjos:lead:appeal:create'),
('part_time_partner','zsjos:cashback:my-query'),
('part_time_partner','zsjos:withdrawal:my-query'),
('part_time_partner','zsjos:withdrawal:apply'),
('finance_manager','zsjos:sales-order:query'),
('finance_manager','zsjos:sales-order:review'),
('finance_manager','zsjos:cashback:finance-query'),
('finance_manager','zsjos:withdrawal:finance-query'),
('finance_manager','zsjos:withdrawal:review'),
('finance_manager','zsjos:withdrawal:payout'),
('finance_manager','zsjos:export:query'),
('finance_manager','zsjos:export:order'),
('finance_manager','zsjos:export:finance-order'),
('finance_manager','zsjos:export:cashback'),
('finance_manager','zsjos:export:withdrawal'),
('finance_specialist','zsjos:sales-order:query'),
('finance_specialist','zsjos:sales-order:review'),
('finance_specialist','zsjos:cashback:finance-query'),
('finance_specialist','zsjos:withdrawal:finance-query'),
('finance_specialist','zsjos:withdrawal:review'),
('finance_specialist','zsjos:withdrawal:payout'),
('finance_specialist','zsjos:export:query'),
('finance_specialist','zsjos:export:order'),
('finance_specialist','zsjos:export:finance-order'),
('finance_specialist','zsjos:export:cashback'),
('finance_specialist','zsjos:export:withdrawal'),
('enrollment_manager','zsjos:sales-order:query'),
('enrollment_manager','zsjos:sales-order:review'),
('enrollment_specialist','zsjos:sales-order:query'),
('enrollment_specialist','zsjos:sales-order:review'),
('quality_manager','zsjos:lead:appeal:query'),
('quality_manager','zsjos:lead:appeal:review-quality'),
('quality_specialist','zsjos:lead:appeal:query'),
('quality_specialist','zsjos:lead:appeal:review-quality'),
('boss','zsjos:lead:appeal:query'),
('boss','zsjos:lead:appeal:review-chairman');

-- Administrator allowlist: configuration, global lead administration, personnel, audit,
-- lead export, notification rules, and read-only withdrawal. Financial actions remain excluded.
INSERT INTO `tmp_v071_permission_grant` (`role_code`,`permission`) VALUES
('system_administrator','zsjos:audit:query'),
('system_administrator','zsjos:audit:query-impersonation'),
('system_administrator','zsjos:export:query'),
('system_administrator','zsjos:export:lead'),
('system_administrator','zsjos:impersonation:query'),
('system_administrator','zsjos:impersonation:start'),
('system_administrator','zsjos:lead-aging-pool:query'),
('system_administrator','zsjos:lead-aging-pool:manage'),
('system_administrator','zsjos:lead-aging-pool:manage-all'),
('system_administrator','zsjos:lead-assignment:query'),
('system_administrator','zsjos:lead-assignment:update'),
('system_administrator','zsjos:lead-assignment:log-query'),
('system_administrator','zsjos:lead-assignment:manage-all'),
('system_administrator','zsjos:lead-duplicate-review:query'),
('system_administrator','zsjos:lead-duplicate-review:process'),
('system_administrator','zsjos:lead-duplicate-review:manage-all'),
('system_administrator','zsjos:lead-filter:query'),
('system_administrator','zsjos:lead-filter:update'),
('system_administrator','zsjos:lead-filter:publish'),
('system_administrator','zsjos:lead-follow-up-rule:query'),
('system_administrator','zsjos:lead-follow-up-rule:update'),
('system_administrator','zsjos:lead-rule:query'),
('system_administrator','zsjos:lead-rule:update'),
('system_administrator','zsjos:lead:query'),
('system_administrator','zsjos:lead:query-all'),
('system_administrator','zsjos:lead:transfer'),
('system_administrator','zsjos:partner:query'),
('system_administrator','zsjos:partner:create'),
('system_administrator','zsjos:partner:update-state'),
('system_administrator','zsjos:partner:convert'),
('system_administrator','zsjos:personnel:query'),
('system_administrator','zsjos:personnel:update-state'),
('system_administrator','zsjos:product-category:query'),
('system_administrator','zsjos:product-category:create'),
('system_administrator','zsjos:product-category:update'),
('system_administrator','zsjos:product-category:delete'),
('system_administrator','zsjos:product-category:status'),
('system_administrator','zsjos:product:query'),
('system_administrator','zsjos:product:create'),
('system_administrator','zsjos:product:update'),
('system_administrator','zsjos:product:delete'),
('system_administrator','zsjos:product:status'),
('system_administrator','zsjos:product:sku-query'),
('system_administrator','zsjos:product:sku-create'),
('system_administrator','zsjos:product:sku-update'),
('system_administrator','zsjos:product:sku-delete'),
('system_administrator','zsjos:product:sku-status'),
('system_administrator','zsjos:product:attr-query'),
('system_administrator','zsjos:product:attr-update'),
('system_administrator','zsjos:user-relation-scene:query'),
('system_administrator','zsjos:user-relation-scene:create'),
('system_administrator','zsjos:user-relation-scene:update'),
('system_administrator','zsjos:user-relation-scene:delete'),
('system_administrator','zsjos:user-relation:query'),
('system_administrator','zsjos:user-relation:update'),
('system_administrator','zsjos:user-relation:log-query'),
('system_administrator','zsjos:withdrawal:admin-query'),
('system_administrator','system:notify-rule:query'),
('system_administrator','system:notify-rule:create'),
('system_administrator','system:notify-rule:update'),
('system_administrator','system:notify-rule:delete');

-- App-only permission entries, and partner permissions orphaned by V069, are
-- root-level buttons. Shared permissions already attached to a valid admin page stay there.
UPDATE `system_menu`
SET `parent_id`=0, `type`=3, `path`='', `icon`='', `component`='', `component_name`=NULL,
    `status`=0, `visible`=b'1', `deleted`=b'0',
    `updater`='migration-V071', `update_time`=NOW()
WHERE `permission` IN
  ('zsjos:partner:self-query','zsjos:lead:submit','zsjos:lead:query-submitted',
   'zsjos:lead:submitter-supplement','zsjos:lead:urge','zsjos:lead-complaint:create',
   'zsjos:lead:appeal:create','zsjos:cashback:my-query',
   'zsjos:withdrawal:my-query','zsjos:withdrawal:apply')
  AND (`permission`='zsjos:partner:self-query' OR `parent_id`=0 OR NOT EXISTS (
    SELECT 1 FROM (SELECT `id`,`deleted` FROM `system_menu`) parent_menu
    WHERE parent_menu.id=`system_menu`.`parent_id` AND parent_menu.deleted=b'0'
  ));

DROP TEMPORARY TABLE IF EXISTS `tmp_v071_canonical_menu`;
CREATE TEMPORARY TABLE `tmp_v071_canonical_menu` AS
SELECT `permission`, MIN(`id`) AS `menu_id`
FROM `system_menu`
WHERE `deleted`=b'0' AND `permission`<>''
GROUP BY `permission`;
ALTER TABLE `tmp_v071_canonical_menu` ADD PRIMARY KEY (`permission`);

DROP TEMPORARY TABLE IF EXISTS `tmp_v071_restore`;
CREATE TEMPORARY TABLE `tmp_v071_restore` AS
SELECT MIN(rm.id) AS role_menu_id
FROM `tmp_v071_permission_grant` target
JOIN `system_role` r ON BINARY r.code=BINARY target.role_code AND r.deleted=b'0'
JOIN `tmp_v071_canonical_menu` canonical ON BINARY canonical.permission=BINARY target.permission
JOIN `system_role_menu` rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id
  AND rm.menu_id=canonical.menu_id AND rm.deleted=b'1'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` active
  WHERE active.role_id=r.id AND active.tenant_id=r.tenant_id
    AND active.menu_id=canonical.menu_id AND active.deleted=b'0'
)
GROUP BY r.id, r.tenant_id, canonical.menu_id;

UPDATE `system_role_menu` rm
JOIN `tmp_v071_restore` restore_row ON restore_row.role_menu_id=rm.id
SET rm.deleted=b'0', rm.updater='migration-V071', rm.update_time=NOW()
WHERE rm.id=restore_row.role_menu_id AND rm.deleted=b'1';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,canonical.menu_id,'migration-V071',NOW(),'migration-V071',NOW(),b'0',r.tenant_id
FROM `tmp_v071_permission_grant` target
JOIN `system_role` r ON BINARY r.code=BINARY target.role_code AND r.deleted=b'0'
JOIN `tmp_v071_canonical_menu` canonical ON BINARY canonical.permission=BINARY target.permission
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` rm
  WHERE rm.role_id=r.id AND rm.tenant_id=r.tenant_id
    AND rm.menu_id=canonical.menu_id AND rm.deleted=b'0'
);

-- Page containers without permissions are assigned by stable route identity.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V071',NOW(),'migration-V071',NOW(),b'0',r.tenant_id
FROM `system_role` r
JOIN `system_menu` m ON m.deleted=b'0' AND m.permission=''
  AND ((r.code IN ('finance_manager','finance_specialist','system_administrator')
        AND m.path='/zsjos' AND m.parent_id=0)
    OR (r.code IN ('finance_manager','finance_specialist','system_administrator')
        AND m.path='withdrawal' AND m.component_name='ZsjosWithdrawal')
    OR (r.code='system_administrator' AND m.path='notify-rule' AND m.component_name='SystemNotifyRule'))
WHERE r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm WHERE rm.role_id=r.id
                  AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0');

-- These roles are declarative allowlists. Other role assignments remain untouched.
UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0'
SET rm.deleted=b'1', rm.updater='migration-V071', rm.update_time=NOW()
WHERE rm.deleted=b'0' AND r.deleted=b'0'
  AND r.code IN ('part_time_partner','finance_manager','finance_specialist',
                 'enrollment_manager','enrollment_specialist','quality_manager',
                 'quality_specialist','boss','system_administrator')
  AND m.permission LIKE 'zsjos:%'
  AND NOT EXISTS (SELECT 1 FROM `tmp_v071_permission_grant` target
                  WHERE BINARY target.role_code=BINARY r.code
                    AND BINARY target.permission=BINARY m.permission);

-- Roles whose business modules are not implemented intentionally have no ZSJOS menu permissions.
UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0'
SET rm.deleted=b'1', rm.updater='migration-V071', rm.update_time=NOW()
WHERE rm.deleted=b'0' AND r.deleted=b'0' AND m.permission LIKE 'zsjos:%'
  AND r.code IN ('center_head','content_director','filming_editor','study_planner',
                 'academic_specialist','delivery_manager','exam_manager','exam_specialist',
                 'career_planner','career_manager','ip_teacher','product_rd_head',
                 'teaching_assistant','recruitment_manager','recruitment_specialist',
                 'hr_specialist','admin_manager','admin_specialist');

-- Keep one active relation per tenant, role, and stable permission identity.
DROP TEMPORARY TABLE IF EXISTS `tmp_v071_duplicate_keep`;
CREATE TEMPORARY TABLE `tmp_v071_duplicate_keep` AS
SELECT rm.role_id,rm.tenant_id,m.permission,MIN(rm.id) AS keep_id
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.permission<>''
WHERE rm.deleted=b'0'
GROUP BY rm.role_id,rm.tenant_id,m.permission
HAVING COUNT(*)>1;

UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0'
JOIN `tmp_v071_duplicate_keep` keep_row ON keep_row.role_id=rm.role_id
  AND keep_row.tenant_id=rm.tenant_id AND keep_row.permission=m.permission
SET rm.deleted=b'1', rm.updater='migration-V071', rm.update_time=NOW()
WHERE rm.deleted=b'0' AND rm.id<>keep_row.keep_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_v071_duplicate_keep`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v071_restore`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v071_canonical_menu`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v071_permission_grant`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V071','Repair H5 and role permissions','V071__repair_h5_and_role_permissions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V071','Repair H5 and role permissions',
        SHA2('V071__repair_h5_and_role_permissions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
