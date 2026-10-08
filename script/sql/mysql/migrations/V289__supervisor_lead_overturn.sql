-- UTF-8. Core V289; execute after V288, before backend and Workbench deployment.
-- Scope: one System button, one default notification template and missing per-tenant in-app rules.
-- No role assignments, business rows, schema changes or historical migrations are rewritten.
-- Repeatable: preserves administrator configuration and existing checksums; repairs missing metadata.
-- All metadata and both ledger writes share a rollback-controlled transaction. On rollback of the
-- application, disable the button/rule through System management; retain events and sent messages.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v289_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v289_apply()
BEGIN
  DECLARE parent_menu BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V288')
    OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V288') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V289 requires Core V288';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
      AND table_name IN ('system_menu','system_notify_template','system_notify_rule',
                        'zsjos_schema_version','zsjos_module_schema_version')) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V289 requires transactional metadata and ledgers';
  END IF;
  START TRANSACTION;
-- BEGIN SUPERVISOR OVERTURN METADATA
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:query' AND type=2 AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Supervisor page missing or ambiguous';
  END IF;
  SELECT id INTO parent_menu FROM system_menu WHERE permission='zsjos:subordinate-sales:query' AND type=2 AND deleted=b'0';
  IF EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:subordinate-sales:lead-overturn-valid'
      AND (type<>3 OR parent_id<>parent_menu OR deleted<>b'0'))
    OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:lead-overturn-valid') > 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Supervisor overturn button identity conflict';
  END IF;
  IF EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_LEAD_SUPERVISOR_OVERTURNED'
      AND (scene_code<>'zsjos.lead.supervisor_overturned' OR deleted<>b'0'))
    OR (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_LEAD_SUPERVISOR_OVERTURNED') > 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Supervisor overturn template identity conflict';
  END IF;
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
  SELECT '主管直接改判有效','zsjos:subordinate-sales:lead-overturn-valid',3,11,parent_menu,'','','',0,b'1',b'1',b'0','V289','V289',b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:subordinate-sales:lead-overturn-valid');
  INSERT INTO system_notify_template(name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,updater,deleted)
  SELECT '主管直接改判有效','ZSJOS_LEAD_SUPERVISOR_OVERTURNED','中世健消息中心','zsjos.lead.supervisor_overturned',
    '主管已将客资改判有效','客资{{lead.no}}已由主管改判有效',
    '主管{{operator.name}}已将客资{{lead.no}}改判有效。理由：{{overturn.reason}}。原销售归属保留，请继续跟进。',
    2,'["lead.no","operator.name","overturn.reason"]',0,'主管直接改判，保留原申诉历史','V289','V289',b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_LEAD_SUPERVISOR_OVERTURNED');
  INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,updater,deleted,tenant_id)
  SELECT '主管直接改判通知','zsjos.lead.supervisor_overturned','in_app',template.id,'["submitter","owner"]','[]','business_detail',0,'V289','V289',b'0',tenant.id
  FROM system_tenant tenant JOIN system_notify_template template ON template.code='ZSJOS_LEAD_SUPERVISOR_OVERTURNED' AND template.deleted=b'0'
  WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule rule
    WHERE rule.tenant_id=tenant.id AND rule.scene_code='zsjos.lead.supervisor_overturned' AND rule.deleted=b'0');
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:lead-overturn-valid' AND type=3 AND parent_id=parent_menu AND deleted=b'0') <> 1
    OR (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_LEAD_SUPERVISOR_OVERTURNED' AND deleted=b'0') <> 1
    OR EXISTS (SELECT 1 FROM system_tenant tenant WHERE tenant.deleted=b'0' AND NOT EXISTS
      (SELECT 1 FROM system_notify_rule rule WHERE rule.tenant_id=tenant.id AND rule.scene_code='zsjos.lead.supervisor_overturned' AND rule.deleted=b'0')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Supervisor overturn metadata postcondition failed';
  END IF;
-- END SUPERVISOR OVERTURN METADATA
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V289','Supervisor direct Lead overturn',SHA2('V289__supervisor_lead_overturn.sql',256)
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V289');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V289','Supervisor direct Lead overturn',SHA2('V289__supervisor_lead_overturn.sql',256),'baseline'
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V289');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v289_apply();
DROP PROCEDURE zsjos_v289_apply;
