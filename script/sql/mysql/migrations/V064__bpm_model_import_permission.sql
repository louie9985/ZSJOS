-- V064: add the missing BPM model import permission.
-- Depends on the standard BPM model menu (id 1193) from the system seed.
-- This migration adds one button permission only; it does not grant it to roles.
-- Reruns preserve administrator edits. Rollback should retain the menu while any
-- administrator-created role grant references it.

INSERT IGNORE INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6913, '模型导入', 'bpm:model:import', 3, 3, 1193, '', '', '', NULL,
   0, b'1', b'1', b'1', 'migration-V064', NOW(), 'migration-V064', NOW(), b'0');

INSERT INTO `zsjos_schema_version`
  (`version`, `description`, `checksum`, `installed_at`)
VALUES
  ('V064', 'BPM model import permission', 'V064__bpm_model_import_permission.sql', NOW())
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `checksum` = VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES
  ('core', 'V064', 'BPM model import permission', SHA2('V064__bpm_model_import_permission.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `checksum` = VALUES(`checksum`);
