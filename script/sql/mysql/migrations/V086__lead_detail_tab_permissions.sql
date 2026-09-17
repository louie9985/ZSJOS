-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V086: independent Lead-detail tab permissions with compatibility grants.
-- Dependency/order: apply after V085; Lead management menu 6770 must already exist.
-- Data scope: four System menu permission rows and role-menu relations only.
-- Deletion scope: none. No account, Lead, order, task, workflow, or history row is changed.
-- Repeatability: stable IDs/permissions, restore-before-insert relations, and version upserts make reruns safe.
-- Recovery: disable or reassign the four permissions through System role management; do not delete history.

DROP PROCEDURE IF EXISTS `zsjos_v086_assert_menu_ids`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v086_assert_menu_ids`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V086 blocked: Lead management menu 6770 is missing or incompatible';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id` BETWEEN 6920 AND 6923
             AND `permission` NOT IN ('zsjos:lead-detail:follow-up-read','zsjos:lead-detail:appeal-read',
                                      'zsjos:lead-detail:complaint-read','zsjos:lead-detail:order-read')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V086 blocked: menu IDs 6920-6923 are already occupied';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` IN
             ('zsjos:lead-detail:follow-up-read','zsjos:lead-detail:appeal-read',
              'zsjos:lead-detail:complaint-read','zsjos:lead-detail:order-read')
             AND `id` NOT BETWEEN 6920 AND 6923) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V086 blocked: Lead detail permissions use unexpected menu IDs';
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v086_assert_menu_ids`();
DROP PROCEDURE IF EXISTS `zsjos_v086_assert_menu_ids`;

START TRANSACTION;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6920,'查看跟进记录','zsjos:lead-detail:follow-up-read',3,30,6770,'','','',NULL,0,b'1',b'1',b'0','migration-V086',NOW(),'migration-V086',NOW(),b'0'),
(6921,'查看申诉记录','zsjos:lead-detail:appeal-read',3,31,6770,'','','',NULL,0,b'1',b'1',b'0','migration-V086',NOW(),'migration-V086',NOW(),b'0'),
(6922,'查看投诉记录','zsjos:lead-detail:complaint-read',3,32,6770,'','','',NULL,0,b'1',b'1',b'0','migration-V086',NOW(),'migration-V086',NOW(),b'0'),
(6923,'查看订单记录','zsjos:lead-detail:order-read',3,33,6770,'','','',NULL,0,b'1',b'1',b'0','migration-V086',NOW(),'migration-V086',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
  `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`status`=0,`deleted`=b'0',
  `updater`='migration-V086',`update_time`=NOW();

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V086','Add configurable Lead detail tab permissions','V086__lead_detail_tab_permissions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V086','Add configurable Lead detail tab permissions',
        SHA2('V086__lead_detail_tab_permissions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
