-- Non-destructive repair for an existing ZSJOS database.
-- Scope: missing dictionary definitions/data and missing role-menu grants.
-- This file never deletes, disables, or rewrites existing rows. It does not
-- seed users, business instances, BPM runtime tables, or Flowable IDs.
-- Run from the repository root with mysql so SOURCE paths resolve.
SET NAMES utf8mb4;

-- Core dictionary baseline. INSERT IGNORE plus the baseline's stable guards
-- preserve administrator edits and fill only missing rows.
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/04-bootstrap-zsjos-feedback-dictionary.sql;

-- Later dictionary-only additions whose migrations also contain unrelated DDL
-- are reproduced here through their idempotent data statements.
SOURCE script/sql/mysql/migrations/V104__new_media_business_dictionary.sql;
SOURCE script/sql/mysql/migrations/V129__seed_director_form_dictionaries.sql;

INSERT INTO `system_dict_data`
  (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 99,'未知','unknown','zsjos_certificate_practice',0,'default','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_certificate_practice' AND `value`='unknown' AND `deleted`=b'0');
INSERT INTO `system_dict_data`
  (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 99,'部分掌握','partial','zsjos_video_skill',0,'default','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_video_skill' AND `value`='partial' AND `deleted`=b'0');

-- V071's declarative grants, insert-only. Resolution uses role.code and
-- menu.permission, never environment-specific IDs. Existing extra grants are
-- intentionally preserved for administrator review.
DROP TEMPORARY TABLE IF EXISTS `tmp_sync_role_permission`;
CREATE TEMPORARY TABLE `tmp_sync_role_permission` (
  `role_code` varchar(100) NOT NULL,
  `permission` varchar(100) NOT NULL,
  PRIMARY KEY (`role_code`,`permission`)
) ENGINE=MEMORY;

