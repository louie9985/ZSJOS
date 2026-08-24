-- Allow the configured new-media operator role to maintain partner/student links used by H5 confirmation.
-- Dependencies: V071 partner permissions and V098 partner-student link schema.
-- Data scope: additive tenant role-menu grant only; no existing grant is removed.
-- Repeatability/recovery: guarded insert; remove this one relation to revoke locally.

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V111',NOW(),'migration-V111',NOW(),b'0',role.tenant_id
FROM `system_role` role
JOIN `system_menu` menu ON menu.permission='zsjos:partner:update-state' AND menu.deleted=b'0'
WHERE role.code='new_media_operator' AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=menu.id AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V111','New-media operator partner student link permission','V111__new_media_operator_partner_student_link_permission.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V111','New-media operator partner student link permission',
        SHA2('V111__new_media_operator_partner_student_link_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
