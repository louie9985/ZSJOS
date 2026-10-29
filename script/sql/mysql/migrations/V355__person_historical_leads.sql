-- UTF-8. Upgrade V354 -> V355 -> matching identity/submission backend.
-- Scope: Lead uniqueness and nullable original-identity scope columns; no business rows or grants changed.
-- Repeatable after partial DDL; validate actual schema even when markers already exist.
-- DDL commits implicitly. Rollback requires auditing multiple Leads before restoring uniqueness.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v355_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v355_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V354')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V354') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V355 requires V354 in both Core ledgers';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_person_contact_alias (
  id bigint NOT NULL AUTO_INCREMENT,
  person_id bigint NOT NULL COMMENT '归一后的客户',
  source_person_id bigint NOT NULL COMMENT '联系方式原客户',
  contact_type varchar(16) NOT NULL COMMENT 'mobile或wechat技术字段',
  contact_value varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '核实保留的联系方式',
  merge_key varchar(128) NOT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(id),
  UNIQUE KEY uk_contact_alias(tenant_id,source_person_id,contact_type,contact_value),
  KEY idx_contact_alias_person(tenant_id,person_id,deleted)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户归一核实联系方式';
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name='zsjos_lead' AND column_name='identity_origin_person_id') THEN
  ALTER TABLE zsjos_lead ADD COLUMN identity_origin_person_id bigint DEFAULT NULL COMMENT '合并前身份权限范围';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name='zsjos_order' AND column_name='identity_origin_person_id') THEN
  ALTER TABLE zsjos_order ADD COLUMN identity_origin_person_id bigint DEFAULT NULL COMMENT '合并前身份权限范围';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_lead') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V355 requires zsjos_lead';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
           AND table_name='zsjos_lead' AND index_name='uk_tenant_active_person') THEN
  ALTER TABLE zsjos_lead DROP INDEX uk_tenant_active_person;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V355 requires zsjos_opportunity';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
           AND table_name='zsjos_opportunity' AND index_name='uk_tenant_active_person') THEN
  ALTER TABLE zsjos_opportunity DROP INDEX uk_tenant_active_person;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
           AND table_name IN ('zsjos_lead','zsjos_opportunity') AND index_name='uk_tenant_active_person') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V355 Lead uniqueness postcondition failed';
 END IF;
 START TRANSACTION;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
     AND table_name='zsjos_person_contact_alias' AND column_name IN ('id','person_id','source_person_id','contact_type','contact_value','merge_key','tenant_id','deleted'))<>8
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
     AND table_name='zsjos_person_contact_alias' AND index_name='uk_contact_alias' AND non_unique=0)<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V355 verified contact alias postcondition failed';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
     AND table_name IN ('zsjos_lead','zsjos_order') AND column_name='identity_origin_person_id'
     AND data_type='bigint' AND is_nullable='YES')<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V355 identity origin columns postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V355','客户历史多客资',SHA2('V355__person_historical_leads.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V355');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V355','客户历史多客资',SHA2('V355__person_historical_leads.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V355');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v355_apply();
DROP PROCEDURE zsjos_v355_apply;
