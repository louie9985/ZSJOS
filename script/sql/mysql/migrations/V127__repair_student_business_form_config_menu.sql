-- V127: repair business-form configuration menu visibility and component metadata.
-- Additive and repeatable. Apply after V126; no business rows are changed.

UPDATE `system_menu`
SET `component_name` = 'ZsjosBusinessFormConfig',
    `component` = 'zsjos/studentContactConfig/index',
    `path` = 'business-form-config',
    `updater` = 'migration-V127',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 73460
  AND `permission` = 'zsjos:student-contact-config:forms';

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role_row.id, menu_row.id, 'migration-V127', NOW(), 'migration-V127', NOW(), b'0', role_row.tenant_id
FROM `system_role` role_row
JOIN `system_menu` menu_row ON menu_row.id IN (73400, 73401, 73402, 73460)
WHERE role_row.code IN ('system_administrator', 'super_admin')
  AND role_row.deleted = b'0'
  AND menu_row.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id = role_row.id
      AND existing.menu_id = menu_row.id
      AND existing.tenant_id = role_row.tenant_id
      AND existing.deleted = b'0'
  );

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`, `installed_at`)
VALUES ('V127', 'Repair student business form configuration menu grant', 'V127__repair_student_business_form_config_menu.sql', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES ('core', 'V127', 'Repair student business form configuration menu grant',
        SHA2('V127__repair_student_business_form_config_menu.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);
