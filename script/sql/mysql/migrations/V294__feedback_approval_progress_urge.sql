-- UTF-8. V294: feedback round manual urging; apply after Core V155, before application rollout.
-- Scope: nullable last_urged_at on feedback rounds; one button, one template and missing
-- default in-app rules for enabled tenants. Never changes role grants, historical rounds,
-- existing notification rules/messages, or existing version checksums.
-- Repeat/recovery: inspect actual structure even if a marker exists; additive replay repairs
-- partial application. DDL commits implicitly. Back up affected tables and both ledgers;
-- rollback application or disable the new notification rule, retaining schema and history.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v294_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v294_apply()
BEGIN
  DECLARE feedback_menu BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V155')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V155') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 requires Core V155 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
    AND table_name IN ('zsjos_feedback_round','system_menu','system_notify_template','system_notify_rule',
                      'system_tenant','zsjos_schema_version','zsjos_module_schema_version')) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 requires transactional feedback, notification, menu and ledger tables';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:feedback:query' AND type=2 AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 requires exactly one feedback page menu';
  END IF;
  SELECT id INTO feedback_menu FROM system_menu WHERE permission='zsjos:feedback:query' AND type=2 AND deleted=b'0';
  IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:feedback:requirement:urge' AND deleted=b'0'
    AND (type<>3 OR parent_id<>feedback_menu)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 urge permission belongs to a conflicting menu';
  END IF;
  IF EXISTS(SELECT 1 FROM system_notify_template WHERE code='ZSJOS_FEEDBACK_APPROVAL_URGED' AND deleted=b'0'
    AND (scene_code<>'zsjos.feedback.approval_urged' OR type<>2)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 notification template identity conflict';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_feedback_round' AND column_name='last_urged_at') THEN
    ALTER TABLE zsjos_feedback_round ADD COLUMN last_urged_at datetime NULL DEFAULT NULL COMMENT '本轮最近催办时间';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_feedback_round' AND column_name='last_urged_at' AND data_type='datetime'
    AND is_nullable='YES' AND column_default IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 last_urged_at contract mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,
    status,visible,keep_alive,always_show,creator,updater)
  SELECT '催办需求审批','zsjos:feedback:requirement:urge',3,7,feedback_menu,'','','','native',0,b'1',b'1',b'1','migration-V294','migration-V294'
  WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:feedback:requirement:urge' AND deleted=b'0');
  INSERT INTO system_notify_template(name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,updater)
  SELECT '需求审批催办','ZSJOS_FEEDBACK_APPROVAL_URGED','中世健消息中心','zsjos.feedback.approval_urged',
    '有需求等待您审批','提交人催办需求审批',
    '{{submitterName}} 提醒您审批需求 {{feedbackNo}}「{{feedbackTitle}}」，第 {{roundNo}} 轮，当前节点：{{taskName}}。',
    2,'["feedbackNo","feedbackTitle","submitterName","roundNo","taskName","taskId","processInstanceId","deepLink"]',
    0,'V294 提交人手动催办','migration-V294','migration-V294'
  WHERE NOT EXISTS(SELECT 1 FROM system_notify_template WHERE code='ZSJOS_FEEDBACK_APPROVAL_URGED' AND deleted=b'0');
  INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,updater,tenant_id)
  SELECT '需求审批催办通知','zsjos.feedback.approval_urged','in_app',template.id,'["approver"]','[]','business_detail',0,'migration-V294','migration-V294',tenant.id
  FROM system_tenant tenant JOIN system_notify_template template ON template.code='ZSJOS_FEEDBACK_APPROVAL_URGED' AND template.deleted=b'0'
  WHERE tenant.deleted=b'0' AND tenant.status=0 AND NOT EXISTS(SELECT 1 FROM system_notify_rule existing
    WHERE existing.tenant_id=tenant.id AND existing.scene_code='zsjos.feedback.approval_urged' AND existing.deleted=b'0');
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:feedback:requirement:urge' AND type=3 AND parent_id=feedback_menu AND deleted=b'0') <> 1
    OR (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_FEEDBACK_APPROVAL_URGED' AND deleted=b'0') <> 1
    OR EXISTS(SELECT 1 FROM system_tenant tenant WHERE tenant.deleted=b'0' AND tenant.status=0 AND NOT EXISTS(
      SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=tenant.id AND r.scene_code='zsjos.feedback.approval_urged' AND r.deleted=b'0')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V294 metadata postcondition failed';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V294','Feedback approval progress and urging',SHA2('V294__feedback_approval_progress_urge.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V294');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V294','Feedback approval progress and urging',SHA2('V294__feedback_approval_progress_urge.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V294');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v294_apply();
DROP PROCEDURE zsjos_v294_apply;
