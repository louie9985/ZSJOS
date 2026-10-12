-- UTF-8. Tenant 1 only. HRM V003 follows V002 and the current core notification schema.
-- Additive upgrade: preserve original dates and history; no role grants, employee state changes or message sends.
-- Repeatable after partial DDL: exact information_schema checks and row-level migration markers.
-- DDL commits implicitly; rollback requires restoring a backup. Do not downgrade the application after conversion.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS hrm_manual_lifecycle_v003;
DELIMITER $$
CREATE PROCEDURE hrm_manual_lifecycle_v003()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('hrm_employee','hrm_employee_change_record','hrm_employee_quit_info','system_notify_template','system_notify_rule','infra_job','zsjos_schema_version','zsjos_module_schema_version')) <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HRM V003 prerequisites are incomplete';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='planned_regular_time') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `planned_regular_time` datetime DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='planned_leave_time') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `planned_leave_time` datetime DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='version') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `version` bigint NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='type_label') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `type_label` varchar(255) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='status_label') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `status_label` varchar(255) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='entry_status_label') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `entry_status_label` varchar(255) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='legacy_date_source') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `legacy_date_source` varchar(128) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='legacy_regular_time') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `legacy_regular_time` datetime DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' AND column_name='legacy_leave_time') THEN
    ALTER TABLE `hrm_employee` ADD COLUMN `legacy_leave_time` datetime DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='state') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `state` varchar(24) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='version') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `version` bigint NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='operation_key') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `operation_key` varchar(64) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='change_mode') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `change_mode` varchar(24) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='planned_time') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `planned_time` datetime DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='actual_time') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `actual_time` datetime DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='before_snapshot') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `before_snapshot` longtext;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='after_snapshot') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `after_snapshot` longtext;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND column_name='request_hash') THEN
    ALTER TABLE `hrm_employee_change_record` ADD COLUMN `request_hash` varchar(64) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='hrm_employee_change_record' AND index_name='uk_hrm_change_operation') THEN
    ALTER TABLE hrm_employee_change_record ADD UNIQUE INDEX uk_hrm_change_operation(operation_key);
  END IF;
  START TRANSACTION;
  UPDATE hrm_employee SET legacy_regular_time=regular_time, legacy_leave_time=leave_time
  WHERE tenant_id=1 AND legacy_date_source IS NULL;
  UPDATE hrm_employee e LEFT JOIN hrm_employee_quit_info q ON q.employee_id=e.id AND q.deleted=b'0'
  SET e.planned_regular_time=CASE WHEN e.status=2 THEN e.legacy_regular_time ELSE e.planned_regular_time END,
      e.planned_leave_time=CASE WHEN e.entry_status=3 THEN COALESCE(e.legacy_leave_time,q.plan_quit_time) ELSE e.planned_leave_time END,
      e.regular_time=CASE WHEN e.status=1 THEN e.legacy_regular_time ELSE NULL END,
      e.leave_time=CASE WHEN e.entry_status=4 THEN e.legacy_leave_time ELSE NULL END,
      e.legacy_date_source=CASE WHEN (q.plan_quit_time IS NOT NULL AND e.legacy_leave_time IS NOT NULL AND q.plan_quit_time<>e.legacy_leave_time)
          OR (e.status NOT IN(1,2) AND e.legacy_regular_time IS NOT NULL)
          OR (e.entry_status NOT IN(3,4) AND e.legacy_leave_time IS NOT NULL)
          THEN '旧系统日期待核实' ELSE '旧系统记录（按状态承接）' END
  WHERE e.tenant_id=1 AND e.legacy_date_source IS NULL;
  UPDATE hrm_employee_change_record SET state=CASE WHEN applied_time IS NULL THEN 'PENDING' ELSE 'APPLIED' END,
      change_mode=CASE WHEN applied_time IS NULL THEN 'PLAN' ELSE 'BUSINESS' END,
      planned_time=CASE WHEN applied_time IS NULL THEN effect_time ELSE NULL END,
      actual_time=CASE WHEN applied_time IS NOT NULL THEN effect_time ELSE NULL END
  WHERE tenant_id=1 AND state IS NULL;
  UPDATE hrm_employee_change_record r JOIN hrm_employee e ON e.id=r.employee_id
  SET r.planned_time=e.planned_regular_time
  WHERE r.tenant_id=1 AND e.tenant_id=1 AND r.state='PENDING' AND r.type=4 AND e.planned_regular_time IS NOT NULL;

  INSERT INTO hrm_employee_change_record(employee_id,type,state,version,change_mode,planned_time,effect_time,remark,tenant_id)
  SELECT e.id,4,'PENDING',0,'PLAN',e.planned_regular_time,e.planned_regular_time,'旧系统转正计划，请先核对',1
  FROM hrm_employee e WHERE e.tenant_id=1 AND e.deleted=b'0' AND e.status=2 AND e.entry_status IN(1,3) AND e.planned_regular_time IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM hrm_employee_change_record r WHERE r.employee_id=e.id AND r.type=4 AND r.state='PENDING' AND r.deleted=b'0');
  INSERT INTO hrm_employee_change_record(employee_id,type,state,version,change_mode,planned_time,effect_time,remark,tenant_id)
  SELECT e.id,11,'PENDING',0,'PLAN',e.planned_leave_time,e.planned_leave_time,'旧系统离职计划，请先核对',1
  FROM hrm_employee e WHERE e.tenant_id=1 AND e.deleted=b'0' AND e.entry_status IN(1,3) AND e.planned_leave_time IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM hrm_employee_change_record r WHERE r.employee_id=e.id AND r.type=11 AND r.state='PENDING' AND r.deleted=b'0');
  INSERT INTO system_notify_template (name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,create_time,updater,update_time,deleted)
  SELECT '计划转正提醒','HRM_LIFECYCLE_REGULAR','中世健人力资源中心','hrm.employee.lifecycle_regular','计划转正提醒','{{employee.name}}的人事计划提醒','{{employee.name}}的计划日期为{{plan.date}}，请核对并按实际情况维护员工档案。系统不会自动修改状态。',2,'["employee.name","plan.date"]',0,'HRM V003：只发消息','hrm-v003',NOW(),'hrm-v003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='HRM_LIFECYCLE_REGULAR' AND deleted=b'0');
  INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT '计划转正提醒','hrm.employee.lifecycle_regular','in_app',t.id,'["hrm_lifecycle_recipient"]','[]','business_detail',0,'hrm-v003',NOW(),'hrm-v003',NOW(),b'0',1
  FROM system_notify_template t WHERE t.code='HRM_LIFECYCLE_REGULAR' AND t.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM system_notify_rule WHERE tenant_id=1 AND scene_code='hrm.employee.lifecycle_regular' AND deleted=b'0');
  INSERT INTO system_notify_template (name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,create_time,updater,update_time,deleted)
  SELECT '计划离职提醒','HRM_LIFECYCLE_LEAVE','中世健人力资源中心','hrm.employee.lifecycle_leave','计划离职提醒','{{employee.name}}的人事计划提醒','{{employee.name}}的计划日期为{{plan.date}}，请核对并按实际情况维护员工档案。系统不会自动修改状态。',2,'["employee.name","plan.date"]',0,'HRM V003：只发消息','hrm-v003',NOW(),'hrm-v003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='HRM_LIFECYCLE_LEAVE' AND deleted=b'0');
  INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT '计划离职提醒','hrm.employee.lifecycle_leave','in_app',t.id,'["hrm_lifecycle_recipient"]','[]','business_detail',0,'hrm-v003',NOW(),'hrm-v003',NOW(),b'0',1
  FROM system_notify_template t WHERE t.code='HRM_LIFECYCLE_LEAVE' AND t.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM system_notify_rule WHERE tenant_id=1 AND scene_code='hrm.employee.lifecycle_leave' AND deleted=b'0');
  INSERT INTO system_notify_template (name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,create_time,updater,update_time,deleted)
  SELECT '人事异动提醒','HRM_LIFECYCLE_CHANGE','中世健人力资源中心','hrm.employee.lifecycle_change','人事异动提醒','{{employee.name}}的人事计划提醒','{{employee.name}}的计划日期为{{plan.date}}，请核对并按实际情况维护员工档案。系统不会自动修改状态。',2,'["employee.name","plan.date"]',0,'HRM V003：只发消息','hrm-v003',NOW(),'hrm-v003',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='HRM_LIFECYCLE_CHANGE' AND deleted=b'0');
  INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT '人事异动提醒','hrm.employee.lifecycle_change','in_app',t.id,'["hrm_lifecycle_recipient"]','[]','business_detail',0,'hrm-v003',NOW(),'hrm-v003',NOW(),b'0',1
  FROM system_notify_template t WHERE t.code='HRM_LIFECYCLE_CHANGE' AND t.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM system_notify_rule WHERE tenant_id=1 AND scene_code='hrm.employee.lifecycle_change' AND deleted=b'0');
  INSERT INTO infra_job(name,status,handler_name,handler_param,cron_expression,retry_count,retry_interval,monitor_timeout,creator,create_time,updater,update_time,deleted)
  SELECT '人事到期消息提醒',2,'hrmEmployeeChangeJob',NULL,'0 0/10 * * * ?',0,0,0,'hrm-v003',NOW(),'hrm-v003',NOW(),b'0'
  WHERE NOT EXISTS(SELECT 1 FROM infra_job WHERE handler_name='hrmEmployeeChangeJob' AND deleted=b'0');
  IF EXISTS(SELECT 1 FROM hrm_employee WHERE tenant_id=1 AND legacy_date_source IS NULL) OR EXISTS(SELECT 1 FROM hrm_employee_change_record WHERE tenant_id=1 AND state IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HRM V003 conversion incomplete'; END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('HRM_V003','HRM manual lifecycle','hrm-manual-lifecycle-v1') ON DUPLICATE KEY UPDATE description=VALUES(description);
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES('hrm','V003','HRM manual lifecycle',SHA2('V003__manual_employee_lifecycle.sql',256),'upgrade',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description);
  COMMIT;
END$$
DELIMITER ;
CALL hrm_manual_lifecycle_v003();
DROP PROCEDURE IF EXISTS hrm_manual_lifecycle_v003;
