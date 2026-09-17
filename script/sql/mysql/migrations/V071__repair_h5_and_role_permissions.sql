-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- V071: repair partner H5 menu metadata after V070; no role assignments.
-- Repeatable metadata update; rollback preserves menu IDs and existing administrator grants.
SET NAMES utf8mb4;

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

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V071','Repair H5 and role permissions','V071__repair_h5_and_role_permissions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V071','Repair H5 and role permissions',
        SHA2('V071__repair_h5_and_role_permissions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
