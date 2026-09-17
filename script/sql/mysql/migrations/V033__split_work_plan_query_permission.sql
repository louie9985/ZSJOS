-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- Separates the Work Plan route from its query permission so role administrators can grant read-only access.
-- Dependencies: V022 menu id 6900 and its current role grants must exist.
-- Execution order: convert 6900 to a permission-free route, add query button 6908, inherit role and package access, record V033.
-- Repeatability: stable menu ID, upsert, NOT EXISTS, and JSON_CONTAINS guards make reruns idempotent.
-- Data scope: work-plan menu metadata, inherited role-menu grants, and tenant-package menu IDs only.
-- Recovery: forward-only. Existing grants are preserved; administrators can remove 6908 through role management if needed.

UPDATE `system_menu`
SET `name`='工作计划', `permission`='', `updater`='migration-V033', `update_time`=NOW()
WHERE `id`=6900 AND `deleted`=b'0';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6908,'查看工作计划','zsjos:work-plan:query',3,0,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V033',NOW(),'migration-V033',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
  `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `updater`='migration-V033', `update_time`=NOW(), `deleted`=b'0';

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 6908),
    `updater`='migration-V033', `update_time`=NOW()
WHERE `deleted`=b'0'
  AND JSON_CONTAINS(`menu_ids`, '6900', '$')
  AND NOT JSON_CONTAINS(`menu_ids`, '6908', '$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V033','Separate work-plan route and query permission','split-work-plan-query-permission-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
