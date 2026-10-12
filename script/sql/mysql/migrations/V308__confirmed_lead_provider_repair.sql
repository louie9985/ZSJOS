-- UTF-8. V308 requires V307; tenant 1 fixed manifest, 153 self-sourced + 16 confirmed media providers.
-- User confirmed all 153 are unassisted; media: Cheng Xu 13, Wang Wenxin 2, Chen Wei 1.
-- Fourteen media identities corroborated by original 2026-09-20 dump; two explicitly confirmed by user.
-- Original dump SHA256 bb7f6234c81647ea750ad854622478830e9b90f538a4ed02e2e53a4db60c95db.
-- Only provider fields, explicit-recording flag and modification version/audit change.
-- Preserve source_type/source_user_id, owner, organization/counting facts, orders, permissions and frozen history.
-- Legacy source-type inconsistency is NOT silently repaired by this provider migration.
-- Retain before/after/provenance in existing LONGTEXT repair table, key confirmed-provider-v308.
-- Transaction covers backup, updates, postconditions, both ledgers; client MUST stop on errors.
-- Repeat/partial recovery require actual before/after state, even with version markers; later edits reject.
-- Empty environment no-op; partially present manifest rejects. No invented seed users/business data.
-- Rollback requires after-image matching and reviewed restore; preserve provenance; procedure DDL is not atomic.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS zsjos_v308_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v308_apply()
BEGIN
 DECLARE v_now DATETIME DEFAULT NOW();
 DECLARE v_count INT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V307') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V307') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 requires V307 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 requires LONGTEXT backup';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_partner','zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version') AND engine='InnoDB')<>5 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 requires transactional tables';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v308_manifest;
 CREATE TEMPORARY TABLE v308_manifest(lead_no VARCHAR(64) PRIMARY KEY,provider_id BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,source_type VARCHAR(32),provider_name VARCHAR(100)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 INSERT INTO v308_manifest VALUES
('LD202608100016',60,'self','sales_self_sourced',NULL),
('LD202608100023',60,'self','sales_self_sourced',NULL),
('LD202608140025',41,'self','sales_self_sourced',NULL),
('LD202608170031',41,'self','sales_self_sourced',NULL),
('LD202608210014',41,'self','sales_self_sourced',NULL),
('LD202608210043',41,'self','sales_self_sourced',NULL),
('LD202608240003',61,'self','sales_self_sourced',NULL),
('LD202608240035',51,'self','education_self_sourced',NULL),
('LD202608240043',291,'self','sales_self_sourced',NULL),
('LD202608250041',15,'self','education_self_sourced',NULL),
('LD202608250063',291,'self','sales_self_sourced',NULL),
('LD202608260015',23,'self','sales_self_sourced',NULL),
('LD202608260018',51,'self','education_self_sourced',NULL),
('LD202608270029',41,'self','sales_self_sourced',NULL),
('LD202608270054',17,'self','sales_self_sourced',NULL),
('LD202608270056',17,'self','sales_self_sourced',NULL),
('LD202608280012',41,'self','sales_self_sourced',NULL),
('LD202608280041',15,'self','education_self_sourced',NULL),
('LD202608280044',41,'self','sales_self_sourced',NULL),
('LD202608280054',22,'self','sales_self_sourced',NULL),
('LD202608280057',37,'self','education_self_sourced',NULL),
('LD202608290005',37,'self','education_self_sourced',NULL),
('LD202608300048',17,'self','sales_self_sourced',NULL),
('LD202608300068',37,'self','education_self_sourced',NULL),
('LD202608300074',294,'self','sales_self_sourced',NULL),
('LD202608310007',17,'self','sales_self_sourced',NULL),
('LD202608310052',291,'self','sales_self_sourced',NULL),
('LD202609010024',37,'self','education_self_sourced',NULL),
('LD202609010047',37,'self','education_self_sourced',NULL),
('LD202609020037',51,'self','education_self_sourced',NULL),
('LD202609020048',51,'self','education_self_sourced',NULL),
('LD202609020054',37,'self','education_self_sourced',NULL),
('LD202609020062',400,'self','education_self_sourced',NULL),
('LD202609020074',17,'self','sales_self_sourced',NULL),
('LD202609020077',400,'self','education_self_sourced',NULL),
('LD202609030026',51,'self','education_self_sourced',NULL),
('LD202609030028',51,'self','education_self_sourced',NULL),
('LD202609030030',15,'self','education_self_sourced',NULL),
('LD202609030032',15,'self','education_self_sourced',NULL),
('LD202609030051',37,'self','education_self_sourced',NULL),
('LD202609030052',37,'self','education_self_sourced',NULL),
('LD202609030054',37,'self','education_self_sourced',NULL),
('LD202609030078',27,'self','education_self_sourced',NULL),
('LD202609030079',27,'self','education_self_sourced',NULL),
('LD202609040007',30,'self','education_self_sourced',NULL),
('LD202609040029',17,'self','sales_self_sourced',NULL),
('LD202609040040',37,'self','education_self_sourced',NULL),
('LD202609040041',37,'self','education_self_sourced',NULL),
('LD202609040043',37,'self','education_self_sourced',NULL),
('LD202609040054',27,'self','education_self_sourced',NULL),
('LD202609040074',27,'self','education_self_sourced',NULL),
('LD202609040075',27,'self','education_self_sourced',NULL),
('LD202609040076',27,'self','education_self_sourced',NULL),
('LD202609060043',37,'self','education_self_sourced',NULL),
('LD202609060050',27,'self','education_self_sourced',NULL),
('LD202609060052',30,'self','education_self_sourced',NULL),
('LD202609060053',51,'self','education_self_sourced',NULL),
('LD202609060064',51,'self','education_self_sourced',NULL),
('LD202609060067',30,'self','education_self_sourced',NULL),
('LD202609060068',15,'self','education_self_sourced',NULL),
('LD202609060069',27,'self','education_self_sourced',NULL),
('LD202609060070',27,'self','education_self_sourced',NULL),
('LD202609070006',51,'self','education_self_sourced',NULL),
('LD202609070014',60,'self','sales_self_sourced',NULL),
('LD202609070017',51,'self','education_self_sourced',NULL),
('LD202609070030',51,'self','education_self_sourced',NULL),
('LD202609070032',27,'self','education_self_sourced',NULL),
('LD202609070034',17,'self','sales_self_sourced',NULL),
('LD202609070035',291,'self','sales_self_sourced',NULL),
('LD202609070044',291,'self','sales_self_sourced',NULL),
('LD202609070046',27,'self','education_self_sourced',NULL),
('LD202609070048',27,'self','education_self_sourced',NULL),
('LD202609070052',37,'self','education_self_sourced',NULL),
('LD202609070053',37,'self','education_self_sourced',NULL),
('LD202609070061',27,'self','education_self_sourced',NULL),
('LD202609070072',37,'self','education_self_sourced',NULL),
('LD202609070075',37,'self','education_self_sourced',NULL),
('LD202609070079',51,'self','education_self_sourced',NULL),
('LD202609070080',27,'self','education_self_sourced',NULL),
('LD202609080050',51,'self','education_self_sourced',NULL),
('LD202609080055',37,'self','education_self_sourced',NULL),
('LD202609080058',37,'self','education_self_sourced',NULL),
('LD202609080073',30,'self','education_self_sourced',NULL),
('LD202609080075',30,'self','education_self_sourced',NULL),
('LD202609080076',400,'self','education_self_sourced',NULL),
('LD202609080078',27,'self','education_self_sourced',NULL),
('LD202609080079',27,'self','education_self_sourced',NULL),
('LD202609090031',37,'self','education_self_sourced',NULL),
('LD202609090033',37,'self','education_self_sourced',NULL),
('LD202609090038',51,'self','education_self_sourced',NULL),
('LD202609090040',37,'self','education_self_sourced',NULL),
('LD202609090041',37,'self','education_self_sourced',NULL),
('LD202609090053',400,'self','education_self_sourced',NULL),
('LD202609090054',61,'self','sales_self_sourced',NULL),
('LD202609090055',22,'self','sales_self_sourced',NULL),
('LD202609090056',400,'self','education_self_sourced',NULL),
('LD202609090058',51,'self','education_self_sourced',NULL),
('LD202609090059',30,'self','education_self_sourced',NULL),
('LD202609090063',61,'self','sales_self_sourced',NULL),
('LD202609090065',15,'self','education_self_sourced',NULL),
('LD202609100029',30,'self','education_self_sourced',NULL),
('LD202609100045',37,'self','education_self_sourced',NULL),
('LD202609100048',30,'self','education_self_sourced',NULL),
('LD202609100063',51,'self','education_self_sourced',NULL),
('LD202609100064',37,'self','education_self_sourced',NULL),
('LD202609100066',37,'self','education_self_sourced',NULL),
('LD202609100073',27,'self','education_self_sourced',NULL),
('LD202609100074',27,'self','education_self_sourced',NULL),
('LD202609100075',400,'self','education_self_sourced',NULL),
('LD202609100076',400,'self','education_self_sourced',NULL),
('LD202609110024',27,'self','education_self_sourced',NULL),
('LD202609110028',41,'self','sales_self_sourced',NULL),
('LD202609110032',30,'self','education_self_sourced',NULL),
('LD202609110043',41,'self','sales_self_sourced',NULL),
('LD202609110048',60,'self','sales_self_sourced',NULL),
('LD202609110056',41,'self','sales_self_sourced',NULL),
('LD202609110057',27,'self','education_self_sourced',NULL),
('LD202609110059',41,'self','sales_self_sourced',NULL),
('LD202609110060',41,'self','sales_self_sourced',NULL),
('LD202609110066',37,'self','education_self_sourced',NULL),
('LD202609110079',30,'self','education_self_sourced',NULL),
('LD202609110082',400,'self','education_self_sourced',NULL),
('LD202609110083',30,'self','education_self_sourced',NULL),
('LD202609110084',27,'self','education_self_sourced',NULL),
('LD202609110085',37,'self','education_self_sourced',NULL),
('LD202609120011',17,'self','sales_self_sourced',NULL),
('LD202609120034',41,'self','sales_self_sourced',NULL),
('LD202609140020',291,'self','sales_self_sourced',NULL),
('LD202609140062',30,'self','education_self_sourced',NULL),
('LD202609140075',27,'self','education_self_sourced',NULL),
('LD202609140076',27,'self','education_self_sourced',NULL),
('LD202609140078',37,'self','education_self_sourced',NULL),
('LD202609140081',37,'self','education_self_sourced',NULL),
('LD202609140111',37,'self','education_self_sourced',NULL),
('LD202609140112',27,'self','education_self_sourced',NULL),
('LD202609140122',51,'self','education_self_sourced',NULL),
('LD202609140123',51,'self','education_self_sourced',NULL),
('LD202609140127',400,'self','education_self_sourced',NULL),
('LD202609140128',400,'self','education_self_sourced',NULL),
('LD202609140129',51,'self','education_self_sourced',NULL),
('LD202609140131',400,'self','education_self_sourced',NULL),
('LD202609140134',30,'self','education_self_sourced',NULL),
('LD202609140136',27,'self','education_self_sourced',NULL),
('LD202609140138',291,'self','sales_self_sourced',NULL),
('LD202609150021',61,'self','sales_self_sourced',NULL),
('LD202609150031',291,'self','sales_self_sourced',NULL),
('LD202609150035',27,'self','education_self_sourced',NULL),
('LD202609150041',61,'self','sales_self_sourced',NULL),
('LD202609150044',37,'self','education_self_sourced',NULL),
('LD202609150049',37,'self','education_self_sourced',NULL),
('LD202609150050',30,'self','education_self_sourced',NULL),
('LD202609150051',30,'self','education_self_sourced',NULL),
('LD202609150053',37,'self','education_self_sourced',NULL),
('LD202607070015',20,'media','internal_new_media','陈薇'),
('LD202607080013',42,'media','internal_new_media','王文欣'),
('LD202607090002',26,'media','internal_new_media','程旭'),
('LD202607100005',42,'media','internal_new_media','王文欣'),
('LD202607130022',26,'media','internal_new_media','程旭'),
('LD202607160021',26,'media','internal_new_media','程旭'),
('LD202607170019',26,'media','internal_new_media','程旭'),
('LD202607220003',26,'media','internal_new_media','程旭'),
('LD202607220004',26,'media','internal_new_media','程旭'),
('LD202607270013',26,'media','internal_new_media','程旭'),
('LD202607310008',26,'media','internal_new_media','程旭'),
('LD202608050002',26,'media','internal_new_media','程旭'),
('LD202608060022',26,'media','internal_new_media','程旭'),
('LD202608070021',26,'media','internal_new_media','程旭'),
('LD202608110024',26,'media','internal_new_media','程旭'),
('LD202608130016',26,'media','internal_new_media','程旭');
 SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
 START TRANSACTION;
 SELECT COUNT(*) INTO v_count FROM zsjos_lead l JOIN v308_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 FOR UPDATE;
 IF v_count NOT IN (0,169) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 partial manifest; review deployment data'; END IF;
 IF v_count=169 AND (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V308') OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V308')) AND (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='confirmed-provider-v308')<>507 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 ledger without complete repair evidence';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_lead l JOIN v308_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 AND (l.deleted<>0 OR NOT(l.source_type <=> m.source_type) OR (m.kind='self' AND (NOT(l.source_user_id <=> m.provider_id) OR l.provider_owner_type IS NOT NULL OR l.provider_owner_id IS NOT NULL OR l.source_provider_user_id IS NOT NULL)) OR (m.kind='media' AND (l.source_user_id IS NOT NULL OR l.contribution_user_id_snapshot IS NOT NULL OR l.contribution_dept_id_snapshot IS NOT NULL)))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 reviewed identity changed';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='confirmed-provider-v308' GROUP BY table_name,row_id,column_name HAVING COUNT(*)>1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 duplicate backup'; END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b LEFT JOIN zsjos_lead l ON l.id=b.row_id AND l.tenant_id=1 LEFT JOIN v308_manifest m ON m.lead_no=l.lead_no WHERE b.repair_key='confirmed-provider-v308' AND (m.lead_no IS NULL OR b.table_name<>'zsjos_lead')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 backup target missing'; END IF;
 DROP TEMPORARY TABLE IF EXISTS v308_images;
 CREATE TEMPORARY TABLE v308_images(row_id BIGINT PRIMARY KEY,kind VARCHAR(16),before_image JSON,after_image JSON,current_image JSON,provenance JSON) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 INSERT INTO v308_images SELECT l.id,m.kind,JSON_OBJECT('source_type',l.source_type,'source_user_id',l.source_user_id,'source_provider_user_id',l.source_provider_user_id,'source_provider_recorded',CAST(l.source_provider_recorded AS UNSIGNED),'provider_owner_type',l.provider_owner_type,'provider_owner_id',l.provider_owner_id,'provider_owner_name_snapshot',l.provider_owner_name_snapshot,'contribution_user_id_snapshot',l.contribution_user_id_snapshot,'contribution_user_name_snapshot',l.contribution_user_name_snapshot,'contribution_supervisor_user_id_snapshot',l.contribution_supervisor_user_id_snapshot,'contribution_supervisor_name_snapshot',l.contribution_supervisor_name_snapshot,'contribution_dept_id_snapshot',l.contribution_dept_id_snapshot,'contribution_dept_name_snapshot',l.contribution_dept_name_snapshot,'counted_at',DATE_FORMAT(l.counted_at,'%Y-%m-%d %H:%i:%s'),'version',l.version,'updater',l.updater,'update_time',DATE_FORMAT(l.update_time,'%Y-%m-%d %H:%i:%s')),JSON_OBJECT('source_type',l.source_type,'source_user_id',l.source_user_id,'source_provider_user_id',l.source_provider_user_id,'source_provider_recorded',CAST(l.source_provider_recorded AS UNSIGNED),'provider_owner_type',l.provider_owner_type,'provider_owner_id',l.provider_owner_id,'provider_owner_name_snapshot',l.provider_owner_name_snapshot,'contribution_user_id_snapshot',l.contribution_user_id_snapshot,'contribution_user_name_snapshot',l.contribution_user_name_snapshot,'contribution_supervisor_user_id_snapshot',l.contribution_supervisor_user_id_snapshot,'contribution_supervisor_name_snapshot',l.contribution_supervisor_name_snapshot,'contribution_dept_id_snapshot',l.contribution_dept_id_snapshot,'contribution_dept_name_snapshot',l.contribution_dept_name_snapshot,'counted_at',DATE_FORMAT(l.counted_at,'%Y-%m-%d %H:%i:%s'),'version',l.version,'updater',l.updater,'update_time',DATE_FORMAT(l.update_time,'%Y-%m-%d %H:%i:%s')),JSON_OBJECT('source_type',l.source_type,'source_user_id',l.source_user_id,'source_provider_user_id',l.source_provider_user_id,'source_provider_recorded',CAST(l.source_provider_recorded AS UNSIGNED),'provider_owner_type',l.provider_owner_type,'provider_owner_id',l.provider_owner_id,'provider_owner_name_snapshot',l.provider_owner_name_snapshot,'contribution_user_id_snapshot',l.contribution_user_id_snapshot,'contribution_user_name_snapshot',l.contribution_user_name_snapshot,'contribution_supervisor_user_id_snapshot',l.contribution_supervisor_user_id_snapshot,'contribution_supervisor_name_snapshot',l.contribution_supervisor_name_snapshot,'contribution_dept_id_snapshot',l.contribution_dept_id_snapshot,'contribution_dept_name_snapshot',l.contribution_dept_name_snapshot,'counted_at',DATE_FORMAT(l.counted_at,'%Y-%m-%d %H:%i:%s'),'version',l.version,'updater',l.updater,'update_time',DATE_FORMAT(l.update_time,'%Y-%m-%d %H:%i:%s')),JSON_OBJECT('source',IF(m.kind='media','user-confirmed media provider; 14 corroborated by original dump; two user-confirmed overrides','user-confirmed unassisted self-sourcing'),'provider_id',m.provider_id,'evidence',CASE WHEN m.kind='self' THEN 'user confirmed all 42 sales and 111 education records unassisted' WHEN m.lead_no IN ('LD202607070015','LD202607090002') THEN 'explicit user identity confirmation; old dump attribution absent' ELSE 'old dump media_attribution_employee_id and submitter_media_staff_name_snapshot corroborate identity' END,'original_dump_sha256','bb7f6234c81647ea750ad854622478830e9b90f538a4ed02e2e53a4db60c95db','approved_on','2026-10-10','captured_at',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')) FROM zsjos_lead l JOIN v308_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1;
 IF EXISTS(SELECT 1 FROM v308_images i JOIN zsjos_data_repair_backup b ON b.repair_key='confirmed-provider-v308' AND b.table_name='zsjos_lead' AND b.row_id=i.row_id WHERE (SELECT COUNT(*) FROM zsjos_data_repair_backup z WHERE z.repair_key=b.repair_key AND z.table_name=b.table_name AND z.row_id=b.row_id AND z.column_name IN ('__before__','__after__','__provenance__'))<>3) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 incomplete backup'; END IF;
 IF EXISTS(SELECT 1 FROM v308_images i LEFT JOIN zsjos_data_repair_backup b ON b.repair_key='confirmed-provider-v308' AND b.table_name='zsjos_lead' AND b.row_id=i.row_id WHERE b.id IS NULL AND (JSON_TYPE(i.before_image->'$.provider_owner_name_snapshot')<>'NULL' OR JSON_TYPE(i.before_image->'$.provider_owner_type')<>'NULL' OR JSON_TYPE(i.before_image->'$.provider_owner_id')<>'NULL' OR JSON_TYPE(i.before_image->'$.source_provider_user_id')<>'NULL' OR NOT(i.before_image->>'$.source_provider_recorded' <=> '0'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 original reviewed state changed'; END IF;
 IF EXISTS(SELECT 1 FROM v308_manifest m JOIN zsjos_lead l ON l.lead_no=m.lead_no AND l.tenant_id=1 LEFT JOIN system_users u ON u.id=m.provider_id AND u.tenant_id=1 AND u.deleted=0 WHERE m.kind='media' AND u.id IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 confirmed media user missing'; END IF;
 UPDATE v308_images i JOIN zsjos_lead l ON l.id=i.row_id JOIN v308_manifest m ON m.lead_no=l.lead_no SET i.after_image=JSON_SET(i.after_image,'$.provider_owner_type','system_user','$.provider_owner_id',m.provider_id,'$.provider_owner_name_snapshot',m.provider_name,'$.source_provider_user_id',m.provider_id,'$.source_provider_recorded',1) WHERE i.kind='media';
 UPDATE v308_images SET after_image=JSON_SET(after_image,'$.source_provider_recorded',1) WHERE kind='self';
 UPDATE v308_images SET after_image=JSON_SET(after_image,'$.version',CAST(before_image->>'$.version' AS UNSIGNED)+1,'$.updater','confirmed-provider-v308','$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s'));
 UPDATE v308_images i JOIN zsjos_data_repair_backup b ON b.repair_key='confirmed-provider-v308' AND b.table_name='zsjos_lead' AND b.row_id=i.row_id AND b.column_name='__before__' JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__' SET i.before_image=CAST(b.old_value AS JSON),i.after_image=CAST(a.old_value AS JSON);
 IF EXISTS(SELECT 1 FROM v308_images WHERE NOT(current_image=before_image OR current_image=after_image)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 later edits conflict; refusing overwrite'; END IF;
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT 'confirmed-provider-v308','zsjos_lead',i.row_id,'__before__',CAST(i.before_image AS CHAR CHARACTER SET utf8mb4) FROM v308_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='confirmed-provider-v308' AND b.row_id=i.row_id AND b.table_name='zsjos_lead' AND b.column_name='__before__');
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT 'confirmed-provider-v308','zsjos_lead',i.row_id,'__after__',CAST(i.after_image AS CHAR CHARACTER SET utf8mb4) FROM v308_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='confirmed-provider-v308' AND b.row_id=i.row_id AND b.table_name='zsjos_lead' AND b.column_name='__after__');
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT 'confirmed-provider-v308','zsjos_lead',i.row_id,'__provenance__',CAST(i.provenance AS CHAR CHARACTER SET utf8mb4) FROM v308_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='confirmed-provider-v308' AND b.row_id=i.row_id AND b.table_name='zsjos_lead' AND b.column_name='__provenance__');
 UPDATE zsjos_lead l JOIN v308_images i ON i.row_id=l.id SET l.source_provider_user_id=CAST(NULLIF(i.after_image->>'$.source_provider_user_id','null') AS UNSIGNED),l.provider_owner_type=NULLIF(i.after_image->>'$.provider_owner_type','null'),l.provider_owner_id=CAST(NULLIF(i.after_image->>'$.provider_owner_id','null') AS UNSIGNED),l.provider_owner_name_snapshot=IF(JSON_TYPE(i.after_image->'$.provider_owner_name_snapshot')='NULL',NULL,i.after_image->>'$.provider_owner_name_snapshot'),l.source_provider_recorded=CAST(NULLIF(i.after_image->>'$.source_provider_recorded','null') AS UNSIGNED),l.version=CAST(i.after_image->>'$.version' AS UNSIGNED),l.updater=i.after_image->>'$.updater',l.update_time=i.after_image->>'$.update_time' WHERE i.current_image<>i.after_image;
 IF EXISTS(SELECT 1 FROM zsjos_lead l JOIN v308_images i ON i.row_id=l.id WHERE NOT(JSON_OBJECT('source_type',l.source_type,'source_user_id',l.source_user_id,'source_provider_user_id',l.source_provider_user_id,'source_provider_recorded',CAST(l.source_provider_recorded AS UNSIGNED),'provider_owner_type',l.provider_owner_type,'provider_owner_id',l.provider_owner_id,'provider_owner_name_snapshot',l.provider_owner_name_snapshot,'contribution_user_id_snapshot',l.contribution_user_id_snapshot,'contribution_user_name_snapshot',l.contribution_user_name_snapshot,'contribution_supervisor_user_id_snapshot',l.contribution_supervisor_user_id_snapshot,'contribution_supervisor_name_snapshot',l.contribution_supervisor_name_snapshot,'contribution_dept_id_snapshot',l.contribution_dept_id_snapshot,'contribution_dept_name_snapshot',l.contribution_dept_name_snapshot,'counted_at',DATE_FORMAT(l.counted_at,'%Y-%m-%d %H:%i:%s'),'version',l.version,'updater',l.updater,'update_time',DATE_FORMAT(l.update_time,'%Y-%m-%d %H:%i:%s'))=i.after_image)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V308 postcondition failed'; END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V308','Repair reviewed Lead provider snapshots',SHA2('V308__confirmed_lead_provider_repair.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V308');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V308','Repair reviewed Lead provider snapshots',SHA2('V308__confirmed_lead_provider_repair.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V308');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v308_apply();
DROP PROCEDURE zsjos_v308_apply;
