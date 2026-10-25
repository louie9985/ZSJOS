-- UTF-8. Apply after V348 in both Core ledgers, before the matching backend.
-- Scope: one system-owned notification template and a tenant-1 default in-app rule.
-- No role grants, employee selection, historical message backfill or business-row changes.
-- Replay preserves existing administrator templates/rules (including disabled rules).
-- Partial metadata is completed even if a version marker already exists. All DML and
-- both success markers share one InnoDB transaction; failures propagate to the runner.
-- Rollback: disable the new rule in System notification management; keep message history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v349_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v349_apply()
BEGIN
 DECLARE template_id_value BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V348')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V348') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V349 requires V348 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('system_notify_template','system_notify_rule','zsjos_schema_version','zsjos_module_schema_version') AND engine='InnoDB')<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V349 requires transactional notification metadata and ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM system_tenant WHERE id=1 AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V349 requires tenant 1';
 END IF;
 START TRANSACTION;
 INSERT INTO system_notify_template(name,code,nickname,scene_code,title,summary,content,type,params,channel_code,status,remark,creator,updater)
 SELECT '学员教学服务关闭','ZSJOS_TEACHING_SERVICE_CLOSED','中世健消息中心','zsjos.student.service_closed',
 '学员服务已关闭','{{planner.name}}已关闭学员{{student.name}}的{{course.name}}服务。',
 '学员：{{student.name}}；课程：{{course.name}}；学习规划师：{{planner.name}}；关闭原因：{{close.reason}}；关闭时间：{{close.time}}。请查看当前课程服务。',
 2,'["student.name","course.name","planner.name","close.reason","close.time"]','in_app',0,'V349 教学服务关闭通知','migration-V349','migration-V349'
 WHERE NOT EXISTS(SELECT 1 FROM system_notify_template WHERE code='ZSJOS_TEACHING_SERVICE_CLOSED' AND deleted=0);
 IF (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_TEACHING_SERVICE_CLOSED' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V349 requires one teaching-close template';
 END IF;
 SELECT id INTO template_id_value FROM system_notify_template WHERE code='ZSJOS_TEACHING_SERVICE_CLOSED' AND deleted=0;
 INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,updater,tenant_id)
 SELECT '学员教学服务关闭通知主管','zsjos.student.service_closed','in_app',template_id_value,
 '["delivery_supervisor"]','[]','business_detail',0,'migration-V349','migration-V349',1
 WHERE NOT EXISTS(SELECT 1 FROM system_notify_rule WHERE tenant_id=1 AND scene_code='zsjos.student.service_closed' AND deleted=0);
 IF NOT EXISTS(SELECT 1 FROM system_notify_rule WHERE tenant_id=1 AND scene_code='zsjos.student.service_closed' AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V349 notification rule postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V349','教学服务关闭通知主管',SHA2('V349__teaching_service_closed_notification.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V349');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V349','教学服务关闭通知主管',SHA2('V349__teaching_service_closed_notification.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V349');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v349_apply();
DROP PROCEDURE zsjos_v349_apply;
