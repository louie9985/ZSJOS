-- UTF-8. Core V295; execute after V294, before deploying newcomer-pool backend/UI.
-- Scope: two empty ZSJOS tables, one history column, one System button metadata row, two version rows.
-- Repeatable after partial DDL; no business rows or role assignments are changed.
-- Rollback: disable the button and application feature; retain history, counts and schema for audit.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v295_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v295_apply()
BEGIN
  DECLARE parent_menu BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V294')
    OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V294') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 requires Core V294';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_assignment_history' AND column_name='newcomer_priority') = 0 THEN
    ALTER TABLE zsjos_lead_assignment_history ADD COLUMN newcomer_priority bit(1) NOT NULL DEFAULT b'0'
      COMMENT '是否按新手池优先派出';
  END IF;
  CREATE TABLE IF NOT EXISTS zsjos_newcomer_pool_config (
    id bigint NOT NULL AUTO_INCREMENT, user_id bigint NOT NULL, enabled bit(1) NOT NULL DEFAULT b'0',
    daily_quota int NOT NULL, creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT '0',
    PRIMARY KEY(id), UNIQUE KEY uk_tenant_user(tenant_id,user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售新手池配置';
  CREATE TABLE IF NOT EXISTS zsjos_newcomer_pool_daily (
    id bigint NOT NULL AUTO_INCREMENT, user_id bigint NOT NULL, business_date date NOT NULL,
    accepted_count int NOT NULL DEFAULT '0', creator varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT '0',
    PRIMARY KEY(id), UNIQUE KEY uk_tenant_user_date(tenant_id,user_id,business_date)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售新手池每日成功接单数';
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_newcomer_pool_config','zsjos_newcomer_pool_daily') AND engine='InnoDB') <> 2
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_assignment_history' AND column_name='newcomer_priority'
      AND column_type='bit(1)' AND is_nullable='NO' AND column_default="b'0'") <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 schema postcondition failed';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_newcomer_pool_config','zsjos_newcomer_pool_daily')
      AND ((column_name IN ('id','user_id','tenant_id') AND data_type='bigint' AND is_nullable='NO')
        OR (column_name IN ('creator','updater') AND column_type='varchar(64)')
        OR (column_name IN ('create_time','update_time') AND data_type='datetime' AND is_nullable='NO')
        OR (column_name='deleted' AND column_type='bit(1)' AND is_nullable='NO')
        OR (table_name='zsjos_newcomer_pool_config' AND column_name='enabled' AND column_type='bit(1)' AND is_nullable='NO')
        OR (table_name='zsjos_newcomer_pool_config' AND column_name='daily_quota' AND data_type='int' AND is_nullable='NO')
        OR (table_name='zsjos_newcomer_pool_daily' AND column_name='business_date' AND data_type='date' AND is_nullable='NO')
        OR (table_name='zsjos_newcomer_pool_daily' AND column_name='accepted_count' AND data_type='int' AND is_nullable='NO'))) <> 20
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_newcomer_pool_config','zsjos_newcomer_pool_daily')
      AND column_name='id' AND extra LIKE '%auto_increment%') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 table structure mismatch';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_newcomer_pool_config' AND index_name='uk_tenant_user'
        AND non_unique=0),'') <> 'tenant_id,user_id'
    OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_newcomer_pool_daily' AND index_name='uk_tenant_user_date'
        AND non_unique=0),'') <> 'tenant_id,user_id,business_date' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 tenant uniqueness mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:query'
      AND type=2 AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 supervisor page missing or ambiguous';
  END IF;
  SELECT id INTO parent_menu FROM system_menu WHERE permission='zsjos:subordinate-sales:query'
    AND type=2 AND deleted=b'0';
  IF EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:subordinate-sales:newcomer-pool'
      AND (type<>3 OR parent_id<>parent_menu OR deleted<>b'0'))
    OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:newcomer-pool') > 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 newcomer button identity conflict';
  END IF;
  START TRANSACTION;
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,
                          keep_alive,always_show,creator,updater,deleted)
  SELECT '配置新手池','zsjos:subordinate-sales:newcomer-pool',3,12,parent_menu,'','','',0,b'1',
         b'1',b'0','V295','V295',b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:subordinate-sales:newcomer-pool');
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:subordinate-sales:newcomer-pool'
      AND type=3 AND parent_id=parent_menu AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V295 menu postcondition failed';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
    SELECT 'V295','Newcomer priority dispatch',SHA2('V295__newcomer_priority_dispatch.sql',256)
    WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V295');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
    SELECT 'core','V295','Newcomer priority dispatch',SHA2('V295__newcomer_priority_dispatch.sql',256),'baseline'
    WHERE NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V295');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v295_apply();
DROP PROCEDURE zsjos_v295_apply;
