-- UTF-8. V352: per-course teaching annotation, before the matching backend release.
-- Prerequisites: V351 in both core ledgers, service relation table, unique student page.
-- Scope: one new empty tenant-owned table, two menu buttons and two version records.
-- No existing business data, dictionary entries, role grants or tenant packages change.
-- Repeat execution verifies actual schema and repairs missing metadata even with markers.
-- DDL commits implicitly: after failure the empty table may remain; rerun after correcting
-- the prerequisite. DML and both ledger writes share a transaction and propagate failures.
-- Rollback: disable new menu buttons; retain table/data and ledger history. No destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v352_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v352_apply()
BEGIN
 DECLARE parent BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V351')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V351')
 OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V352 requires V351 and service relations';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V352 requires exactly one student page';
 END IF;
 SELECT id INTO parent FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0;
 CREATE TABLE IF NOT EXISTS zsjos_student_service_annotation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  service_relation_id BIGINT NOT NULL COMMENT '课程服务ID',
  proxy_student VARCHAR(5) NULL COMMENT 'infra_boolean_string 字典值',
  proxy_student_label VARCHAR(100) NULL COMMENT '代理学员选择标签快照',
  agent_name VARCHAR(100) NULL COMMENT '代理人',
  service_needed VARCHAR(5) NULL COMMENT 'infra_boolean_string 字典值，仅批注',
  service_needed_label VARCHAR(100) NULL COMMENT '需要服务选择标签快照',
  version INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  tenant_id BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_service (tenant_id,service_relation_id,deleted)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员课程服务批注';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='zsjos_student_service_annotation' AND column_name IN
   ('id','service_relation_id','proxy_student','proxy_student_label','agent_name','service_needed',
    'service_needed_label','version','creator','create_time','updater','update_time','deleted','tenant_id'))<>14
 OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
   AND table_name='zsjos_student_service_annotation' AND index_name='uk_tenant_service' AND non_unique=0
   GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='tenant_id,service_relation_id,deleted') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V352 annotation schema conflict';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN
  ('zsjos:student-service-annotation:read','zsjos:student-service-annotation:update') GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission IN
  ('zsjos:student-service-annotation:read','zsjos:student-service-annotation:update')
  AND (parent_id<>parent OR type<>3 OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V352 annotation menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '查看学员批注','zsjos:student-service-annotation:read',3,90,parent,'','','',NULL,0,b'1',b'1',b'0','V352','V352',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-service-annotation:read');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '修改学员批注','zsjos:student-service-annotation:update',3,91,parent,'','','',NULL,0,b'1',b'1',b'0','V352','V352',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-service-annotation:update');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN
  ('zsjos:student-service-annotation:read','zsjos:student-service-annotation:update') AND parent_id=parent AND type=3 AND deleted=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V352 annotation menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V352','学员课程服务批注',SHA2('V352__student_service_annotation.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V352');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V352','学员课程服务批注',SHA2('V352__student_service_annotation.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V352');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v352_apply();
DROP PROCEDURE zsjos_v352_apply;
