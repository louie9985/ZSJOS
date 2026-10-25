-- UTF-8. V347: standalone web attendance application; tenant 1 only.
-- Prerequisites: System menu and both schema ledgers, one active /hrm directory.
-- Order: numeric core migration order, then matching backend/Workbench deployment.
-- Scope: two new application tables; empty personnel; actual desktop department schedules;
-- one native HRM child menu and one access permission. No role assignments or historical data import.
-- Optional @zsjkqt_file_checksum supplies the file-byte SHA256 during scoped application.
-- Replay preserves drafts/config/menu grants and existing ledger checksums. DDL commits implicitly.
-- Partial recovery: inspect both tables and both ledgers, correct mismatch and replay this file.
-- Development correction: validate exact unique columns and active config rows. This source change
-- does not rewrite existing ledger checksums or restore administrator-deleted configuration.
-- Rollback retains tables/config/drafts; disable entry via menu management. No destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjkqt_v347_apply;
DELIMITER $$
CREATE PROCEDURE zsjkqt_v347_apply()
BEGIN
 DECLARE hrm_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('system_menu','zsjos_schema_version','zsjos_module_schema_version'))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 prerequisite tables missing';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/hrm' AND type=1 AND deleted=0 AND status=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 requires one enabled HRM directory';
 END IF;
 SELECT id INTO hrm_id FROM system_menu WHERE path='/hrm' AND type=1 AND deleted=0 AND status=0;
CREATE TABLE IF NOT EXISTS zsjkqt_draft (
 id bigint NOT NULL AUTO_INCREMENT,
 client_id varchar(64) NOT NULL,
 name varchar(120) NOT NULL,
 year int NOT NULL, month int NOT NULL,
 source_file_name varchar(255) NOT NULL DEFAULT '', source_file_hash varchar(64) NOT NULL DEFAULT '',
 employee_count int NOT NULL DEFAULT 0, confirmed_count int NOT NULL DEFAULT 0,
 snapshot_json longtext NOT NULL, version int NOT NULL DEFAULT 0,
 updater_name varchar(200) NOT NULL DEFAULT '',
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 1,
 PRIMARY KEY(id), UNIQUE KEY uk_zsjkqt_client(tenant_id,client_id),
 KEY idx_zsjkqt_month(tenant_id,deleted,year,month,update_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中世健考勤通共享草稿';
CREATE TABLE IF NOT EXISTS zsjkqt_config (
 id bigint NOT NULL AUTO_INCREMENT, config_key varchar(32) NOT NULL, value_json longtext NOT NULL,
 version int NOT NULL DEFAULT 0,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 1,
 PRIMARY KEY(id), UNIQUE KEY uk_zsjkqt_config(tenant_id,config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中世健考勤通独立配置';

 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjkqt_draft')<>18
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjkqt_config')<>10
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjkqt_draft' AND ((column_name IN ('id','tenant_id') AND data_type='bigint') OR (column_name='snapshot_json' AND data_type='longtext') OR (column_name IN ('year','month','version') AND data_type='int')))<>6
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjkqt_config' AND column_name='value_json' AND data_type='longtext')<>1
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjkqt_draft' AND index_name='uk_zsjkqt_client' AND non_unique=0)<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjkqt_draft' AND index_name='uk_zsjkqt_client' AND non_unique=0 AND sub_part IS NULL AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='client_id')))<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjkqt_config' AND index_name='uk_zsjkqt_config' AND non_unique=0)<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjkqt_config' AND index_name='uk_zsjkqt_config' AND non_unique=0 AND sub_part IS NULL AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='config_key')))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 schema postcondition failed';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT 1 FROM zsjkqt_config WHERE tenant_id=1 AND config_key IN ('profiles','schedules') AND deleted<>b'0') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 config is deleted; review intended state before recovery';
 END IF;
 INSERT INTO zsjkqt_config(config_key,value_json,tenant_id) SELECT 'profiles','{}',1 WHERE NOT EXISTS(SELECT 1 FROM zsjkqt_config WHERE config_key='profiles' AND tenant_id=1 AND deleted=b'0');
 INSERT INTO zsjkqt_config(config_key,value_json,tenant_id) SELECT 'schedules','{
  "default": {
    "afternoon": {
      "end": "18:00",
      "start": "13:30"
    },
    "morning": {
      "end": "12:00",
      "start": "08:30"
    }
  },
  "departments": {
    "AI应用部": {
      "afternoon": {
        "end": "18:00",
        "start": "13:00"
      },
      "mode": "custom",
      "morning": {
        "end": "12:00",
        "start": "09:00"
      }
    },
    "客资部": {
      "mode": "default"
    },
    "市场部": {
      "mode": "default"
    },
    "教务部": {
      "mode": "default"
    },
    "教研部": {
      "mode": "default"
    },
    "综合教研部": {
      "mode": "default"
    },
    "综合管理层队伍": {
      "mode": "default"
    },
    "综合行政中心": {
      "mode": "default"
    },
    "考务部": {
      "mode": "default"
    },
    "财务部": {
      "mode": "default"
    },
    "运营部": {
      "mode": "default"
    },
    "销售部": {
      "afternoon": {
        "end": "21:30",
        "start": "13:30"
      },
      "mode": "custom",
      "morning": {
        "end": "12:00",
        "start": "11:00"
      }
    }
  }
}',1 WHERE NOT EXISTS(SELECT 1 FROM zsjkqt_config WHERE config_key='schedules' AND tenant_id=1 AND deleted=b'0');
 IF EXISTS(SELECT 1 FROM system_menu WHERE parent_id=hrm_id AND path='zsjkqt' AND deleted=0 AND (NOT (permission <=> 'zsjos:zsjkqt:access') OR NOT (component <=> 'zsjos-workbench') OR NOT (workbench_render_mode <=> 'native'))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 existing attendance menu contract conflicts';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,workbench_render_mode,creator,updater)
 SELECT '中世健考勤通','zsjos:zsjkqt:access',2,90,hrm_id,'zsjkqt','ep:calendar','zsjos-workbench','Zsjkqt',0,1,0,1,'native','1','1'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE parent_id=hrm_id AND path='zsjkqt' AND deleted=0);
 IF (SELECT COUNT(*) FROM system_menu WHERE parent_id=hrm_id AND path='zsjkqt' AND permission='zsjos:zsjkqt:access' AND component='zsjos-workbench' AND workbench_render_mode='native' AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 menu postcondition failed';
 END IF;
 IF (SELECT COUNT(*) FROM zsjkqt_config WHERE tenant_id=1 AND config_key IN ('profiles','schedules') AND deleted=b'0' AND JSON_VALID(value_json))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V347 config postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V347','中世健考勤通',COALESCE(@zsjkqt_file_checksum,SHA2('V347__zsjkqt.sql',256)) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V347');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V347','中世健考勤通',COALESCE(@zsjkqt_file_checksum,SHA2('V347__zsjkqt.sql',256)),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V347');
 COMMIT;
END$$
DELIMITER ;
CALL zsjkqt_v347_apply();
DROP PROCEDURE zsjkqt_v347_apply;
