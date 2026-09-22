-- UTF-8. Migration-Owner: ai
-- Requires V275 and System /zsjos root. Execute after V275.
-- Scope: four empty tenant tables and two Workbench-only page definitions/buttons.
-- No role assignments, business backfill, dictionary options or existing snapshots are changed.
-- Repeatable: CREATE IF NOT EXISTS and missing-only menu inserts preserve administrator edits.
-- Rollback: disable new menu access through System; retain tables/audit/snapshots. No destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v276_prerequisite;
DELIMITER $$
CREATE PROCEDURE zsjos_v276_prerequisite()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V275') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V276 requires V275';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0) <> 1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V276 requires exactly one active Workbench root';
 END IF;
END$$
DELIMITER ;
CALL zsjos_v276_prerequisite();
DROP PROCEDURE zsjos_v276_prerequisite;

CREATE TABLE IF NOT EXISTS zsjos_performance_org (
 id bigint NOT NULL AUTO_INCREMENT,
 dept_id bigint NOT NULL, center_id bigint NOT NULL, kind varchar(16) NOT NULL, version int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_org(tenant_id,dept_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zsjos_performance_target (
 id bigint NOT NULL AUTO_INCREMENT,
 scope_type varchar(16) NOT NULL, scope_id bigint NOT NULL, dept_id bigint NULL, center_id bigint NULL, period_type varchar(16) NOT NULL, period_start date NOT NULL, floor_amount decimal(18,2) NULL, sprint_amount decimal(18,2) NULL, manual bit(1) NOT NULL DEFAULT b'0', reason varchar(500) NOT NULL, version int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_target(tenant_id,scope_type,scope_id,period_type,period_start,deleted), KEY idx_target_dept(tenant_id,dept_id,period_type,period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zsjos_performance_revision (
 id bigint NOT NULL AUTO_INCREMENT,
 target_id bigint NOT NULL, before_json longtext NULL, after_json longtext NOT NULL, reason varchar(500) NOT NULL, operator_id bigint NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id), KEY idx_revision(tenant_id,target_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zsjos_performance_attribution (
 id bigint NOT NULL AUTO_INCREMENT,
 outcome varchar(32) NULL, completed_at datetime NULL, fact_type varchar(16) NOT NULL, fact_id bigint NOT NULL, user_id bigint NULL, user_name varchar(100) NULL, dept_id bigint NULL, dept_name varchar(100) NULL, center_id bigint NULL, center_name varchar(100) NULL, lead_id bigint NULL, assignment_id bigint NULL, received_at datetime NULL, source_group varchar(32) NULL, channel_code varchar(100) NULL, channel_label varchar(255) NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_fact(tenant_id,fact_type,fact_id,deleted), KEY idx_user(tenant_id,user_id,fact_type,received_at), KEY idx_dept(tenant_id,dept_id,fact_type,received_at), KEY idx_center(tenant_id,center_id,fact_type,received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '业绩统计','zsjos:sales-performance:query',2,95,p.id,'sales-performance','ep:data-analysis','zsjos/salesPerformance/index',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.path='/zsjos' AND p.type=1 AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance:query' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '销售业绩设置','zsjos:sales-performance-target:query',2,95,p.id,'sales-performance-target','ep:data-analysis','zsjos/salesPerformanceTarget/index',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.path='/zsjos' AND p.type=1 AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance-target:query' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '销售本人业绩','zsjos:sales-performance:self',3,10,p.id,'','','',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.permission='zsjos:sales-performance:query' AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance:self' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '销售部门业绩','zsjos:sales-performance:department',3,10,p.id,'','','',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.permission='zsjos:sales-performance:query' AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance:department' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '销售中心业绩','zsjos:sales-performance:center',3,10,p.id,'','','',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.permission='zsjos:sales-performance:query' AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance:center' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '业绩统计明细','zsjos:sales-performance:detail',3,10,p.id,'','','',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.permission='zsjos:sales-performance:query' AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance:detail' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '修改销售业绩目标','zsjos:sales-performance-target:update',3,10,p.id,'','','',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.permission='zsjos:sales-performance-target:query' AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance-target:update' AND m.deleted=0);
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '配置销售统计组织','zsjos:sales-performance-target:configure',3,10,p.id,'','','',0,b'1',b'1',b'0','V276','V276',b'0'
FROM system_menu p WHERE p.permission='zsjos:sales-performance-target:query' AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:sales-performance-target:configure' AND m.deleted=0);
INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES('V276','Sales performance targets and statistics',SHA2('V276__sales_performance.sql',256))
ON DUPLICATE KEY UPDATE description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES('core','V276','Sales performance targets and statistics',SHA2('V276__sales_performance.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description);
