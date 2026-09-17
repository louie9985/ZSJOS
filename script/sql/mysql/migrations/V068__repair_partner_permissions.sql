-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V068: repair partner permissions after the V063 fixed-ID collision.
-- Additive and repeatable. It resolves menus by permission identity, never by the
-- historical IDs 6901-6912, and removes only the accidental work-plan grants
-- owned by the part_time_partner role.

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '兼职端权限','',2,90,0,'partner-portal','ep:user','', 'ZsjosPartnerPortal',0,b'1',b'1',b'1',
       'migration-V068',NOW(),'migration-V068',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission`=''
  AND `path`='partner-portal' AND `component_name`='ZsjosPartnerPortal' AND `deleted`=b'0');

INSERT INTO `system_menu`
(`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT src.name,src.permission,3,src.sort,parent.id,'','','',NULL,0,b'1',b'1',b'1',
       'migration-V068',NOW(),'migration-V068',NOW(),b'0'
FROM (
  SELECT '个人信息' name,'zsjos:partner:self-query' permission,1 sort
  UNION ALL SELECT '提交客资','zsjos:lead:submit',2
  UNION ALL SELECT '查询客资','zsjos:lead:query',3
  UNION ALL SELECT '查询已提交客资','zsjos:lead:query-submitted',4
  UNION ALL SELECT '客资补充','zsjos:lead:submitter-supplement',5
  UNION ALL SELECT '催办客资','zsjos:lead:urge',6
  UNION ALL SELECT '创建客诉','zsjos:lead-complaint:create',7
  UNION ALL SELECT '提交申诉','zsjos:lead:appeal:create',8
  UNION ALL SELECT '返现查询','zsjos:cashback:my-query',9
  UNION ALL SELECT '提现查询','zsjos:withdrawal:my-query',10
  UNION ALL SELECT '申请提现','zsjos:withdrawal:apply',11
) src
JOIN `system_menu` parent ON parent.path='partner-portal' AND parent.component_name='ZsjosPartnerPortal'
  AND parent.deleted=b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` m WHERE m.permission=src.permission AND m.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V068','Repair partner permission menu collision','V068__repair_partner_permissions.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V068','Repair partner permission menu collision',SHA2('V068__repair_partner_permissions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
