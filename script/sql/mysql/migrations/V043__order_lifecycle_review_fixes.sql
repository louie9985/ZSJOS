-- V043: forward repair for order command idempotency and Person contact uniqueness.
-- Apply after V042. Repeatable; it does not merge, truncate, or delete Person business data.

SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='terminated_at'),'SELECT 1','ALTER TABLE zsjos_order ADD COLUMN terminated_at datetime DEFAULT NULL COMMENT ''终止时间'' AFTER termination_reason'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='registration_decision_idempotency_key'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD COLUMN registration_decision_idempotency_key varchar(128) DEFAULT NULL COMMENT ''报名履约决策幂等键'' AFTER submission_idempotency_key'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='finance_decision_idempotency_key'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD COLUMN finance_decision_idempotency_key varchar(128) DEFAULT NULL COMMENT ''财务决策幂等键'' AFTER registration_decision_idempotency_key'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='termination_idempotency_key'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD COLUMN termination_idempotency_key varchar(128) DEFAULT NULL COMMENT ''终止幂等键'' AFTER finance_decision_idempotency_key'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='version'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD COLUMN version int NOT NULL DEFAULT 0 COMMENT ''并发版本'' AFTER termination_idempotency_key'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND index_name='uk_tenant_active_repurchase'),'SELECT 1','ALTER TABLE zsjos_order ADD UNIQUE KEY uk_tenant_active_repurchase(tenant_id,active_repurchase_person_id)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND index_name='uk_tenant_registration_decision_key'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD UNIQUE KEY uk_tenant_registration_decision_key(tenant_id,registration_decision_idempotency_key)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND index_name='uk_tenant_finance_decision_key'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD UNIQUE KEY uk_tenant_finance_decision_key(tenant_id,finance_decision_idempotency_key)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND index_name='uk_tenant_termination_key'),'SELECT 1','ALTER TABLE zsjos_order_approval_round ADD UNIQUE KEY uk_tenant_termination_key(tenant_id,termination_idempotency_key)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP PROCEDURE IF EXISTS zsjos_v043_assert_person_contacts;
DELIMITER $$
CREATE PROCEDURE zsjos_v043_assert_person_contacts()
BEGIN
  DECLARE conflict_count bigint DEFAULT 0;
  SELECT COUNT(*) INTO conflict_count FROM zsjos_person
   WHERE deleted=b'0' AND ((mobile IS NOT NULL AND TRIM(mobile)='') OR (wechat_id IS NOT NULL AND TRIM(wechat_id)=''));
  IF conflict_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V043 blocked: blank Person contact values exist'; END IF;
  SELECT COUNT(*) INTO conflict_count FROM zsjos_person
   WHERE deleted=b'0' AND wechat_id IS NOT NULL AND CHAR_LENGTH(TRIM(wechat_id))>64;
  IF conflict_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V043 blocked: Person wechat_id exceeds 64 characters'; END IF;
  SELECT COUNT(*) INTO conflict_count FROM (
    SELECT tenant_id,contact_value FROM (
      SELECT tenant_id,CONVERT(TRIM(mobile) USING utf8mb4) COLLATE utf8mb4_bin contact_value,id FROM zsjos_person WHERE deleted=b'0' AND mobile IS NOT NULL
      UNION ALL
      SELECT tenant_id,CONVERT(TRIM(wechat_id) USING utf8mb4) COLLATE utf8mb4_bin,id FROM zsjos_person WHERE deleted=b'0' AND wechat_id IS NOT NULL
    ) contacts GROUP BY tenant_id,contact_value HAVING COUNT(DISTINCT id)>1
  ) conflicts;
  IF conflict_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V043 blocked: duplicate or cross-field Person contacts exist'; END IF;
END$$
DELIMITER ;
CALL zsjos_v043_assert_person_contacts();
DROP PROCEDURE zsjos_v043_assert_person_contacts;

