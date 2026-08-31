-- V061: repair the V048/V055 menu-ID collision without rewriting either applied migration.
-- Dependencies/order: apply after V060; V048 owns personnel IDs 6850-6855 and V055 owns
-- the supervisor-confirmation schema behavior.
-- Data scope: inserts only the unassigned supervisor-confirmation menu and migration metadata.
-- Repeatability: the stable ID and permission guards plus version upserts make reruns safe.
-- Rollback limitation: retain the menu and registry records; role grants, if later assigned by an
-- administrator, must be reviewed before any separately approved rollback.

DROP PROCEDURE IF EXISTS `zsjos_v061_assert_menu_slot`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v061_assert_menu_slot`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 6856
      AND NOT (`permission` = 'zsjos:sales-order:supervisor-confirm'
               AND `component` = 'zsjos/salesOrderSupervisorConfirmation/index'
               AND `deleted` = b'0')
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V061 blocked: system_menu ID 6856 is owned by another menu';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'zsjos:sales-order:supervisor-confirm'
      AND `deleted` = b'0' AND `id` <> 6856
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V061 blocked: supervisor-confirm permission already uses another menu ID';
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v061_assert_menu_slot`();
DROP PROCEDURE IF EXISTS `zsjos_v061_assert_menu_slot`;

START TRANSACTION;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 6856,'主管确认','zsjos:sales-order:supervisor-confirm',2,18,6735,
       'sales-order-supervisor-confirmations','ep:stamp',
       'zsjos/salesOrderSupervisorConfirmation/index','ZsjosSalesOrderSupervisorConfirmation',
       0,b'1',b'1',b'1','migration-V061',NOW(),'migration-V061',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 6856)
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'zsjos:sales-order:supervisor-confirm' AND `deleted` = b'0'
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V061','Repair sales-order supervisor menu ID collision',
        'V061__sales_order_supervisor_menu_id_collision.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V061','Repair sales-order supervisor menu ID collision',
        SHA2('V061__sales_order_supervisor_menu_id_collision.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
