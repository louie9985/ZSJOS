-- V132: add the server-owned Workbench rendering mode to System menus.
-- Additive and repeatable. It changes menu metadata only; permissions and business rows are untouched.
SET NAMES utf8mb4;

SET @zsjos_v132_has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'system_menu'
    AND column_name = 'workbench_render_mode'
);
SET @zsjos_v132_add_column := IF(
  @zsjos_v132_has_column = 0,
  'ALTER TABLE `system_menu` ADD COLUMN `workbench_render_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''native'' COMMENT ''Workbench呈现方式：native、admin_embed、admin_only'' AFTER `component_name`',
  'SELECT 1'
);
PREPARE zsjos_v132_stmt FROM @zsjos_v132_add_column;
EXECUTE zsjos_v132_stmt;
DEALLOCATE PREPARE zsjos_v132_stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V132','Workbench menu rendering mode',SHA2('V132__workbench_menu_render_mode.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V132');

INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V132','Workbench menu rendering mode',SHA2('V132__workbench_menu_render_mode.sql',256),'baseline',NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code`='core' AND `version`='V132'
);
