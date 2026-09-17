-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',79920),`updater`='V150',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6852','$')
  AND NOT JSON_CONTAINS(`menu_ids`,'79920','$');

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
