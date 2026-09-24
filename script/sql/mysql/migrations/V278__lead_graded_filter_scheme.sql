-- UTF-8. Migration-Owner: ai
-- Scope: register the unified Lead-management filter scheme (audience='management') for existing tenants.
--        Adds configuration metadata only; no Lead, user, role, task, order or history rows are changed.
-- Prerequisites/order: V277, the V005/V047 filter tables and both schema version tables. Run with an utf8mb4 client.
-- Repeatability: INSERT ... NOT EXISTS keyed by tenant + audience; reruns insert nothing and keep administrator edits.
--        Existing submitter/owner/reviewer/agingPool JSON is left untouched; legacy single-row shapes stay readable
--        and are migrated to sections only when an administrator saves that scheme.
-- Rollback: disable the scheme through System filter-plan management, or restore the application code; the immutable
--        version snapshot is intentionally retained and no historical row is rewritten.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS zsjos_v278_preflight;
DELIMITER $$
CREATE PROCEDURE zsjos_v278_preflight()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V277') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V278 requires Core V277';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name='zsjos_lead_inbox_filter_scheme') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V278 blocked: Lead inbox filter scheme table is missing';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
               AND column_name='status' AND column_type NOT LIKE 'varchar(32)%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V278 blocked: zsjos_lead.status is incompatible';
  END IF;
END$$
DELIMITER ;
CALL zsjos_v278_preflight();
DROP PROCEDURE zsjos_v278_preflight;