SET @ddl=(SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='zsjos_person' AND column_name='wechat_id'
     AND character_maximum_length=64 AND collation_name='utf8mb4_unicode_ci'
),'SELECT 1','ALTER TABLE zsjos_person MODIFY COLUMN wechat_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''微信号（脱敏展示）'''));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS zsjos_person_contact_claim (
  id bigint NOT NULL AUTO_INCREMENT,
  contact_value varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'trim 后手机号或微信号',
  person_id bigint DEFAULT NULL COMMENT '占用客户编号',
  reservation_key varchar(64) DEFAULT NULL COMMENT '事务预占键',
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(id), UNIQUE KEY uk_tenant_contact_value(tenant_id,contact_value),
  KEY idx_tenant_person(tenant_id,person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Person 联系方式唯一占用';

INSERT INTO zsjos_person_contact_claim(contact_value,person_id,reservation_key,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT contact_value,MIN(person_id),NULL,'migration-V043',NOW(),'migration-V043',NOW(),b'0',tenant_id FROM (
  SELECT tenant_id,id person_id,CONVERT(TRIM(mobile) USING utf8mb4) COLLATE utf8mb4_bin contact_value FROM zsjos_person WHERE deleted=b'0' AND mobile IS NOT NULL
  UNION ALL
  SELECT tenant_id,id,CONVERT(TRIM(wechat_id) USING utf8mb4) COLLATE utf8mb4_bin FROM zsjos_person WHERE deleted=b'0' AND wechat_id IS NOT NULL
) contacts GROUP BY tenant_id,contact_value
ON DUPLICATE KEY UPDATE person_id=VALUES(person_id),reservation_key=NULL;

CREATE TABLE IF NOT EXISTS zsjos_order_command (
  id bigint NOT NULL AUTO_INCREMENT, idempotency_key varchar(128) NOT NULL,
  order_id bigint NOT NULL, approval_round_id bigint NOT NULL, process_instance_id varchar(128) NOT NULL,
  command_type varchar(32) NOT NULL, task_definition_key varchar(128) DEFAULT NULL, bpm_task_id varchar(128) DEFAULT NULL,
  operator_user_id bigint NOT NULL, request_fingerprint char(64) NOT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(id), UNIQUE KEY uk_tenant_order_command_key(tenant_id,idempotency_key),
  KEY idx_tenant_order_round(tenant_id,order_id,approval_round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单审批与终止命令账本';

DROP PROCEDURE IF EXISTS zsjos_v043_normalize_filter_doc;
DELIMITER $$
CREATE PROCEDURE zsjos_v043_normalize_filter_doc(IN source_doc JSON, OUT result_doc JSON)
BEGIN
  DECLARE g int DEFAULT 0; DECLARE c int; DECLARE o int; DECLARE v int; DECLARE legacy_option boolean;
  SET result_doc=source_doc;
  WHILE g < COALESCE(JSON_LENGTH(result_doc,'$.groups'),0) DO
    SET c=0;
    WHILE c < COALESCE(JSON_LENGTH(result_doc,CONCAT('$.groups[',g,'].conditions')),0) DO
      IF JSON_UNQUOTE(JSON_EXTRACT(result_doc,CONCAT('$.groups[',g,'].conditions[',c,'].field')))='status' THEN
        SET v=0;
        WHILE v < COALESCE(JSON_LENGTH(result_doc,CONCAT('$.groups[',g,'].conditions[',c,'].values')),0) DO
          IF JSON_UNQUOTE(JSON_EXTRACT(result_doc,CONCAT('$.groups[',g,'].conditions[',c,'].values[',v,']')))='converted' THEN
            SET result_doc=JSON_SET(result_doc,CONCAT('$.groups[',g,'].conditions[',c,'].values[',v,']'),'won');
          END IF; SET v=v+1;
        END WHILE;
      END IF; SET c=c+1;
    END WHILE;
    SET o=0;
    WHILE o < COALESCE(JSON_LENGTH(result_doc,CONCAT('$.groups[',g,'].options')),0) DO
      SET c=0; SET legacy_option=false;
      WHILE c < COALESCE(JSON_LENGTH(result_doc,CONCAT('$.groups[',g,'].options[',o,'].conditions')),0) DO
        IF JSON_UNQUOTE(JSON_EXTRACT(result_doc,CONCAT('$.groups[',g,'].options[',o,'].conditions[',c,'].field')))='status' THEN
          SET v=0;
          WHILE v < COALESCE(JSON_LENGTH(result_doc,CONCAT('$.groups[',g,'].options[',o,'].conditions[',c,'].values')),0) DO
            IF JSON_UNQUOTE(JSON_EXTRACT(result_doc,CONCAT('$.groups[',g,'].options[',o,'].conditions[',c,'].values[',v,']')))='converted' THEN
              SET legacy_option=true;
              SET result_doc=JSON_SET(result_doc,CONCAT('$.groups[',g,'].options[',o,'].conditions[',c,'].values[',v,']'),'won');
            END IF; SET v=v+1;
          END WHILE;
        END IF; SET c=c+1;
      END WHILE;
      IF legacy_option AND JSON_UNQUOTE(JSON_EXTRACT(result_doc,CONCAT('$.groups[',g,'].options[',o,'].key')))='converted' THEN
        SET result_doc=JSON_SET(result_doc,CONCAT('$.groups[',g,'].options[',o,'].key'),'won');
        IF JSON_UNQUOTE(JSON_EXTRACT(result_doc,CONCAT('$.groups[',g,'].options[',o,'].label')))='已进入转化' THEN
          SET result_doc=JSON_SET(result_doc,CONCAT('$.groups[',g,'].options[',o,'].label'),'已成交');
        END IF;
      END IF;
      SET o=o+1;
    END WHILE;
    SET g=g+1;
  END WHILE;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS zsjos_v043_normalize_filters;
DELIMITER $$
CREATE PROCEDURE zsjos_v043_normalize_filters()
BEGIN
  DECLARE done int DEFAULT 0; DECLARE row_id bigint; DECLARE draft_doc JSON; DECLARE published_doc JSON;
  DECLARE normalized_draft JSON; DECLARE normalized_published JSON;
  DECLARE rows_cursor CURSOR FOR SELECT id,draft_config_json,published_config_json FROM zsjos_lead_inbox_filter_scheme
    WHERE audience IN('submitter','owner') AND deleted=b'0';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN rows_cursor;
  read_loop: LOOP
    FETCH rows_cursor INTO row_id,draft_doc,published_doc; IF done=1 THEN LEAVE read_loop; END IF;
    CALL zsjos_v043_normalize_filter_doc(draft_doc,normalized_draft);
    CALL zsjos_v043_normalize_filter_doc(published_doc,normalized_published);
    IF NOT (normalized_draft <=> draft_doc) OR NOT (normalized_published <=> published_doc) THEN
      UPDATE zsjos_lead_inbox_filter_scheme SET draft_config_json=normalized_draft,
        published_config_json=normalized_published,updater='migration-V043',update_time=NOW() WHERE id=row_id;
    END IF;
  END LOOP;
  CLOSE rows_cursor;
END$$
DELIMITER ;
CALL zsjos_v043_normalize_filters();
DROP PROCEDURE zsjos_v043_normalize_filters;
DROP PROCEDURE zsjos_v043_normalize_filter_doc;

INSERT INTO zsjos_schema_version(version,description,checksum) VALUES
('V043','Repair order lifecycle review findings','order-lifecycle-review-fixes-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES
('core','V043','Repair order lifecycle review findings',SHA2('order-lifecycle-review-fixes-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
