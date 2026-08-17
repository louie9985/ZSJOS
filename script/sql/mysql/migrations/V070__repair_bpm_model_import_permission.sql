-- V070: forward repair for the BPM model import permission missed by V064.
-- V064 used menu id 6913, which already belongs to a work-plan permission, so
-- its INSERT IGNORE recorded the migration without creating bpm:model:import.
-- This repair depends on the standard BPM model menu (id 1193), resolves the
-- permission by stable code, and lets MySQL allocate a collision-free menu id.
-- It grants no role. Reruns preserve an existing active permission. Rollback
-- should retain the menu while administrator-created role grants reference it.

DROP PROCEDURE IF EXISTS `zsjos_v070_assert_bpm_model_import`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v070_assert_bpm_model_import`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 1193 AND `deleted` = b'0'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V070 requires active BPM model menu id 1193';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'bpm:model:import' AND `deleted` = b'0'
      AND (`parent_id` <> 1193 OR `type` <> 3 OR `status` <> 0)
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V070 found conflicting active bpm:model:import menu';
  END IF;

  IF (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `permission` = 'bpm:model:import' AND `deleted` = b'0'
  ) > 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V070 found duplicate active bpm:model:import menus';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v070_assert_bpm_model_import`();
DROP PROCEDURE `zsjos_v070_assert_bpm_model_import`;

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT
  '模型导入', 'bpm:model:import', 3, 3, parent.`id`, '', '', '', NULL,
  0, b'1', b'1', b'1', 'migration-V070', NOW(), 'migration-V070', NOW(), b'0'
FROM `system_menu` parent
WHERE parent.`id` = 1193
  AND parent.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_menu` existing
    WHERE existing.`permission` = 'bpm:model:import'
      AND existing.`deleted` = b'0'
  );

INSERT INTO `zsjos_schema_version`
  (`version`, `description`, `checksum`, `installed_at`)
VALUES
  ('V070', 'Repair BPM model import permission', 'V070__repair_bpm_model_import_permission.sql', NOW())
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `checksum` = VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES
  ('core', 'V070', 'Repair BPM model import permission', SHA2('V070__repair_bpm_model_import_permission.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `checksum` = VALUES(`checksum`);
