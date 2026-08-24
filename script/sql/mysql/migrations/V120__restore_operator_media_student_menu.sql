-- V120: restore the media-student center for new-media operators.
-- V103 removed this grant when the page was director-only; V113 subsequently
-- established the shared director/operator student center contract.
-- Forward-only, repeatable, and limited to system_role_menu metadata.
SET NAMES utf8mb4;

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id, m.id, 'migration-V120', NOW(), 'migration-V120', NOW(), b'0', r.tenant_id
FROM `system_role` r
JOIN `system_menu` m ON m.id=7022
WHERE r.code='new_media_operator' AND r.status=0 AND r.deleted=b'0'
  AND m.parent_id=(SELECT id FROM `system_menu`
                   WHERE path='/zsjos' AND parent_id=0 AND status=0 AND deleted=b'0')
  AND m.type=2 AND m.permission='zsjos:media-student:query-my'
  AND m.status=0 AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.role_id=r.id AND rm.menu_id=m.id
                    AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V120','Restore operator media-student menu grant','restore-operator-media-student-menu-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V120','Restore operator media-student menu grant',
        SHA2('restore-operator-media-student-menu-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