INSERT INTO `tmp_sync_role_permission` (`role_code`,`permission`) VALUES
('part_time_partner','zsjos:partner:self-query'),('part_time_partner','zsjos:lead:submit'),('part_time_partner','zsjos:lead:query-submitted'),('part_time_partner','zsjos:lead:submitter-supplement'),('part_time_partner','zsjos:lead:urge'),('part_time_partner','zsjos:lead-complaint:create'),('part_time_partner','zsjos:lead:appeal:create'),('part_time_partner','zsjos:cashback:my-query'),('part_time_partner','zsjos:withdrawal:my-query'),('part_time_partner','zsjos:withdrawal:apply'),
('finance_manager','zsjos:sales-order:query'),('finance_manager','zsjos:sales-order:review'),('finance_manager','zsjos:cashback:finance-query'),('finance_manager','zsjos:withdrawal:finance-query'),('finance_manager','zsjos:withdrawal:review'),('finance_manager','zsjos:withdrawal:payout'),('finance_manager','zsjos:export:query'),('finance_manager','zsjos:export:order'),('finance_manager','zsjos:export:finance-order'),('finance_manager','zsjos:export:cashback'),('finance_manager','zsjos:export:withdrawal'),
('finance_specialist','zsjos:sales-order:query'),('finance_specialist','zsjos:sales-order:review'),('finance_specialist','zsjos:cashback:finance-query'),('finance_specialist','zsjos:withdrawal:finance-query'),('finance_specialist','zsjos:withdrawal:review'),('finance_specialist','zsjos:withdrawal:payout'),('finance_specialist','zsjos:export:query'),('finance_specialist','zsjos:export:order'),('finance_specialist','zsjos:export:finance-order'),('finance_specialist','zsjos:export:cashback'),('finance_specialist','zsjos:export:withdrawal'),
('enrollment_manager','zsjos:sales-order:query'),('enrollment_manager','zsjos:sales-order:review'),('enrollment_specialist','zsjos:sales-order:query'),('enrollment_specialist','zsjos:sales-order:review'),('quality_manager','zsjos:lead:appeal:query'),('quality_manager','zsjos:lead:appeal:review-quality'),('quality_specialist','zsjos:lead:appeal:query'),('quality_specialist','zsjos:lead:appeal:review-quality'),('boss','zsjos:lead:appeal:query'),('boss','zsjos:lead:appeal:review-chairman'),
('system_administrator','zsjos:audit:query'),('system_administrator','zsjos:audit:query-impersonation'),('system_administrator','zsjos:export:query'),('system_administrator','zsjos:export:lead'),('system_administrator','zsjos:impersonation:query'),('system_administrator','zsjos:impersonation:start'),('system_administrator','zsjos:lead-aging-pool:query'),('system_administrator','zsjos:lead-aging-pool:manage'),('system_administrator','zsjos:lead-aging-pool:manage-all'),('system_administrator','zsjos:lead-assignment:query'),('system_administrator','zsjos:lead-assignment:update'),('system_administrator','zsjos:lead-assignment:log-query'),('system_administrator','zsjos:lead-assignment:manage-all'),('system_administrator','zsjos:lead-duplicate-review:query'),('system_administrator','zsjos:lead-duplicate-review:process'),('system_administrator','zsjos:lead-duplicate-review:manage-all'),('system_administrator','zsjos:lead-filter:query'),('system_administrator','zsjos:lead-filter:update'),('system_administrator','zsjos:lead-filter:publish'),('system_administrator','zsjos:lead-follow-up-rule:query'),('system_administrator','zsjos:lead-follow-up-rule:update'),('system_administrator','zsjos:lead-rule:query'),('system_administrator','zsjos:lead-rule:update'),('system_administrator','zsjos:lead:query'),('system_administrator','zsjos:lead:query-all'),('system_administrator','zsjos:lead:transfer'),('system_administrator','zsjos:partner:query'),('system_administrator','zsjos:partner:create'),('system_administrator','zsjos:partner:update-state'),('system_administrator','zsjos:partner:convert'),('system_administrator','zsjos:personnel:query'),('system_administrator','zsjos:personnel:update-state'),('system_administrator','zsjos:product-category:query'),('system_administrator','zsjos:product-category:create'),('system_administrator','zsjos:product-category:update'),('system_administrator','zsjos:product-category:delete'),('system_administrator','zsjos:product-category:status'),('system_administrator','zsjos:product:query'),('system_administrator','zsjos:product:create'),('system_administrator','zsjos:product:update'),('system_administrator','zsjos:product:delete'),('system_administrator','zsjos:product:status'),('system_administrator','zsjos:product:sku-query'),('system_administrator','zsjos:product:sku-create'),('system_administrator','zsjos:product:sku-update'),('system_administrator','zsjos:product:sku-delete'),('system_administrator','zsjos:product:sku-status'),('system_administrator','zsjos:product:attr-query'),('system_administrator','zsjos:product:attr-update'),('system_administrator','zsjos:user-relation-scene:query'),('system_administrator','zsjos:user-relation-scene:create'),('system_administrator','zsjos:user-relation-scene:update'),('system_administrator','zsjos:user-relation-scene:delete'),('system_administrator','zsjos:user-relation:query'),('system_administrator','zsjos:user-relation:update'),('system_administrator','zsjos:user-relation:log-query'),('system_administrator','zsjos:withdrawal:admin-query'),('system_administrator','system:notify-rule:query'),('system_administrator','system:notify-rule:create'),('system_administrator','system:notify-rule:update'),('system_administrator','system:notify-rule:delete');

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`,m.`id`,'sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0',r.`tenant_id`
FROM `tmp_sync_role_permission` p
JOIN `system_role` r ON BINARY r.`code`=BINARY p.`role_code` AND r.`deleted`=b'0'
JOIN (
  SELECT `permission`, MIN(`id`) AS `id`
  FROM `system_menu`
  WHERE `deleted`=b'0' AND `permission`<>''
  GROUP BY `permission`
) canonical ON BINARY canonical.`permission`=BINARY p.`permission`
JOIN `system_menu` m ON m.`id`=canonical.`id`
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` x
  WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id` AND x.`tenant_id`=r.`tenant_id` AND x.`deleted`=b'0'
);

DROP TEMPORARY TABLE `tmp_sync_role_permission`;

-- BPM assets are deliberately not SQL. Validate and import them through the
-- BPM management UI using script/bpm/manifest.json and the procedure documented
-- in docs/operations/zsjos-existing-db-initialization.md.
