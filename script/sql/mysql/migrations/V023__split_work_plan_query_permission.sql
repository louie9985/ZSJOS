-- Separates the Work Plan route from its query permission so role administrators can grant read-only access.
-- Dependencies: V022 menu id 6900 and its current role grants must exist.
-- Execution order: convert 6900 to a permission-free route, add query button 6908, inherit role and package access, record V023.
-- Repeatability: stable menu ID, upsert, NOT EXISTS, and JSON_CONTAINS guards make reruns idempotent.
-- Data scope: work-plan menu metadata, inherited role-menu grants, and tenant-package menu IDs only.
-- Recovery: forward-only. Existing grants are preserved; administrators can remove 6908 through role management if needed.

UPDATE `system_menu`
SET `name`='工作计划', `permission`='', `updater`='migration-V023', `update_time`=NOW()
WHERE `id`=6900 AND `deleted`=b'0';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6908,'查看工作计划','zsjos:work-plan:query',3,0,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V023',NOW(),'migration-V023',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
  `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `updater`='migration-V023', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT source.role_id, 6908, 'migration-V023', NOW(), 'migration-V023', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
WHERE source.menu_id=6900 AND source.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=source.role_id AND existing.menu_id=6908
      AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0'
  );

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 6908),
    `updater`='migration-V023', `update_time`=NOW()
WHERE `deleted`=b'0'
  AND JSON_CONTAINS(`menu_ids`, '6900', '$')
  AND NOT JSON_CONTAINS(`menu_ids`, '6908', '$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V023','Separate work-plan route and query permission','split-work-plan-query-permission-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
