-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V091: add the Lead flow-history detail permission for enabled sales managers.
-- Dependencies/order: apply after V090; Lead management menu 6770 and V086 detail permissions must exist.
-- Data scope: one System button permission and sales_manager role-menu relations only.
-- No Lead, event, assignment, aging-pool, account, message, or history row is modified.
-- Repeatability: stable menu ID/permission; default role grants run only before the V091 marker exists.
-- Recovery: forward-only; administrators may remove the role grant while retaining the permission definition.

START TRANSACTION;

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 6924,'查看流转记录','zsjos:lead-detail:flow-read',3,34,6770,'','','',NULL,0,b'1',b'1',b'0',
       'migration-V091',NOW(),'migration-V091',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6924 AND `deleted`=b'0')
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission`='zsjos:lead-detail:flow-read' AND `deleted`=b'0');

UPDATE `system_menu`
SET `name`='查看流转记录',`type`=3,`sort`=34,`parent_id`=6770,`status`=0,`deleted`=b'0',
    `updater`='migration-V091',`update_time`=NOW()
WHERE `id`=6924 AND `permission`='zsjos:lead-detail:flow-read';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V091','Lead flow history permission','V091__lead_flow_history_permission.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V091','Lead flow history permission',SHA2('V091__lead_flow_history_permission.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
