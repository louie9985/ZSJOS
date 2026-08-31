-- V081: configurable HRM employee birthday care.
-- Dependencies: V080, HRM employee/config tables, notification and business-task tables.
-- Repeatability: stable menu, template, handler and tenant/scene guards.
-- Data scope: menu permissions, one global notification template/rule per tenant, and one 10-minute job definition.
-- Rollback: forward-only; disable the job/rules and preserve delivered messages/tasks.

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 601940,'生日关怀设置','hrm:birthday-care-config:query',2,90,601476,'birthday-care','ep:present','hrm/birthday-care/index','HrmBirthdayCare',0,b'1',b'1',b'1','migration-V081',NOW(),'migration-V081',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=601940);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 601941,'生日关怀查询','hrm:birthday-care-config:query',3,1,601940,'','','',NULL,0,b'1',b'1',b'1','migration-V081',NOW(),'migration-V081',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=601941);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 601942,'生日关怀修改','hrm:birthday-care-config:update',3,2,601940,'','','',NULL,0,b'1',b'1',b'1','migration-V081',NOW(),'migration-V081',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=601942);

INSERT INTO `system_notify_template`
(`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '员工生日关怀','ZSJOS_HRM_BIRTHDAY_CARE','中世健人力资源中心','hrm.employee.birthday_care',
       '员工生日关怀提醒','{{employee.name}}将于近期生日',
       '{{employee.department}}员工{{employee.name}}将于{{employee.birthday}}生日，请及时完成生日关怀。',2,
       '["employee.name","employee.department","employee.birthday"]',0,'V081 员工生日关怀通知','migration-V081',NOW(),'migration-V081',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code`='ZSJOS_HRM_BIRTHDAY_CARE' AND `deleted`=b'0');

INSERT INTO `system_notify_rule`
(`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '员工生日关怀','hrm.employee.birthday_care','in_app',template.id,'["birthday_care_recipient"]','[]','message_detail',0,
       'migration-V081',NOW(),'migration-V081',NOW(),b'0',tenant.id
FROM `system_tenant` tenant JOIN `system_notify_template` template
  ON template.code='ZSJOS_HRM_BIRTHDAY_CARE' AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` rule_row
  WHERE rule_row.tenant_id=tenant.id AND rule_row.scene_code='hrm.employee.birthday_care'
    AND rule_row.creator='migration-V081' AND rule_row.deleted=b'0');

INSERT INTO `infra_job`
(`name`,`status`,`handler_name`,`handler_param`,`cron_expression`,`retry_count`,`retry_interval`,`monitor_timeout`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '员工生日关怀','1','employeeBirthdayCareJob',NULL,'0 0/10 * * * ?',0,0,0,'migration-V081',NOW(),'migration-V081',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `infra_job` WHERE `handler_name`='employeeBirthdayCareJob' AND `deleted`=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V081','Employee birthday care','employee-birthday-care-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
