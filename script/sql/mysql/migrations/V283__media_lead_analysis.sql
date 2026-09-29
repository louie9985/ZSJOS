-- UTF-8. Migration-Owner: ai
-- Scope: new-media lead target table and menu/permission metadata.
-- Active development only; deployed checksum changes require separately reviewed rollout.
-- Requires Core V282 and the existing /zsjos Workbench root. No role assignments, business data or dictionary data.
-- Repeatable: missing-only menu inserts preserve administrator edits. Rollback: disable the pages through System menu management; metadata is retained.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v283_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v283_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V282')
     OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V282') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V283 requires Core V282';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V283 requires exactly one active Workbench root';
  END IF;

  CREATE TABLE IF NOT EXISTS zsjos_media_lead_target (
  id bigint NOT NULL AUTO_INCREMENT,
  scope_type varchar(16) NOT NULL,
  scope_id bigint NOT NULL,
  dept_id bigint DEFAULT NULL,
  center_id bigint DEFAULT NULL,
  period_start date NOT NULL,
  target_count int NOT NULL,
  manual bit(1) NOT NULL DEFAULT b'1',
  reason varchar(500) NOT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_media_lead_target (tenant_id,scope_type,scope_id,period_start,deleted),
  KEY idx_media_lead_target_dept (tenant_id,dept_id,period_start,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  CREATE TABLE IF NOT EXISTS zsjos_media_lead_org (
    id bigint NOT NULL AUTO_INCREMENT, dept_id bigint NOT NULL, center_id bigint NOT NULL,
    kind varchar(16) NOT NULL, version int NOT NULL DEFAULT 0,
    creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(id), UNIQUE KEY uk_media_lead_org_dept(tenant_id,dept_id,deleted),
    KEY idx_media_lead_org_center(tenant_id,center_id,deleted)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  CREATE TABLE IF NOT EXISTS zsjos_media_lead_target_revision (
    id bigint NOT NULL AUTO_INCREMENT, target_id bigint NOT NULL,
    before_json longtext NULL, after_json longtext NOT NULL, reason varchar(500) NOT NULL,
    operator_id bigint NOT NULL, creator varchar(64) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) NOT NULL DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(id), KEY idx_media_lead_revision_target(tenant_id,target_id,id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name='zsjos_media_lead_target' AND column_name IN ('scope_type','scope_id','period_start','target_count','manual','version','tenant_id'))
        OR (table_name='zsjos_media_lead_org' AND column_name IN ('dept_id','center_id','kind','tenant_id'))
        OR (table_name='zsjos_media_lead_target_revision' AND column_name IN ('target_id','before_json','after_json','operator_id','tenant_id')))) <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V283 media table columns missing';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND (table_name='zsjos_media_lead_target' AND index_name='uk_media_lead_target' AND seq_in_index=1
        OR table_name='zsjos_media_lead_org' AND index_name='uk_media_lead_org_dept' AND seq_in_index=1
        OR table_name='zsjos_media_lead_target_revision' AND index_name='idx_media_lead_revision_target' AND seq_in_index=1)) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V283 media table indexes missing';
  END IF;
  -- MySQL DDL commits implicitly. Start the menu/ledger transaction only after all three tables are verified.
  START TRANSACTION;


INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '新媒体客资分析大盘','zsjos:media-lead-analysis:query',2,96,p.id,'media-lead-analysis','ep:data-analysis','zsjos/mediaLeadAnalysis/index',0,b'1',b'1',b'0','V283','V283',b'0'
FROM system_menu p WHERE p.path='/zsjos' AND p.type=1 AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:media-lead-analysis:query' AND m.deleted=0);

INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '客资引流人数指标设置','zsjos:media-lead-target:query',2,97,p.id,'media-lead-target','ep:data-analysis','zsjos/mediaLeadTarget/index',0,b'1',b'1',b'0','V283','V283',b'0'
FROM system_menu p WHERE p.path='/zsjos' AND p.type=1 AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:media-lead-target:query' AND m.deleted=0);

INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT x.name,x.permission,3,10,p.id,'','','',0,b'1',b'1',b'0','V283','V283',b'0'
FROM (SELECT '查看本人新媒体客资' name,'zsjos:media-lead-analysis:self' permission UNION ALL
      SELECT '查看新媒体部门客资','zsjos:media-lead-analysis:department' UNION ALL
      SELECT '查看新媒体中心客资','zsjos:media-lead-analysis:center' UNION ALL
      SELECT '查看新媒体客资明细','zsjos:media-lead-analysis:detail') x
JOIN system_menu p ON p.permission='zsjos:media-lead-analysis:query' AND p.deleted=0
WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=x.permission AND m.deleted=0);

INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT x.name,x.permission,3,10,p.id,'','','',0,b'1',b'1',b'0','V283','V283',b'0'
FROM (SELECT '修改引流人数目标' name,'zsjos:media-lead-target:update' permission UNION ALL
      SELECT '配置新媒体统计组织','zsjos:media-lead-target:configure') x
JOIN system_menu p ON p.permission='zsjos:media-lead-target:query' AND p.deleted=0
WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=x.permission AND m.deleted=0);

IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission IN (
 'zsjos:media-lead-analysis:query','zsjos:media-lead-analysis:self',
 'zsjos:media-lead-analysis:department','zsjos:media-lead-analysis:center','zsjos:media-lead-analysis:detail',
 'zsjos:media-lead-target:query','zsjos:media-lead-target:update','zsjos:media-lead-target:configure')) <> 8 THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V283 expected exactly eight active permission definitions';
END IF;

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES('V283','New-media lead target and analysis menu permissions',SHA2('V283__media_lead_analysis.sql',256))
ON DUPLICATE KEY UPDATE description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES('core','V283','New-media lead target and analysis menu permissions',SHA2('V283__media_lead_analysis.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description);

COMMIT;
END$$
DELIMITER ;
CALL zsjos_v283_apply();
DROP PROCEDURE zsjos_v283_apply;
