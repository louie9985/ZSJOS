-- V045 makes employee Workbench routes loadable by both Vue Admin and React Workbench.
-- Dependencies: visible Workbench page menus 6736, 6778, 6779, 6780, 6840, 6844, 6848 and 6849.
-- Repeatability: targeted updates assign stable Vue component metadata and preserve every other menu field.
-- Data scope: system_menu component metadata and schema-version metadata only; no role grants or business rows.
-- Rollback limitation: restore the former component metadata only after Vue no longer needs these routes.

UPDATE `system_menu`
SET `component` = CASE `id`
      WHEN 6736 THEN 'zsjos/leadSubmission/index'
      WHEN 6778 THEN 'zsjos/leadInbox/submitted'
      WHEN 6779 THEN 'zsjos/leadInbox/owned'
      WHEN 6780 THEN 'zsjos/todayTask/index'
      WHEN 6840 THEN 'zsjos/leadDuplicateReview/index'
      WHEN 6844 THEN 'zsjos/leadSelfSourced/index'
      WHEN 6848 THEN 'zsjos/leadComplaint/index'
      WHEN 6849 THEN 'zsjos/externalRepurchase/index'
    END,
    `updater` = 'migration-V045',
    `update_time` = NOW()
WHERE `id` IN (6736, 6778, 6779, 6780, 6840, 6844, 6848, 6849)
  AND `parent_id` = 6735
  AND `type` = 2
  AND `deleted` = b'0';

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V045', 'Register Vue components for dual-frontend Workbench menus',
        'dual-frontend-workbench-menu-components-v1');

INSERT IGNORE INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V045','Register Vue components for dual-frontend Workbench menus',
        SHA2('dual-frontend-workbench-menu-components-v1',256),'baseline',NOW());