SET @management_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[],"sections":[{"key":"current_stage","label":"当前环节","sort":0,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"unassigned","label":"待分配","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["unassigned"]}]},{"key":"pending_acceptance","label":"待接单","sort":20,"enabled":true,"conditions":[{"field":"assignment_status","values":["pending_acceptance"]}]},{"key":"public_pool","label":"抢单池","sort":30,"enabled":true,"conditions":[{"field":"assignment_status","values":["public_pool"]}]},{"key":"recycle_pending","label":"回收待处理","sort":40,"enabled":true,"conditions":[{"field":"assignment_status","values":["recycle_pending"]}]},{"key":"first_follow_pending","label":"待首跟","sort":50,"enabled":true,"conditions":[{"field":"handling_stage","values":["first_follow_pending"]}]},{"key":"qualification_pending","label":"待判定","sort":60,"enabled":true,"conditions":[{"field":"handling_stage","values":["qualification_pending"]}]},{"key":"following","label":"正常推进","sort":70,"enabled":true,"conditions":[{"field":"sales_progress","values":["following"]}]},{"key":"deal_pending_approval","label":"成交待审核","sort":80,"enabled":true,"conditions":[{"field":"sales_progress","values":["deal_pending_approval"]}]},{"key":"won","label":"已成交","sort":90,"enabled":true,"conditions":[{"field":"sales_progress","values":["won"]}]},{"key":"suspended","label":"已挂起","sort":100,"enabled":true,"conditions":[{"field":"status","values":["suspended"]}]}]},{"key":"quick_condition","label":"快捷条件","sort":10,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"today","label":"今日待跟进","sort":10,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["today"]}]},{"key":"overdue","label":"跟进已逾期","sort":20,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["overdue"]}]},{"key":"transferred_pending","label":"有效转派待跟进","sort":30,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["transferred_pending"]}]}]}]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":null,"conditions":[{"field":"status","values":["submitted","suspended"]}],"options":[],"sections":[{"key":"current_stage","label":"当前环节","sort":0,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"first_follow_pending","label":"待首跟","sort":10,"enabled":true,"conditions":[{"field":"handling_stage","values":["first_follow_pending"]}]},{"key":"qualification_pending","label":"待判定","sort":20,"enabled":true,"conditions":[{"field":"handling_stage","values":["qualification_pending"]}]},{"key":"suspended","label":"已挂起","sort":30,"enabled":true,"conditions":[{"field":"status","values":["suspended"]}]}]},{"key":"quick_condition","label":"快捷条件","sort":10,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"today","label":"今日待跟进","sort":10,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["today"]}]},{"key":"overdue","label":"跟进已逾期","sort":20,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["overdue"]}]},{"key":"transferred_pending","label":"有效转派待跟进","sort":30,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["transferred_pending"]}]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":null,"conditions":[{"field":"status","values":["valid","won"]}],"options":[],"sections":[{"key":"current_stage","label":"当前环节","sort":0,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"following","label":"正常推进","sort":10,"enabled":true,"conditions":[{"field":"sales_progress","values":["following"]}]},{"key":"deal_pending_approval","label":"成交待审核","sort":20,"enabled":true,"conditions":[{"field":"sales_progress","values":["deal_pending_approval"]}]},{"key":"won","label":"已成交","sort":30,"enabled":true,"conditions":[{"field":"sales_progress","values":["won"]}]}]},{"key":"quick_condition","label":"快捷条件","sort":10,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"today","label":"今日待跟进","sort":10,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["today"]}]},{"key":"overdue","label":"跟进已逾期","sort":20,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["overdue"]}]},{"key":"transferred_pending","label":"有效转派待跟进","sort":30,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["transferred_pending"]}]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":null,"conditions":[{"field":"status","values":["invalid"]}],"options":[],"sections":[{"key":"current_stage","label":"当前环节","sort":0,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"quick_condition","label":"快捷条件","sort":10,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"today","label":"今日待跟进","sort":10,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["today"]}]},{"key":"overdue","label":"跟进已逾期","sort":20,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["overdue"]}]},{"key":"transferred_pending","label":"有效转派待跟进","sort":30,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["transferred_pending"]}]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":null,"conditions":[{"field":"status","values":["closed"]}],"options":[],"sections":[{"key":"current_stage","label":"当前环节","sort":0,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]},{"key":"quick_condition","label":"快捷条件","sort":10,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"today","label":"今日待跟进","sort":10,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["today"]}]},{"key":"overdue","label":"跟进已逾期","sort":20,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["overdue"]}]},{"key":"transferred_pending","label":"有效转派待跟进","sort":30,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["transferred_pending"]}]}]}]},{"key":"manual","label":"手动录入客资","sort":50,"enabled":true,"sectionLabel":null,"conditions":[{"field":"source_type","values":["sales_self_sourced","education_self_sourced"]}],"options":[],"sections":[{"key":"current_stage","label":"当前环节","sort":0,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"unassigned","label":"待分配","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["unassigned"]}]},{"key":"pending_acceptance","label":"待接单","sort":20,"enabled":true,"conditions":[{"field":"assignment_status","values":["pending_acceptance"]}]},{"key":"public_pool","label":"抢单池","sort":30,"enabled":true,"conditions":[{"field":"assignment_status","values":["public_pool"]}]},{"key":"recycle_pending","label":"回收待处理","sort":40,"enabled":true,"conditions":[{"field":"assignment_status","values":["recycle_pending"]}]},{"key":"first_follow_pending","label":"待首跟","sort":50,"enabled":true,"conditions":[{"field":"handling_stage","values":["first_follow_pending"]}]},{"key":"qualification_pending","label":"待判定","sort":60,"enabled":true,"conditions":[{"field":"handling_stage","values":["qualification_pending"]}]},{"key":"following","label":"正常推进","sort":70,"enabled":true,"conditions":[{"field":"sales_progress","values":["following"]}]},{"key":"deal_pending_approval","label":"成交待审核","sort":80,"enabled":true,"conditions":[{"field":"sales_progress","values":["deal_pending_approval"]}]},{"key":"won","label":"已成交","sort":90,"enabled":true,"conditions":[{"field":"sales_progress","values":["won"]}]},{"key":"suspended","label":"已挂起","sort":100,"enabled":true,"conditions":[{"field":"status","values":["suspended"]}]}]},{"key":"quick_condition","label":"快捷条件","sort":10,"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"today","label":"今日待跟进","sort":10,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["today"]}]},{"key":"overdue","label":"跟进已逾期","sort":20,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["overdue"]}]},{"key":"transferred_pending","label":"有效转派待跟进","sort":30,"enabled":true,"conditions":[{"field":"follow_up_condition","values":["transferred_pending"]}]}]}]}]}
';

INSERT INTO `zsjos_lead_inbox_filter_scheme`
(`audience`,`name`,`draft_config_json`,`published_config_json`,`published_version`,`published_by`,`published_at`,
 `version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'management','统一客资管理视角',@management_filter,@management_filter,1,0,NOW(),0,
       'migration-V278',NOW(),'migration-V278',NOW(),b'0',t.id
FROM `system_tenant` t
WHERE t.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_scheme` s
    WHERE s.tenant_id=t.id AND s.audience='management' AND s.deleted=b'0');

INSERT INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`,`version_no`,`config_json`,`published_by`,`published_at`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT s.id,1,s.published_config_json,COALESCE(s.published_by,0),COALESCE(s.published_at,NOW()),
       'migration-V278',NOW(),'migration-V278',NOW(),b'0',s.tenant_id
FROM `zsjos_lead_inbox_filter_scheme` s
WHERE s.audience='management' AND s.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_version` v
    WHERE v.tenant_id=s.tenant_id AND v.scheme_id=s.id AND v.version_no=1 AND v.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES('V278','Unified Lead management graded filter scheme',SHA2('V278__lead_graded_filter_scheme.sql',256))
ON DUPLICATE KEY UPDATE description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES('core','V278','Unified Lead management graded filter scheme',SHA2('V278__lead_graded_filter_scheme.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description);
