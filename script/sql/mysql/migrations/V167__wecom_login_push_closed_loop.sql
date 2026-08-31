-- V167: WeCom login and push preference closed loop.
-- Depends on V077 for normalized system_users.wecom_user_id and V072 for Partner accounts.
-- Scope: add user-level WeCom push preference flags only; no accounts, messages, roles or business rows are deleted.
-- Repeatable: guarded by information_schema and records both schema-version registries.
-- Rollback limitation: keep the additive columns while older application versions ignore them, or hide the UI in a later migration.

DROP PROCEDURE IF EXISTS `zsjos_v167_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v167_add_column`(IN table_name_arg varchar(64), IN column_name_arg varchar(64), IN ddl_arg text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=table_name_arg)
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema=DATABASE() AND table_name=table_name_arg AND column_name=column_name_arg) THEN
    SET @zsjos_v167_ddl = ddl_arg;
    PREPARE zsjos_v167_stmt FROM @zsjos_v167_ddl;
    EXECUTE zsjos_v167_stmt;
    DEALLOCATE PREPARE zsjos_v167_stmt;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v167_add_column`('system_users', 'wecom_enabled',
  'ALTER TABLE `system_users` ADD COLUMN `wecom_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否接收企业微信推送'' AFTER `unique_wecom_user_id`');
CALL `zsjos_v167_add_column`('zsjos_partner_account', 'wecom_enabled',
  'ALTER TABLE `zsjos_partner_account` ADD COLUMN `wecom_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否接收企业微信推送'' AFTER `status`');

DROP PROCEDURE IF EXISTS `zsjos_v167_add_column`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V167','WeCom login push closed loop','V167__wecom_login_push_closed_loop.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);

INSERT INTO `zsjos_module_schema_version`
        (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V167','WeCom login push closed loop',
        SHA2('V167__wecom_login_push_closed_loop.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
