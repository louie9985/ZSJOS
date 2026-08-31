-- V121: retire the standalone qualification-exceptions page.
-- Dependency/order: apply after V120.
-- Data scope: one system menu row only; permissions and backend APIs remain active.
-- Repeatability: the stable menu ID update is idempotent.
-- Recovery: restore the previous visible/path/component metadata through a reviewed forward migration if needed.

START TRANSACTION;

UPDATE `system_menu`
SET `visible`=b'0', `path`='', `component`='', `component_name`=NULL,
    `updater`='migration-V121', `update_time`=NOW()
WHERE `id`=6800 AND `permission`='zsjos:lead:qualification:query' AND `deleted`=b'0';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V121','Retire standalone Lead qualification exception menu',
        'V121__retire_lead_qualification_exception_menu.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V121','Retire standalone Lead qualification exception menu',
        SHA2('V121__retire_lead_qualification_exception_menu.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
