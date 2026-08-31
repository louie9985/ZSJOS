-- V076: merge sales-order supervisor confirmation into the sales-order approval page.
-- Dependencies/order: apply after V075. V061 must already own menu 6856.
-- Data scope: menu metadata and role-menu grants only; no order, BPM task, or history rows change.
-- Repeatability: stable menu IDs, guarded inserts, and version upserts make reruns safe.
-- Rollback limitation: use a forward migration; do not remove grants while active supervisor tasks exist.

DROP PROCEDURE IF EXISTS `zsjos_v076_assert_menu_slots`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v076_assert_menu_slots`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id`=6810 AND `path`='sales-order-approvals' AND `deleted`=b'0'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V076 blocked: sales-order approval page menu 6810 is missing or incompatible';
  END IF;
  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id`=76000 AND NOT (`permission`='zsjos:sales-order:review' AND `deleted`=b'0')
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V076 blocked: menu ID 76000 is owned by another permission';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id`=6856 AND `permission`='zsjos:sales-order:supervisor-confirm' AND `deleted`=b'0'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V076 blocked: supervisor-confirm permission menu 6856 is missing';
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v076_assert_menu_slots`();
DROP PROCEDURE IF EXISTS `zsjos_v076_assert_menu_slots`;

START TRANSACTION;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 76000,'处理成交订单审批','zsjos:sales-order:review',3,1,6810,'','','',NULL,
       0,b'1',b'1',b'0','migration-V076',NOW(),'migration-V076',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=76000);

-- Preserve every existing ordinary-review holder before removing permission from the page menu.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.role_id,76000,'migration-V076',NOW(),'migration-V076',NOW(),b'0',rm.tenant_id
FROM `system_role_menu` rm
JOIN `system_menu` menu ON menu.id=rm.menu_id AND menu.deleted=b'0'
WHERE rm.deleted=b'0' AND menu.permission='zsjos:sales-order:review'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=rm.role_id AND existing.menu_id=76000
      AND existing.tenant_id=rm.tenant_id AND existing.deleted=b'0'
  );

UPDATE `system_menu`
SET `name`='成交订单审批',`permission`='',`updater`='migration-V076',`update_time`=NOW()
WHERE `id`=6810 AND `deleted`=b'0';

UPDATE `system_menu`
SET `type`=3,`sort`=2,`parent_id`=6810,`path`='',`icon`='',`component`='',`component_name`=NULL,
    `always_show`=b'0',`updater`='migration-V076',`update_time`=NOW()
WHERE `id`=6856 AND `permission`='zsjos:sales-order:supervisor-confirm' AND `deleted`=b'0';

-- Existing supervisor-confirm holders and the stable sales-manager role need the unified page.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT holder.role_id,6810,'migration-V076',NOW(),'migration-V076',NOW(),b'0',holder.tenant_id
FROM (
  SELECT rm.role_id,rm.tenant_id
  FROM `system_role_menu` rm
  WHERE rm.menu_id=6856 AND rm.deleted=b'0'
  UNION
  SELECT role.id,role.tenant_id
  FROM `system_role` role
  WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
) holder
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.role_id=holder.role_id AND existing.menu_id=6810
    AND existing.tenant_id=holder.tenant_id AND existing.deleted=b'0'
);

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,6856,'migration-V076',NOW(),'migration-V076',NOW(),b'0',role.tenant_id
FROM `system_role` role
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=role.id AND existing.menu_id=6856
      AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0'
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V076','Unify sales-order approval and supervisor confirmation entry',
        'V076__unify_sales_order_approval_entry.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V076','Unify sales-order approval and supervisor confirmation entry',
        SHA2('V076__unify_sales_order_approval_entry.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
