-- UTF-8. Requires V214 delivery tables, V224 permissions, V261 evidence and existing version registries.
-- Apply after V263 and before the matching backend/workbench/admin build. No role grants or business-row deletion.
-- Scope: additive delivery cycle/idempotency columns, unique live-state indexes, pending schedule correction.
-- Historical completion/submission/defer data is retained. Data correction runs once; DDL/version guards are repeatable.
-- Rollback: restore prior application only after reviewing multi-round rows; keep new columns and archive records.
-- DDL auto-commits. Take a scoped backup before rollout; do not rerun old V214 or reconcile its checksum.
SET NAMES utf8mb4;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_plan' AND column_name='round_no')=0,'ALTER TABLE `zsjos_student_delivery_plan` ADD COLUMN `round_no` int NULL COMMENT ''交付轮次''','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_plan' AND column_name='source_submission_id')=0,'ALTER TABLE `zsjos_student_delivery_plan` ADD COLUMN `source_submission_id` bigint NULL','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_plan' AND column_name='restart_requested_at')=0,'ALTER TABLE `zsjos_student_delivery_plan` ADD COLUMN `restart_requested_at` datetime NULL','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_plan' AND column_name='live_account_id')=0,'ALTER TABLE `zsjos_student_delivery_plan` ADD COLUMN `live_account_id` bigint GENERATED ALWAYS AS (CASE WHEN deleted=b''0'' AND status IN (''ACTIVE'',''REPOSITIONING'') THEN account_id ELSE NULL END) STORED','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_defer' AND column_name='new_due_at')=0,'ALTER TABLE `zsjos_student_delivery_defer` ADD COLUMN `new_due_at` datetime NULL','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_defer' AND column_name='idempotency_key')=0,'ALTER TABLE `zsjos_student_delivery_defer` ADD COLUMN `idempotency_key` varchar(128) NULL','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_defer' AND column_name='pending_stage_id')=0,'ALTER TABLE `zsjos_student_delivery_defer` ADD COLUMN `pending_stage_id` bigint GENERATED ALWAYS AS (CASE WHEN deleted=b''0'' AND status=''PENDING'' THEN stage_id ELSE NULL END) STORED','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_submission' AND column_name='idempotency_key')=0,'ALTER TABLE `zsjos_student_delivery_submission` ADD COLUMN `idempotency_key` varchar(128) NULL','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_plan' AND index_name='uk_delivery_plan_active')>0,'ALTER TABLE `zsjos_student_delivery_plan` DROP INDEX `uk_delivery_plan_active`','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_plan' AND index_name='uk_delivery_live')=0,'ALTER TABLE `zsjos_student_delivery_plan` ADD UNIQUE KEY `uk_delivery_live` (tenant_id,live_account_id)','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_defer' AND index_name='uk_delivery_defer_pending')>0,'ALTER TABLE `zsjos_student_delivery_defer` DROP INDEX `uk_delivery_defer_pending`','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_defer' AND index_name='uk_delivery_pending')=0,'ALTER TABLE `zsjos_student_delivery_defer` ADD UNIQUE KEY `uk_delivery_pending` (tenant_id,pending_stage_id)','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_config' AND index_name='uk_delivery_config')>0,'ALTER TABLE `zsjos_student_delivery_config` DROP INDEX `uk_delivery_config`','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_delivery_config' AND index_name='uk_delivery_config_version')=0,'ALTER TABLE `zsjos_student_delivery_config` ADD UNIQUE KEY `uk_delivery_config_version` (tenant_id,version,deleted)','SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl; EXECUTE delivery_stmt; DEALLOCATE PREPARE delivery_stmt;

SET @delivery_first=(SELECT COUNT(*)=0 FROM zsjos_schema_version WHERE version='V264');
-- Existing rounds are ordered by persisted plan identity, never inferred completion dates.
UPDATE zsjos_student_delivery_plan p JOIN (SELECT id,ROW_NUMBER() OVER(PARTITION BY tenant_id,account_id ORDER BY id) n FROM zsjos_student_delivery_plan) n ON n.id=p.id SET p.round_no=n.n WHERE p.round_no IS NULL;
UPDATE zsjos_student_delivery_config SET s3_days=7,s4_days=10,version=version+1 WHERE @delivery_first AND enabled=b'1' AND deleted=b'0';
-- Preserve deferred and completed stages; only reconstruct pending schedule where its recorded anchor exists.
UPDATE zsjos_student_delivery_stage s JOIN zsjos_student_delivery_plan p ON p.id=s.plan_id AND p.tenant_id=s.tenant_id
LEFT JOIN zsjos_student_delivery_stage parent ON parent.plan_id=s.plan_id AND parent.tenant_id=s.tenant_id AND parent.deleted=b'0'
 AND parent.stage_code=CASE s.stage_code WHEN 'S1' THEN 'S0' WHEN 'S2' THEN 'S1' ELSE 'S2' END
SET s.trigger_at=DATE_ADD(CASE WHEN s.stage_code='S0' THEN p.account_opened_at ELSE parent.completed_at END,
 INTERVAL CASE s.stage_code WHEN 'S0' THEN 3 WHEN 'S4' THEN 10 WHEN 'S5' THEN 14 ELSE 7 END DAY),
 s.due_at=CASE WHEN s.status='WAITING' THEN NULL ELSE DATE_ADD(CASE WHEN s.stage_code='S0' THEN p.account_opened_at ELSE parent.completed_at END,
 INTERVAL CASE s.stage_code WHEN 'S0' THEN 3 WHEN 'S4' THEN 10 WHEN 'S5' THEN 14 ELSE 7 END DAY) END,
 s.version=s.version+1
WHERE @delivery_first AND s.deleted=b'0' AND p.deleted=b'0' AND p.status='ACTIVE' AND s.status IN ('WAITING','PENDING','OVERDUE') AND s.stage_code<>'S6'
 AND (s.stage_code='S0' OR parent.completed_at IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM zsjos_student_delivery_defer d WHERE d.tenant_id=s.tenant_id AND d.stage_id=s.id AND d.deleted=b'0');
UPDATE zsjos_student_delivery_stage s JOIN zsjos_student_delivery_plan p ON p.id=s.plan_id AND p.tenant_id=s.tenant_id
SET s.status='NEEDS_REVIEW',s.version=s.version+1
WHERE @delivery_first AND p.status='ACTIVE' AND s.status IN ('WAITING','PENDING','OVERDUE') AND s.stage_code IN ('S1','S2','S3','S4','S5') AND s.deleted=b'0'
 AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM zsjos_student_delivery_stage) parent WHERE parent.plan_id=s.plan_id AND parent.tenant_id=s.tenant_id AND parent.deleted=b'0' AND parent.completed_at IS NOT NULL AND parent.stage_code=CASE s.stage_code WHEN 'S1' THEN 'S0' WHEN 'S2' THEN 'S1' ELSE 'S2' END);
-- New button metadata only; administrators assign the permission in System role management.
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '结束本期并重新定位','zsjos:student-delivery:reposition',3,7,m.parent_id,'','','',0,b'1',b'1',b'1','V264',NOW(),'V264',NOW(),b'0'
FROM system_menu m WHERE m.permission='zsjos:student-delivery:query' AND m.deleted=b'0'
 AND NOT EXISTS(SELECT 1 FROM system_menu n WHERE n.permission='zsjos:student-delivery:reposition' AND n.deleted=b'0') LIMIT 1;
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum) VALUES('V264','账号复盘与交付轮次',SHA2('V264__account_review_delivery_cycles.sql',256));
INSERT IGNORE INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES('core','V264','账号复盘与交付轮次',SHA2('V264__account_review_delivery_cycles.sql',256),'baseline',NOW());
