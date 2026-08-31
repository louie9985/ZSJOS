-- V049: global maintenance mode configuration and server-owned administration menu.
-- Dependencies/order: apply after V048 and the Infra config/System menu baseline.
-- Data scope: one System-owned config row and two menu records; no role grants or business rows.
-- Repeatability: inserts use stable keys/IDs and metadata upserts.
-- Rollback limitation: disable the menu and set the config value to false; history is retained.
-- This file must not be executed without separate environment approval.

INSERT INTO `infra_config` (`category`,`type`,`name`,`config_key`,`value`,`visible`,`remark`)
SELECT '中世健系统运行', 1, '维护模式', 'zsjos.system.maintenance-enabled', 'false', b'1',
       '全局开关；开启后普通写请求返回 HTTP 503'
WHERE NOT EXISTS (SELECT 1 FROM `infra_config`
                  WHERE `config_key`='zsjos.system.maintenance-enabled' AND `deleted`=b'0');

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6860,'维护模式','',2,30,1,'maintenance','ep:tools','system/maintenance/index','SystemMaintenance',0,b'1',b'1',b'1','migration-V049',NOW(),'migration-V049',NOW(),b'0'),
(6861,'切换维护模式','system:maintenance:update',3,1,6860,'','','',NULL,0,b'1',b'1',b'1','migration-V049',NOW(),'migration-V049',NOW(),b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V049','Global maintenance mode and scheduler guard','maintenance-mode-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V049','Global maintenance mode and scheduler guard',SHA2('maintenance-mode-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`),
  `release_version`=VALUES(`release_version`),`installed_at`=VALUES(`installed_at`);
