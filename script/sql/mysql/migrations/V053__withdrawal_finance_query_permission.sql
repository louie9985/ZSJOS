-- V053 additive withdrawal finance query permission. Apply after V052.
-- No role grants or business rows. Repeatable through INSERT IGNORE and schema-version upsert.
UPDATE `system_menu`
SET `name`='财务查询提现', `permission`='zsjos:withdrawal:finance-query', `type`=3, `sort`=6,
    `parent_id`=6890, `status`=0, `visible`=b'1', `deleted`=b'0', `updater`='migration-V053', `update_time`=NOW()
WHERE `id`=6896 AND `permission`='zsjos:withdrawal:finance-query';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 6896,'财务查询提现','zsjos:withdrawal:finance-query',3,6,6890,'','','',NULL,0,b'1',b'1',b'1','migration-V053',NOW(),'migration-V053',NOW(),b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6896)
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`='zsjos:withdrawal:finance-query' AND `deleted`=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
SELECT 'V053','Withdrawal finance query permission','withdrawal-finance-query-v1'
FROM `system_menu`
WHERE `id`=6896 AND `permission`='zsjos:withdrawal:finance-query' AND `deleted`=b'0'
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V053','Withdrawal finance query permission',SHA2('withdrawal-finance-query-v1',256),'legacy',NOW()
FROM `system_menu`
WHERE `id`=6896 AND `permission`='zsjos:withdrawal:finance-query' AND `deleted`=b'0'
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`),
  `release_version`=VALUES(`release_version`),`installed_at`=VALUES(`installed_at`);
