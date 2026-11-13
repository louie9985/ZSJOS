-- UTF-8. Optional deployment configuration for registration collection submission.
-- Prerequisites: existing system_notify_template/system_notify_rule and tenant 1.
-- Scope: one new system-owned in-app template and its default tenant-1 rule only.
-- Apply with the new application before accepting public collection submissions.
-- Repeatable: preserve any existing template/rule, including administrator changes.
-- Recovery: replay after partial application. No schema/version ledger changes.
-- Rollback: disable the new rule through notification management; preserve message history.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS configure_registration_collection_notification$$
CREATE PROCEDURE configure_registration_collection_notification()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM system_tenant WHERE id=1 AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tenant 1 is required';
  END IF;
  START TRANSACTION;
  INSERT INTO system_notify_template
    (name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,create_time,updater,update_time,deleted)
  SELECT '报名信息收集表已填写','ZSJOS_REGISTRATION_INFO_SUBMITTED','中世健消息中心',
    'zsjos.registration.info_submitted','报名信息收集表已填写',
    '{{student.name}}学员填写了报名信息收集表',
    '{{student.name}}学员填写了报名信息收集表',
    2,'["student.name"]',0,'报名收集正式提交通知',
    'registration-collection',NOW(),'registration-collection',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_REGISTRATION_INFO_SUBMITTED' AND deleted=b'0');
  IF (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_REGISTRATION_INFO_SUBMITTED'
      AND scene_code='zsjos.registration.info_submitted' AND deleted=b'0')<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Collection template code or scene conflict';
  END IF;
  INSERT INTO system_notify_rule
    (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT '报名信息收集表已填写','zsjos.registration.info_submitted','in_app',t.id,
    '["pool_handlers"]','[]','message_detail',0,'registration-collection',NOW(),'registration-collection',NOW(),b'0',1
  FROM system_notify_template t
  WHERE t.code='ZSJOS_REGISTRATION_INFO_SUBMITTED' AND t.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=1
      AND r.scene_code='zsjos.registration.info_submitted' AND r.deleted=b'0');
  IF NOT EXISTS (SELECT 1 FROM system_notify_rule WHERE tenant_id=1
      AND scene_code='zsjos.registration.info_submitted' AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Collection rule missing';
  END IF;
  COMMIT;
END$$
CALL configure_registration_collection_notification()$$
DROP PROCEDURE configure_registration_collection_notification$$
DELIMITER ;
