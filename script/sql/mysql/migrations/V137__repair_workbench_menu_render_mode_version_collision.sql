-- V137: ensure the Workbench menu rendering column after the historical local V132 collision.
-- Dependencies: run after V132-V136. Additive and repeatable; no menu permissions or business rows change.
-- Existing V132/V133/V134 markers are preserved because they may identify already-applied local migrations.
SET NAMES utf8mb4;

SET @zsjos_v137_has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'system_menu'
    AND column_name = 'workbench_render_mode'
);
SET @zsjos_v137_add_column := IF(
  @zsjos_v137_has_column = 0,
  'ALTER TABLE `system_menu` ADD COLUMN `workbench_render_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''native'' COMMENT ''Workbench呈现方式：native、admin_embed、admin_only'' AFTER `component_name`',
  'SELECT 1'
);
PREPARE zsjos_v137_stmt FROM @zsjos_v137_add_column;
EXECUTE zsjos_v137_stmt;
DEALLOCATE PREPARE zsjos_v137_stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V137','Repair Workbench menu rendering mode version collision',
       SHA2('V137__repair_workbench_menu_render_mode_version_collision.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V137');

INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V137','Repair Workbench menu rendering mode version collision',
       SHA2('V137__repair_workbench_menu_render_mode_version_collision.sql',256),'baseline',NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `zsjos_module_schema_version`
  WHERE `module_code`='core' AND `version`='V137'
);
