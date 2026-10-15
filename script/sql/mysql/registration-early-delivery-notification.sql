-- UTF-8. Optional deployment configuration for registration early delivery.
-- Prerequisites: existing system_notify_template/system_notify_rule and tenant 1.
-- Scope: one new system-owned in-app template and its default tenant-1 rule only.
-- Apply with the new application before accepting early-delivery cancellations.
-- Repeatable: preserve any existing template/rule, including administrator changes.
-- Recovery: replay after partial application. No schema/version ledger changes.
-- Rollback: disable the new rule through notification management; preserve message history.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS configure_registration_early_delivery$$
CREATE PROCEDURE configure_registration_early_delivery()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM system_tenant WHERE id=1 AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tenant 1 is required';
  END IF;
  START TRANSACTION;
  INSERT INTO system_notify_template
    (name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,create_time,updater,update_time,deleted)
  SELECT '提前交付已停止','ZSJOS_REGISTRATION_DELIVERY_CANCELLED','中世健消息中心',
    'zsjos.registration.delivery_cancelled','提前交付已停止',
    '报名任务 {{registration.caseId}} 的交付已停止：{{reason}}',
    '报名任务 {{registration.caseId}} 的交付已停止：{{reason}}。已发生的交付记录保留，请勿继续执行该服务待办。',
    2,'["registration.caseId","reason"]',0,'报名提前交付停止通知',
    'registration-early-delivery',NOW(),'registration-early-delivery',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_REGISTRATION_DELIVERY_CANCELLED' AND deleted=b'0');
  IF (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_REGISTRATION_DELIVERY_CANCELLED'
      AND scene_code='zsjos.registration.delivery_cancelled' AND deleted=b'0')<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cancellation template code or scene conflict';
  END IF;
  INSERT INTO system_notify_rule
    (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT '提前交付已停止','zsjos.registration.delivery_cancelled','in_app',t.id,
    '["study_planner"]','[]','message_detail',0,'registration-early-delivery',NOW(),'registration-early-delivery',NOW(),b'0',1
  FROM system_notify_template t
  WHERE t.code='ZSJOS_REGISTRATION_DELIVERY_CANCELLED' AND t.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=1
      AND r.scene_code='zsjos.registration.delivery_cancelled' AND r.deleted=b'0');
  IF NOT EXISTS (SELECT 1 FROM system_notify_rule WHERE tenant_id=1
      AND scene_code='zsjos.registration.delivery_cancelled' AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cancellation rule missing';
  END IF;
  COMMIT;
END$$
CALL configure_registration_early_delivery()$$
DROP PROCEDURE configure_registration_early_delivery$$
DELIMITER ;
