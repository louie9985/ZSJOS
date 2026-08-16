-- V063: independent partner portal contract. Apply after V062.
-- Additive/repeatable. Source/category options remain administrator-maintained dictionaries.
INSERT INTO `system_role` (`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '兼职端','part_time_partner',90,5,'[]',0,2,'ZSJOS 兼职端本人数据角色','migration-V063',NOW(),'migration-V063',NOW(),b'0',t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM `system_role` WHERE deleted=b'0') t
WHERE NOT EXISTS (SELECT 1 FROM `system_role` r WHERE r.tenant_id=t.tenant_id AND r.code='part_time_partner' AND r.deleted=b'0');
INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6900,'兼职端权限','',2,90,0,'partner-portal','ep:user','', 'ZsjosPartnerPortal',0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6901,'个人信息','zsjos:partner:self-query',3,1,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6902,'提交客资','zsjos:lead:submit',3,2,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6903,'查询客资','zsjos:lead:query',3,3,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6904,'查询已提交客资','zsjos:lead:query-submitted',3,4,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6905,'客资补充','zsjos:lead:submitter-supplement',3,5,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6906,'催办客资','zsjos:lead:urge',3,6,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6907,'创建客诉','zsjos:lead-complaint:create',3,7,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6908,'提交申诉','zsjos:lead:appeal:create',3,8,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6909,'返现查询','zsjos:cashback:my-query',3,9,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6910,'提现查询','zsjos:withdrawal:my-query',3,10,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6911,'申请提现','zsjos:withdrawal:apply',3,11,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0'),
(6912,'提现审核','zsjos:withdrawal:review',3,12,6900,'','','',NULL,0,b'1',b'1',b'1','migration-V063',NOW(),'migration-V063',NOW(),b'0');
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V063',NOW(),'migration-V063',NOW(),b'0',r.tenant_id FROM `system_role` r JOIN `system_menu` m ON m.id BETWEEN 6901 AND 6911 AND m.deleted=b'0'
WHERE r.code='part_time_partner' AND r.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');
INSERT INTO `system_user_role` (`user_id`,`role_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT p.bound_system_user_id,r.id,'migration-V063',NOW(),'migration-V063',NOW(),b'0',p.tenant_id FROM `zsjos_partner` p JOIN `system_role` r ON r.tenant_id=p.tenant_id AND r.code='part_time_partner' AND r.deleted=b'0'
WHERE p.bound_system_user_id IS NOT NULL AND p.status IN ('enabled','disabled') AND NOT EXISTS (SELECT 1 FROM `system_user_role` x WHERE x.user_id=p.bound_system_user_id AND x.role_id=r.id AND x.tenant_id=p.tenant_id AND x.deleted=b'0');
UPDATE `zsjos_product_category` SET `default_valid_cashback_amount`=COALESCE(`default_valid_cashback_amount`,10.00), `default_deal_cashback_rate`=COALESCE(`default_deal_cashback_rate`,0.1000) WHERE `parent_id`=0 AND `deleted`=b'0';
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`) VALUES ('V063','Partner portal app-api role and cashback defaults','V063__partner_portal_app_api_role.sql',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V063','Partner portal app-api role and cashback defaults',SHA2('V063__partner_portal_app_api_role.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
