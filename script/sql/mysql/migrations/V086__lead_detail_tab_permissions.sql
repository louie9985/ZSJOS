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

UPDATE `system_role_menu` target
JOIN `system_menu` target_menu ON target_menu.id=target.menu_id AND target_menu.id BETWEEN 6920 AND 6923
JOIN (
  SELECT DISTINCT source_rm.role_id,source_rm.tenant_id,target_permission.permission
  FROM `system_role_menu` source_rm
  JOIN `system_menu` source_menu ON source_menu.id=source_rm.menu_id AND source_menu.deleted=b'0'
  JOIN (
    SELECT 'zsjos:lead-detail:follow-up-read' permission,'zsjos:lead-follow-up:query' source_permission UNION ALL
    SELECT 'zsjos:lead-detail:follow-up-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:follow-up-read','zsjos:student:query-my' UNION ALL
    SELECT 'zsjos:lead-detail:follow-up-read','zsjos:lead:query-all' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:lead:appeal:create' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:lead:appeal:query' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:lead:query-all' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:lead-complaint:create' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:lead-complaint:handle' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:lead:query-all' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:sales-order:query' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:sales-order:create' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:student:query-my' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:lead:query-all'
  ) target_permission ON target_permission.source_permission=source_menu.permission
  WHERE source_rm.deleted=b'0'
) holder ON holder.role_id=target.role_id AND holder.tenant_id=target.tenant_id
  AND holder.permission=target_menu.permission
SET target.deleted=b'0',target.updater='migration-V086',target.update_time=NOW()
WHERE target.deleted=b'1';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT holder.role_id,target_menu.id,'migration-V086',NOW(),'migration-V086',NOW(),b'0',holder.tenant_id
FROM (
  SELECT DISTINCT source_rm.role_id,source_rm.tenant_id,target_permission.permission
  FROM `system_role_menu` source_rm
  JOIN `system_menu` source_menu ON source_menu.id=source_rm.menu_id AND source_menu.deleted=b'0'
  JOIN (
    SELECT 'zsjos:lead-detail:follow-up-read' permission,'zsjos:lead-follow-up:query' source_permission UNION ALL
    SELECT 'zsjos:lead-detail:follow-up-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:follow-up-read','zsjos:student:query-my' UNION ALL
    SELECT 'zsjos:lead-detail:follow-up-read','zsjos:lead:query-all' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:lead:appeal:create' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:lead:appeal:query' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:appeal-read','zsjos:lead:query-all' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:lead-complaint:create' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:lead-complaint:handle' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:complaint-read','zsjos:lead:query-all' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:sales-order:query' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:sales-order:create' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:subordinate-sales:query' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:student:query-my' UNION ALL
    SELECT 'zsjos:lead-detail:order-read','zsjos:lead:query-all'
  ) target_permission ON target_permission.source_permission=source_menu.permission
  WHERE source_rm.deleted=b'0'
) holder
JOIN `system_menu` target_menu ON target_menu.permission=holder.permission AND target_menu.deleted=b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=holder.role_id AND existing.menu_id=target_menu.id
                    AND existing.tenant_id=holder.tenant_id);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V086','Add configurable Lead detail tab permissions','V086__lead_detail_tab_permissions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V086','Add configurable Lead detail tab permissions',
        SHA2('V086__lead_detail_tab_permissions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
