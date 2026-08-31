-- V138: render HRM/FMS/EAM Workbench pages and directories through Vue Admin embedding.
-- Dependencies: V137 and system_menu.workbench_render_mode.
-- Repeatable and non-destructive: only active menu/directory metadata is changed;
-- button permission rows (type=3) and all grants/business data remain untouched.
SET NAMES utf8mb4;

WITH RECURSIVE `zsjos_hfm_workbench_menu_tree` AS (
  SELECT `id`, `parent_id`, `type`
  FROM `system_menu`
  WHERE `id` IN (601476, 601894, 7100)
    AND `deleted` = b'0'
  UNION ALL
  SELECT child.`id`, child.`parent_id`, child.`type`
  FROM `system_menu` child
  INNER JOIN `zsjos_hfm_workbench_menu_tree` parent
    ON child.`parent_id` = parent.`id`
  WHERE child.`deleted` = b'0'
)
UPDATE `system_menu` menu
INNER JOIN `zsjos_hfm_workbench_menu_tree` target
  ON target.`id` = menu.`id`
SET menu.`workbench_render_mode` = 'admin_embed'
WHERE target.`type` IN (1, 2)
  AND menu.`deleted` = b'0';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V138','HRM/FMS/EAM Workbench admin embed mode',
       SHA2('V138__hrm_fms_eam_workbench_admin_embed.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V138');

INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V138','HRM/FMS/EAM Workbench admin embed mode',
       SHA2('V138__hrm_fms_eam_workbench_admin_embed.sql',256),'baseline',NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code`='core' AND `version`='V138'
);
