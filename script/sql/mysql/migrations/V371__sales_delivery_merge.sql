-- V371 (undeployed draft): sales/delivery merge metadata, target merge provenance and order-driven auto classes.
-- Dependencies: V370 recorded in zsjos_schema_version and core zsjos_module_schema_version; tables system_menu,
--   zsjos_performance_target, zsjos_performance_org, zsjos_media_student_talk_record, zsjos_order (agreed_exam_time)
--   and zsjos_delivery_class (class_kind, product_id, selected_skus_json, homeroom_user_id, status, deleted, tenant_id).
-- Exact data scope: inserts at most five type-3 system_menu permission rows (zsjos:sales:eligible, zsjos:lead:receive,
--   zsjos:sales:cross-department, zsjos:sales:boundary-configure, zsjos:media-student:history-review) and one V371 row
--   in each version ledger. Adds nullable/defaulted columns only: zsjos_performance_target.merged_delivery_target_id,
--   zsjos_performance_org.sales_boundary_root, zsjos_media_student_talk_record.service_relation_id,
--   zsjos_order.agreed_exam_month, zsjos_delivery_class.exam_month and generated auto_class_guard, plus unique key
--   uk_delivery_auto_class. Retires (deleted=1, status=1, visible=0, updater V371) the removed features' System menu
--   metadata only: education team page zsjos:education-team:query and its five buttons (V326), delivery performance
--   pages zsjos:delivery-performance:query / zsjos:delivery-performance-target:query and their six buttons (V321), and
--   the education self-sourced page zsjos:lead:education-self-sourced:create (V257). V257/V321/V326 stay unchanged for
--   deployed checksum compatibility; fresh installs create those rows and this file retires them. Following V364/V365,
--   system_role_menu rows that reference retired menus are neither revoked nor reassigned; they stay historical and
--   inert because deleted menus are filtered out of authorization. Retiring roles, departments and posts is
--   administrator-maintained organization data and is not part of this migration.
--   Also rewrites, in place and keeping their stable codes, the four V094 relation scenes
--   (registration_manager_study_planner, registration_specialist_study_planner, study_planner_content_director,
--   study_planner_career_planner): post code study_planner -> sales_specialist and labels 学习规划师 -> 班主任; and
--   renames notification recipient role keys in zsjos.* system_notify_rule rows ("study_planner" -> "head_teacher",
--   "delivery_supervisor" -> "sales_supervisor") to match the scene providers. Both are repeatable string rewrites.
--   No role grants (system_role_menu), no business-row updates; historical orders keep
--   agreed_exam_month NULL (legacy text stays history only and is never converted) and existing classes keep
--   exam_month NULL, so the new unique key cannot conflict with existing rows.
-- Repeatability: every DDL is guarded by information_schema and followed by a definition postcondition; menu inserts
--   use NOT EXISTS; menu retirement only touches rows not yet retired and is rechecked even when V371 is recorded;
--   an active retired-feature row with an unexpected type/parent, or an active unrelated child under a retired page,
--   stops before any write; ledgers are written last inside the same failure-controlled procedure. Re-run after a partial
--   failure completes the missing parts; a conflicting pre-existing definition stops without a ledger row.
-- Rollback limits: DDL commits implicitly. Rolling back requires dropping uk_delivery_auto_class, auto_class_guard,
--   exam_month and agreed_exam_month only after confirming no auto-class enrollment or new order depends on them,
--   and must preserve merged_delivery_target_id and its target audit records. Restoring a retired menu requires a
--   scoped backup and a backend release that still serves the removed endpoints.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v371_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v371_apply()
BEGIN
 DECLARE parent_id_value bigint;
 DECLARE media_parent_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V370') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V370') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 requires V370 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='agreed_exam_time')
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class'
        AND column_name IN ('class_kind','product_id','selected_skus_json','homeroom_user_id','status','deleted','tenant_id'))<>7 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 requires order exam text and delivery class columns';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:lead:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 requires lead page';
 END IF;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN ('zsjos:sales:eligible','zsjos:lead:receive','zsjos:sales:cross-department','zsjos:sales:boundary-configure') GROUP BY permission HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 permission identity conflict';
 END IF;
 SELECT id INTO parent_id_value FROM system_menu WHERE permission='zsjos:lead:query' AND type=2 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-student:query-my' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 requires media student page';
 END IF;
 SELECT id INTO media_parent_id FROM system_menu WHERE permission='zsjos:media-student:query-my' AND type=2 AND deleted=0;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-student:history-review')>1 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:media-student:history-review' AND (type<>3 OR parent_id<>media_parent_id OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 history review permission conflict';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission IN ('zsjos:sales:eligible','zsjos:lead:receive','zsjos:sales:cross-department','zsjos:sales:boundary-configure') AND (type<>3 OR parent_id<>parent_id_value OR deleted<>0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 permission metadata conflict';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu WHERE deleted=0 AND permission IN ('zsjos:education-team:query','zsjos:delivery-performance:query','zsjos:delivery-performance-target:query','zsjos:lead:education-self-sourced:create') AND type<>2)
    OR EXISTS(SELECT 1 FROM system_menu b WHERE b.deleted=0
        AND (b.permission LIKE 'zsjos:education-team:%' OR b.permission LIKE 'zsjos:delivery-performance%')
        AND b.permission NOT IN ('zsjos:education-team:query','zsjos:delivery-performance:query','zsjos:delivery-performance-target:query')
        AND (b.type<>3 OR NOT EXISTS(SELECT 1 FROM system_menu p WHERE p.id=b.parent_id AND p.type=2
            AND p.permission IN ('zsjos:education-team:query','zsjos:delivery-performance:query','zsjos:delivery-performance-target:query'))))
    OR EXISTS(SELECT 1 FROM system_menu c JOIN system_menu p ON p.id=c.parent_id
        WHERE c.deleted=0 AND p.permission IN ('zsjos:education-team:query','zsjos:delivery-performance:query','zsjos:delivery-performance-target:query','zsjos:lead:education-self-sourced:create')
        AND NOT (COALESCE(c.permission,'') LIKE 'zsjos:education-team:%' OR COALESCE(c.permission,'') LIKE 'zsjos:delivery-performance%')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 retired menu identity conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND column_name='merged_delivery_target_id') THEN
  ALTER TABLE zsjos_performance_target ADD COLUMN merged_delivery_target_id bigint NULL COMMENT '已合并的历史交付目标ID' AFTER id;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND column_name='merged_delivery_target_id' AND data_type='bigint' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 target merge column conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_org' AND column_name='sales_boundary_root') THEN
  ALTER TABLE zsjos_performance_org ADD COLUMN sales_boundary_root bit(1) NOT NULL DEFAULT b'0' COMMENT '销售业务部门边界根' AFTER id;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_org' AND column_name='sales_boundary_root' AND column_type='bit(1)' AND is_nullable='NO') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 sales boundary column conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_student_talk_record' AND column_name='service_relation_id') THEN
  ALTER TABLE zsjos_media_student_talk_record ADD COLUMN service_relation_id bigint NULL COMMENT '谈话归属服务关系' AFTER student_person_id;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_student_talk_record' AND column_name='service_relation_id' AND data_type='bigint' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 talk service relation column conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='agreed_exam_month') THEN
  ALTER TABLE zsjos_order ADD COLUMN agreed_exam_month char(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商定考试月份 YYYY-MM' AFTER agreed_exam_time;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='agreed_exam_month' AND column_type='char(7)' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 order exam month column conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='exam_month') THEN
  ALTER TABLE zsjos_delivery_class ADD COLUMN exam_month char(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自动分班考试月份 YYYY-MM' AFTER selected_skus_json;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='exam_month' AND column_type='char(7)' AND is_nullable='YES' AND extra='') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 class exam month column conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='auto_class_guard') THEN
  ALTER TABLE zsjos_delivery_class ADD COLUMN auto_class_guard bigint GENERATED ALWAYS AS (CASE WHEN (`class_kind`='NORMAL' AND `deleted`=b'0' AND `status`='SERVING' AND `exam_month` > '') THEN `product_id` ELSE NULL END) STORED AFTER exam_month;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='auto_class_guard' AND data_type='bigint' AND extra='STORED GENERATED'
        AND generation_expression LIKE '%exam_month%' AND generation_expression LIKE '%product_id%') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 auto class guard column conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_auto_class') THEN
  ALTER TABLE zsjos_delivery_class ADD UNIQUE KEY uk_delivery_auto_class (tenant_id,auto_class_guard,exam_month,homeroom_user_id);
 END IF;
 IF NOT ((SELECT GROUP_CONCAT(CONCAT(non_unique,':',column_name) ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_auto_class')
    <=> '0:tenant_id,0:auto_class_guard,0:exam_month,0:homeroom_user_id') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 auto class unique key conflict';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '销售业务资格','zsjos:sales:eligible',3,90,parent_id_value,'','','',0,b'1',b'1',b'1','V371',NOW(),'V371',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales:eligible');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '接收提交客资','zsjos:lead:receive',3,91,parent_id_value,'','','',0,b'1',b'1',b'1','V371',NOW(),'V371',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:lead:receive');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '跨销售部门数据访问','zsjos:sales:cross-department',3,92,parent_id_value,'','','',0,b'1',b'1',b'1','V371',NOW(),'V371',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales:cross-department');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '配置销售部门边界','zsjos:sales:boundary-configure',3,93,parent_id_value,'','','',0,b'1',b'1',b'1','V371',NOW(),'V371',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales:boundary-configure');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:sales:eligible','zsjos:lead:receive','zsjos:sales:cross-department','zsjos:sales:boundary-configure') AND type=3 AND parent_id=parent_id_value AND deleted=0)<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 permission postcondition failed';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '核对未归属历史谈话','zsjos:media-student:history-review',3,93,media_parent_id,'','','',0,b'1',b'1',b'1','V371',NOW(),'V371',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:media-student:history-review');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-student:history-review' AND type=3 AND parent_id=media_parent_id AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 history review permission postcondition failed';
 END IF;
 -- Removed features: grants stay as inert history (V364/V365 precedent); only menu metadata is retired.
 UPDATE system_menu SET deleted=b'1', status=1, visible=b'0', updater='V371', update_time=NOW()
 WHERE (permission LIKE 'zsjos:education-team:%' OR permission LIKE 'zsjos:delivery-performance%' OR permission='zsjos:lead:education-self-sourced:create')
   AND (deleted<>b'1' OR status<>1 OR visible<>b'0');
 IF EXISTS(SELECT 1 FROM system_menu
     WHERE (permission LIKE 'zsjos:education-team:%' OR permission LIKE 'zsjos:delivery-performance%' OR permission='zsjos:lead:education-self-sourced:create')
       AND (deleted<>b'1' OR status<>1 OR visible<>b'0')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 retired menu remains active';
 END IF;
 -- Head teachers are sales specialists now: V094 relation scenes keep their stable codes but target the sales post.
 UPDATE zsjos_user_relation_scene
    SET source_post_code=IF(source_post_code='study_planner','sales_specialist',source_post_code),
        target_post_code=IF(target_post_code='study_planner','sales_specialist',target_post_code),
        source_label=IF(source_label='学习规划师','班主任',source_label),
        target_label=IF(target_label='学习规划师','班主任',target_label),
        name=REPLACE(name,'学习规划师','班主任'), updater='V371', update_time=NOW()
  WHERE code IN ('registration_manager_study_planner','registration_specialist_study_planner','study_planner_content_director','study_planner_career_planner')
    AND (source_post_code='study_planner' OR target_post_code='study_planner' OR source_label='学习规划师' OR target_label='学习规划师' OR name LIKE '%学习规划师%');
 IF EXISTS(SELECT 1 FROM zsjos_user_relation_scene WHERE deleted=b'0' AND (source_post_code='study_planner' OR target_post_code='study_planner')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 relation scene still targets study_planner post';
 END IF;
 -- Notification recipient role keys renamed with the scene providers (head_teacher / sales_supervisor).
 UPDATE system_notify_rule
    SET recipient_roles=REPLACE(REPLACE(recipient_roles,'"study_planner"','"head_teacher"'),'"delivery_supervisor"','"sales_supervisor"'),
        updater='V371', update_time=NOW()
  WHERE scene_code LIKE 'zsjos.%' AND (recipient_roles LIKE '%"study_planner"%' OR recipient_roles LIKE '%"delivery_supervisor"%');
 IF EXISTS(SELECT 1 FROM system_notify_rule WHERE scene_code LIKE 'zsjos.%' AND (recipient_roles LIKE '%"study_planner"%' OR recipient_roles LIKE '%"delivery_supervisor"%')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V371 notify rule still uses retired role key';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V371','销售资格、目标合并及自动分班',SHA2('V371__sales_delivery_merge.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V371');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V371','销售资格、目标合并及自动分班',SHA2('V371__sales_delivery_merge.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V371');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v371_apply();
DROP PROCEDURE zsjos_v371_apply;
