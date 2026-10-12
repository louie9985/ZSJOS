-- UTF-8. Requires V295. Additive schema and metadata, no role grants/business options.
-- Execute before backend/UI deployment; configure credentials, publish Simple model and enable
-- pointsIssuanceJob through Infra scheduling afterwards. No external issuing API is called here.
-- Repeatable after partial DDL. MySQL DDL commits implicitly; retain tables after failure.
-- Rollback: disable the feature/job, preserve applications, codes and configuration for audit.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v296_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v296_apply()
BEGIN
  DECLARE root_menu BIGINT;
  DECLARE request_menu BIGINT;
  DECLARE package_menu BIGINT;
  DECLARE config_menu BIGINT;
  DECLARE notification_template BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V295')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V295') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 requires V295';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='infra_config' AND column_name='sensitive') THEN
    ALTER TABLE infra_config ADD COLUMN `sensitive` bit(1) NOT NULL DEFAULT b'0' COMMENT '敏感配置加密存储且不回显';
  END IF;
  CREATE TABLE IF NOT EXISTS zsjos_points_package (
    id bigint NOT NULL AUTO_INCREMENT, name varchar(100) NOT NULL, credits_amount int NOT NULL,
    valid_days int DEFAULT NULL, enabled bit(1) NOT NULL,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换套餐';
  CREATE TABLE IF NOT EXISTS zsjos_points_request (
    id bigint NOT NULL AUTO_INCREMENT, request_no varchar(40) NOT NULL,
    submission_key varchar(64) NOT NULL, applicant_user_id bigint NOT NULL, applicant_name varchar(100) DEFAULT NULL,
    package_id bigint NOT NULL, package_name varchar(100) NOT NULL, credits_amount int NOT NULL,
    valid_days int DEFAULT NULL, reason varchar(2000) NOT NULL, attachments_json text NOT NULL,
    approval_status varchar(32) NOT NULL, issue_status varchar(32) NOT NULL,
    process_instance_id varchar(64) DEFAULT NULL, process_definition_id varchar(128) DEFAULT NULL,
    task_keys_json text NOT NULL, approver_ids_json text NOT NULL, order_id varchar(128) NOT NULL,
    expires_at datetime DEFAULT NULL, platform_url varchar(500) DEFAULT NULL,
    batch_id varchar(64) DEFAULT NULL, external_code_id varchar(64) DEFAULT NULL,
    code_ciphertext varchar(500) DEFAULT NULL, code_status varchar(32) DEFAULT NULL,
    redeemed_at datetime DEFAULT NULL, synced_at datetime DEFAULT NULL, attempts int NOT NULL DEFAULT 0,
    next_attempt_at datetime DEFAULT NULL, last_error varchar(300) DEFAULT NULL,
    notified bit(1) NOT NULL DEFAULT b'0', submitted_at datetime NOT NULL,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(id), UNIQUE KEY uk_order(order_id),
    UNIQUE KEY uk_submission(tenant_id,applicant_user_id,submission_key),
    UNIQUE KEY uk_process(process_instance_id), UNIQUE KEY uk_code(external_code_id),
    KEY idx_due(tenant_id,approval_status,issue_status,notified,next_attempt_at),
    KEY idx_applicant(tenant_id,applicant_user_id,id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换码申请';
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_points_package','zsjos_points_request') AND engine='InnoDB')<>2
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='infra_config' AND column_name='sensitive' AND column_type='bit(1)' AND is_nullable='NO')<>1
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_points_package')<>11
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_points_request')<>37 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 schema postcondition failed';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_points_request'
      AND ((column_name IN ('id','applicant_user_id','package_id','tenant_id') AND data_type='bigint' AND is_nullable='NO')
      OR (column_name IN ('credits_amount','attempts') AND data_type='int' AND is_nullable='NO')
      OR (column_name IN ('attachments_json','task_keys_json','approver_ids_json') AND data_type='text' AND is_nullable='NO')
      OR (column_name='code_ciphertext' AND column_type='varchar(500)')
      OR (column_name='order_id' AND column_type='varchar(128)' AND is_nullable='NO')
      OR (column_name='notified' AND column_type='bit(1)' AND is_nullable='NO')) )<>12
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_points_package'
      AND column_name='credits_amount' AND data_type='int' AND is_nullable='NO')<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 business column types mismatch';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_points_request' AND index_name='uk_submission' AND non_unique=0),'')<>'tenant_id,applicant_user_id,submission_key'
    OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_points_request' AND index_name='uk_order' AND non_unique=0),'')<>'order_id' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 uniqueness postcondition failed';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND deleted=0)<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 Workbench root missing or ambiguous';
  END IF;
  SELECT id INTO root_menu FROM system_menu WHERE path='/zsjos' AND deleted=0;
  SELECT parent_id INTO config_menu FROM system_menu WHERE permission='infra:config:query' AND deleted=0 LIMIT 1;
  IF config_menu IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 Infra config menu missing'; END IF;
  START TRANSACTION;
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '积分兑换码','zsjos:points-redemption:query',2,90,root_menu,'points-redemption','ep:ticket','zsjos-workbench',0,b'1',b'1',b'0','V296','V296',b'0'
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:points-redemption:query');
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '积分套餐','zsjos:points-redemption:package',2,91,root_menu,'points-packages','ep:collection','zsjos/pointsPackages/index',0,b'1',b'1',b'0','V296','V296',b'0'
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:points-redemption:package');
  SELECT id INTO request_menu FROM system_menu WHERE permission='zsjos:points-redemption:query' AND deleted=0;
  SELECT id INTO package_menu FROM system_menu WHERE permission='zsjos:points-redemption:package' AND deleted=0;
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT buttons.name,CONCAT('zsjos:points-redemption:',buttons.suffix),3,1,request_menu,'','','',0,b'1',b'1',b'0','V296','V296',b'0'
    FROM (SELECT '申请兑换码' name,'create' suffix UNION ALL SELECT '查看兑换码','reveal'
      UNION ALL SELECT '取消申请','cancel' UNION ALL SELECT '管理申请','manage'
      UNION ALL SELECT '重试发码','retry' UNION ALL SELECT '作废兑换码','disable'
      UNION ALL SELECT '同步兑换状态','refresh') buttons
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission=CONCAT('zsjos:points-redemption:',buttons.suffix));
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '保存积分套餐','zsjos:points-redemption:package-save',3,1,package_menu,'','','',0,b'1',b'1',b'0','V296','V296',b'0'
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:points-redemption:package-save');
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
    SELECT '修改敏感配置','infra:config:secret-update',3,99,config_menu,'','','',0,b'1',b'1',b'0','V296','V296',b'0'
    WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='infra:config:secret-update');
  INSERT INTO infra_config(category,type,name,config_key,value,visible,`sensitive`,remark)
    SELECT '积分兑换码',1,settings.name,CONCAT('zsjos.points-redemption.',settings.suffix),settings.value,b'0',settings.secret_flag,'V296；由管理员配置'
    FROM (SELECT '积分平台API地址' name,'base-url' suffix,'https://zsj.nbility.ai/api/partner/v1' value,0 secret_flag
      UNION ALL SELECT '积分平台API密钥','api-key','',1
      UNION ALL SELECT '积分兑换码启用','enabled','false',0
      UNION ALL SELECT '积分平台请求超时秒','timeout-seconds','30',0
      UNION ALL SELECT '积分发码自动尝试次数','max-attempts','5',0) settings
    WHERE NOT EXISTS(SELECT 1 FROM infra_config WHERE config_key=CONCAT('zsjos.points-redemption.',settings.suffix) AND deleted=0);
  INSERT INTO system_notify_template(name,code,nickname,scene_code,title,summary,content,type,params,channel_code,status)
    SELECT '积分兑换码已生成','zsjos_points_issued','中世健','zsjos.points-redemption.issued','积分兑换码已生成',
      '您的{creditsAmount}积分兑换码已生成','申请{requestNo}已审批通过，兑换码已生成。请点击查看详情。',1,'["requestNo","creditsAmount"]','in_app',0
    WHERE NOT EXISTS(SELECT 1 FROM system_notify_template WHERE code='zsjos_points_issued' AND deleted=0);
  SELECT id INTO notification_template FROM system_notify_template WHERE code='zsjos_points_issued' AND deleted=0;
  INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,tenant_id)
    SELECT '积分兑换码成功通知','zsjos.points-redemption.issued','in_app',notification_template,'["applicant"]','[]','business_detail',0,1
    WHERE NOT EXISTS(SELECT 1 FROM system_notify_rule WHERE scene_code='zsjos.points-redemption.issued' AND tenant_id=1 AND deleted=0);
  IF (SELECT COUNT(*) FROM infra_config WHERE config_key LIKE 'zsjos.points-redemption.%' AND deleted=0)<>5
    OR (SELECT COUNT(*) FROM infra_config WHERE config_key='zsjos.points-redemption.api-key' AND `sensitive`=1 AND visible=0 AND deleted=0)<>1
    OR (SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:points-redemption:%' AND deleted=0)<>10 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V296 metadata postcondition failed';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
    SELECT 'V296','Points redemption approval',SHA2('V296__points_redemption.sql',256)
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V296');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
    SELECT 'core','V296','Points redemption approval',SHA2('V296__points_redemption.sql',256),'baseline'
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V296');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v296_apply();
DROP PROCEDURE zsjos_v296_apply;
