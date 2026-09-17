-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V176','Employee contract and anniversary reminders',SHA2('V176__employee_contract_anniversary_reminders.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
