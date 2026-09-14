SET NAMES utf8mb4;
-- Dependencies: the ZSJOS root menu (6735) must exist.
-- Scope: add the student-delivery page and operation permissions, then inherit them
-- for roles that already have the ZSJOS root. No users or business rows are changed.
-- Repeatability: stable IDs and guarded role-menu inserts make this migration idempotent.
INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6920,'学员账号交付','',2,40,6735,'student-delivery','ep:calendar','zsjos/studentDelivery/index','ZsjosStudentDelivery',0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6921,'查询学员账号交付','zsjos:student-delivery:query',3,0,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6922,'创建交付计划','zsjos:student-delivery:create',3,1,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6923,'提交交付确认','zsjos:student-delivery:submit',3,2,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6924,'申请交付延期','zsjos:student-delivery:defer',3,3,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6925,'完成交付阶段','zsjos:student-delivery:complete',3,4,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6926,'查询交付配置','zsjos:student-delivery-config:query',3,5,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0'),
(6927,'修改交付配置','zsjos:student-delivery-config:update',3,6,6920,'','','',NULL,0,b'1',b'1',b'1','migration-V224',NOW(),'migration-V224',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),updater='migration-V224',update_time=NOW(),deleted=b'0';

INSERT INTO system_role_menu(role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT DISTINCT rm.role_id,m.id,'migration-V224',NOW(),'migration-V224',NOW(),b'0',rm.tenant_id
FROM system_role_menu rm JOIN system_menu m ON m.id BETWEEN 6920 AND 6927
WHERE rm.menu_id=6735 AND rm.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=m.id AND x.tenant_id=rm.tenant_id AND x.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V224','Student delivery permissions','student-delivery-permissions-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
