-- UTF-8. V275: new sales-stage capability; follows V274; no historical migration rewrite.
-- Migration-Owner: ai
-- Prerequisites: current Core baseline through V274; run with zsjos-db from repository root.
-- Order: freeze historical upper ID/time, add nullable columns, approved dictionary seed,
-- then transactionally initialize unconverted, undeleted, stage-null Leads and record V275.
-- Scope: all existing tenants, each row explicitly retains its tenant; System dictionary is global.
-- No historical follow-ups/events/notifications are inserted or updated; no role grants.
-- Replay: guarded columns and one-time scope/dictionary/completion markers. Administrator
-- dictionary edits and all later stage choices remain untouched. DDL commits implicitly.
-- Recovery: retain nullable columns; before rollout back up affected rows. Restore snapshots
-- only from that scoped backup after verifying no later business writes; never clear stages broadly.
SET NAMES utf8mb4;
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
SELECT 'lead_sales_stage_scope_v1', CONCAT('maxId=',COALESCE(MAX(id),0)), CAST(COALESCE(MAX(id),0) AS CHAR)
FROM zsjos_lead;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='sales_stage'),'SELECT 1','ALTER TABLE zsjos_lead ADD COLUMN sales_stage varchar(100) NULL COMMENT ''当前销售阶段字典值'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='sales_stage_label_snapshot'),'SELECT 1','ALTER TABLE zsjos_lead ADD COLUMN sales_stage_label_snapshot varchar(100) NULL COMMENT ''销售阶段名称快照'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='sales_stage_before'),'SELECT 1','ALTER TABLE zsjos_lead_follow_up_record ADD COLUMN sales_stage_before varchar(100) NULL COMMENT ''跟进前销售阶段'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='sales_stage_before_label_snapshot'),'SELECT 1','ALTER TABLE zsjos_lead_follow_up_record ADD COLUMN sales_stage_before_label_snapshot varchar(100) NULL COMMENT ''跟进前销售阶段名称快照'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='sales_stage_after'),'SELECT 1','ALTER TABLE zsjos_lead_follow_up_record ADD COLUMN sales_stage_after varchar(100) NULL COMMENT ''本次跟进后销售阶段'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='sales_stage_after_label_snapshot'),'SELECT 1','ALTER TABLE zsjos_lead_follow_up_record ADD COLUMN sales_stage_after_label_snapshot varchar(100) NULL COMMENT ''本次跟进后销售阶段名称快照'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity_follow_up_record' AND column_name='sales_stage_before'),'SELECT 1','ALTER TABLE zsjos_opportunity_follow_up_record ADD COLUMN sales_stage_before varchar(100) NULL COMMENT ''跟进前销售阶段'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity_follow_up_record' AND column_name='sales_stage_before_label_snapshot'),'SELECT 1','ALTER TABLE zsjos_opportunity_follow_up_record ADD COLUMN sales_stage_before_label_snapshot varchar(100) NULL COMMENT ''跟进前销售阶段名称快照'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity_follow_up_record' AND column_name='sales_stage_after'),'SELECT 1','ALTER TABLE zsjos_opportunity_follow_up_record ADD COLUMN sales_stage_after varchar(100) NULL COMMENT ''本次跟进后销售阶段'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;
SET @stage_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity_follow_up_record' AND column_name='sales_stage_after_label_snapshot'),'SELECT 1','ALTER TABLE zsjos_opportunity_follow_up_record ADD COLUMN sales_stage_after_label_snapshot varchar(100) NULL COMMENT ''本次跟进后销售阶段名称快照'''));
PREPARE stage_stmt FROM @stage_ddl; EXECUTE stage_stmt; DEALLOCATE PREPARE stage_stmt;

SOURCE script/sql/mysql/dictionary-data/lead-sales-stage.sql;
DROP PROCEDURE IF EXISTS zsjos_v275_initialize;
DELIMITER $$
CREATE PROCEDURE zsjos_v275_initialize()
BEGIN
    DECLARE initial_label varchar(100);
    DECLARE upper_id bigint;
    DECLARE cutoff datetime;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    SELECT CAST(checksum AS UNSIGNED), installed_at INTO upper_id, cutoff
    FROM zsjos_schema_version WHERE version='lead_sales_stage_scope_v1' FOR UPDATE;
    IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V275') THEN
        SELECT MAX(d.label) INTO initial_label FROM system_dict_data d
        JOIN system_dict_type t ON t.type=d.dict_type AND t.deleted=b'0' AND t.status=0
        WHERE d.dict_type='zsjos_lead_sales_stage' AND d.value='contacted' AND d.deleted=b'0' AND d.status=0;
        IF initial_label IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V275 requires enabled contacted sales-stage dictionary entry';
        END IF;
        UPDATE zsjos_lead l
        SET l.sales_stage='contacted', l.sales_stage_label_snapshot=initial_label,
            l.update_time=l.update_time
        WHERE l.id<=upper_id AND l.create_time<=cutoff AND l.tenant_id IS NOT NULL
          AND l.deleted=b'0' AND l.status<>'won' AND l.sales_stage IS NULL;
        INSERT INTO zsjos_schema_version(version,description,checksum)
        VALUES ('V275','Sales stage snapshots and bounded historical initialization','lead-sales-stage-v1');
    END IF;
    COMMIT;
END$$
DELIMITER ;
CALL zsjos_v275_initialize();
DROP PROCEDURE zsjos_v275_initialize;
