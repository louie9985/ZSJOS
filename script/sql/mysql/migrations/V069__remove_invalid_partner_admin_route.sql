-- V069: remove the invalid admin route introduced by V063/V068.
-- The partner portal is an app-api/H5 capability and has no admin Vue route.
-- Keep the existing V063/V068 rows for audit; logical deletion is repeatable.
-- Rollback is forward-only and must retain the deleted menu if any replacement
-- administrator-owned menu references it.

UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id=rm.menu_id
SET rm.deleted=b'1', rm.updater='migration-V069', rm.update_time=NOW()
WHERE m.path='partner-portal'
  AND m.component_name='ZsjosPartnerPortal'
  AND rm.deleted=b'0';

UPDATE `system_menu`
SET `deleted`=b'1', `updater`='migration-V069', `update_time`=NOW()
WHERE `path`='partner-portal'
  AND `component_name`='ZsjosPartnerPortal'
  AND `deleted`=b'0';

INSERT INTO `zsjos_schema_version`
(`version`,`description`,`checksum`,`installed_at`)
VALUES ('V069','Remove invalid partner admin route','V069__remove_invalid_partner_admin_route.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V069','Remove invalid partner admin route',SHA2('V069__remove_invalid_partner_admin_route.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
