-- UTF-8. V329: canonical public sea pool names; upgrade after V328 or the verified public-sea schema.
-- Non-destructive: rename tables/columns/index and active menu/filter/notification metadata in place.
-- Preserve IDs, rows, role-menu assignments, historical events/messages and migration checksums.
-- Stop the application for rollout and drain queued public-sea notifications first.
-- DDL commits implicitly: rerun recovers a partially renamed schema; rollback requires coordinated reverse renames.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v329_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v329_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_rule')
 OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='system_menu') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 requires public-sea and System configuration schema';
 END IF;
 IF EXISTS(SELECT 1 FROM system_notify_business_outbox WHERE scene_code LIKE 'zsjos.lead.aging_pool_%' AND status IN ('pending','processing','retry')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 requires pending legacy public-sea notifications to be drained';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead_aging_pool_cycle','zsjos_lead_public_sea_pool_cycle'))<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 missing or conflicting zsjos_lead_public_sea_pool_cycle';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead_aging_pool_event','zsjos_lead_public_sea_pool_event'))<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 missing or conflicting zsjos_lead_public_sea_pool_event';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead_aging_pool_notify_stage','zsjos_lead_public_sea_pool_notify_stage'))<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 missing or conflicting zsjos_lead_public_sea_pool_notify_stage';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead_public_sea_record','zsjos_lead_public_sea_pool_compatibility_record'))<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 missing or conflicting zsjos_lead_public_sea_pool_compatibility_record';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_rule' AND column_name IN ('aging_pool_timeout_days','public_sea_pool_timeout_days'))<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 missing or conflicting public-sea timeout column';
 END IF;
 IF EXISTS(SELECT 1 FROM system_notify_template old JOIN system_notify_template canonical ON canonical.code=REPLACE(old.code,'AGING_POOL','PUBLIC_SEA_POOL') AND canonical.id<>old.id WHERE old.code LIKE 'ZSJOS_AGING_POOL_%') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 conflicting public-sea notification template codes';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu old JOIN system_menu canonical ON canonical.permission=REPLACE(old.permission,'lead-aging-pool','lead-public-sea-pool') AND canonical.deleted=0 AND canonical.id<>old.id WHERE old.permission LIKE 'zsjos:lead-aging-pool:%' AND old.deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 conflicting public-sea menu permissions';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_lead_inbox_filter_scheme old JOIN zsjos_lead_inbox_filter_scheme canonical ON canonical.tenant_id=old.tenant_id AND canonical.audience='publicSeaPool' AND canonical.deleted=0 WHERE old.audience='agingPool' AND old.deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 conflicting public-sea filter audience';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_lead_aging_pool_cycle') THEN
  RENAME TABLE `zsjos_lead_aging_pool_cycle` TO `zsjos_lead_public_sea_pool_cycle`;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_lead_aging_pool_event') THEN
  RENAME TABLE `zsjos_lead_aging_pool_event` TO `zsjos_lead_public_sea_pool_event`;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_lead_aging_pool_notify_stage') THEN
  RENAME TABLE `zsjos_lead_aging_pool_notify_stage` TO `zsjos_lead_public_sea_pool_notify_stage`;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_lead_public_sea_record') THEN
  RENAME TABLE `zsjos_lead_public_sea_record` TO `zsjos_lead_public_sea_pool_compatibility_record`;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_rule' AND column_name='aging_pool_timeout_days') THEN
  ALTER TABLE zsjos_lead_follow_up_rule RENAME COLUMN aging_pool_timeout_days TO public_sea_pool_timeout_days;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND index_name='idx_tenant_aging_pool_scan') THEN
  ALTER TABLE zsjos_lead RENAME INDEX idx_tenant_aging_pool_scan TO idx_tenant_public_sea_pool_scan;
 END IF;
 START TRANSACTION;
 UPDATE system_menu SET permission=REPLACE(permission,'lead-aging-pool','lead-public-sea-pool'),path=REPLACE(path,'lead-aging-pool','lead-public-sea-pool'),component=REPLACE(component,'leadAgingPool','leadPublicSeaPool'),name=IF(type=2,'公海池',name) WHERE permission LIKE 'zsjos:lead-aging-pool:%';
 UPDATE zsjos_lead_inbox_filter_scheme SET audience='publicSeaPool' WHERE audience='agingPool';
 UPDATE system_notify_rule SET scene_code=REPLACE(scene_code,'aging_pool','public_sea_pool') WHERE scene_code LIKE 'zsjos.lead.aging_pool_%';
 UPDATE system_notify_template SET code=REPLACE(code,'AGING_POOL','PUBLIC_SEA_POOL'),content=REPLACE(content,'agingPool.','publicSeaPool.'),summary=REPLACE(summary,'agingPool.','publicSeaPool.'),params=REPLACE(params,'agingPool.','publicSeaPool.'),scene_code=REPLACE(scene_code,'aging_pool','public_sea_pool') WHERE code LIKE 'ZSJOS_AGING_POOL_%' OR scene_code LIKE 'zsjos.lead.aging_pool_%';
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead_aging_pool_cycle','zsjos_lead_aging_pool_event','zsjos_lead_aging_pool_notify_stage','zsjos_lead_public_sea_record'))
 OR EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_rule' AND column_name='aging_pool_timeout_days')
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission LIKE 'zsjos:lead-aging-pool:%')
 OR EXISTS(SELECT 1 FROM zsjos_lead_inbox_filter_scheme WHERE audience='agingPool')
 OR EXISTS(SELECT 1 FROM system_notify_rule WHERE scene_code LIKE 'zsjos.lead.aging_pool_%') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V329 canonical naming postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V329','公海池内部命名统一',SHA2('V329__public_sea_pool_naming.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V329');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V329','公海池内部命名统一',SHA2('V329__public_sea_pool_naming.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V329');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v329_apply();
DROP PROCEDURE zsjos_v329_apply;
