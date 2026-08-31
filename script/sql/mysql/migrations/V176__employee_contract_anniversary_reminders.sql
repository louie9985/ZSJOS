-- V176: employee contract-expiry and entry-anniversary reminder metadata.
-- Additive, repeatable metadata only; no business rows are seeded.
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 602110,'员工提醒','hrm:employee-reminder-config:query',2,91,601476,'employee-reminder','ep:bell','hrm/birthday-care/index','HrmBirthdayCare',0,b'1',b'1',b'1','migration-V176',NOW(),'migration-V176',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=602110);
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 602111,'员工提醒查询','hrm:employee-reminder-config:query',3,1,602110,'','','',NULL,0,b'1',b'1',b'1','migration-V176',NOW(),'migration-V176',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=602111);
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 602112,'员工提醒修改','hrm:employee-reminder-config:update',3,2,602110,'','','',NULL,0,b'1',b'1',b'1','migration-V176',NOW(),'migration-V176',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=602112);
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V176',NOW(),'migration-V176',NOW(),b'0',r.tenant_id FROM system_role r JOIN system_menu m ON m.id IN (602110,602111,602112)
WHERE r.code='super_admin' AND r.status=0 AND r.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');
INSERT INTO `system_notify_template` (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '员工合同到期提醒','ZSJOS_HRM_CONTRACT_EXPIRY','中世健人力资源中心','hrm.employee.contract_expiry','员工合同到期提醒','{{employee.name}}的合同即将到期','{{employee.department}}员工{{employee.name}}的合同将于{{employee.contractEndDate}}到期，请及时跟进。',2,'["employee.name","employee.department","employee.contractEndDate"]',0,'V176 员工合同到期提醒','migration-V176',NOW(),'migration-V176',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_HRM_CONTRACT_EXPIRY' AND deleted=b'0');
INSERT INTO `system_notify_template` (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '员工入职周年提醒','ZSJOS_HRM_ENTRY_ANNIVERSARY','中世健人力资源中心','hrm.employee.entry_anniversary','员工入职周年提醒','{{employee.name}}即将迎来入职周年','{{employee.department}}员工{{employee.name}}将于{{employee.anniversaryDate}}迎来入职{{employee.anniversaryYears}}周年，请及时完成关怀。',2,'["employee.name","employee.department","employee.anniversaryDate","employee.anniversaryYears"]',0,'V176 员工入职周年提醒','migration-V176',NOW(),'migration-V176',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_HRM_ENTRY_ANNIVERSARY' AND deleted=b'0');
INSERT INTO `infra_job` (`name`,`status`,`handler_name`,`handler_param`,`cron_expression`,`retry_count`,`retry_interval`,`monitor_timeout`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '员工合同到期与入职周年提醒','1','employeeContractAnniversaryReminderJob',NULL,'0 0/10 * * * ?',0,0,0,'migration-V176',NOW(),'migration-V176',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM infra_job WHERE handler_name='employeeContractAnniversaryReminderJob' AND deleted=b'0');
INSERT INTO `system_notify_rule` (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '员工合同到期提醒','hrm.employee.contract_expiry','in_app',t.id,'["employee_reminder_recipient"]','[]','message_detail',0,'migration-V176',NOW(),'migration-V176',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.code='ZSJOS_HRM_CONTRACT_EXPIRY' AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.scene_code='hrm.employee.contract_expiry' AND r.deleted=b'0');
INSERT INTO `system_notify_rule` (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '员工入职周年提醒','hrm.employee.entry_anniversary','in_app',t.id,'["employee_reminder_recipient"]','[]','message_detail',0,'migration-V176',NOW(),'migration-V176',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template t ON t.code='ZSJOS_HRM_ENTRY_ANNIVERSARY' AND t.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.scene_code='hrm.employee.entry_anniversary' AND r.deleted=b'0');
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`) VALUES ('V176','Employee contract and anniversary reminders','employee-reminder-v1') ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
