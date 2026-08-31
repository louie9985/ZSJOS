-- V150: separate claim-pool reading from claiming and consolidate Partner permissions.
-- Dependencies/order: apply after the V143 Partner ownership relationship; V149 is not required.
-- Data scope: menu metadata and role-menu grants only; no Partner, Lead, account or ownership row changes.
-- Repeatability: fixed IDs, guarded grants and idempotent updates make reruns safe.
-- Recovery: forward-only; restore menu metadata and grants through a reviewed follow-up migration.

DROP PROCEDURE IF EXISTS `zsjos_v150_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v150_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V143')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V143') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V150 requires V143 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6749 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V150 requires the claim-pool page 6749';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6852 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V150 requires the Partner page 6852';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79920
             AND (`type`<>3 OR `permission`<>'zsjos:partner:manage')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu ID 79920 is owned by another permission';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `deleted`=b'0' AND `id`<>79920
             AND `permission`='zsjos:partner:manage') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partner manage permission already uses another menu ID';
  END IF;

  START TRANSACTION;

UPDATE `system_menu`
SET `name`='抢单池',`permission`='zsjos:lead:claim-pool:query',
    `component`='zsjos/leadClaimPool/index',`component_name`='ZsjosLeadClaimPool',
    `updater`='V150',`update_time`=NOW()
WHERE `id`=6749 AND `deleted`=b'0';

-- Preserve route visibility for every existing claimant and query-all holder.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT grant_source.role_id,6749,'V150',NOW(),'V150',NOW(),b'0',grant_source.tenant_id
FROM `system_role_menu` grant_source
JOIN `system_menu` source_menu ON source_menu.id=grant_source.menu_id
  AND source_menu.permission IN ('zsjos:lead:claim','zsjos:lead:query-all') AND source_menu.deleted=b'0'
WHERE grant_source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=grant_source.role_id AND existing.menu_id=6749
                    AND existing.tenant_id=grant_source.tenant_id AND existing.deleted=b'0');

-- Product decision: enabled sales managers receive read-only pool access, never the claim action.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,6749,'V150',NOW(),'V150',NOW(),b'0',role.tenant_id
FROM `system_role` role
WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=role.id AND existing.menu_id=6749
                    AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

UPDATE `system_menu`
SET `name`='兼职管理',`permission`='zsjos:partner:query',`path`='partner',
    `component`='zsjos/partner/index',`component_name`='ZsjosPartner',
    `updater`='V150',`update_time`=NOW()
WHERE `id`=6852 AND `deleted`=b'0';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 79920,'管理兼职','zsjos:partner:manage',3,1,6852,'','','',NULL,
       0,b'1',b'1',b'0','V150',NOW(),'V150',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=79920);

-- Every former subordinate viewer keeps a single consolidated Partner page grant.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT grant_source.role_id,6852,'V150',NOW(),'V150',NOW(),b'0',grant_source.tenant_id
FROM `system_role_menu` grant_source
JOIN `system_menu` source_menu ON source_menu.id=grant_source.menu_id
  AND source_menu.permission='zsjos:subordinate-partner:query' AND source_menu.deleted=b'0'
WHERE grant_source.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=grant_source.role_id AND existing.menu_id=6852
                    AND existing.tenant_id=grant_source.tenant_id AND existing.deleted=b'0');

-- Upgrade only roles that held all three former management capabilities.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT qualified.role_id,79920,'V150',NOW(),'V150',NOW(),b'0',qualified.tenant_id
FROM (
  SELECT grant_source.role_id,grant_source.tenant_id
  FROM `system_role_menu` grant_source
  JOIN `system_menu` source_menu ON source_menu.id=grant_source.menu_id
    AND source_menu.permission IN ('zsjos:partner:create','zsjos:partner:update-state','zsjos:partner:assign-owner')
    AND source_menu.deleted=b'0'
  WHERE grant_source.deleted=b'0'
  GROUP BY grant_source.role_id,grant_source.tenant_id
  HAVING COUNT(DISTINCT source_menu.permission)=3
) qualified
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=qualified.role_id AND existing.menu_id=79920
                    AND existing.tenant_id=qualified.tenant_id AND existing.deleted=b'0');

-- A management holder also needs the consolidated page route.
INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT manager_grant.role_id,6852,'V150',NOW(),'V150',NOW(),b'0',manager_grant.tenant_id
FROM `system_role_menu` manager_grant
WHERE manager_grant.menu_id=79920 AND manager_grant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.role_id=manager_grant.role_id AND existing.menu_id=6852
                    AND existing.tenant_id=manager_grant.tenant_id AND existing.deleted=b'0');

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79920),`updater`='V150',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
  AND NOT JSON_CONTAINS(`menu_ids`,'79920','$');

UPDATE `system_role_menu` grant_row
JOIN `system_menu` retired ON retired.id=grant_row.menu_id
SET grant_row.deleted=b'1',grant_row.updater='V150',grant_row.update_time=NOW()
WHERE grant_row.deleted=b'0' AND retired.permission IN (
  'zsjos:partner:create','zsjos:partner:update-state','zsjos:partner:assign-owner',
  'zsjos:partner:convert','zsjos:subordinate-partner:query'
);

UPDATE `system_menu`
SET `deleted`=b'1',`updater`='V150',`update_time`=NOW()
WHERE `deleted`=b'0' AND `permission` IN (
  'zsjos:partner:create','zsjos:partner:update-state','zsjos:partner:assign-owner',
  'zsjos:partner:convert','zsjos:subordinate-partner:query'
);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V150','Claim-pool read and consolidated Partner permissions',
        SHA2('V150__claim_pool_read_and_partner_permissions.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V150','Claim-pool read and consolidated Partner permissions',
        SHA2('V150__claim_pool_read_and_partner_permissions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v150_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v150_apply`;
