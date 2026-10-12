# -*- coding: utf-8 -*-
"""Generate the portable V309 repair from a reviewed business-number manifest (no credentials)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
KEY = 'media-assisted-source-v309'
MANIFEST = [('KZ202609211345520027',12),('LD202608140010',26),('LD202608200041',26),
 ('LD202608250009',26),('LD202608280068',31),('LD202608300016',26),('LD202608300026',26),
 ('LD202608310022',26),('LD202608310037',31),('LD202609010051',31),('LD202609040014',26),
 ('LD202609040073',26),('LD202609090005',26),('LD202609090036',26),('LD202609100079',31),
 ('LD202609110010',26),('LD202609160068',26)]

def image(alias, columns):
    return 'JSON_OBJECT('+','.join("'"+k+"',"+(f"DATE_FORMAT({alias}.{k},'%Y-%m-%d %H:%i:%s')" if k=='update_time' else f'{alias}.{k}') for k in columns)+')'

def generate():
    parts = ["""-- UTF-8. V309 after V308: preserve entry identity; correct only 17 reviewed tenant-1 Leads.
-- Source and business submitter become media/provider; original operator/audit and owner stay intact.
-- source_dept_id uses the existing contribution department snapshot, never current organization.
-- Only historical source classification is corrected (including frozen facts by explicit user decision).
-- Organization, people credited, timestamps, amounts, repurchase classification and workflow facts stay intact.
-- Orders retain their actual submitter; only approval leadProfile source keys are corrected.
-- Before/after images in zsjos_data_repair_backup are captured once; later conflicting edits fail closed.
-- DDL commits implicitly: a failed run may leave the nullable column. Re-execution checks actual state.
-- Empty fresh DB: additive schema only, no business seeds. Partial manifest rejects.
-- Restore requires matching after-images, reviewed before-image recovery and later-migration review.
-- Do not replay older V298/V301 repair scripts after this migration. No role/menu changes or notifications.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS zsjos_v309_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v309_apply()
BEGIN
 DECLARE v_count INT;
 DECLARE v_now DATETIME DEFAULT NOW();
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V308') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V308') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 requires V308 in both ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 requires LONGTEXT repair backup';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_order_approval_round','zsjos_performance_attribution','zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version') AND engine='InnoDB')<>6 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 requires transactional repair tables';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='submission_source_type') THEN
  ALTER TABLE zsjos_lead ADD COLUMN submission_source_type VARCHAR(32) NULL COMMENT '录单入口身份来源，与业务引流来源分离' AFTER source_type;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='submission_source_type' AND data_type='varchar' AND character_maximum_length=32 AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 incompatible entry identity column';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v309_manifest;
 CREATE TEMPORARY TABLE v309_manifest(lead_no VARCHAR(64) PRIMARY KEY,provider_id BIGINT NOT NULL) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 INSERT INTO v309_manifest VALUES
""" + ',\n'.join(f"('{n}',{p})" for n,p in MANIFEST) + ";\n" + """
 SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
 START TRANSACTION;
 SELECT COUNT(*) INTO v_count FROM zsjos_lead l JOIN v309_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 FOR UPDATE;
 IF v_count NOT IN (0,17) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 partial manifest'; END IF;
 IF EXISTS(SELECT 1 FROM zsjos_lead l JOIN v309_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 AND
  (l.deleted<>0 OR l.source_type NOT IN ('sales_self_sourced','internal_new_media') OR l.source_provider_recorded<>1
   OR NOT(l.source_provider_user_id <=> m.provider_id) OR NOT(l.provider_owner_type <=> 'system_user')
   OR NOT(l.provider_owner_id <=> m.provider_id) OR NOT(l.contribution_user_id_snapshot <=> m.provider_id)
   OR l.contribution_dept_id_snapshot IS NULL OR l.provider_owner_name_snapshot IS NULL OR l.partner_id IS NOT NULL)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 reviewed provider evidence changed';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v309_images;
 CREATE TEMPORARY TABLE v309_images(table_name VARCHAR(64),row_id BIGINT,current_image JSON,before_image JSON,after_image JSON,PRIMARY KEY(table_name,row_id));
"""]
    specs = [
      ('zsjos_lead','l', ['source_type','submission_source_type','source_user_id','source_dept_id','version','updater','update_time'],
       "zsjos_lead l JOIN v309_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1",
       "'$.submission_source_type','sales_self_sourced','$.source_type','internal_new_media','$.source_user_id',l.source_provider_user_id,'$.source_dept_id',l.contribution_dept_id_snapshot,'$.version',l.version+1,'$.updater','V309','$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')"),
      ('zsjos_order_approval_round','r',['order_snapshot','version','updater','update_time'],
       "zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id AND o.tenant_id=r.tenant_id JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=o.tenant_id JOIN v309_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 AND o.deleted=0 AND r.deleted=0 AND JSON_TYPE(r.order_snapshot->'$.leadProfile')='OBJECT'",
       "'$.order_snapshot',JSON_SET(r.order_snapshot,'$.leadProfile.sourceType','internal_new_media','$.leadProfile.sourceLabel','新媒体提交','$.leadProfile.sourceUserName',l.provider_owner_name_snapshot,'$.leadProfile.sourceSubjectType','ADMIN','$.leadProfile.sourceSubjectId',l.source_provider_user_id),'$.version',r.version+1,'$.updater','V309','$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')"),
      ('zsjos_performance_attribution','p',['source_group','updater','update_time'],
       "zsjos_performance_attribution p JOIN zsjos_lead l ON l.id=p.lead_id AND l.tenant_id=p.tenant_id JOIN v309_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 AND p.deleted=0 AND p.source_group='self' AND NOT(p.fact_type='ORDER' AND EXISTS(SELECT 1 FROM zsjos_order o WHERE o.id=p.fact_id AND o.tenant_id=p.tenant_id AND o.deleted=0 AND o.order_type='repurchase'))",
       "'$.source_group','inbound','$.updater','V309','$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')")]
    # Include already-corrected facts on replay, and only facts belonging to this repair.
    specs[2] = (*specs[2][:3], specs[2][3].replace("p.source_group='self'", f"(p.source_group='self' OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name='zsjos_performance_attribution' AND b.row_id=p.id))"), specs[2][4])
    for table,alias,cols,join,updates in specs:
        img=image(alias,cols)
        parts.append(f" INSERT INTO v309_images SELECT '{table}',{alias}.id,{img},{img},JSON_SET({img},{updates}) FROM {join};\n")
    parts.append(f"""
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='{KEY}' GROUP BY table_name,row_id,column_name HAVING COUNT(*)<>1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 duplicate evidence'; END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b LEFT JOIN v309_images i ON i.table_name=b.table_name AND i.row_id=b.row_id WHERE b.repair_key='{KEY}' AND (i.row_id IS NULL OR b.column_name NOT IN ('__before__','__after__'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 evidence target missing'; END IF;
 IF EXISTS(SELECT 1 FROM v309_images i WHERE (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id) NOT IN (0,2)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 incomplete images'; END IF;
 IF v_count=17 AND (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309') OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309')) AND (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='{KEY}')<>(SELECT COUNT(*)*2 FROM v309_images) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 ledger lacks evidence'; END IF;
 UPDATE v309_images i JOIN zsjos_data_repair_backup b ON b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__before__' JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__' SET i.before_image=CAST(b.old_value AS JSON),i.after_image=CAST(a.old_value AS JSON);
 IF EXISTS(SELECT 1 FROM v309_images WHERE current_image<>before_image AND current_image<>after_image) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 later edit conflicts with repair'; END IF;
 IF EXISTS(SELECT 1 FROM v309_images i WHERE i.table_name='zsjos_lead' AND i.before_image->>'$.source_type'<>'sales_self_sourced') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 original source evidence missing'; END IF;
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT '{KEY}',i.table_name,i.row_id,'__before__',CAST(i.before_image AS CHAR CHARACTER SET utf8mb4) FROM v309_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__before__');
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT '{KEY}',i.table_name,i.row_id,'__after__',CAST(i.after_image AS CHAR CHARACTER SET utf8mb4) FROM v309_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__after__');
""")
    for table,alias,cols,_,_ in specs:
        assignments=[]
        for col in cols:
            val=f"NULLIF(i.after_image->>'$.{col}','null')"
            if col=='order_snapshot': val="i.after_image->'$.order_snapshot'"
            assignments.append(f'{alias}.{col}={val}')
        parts.append(f" UPDATE {table} {alias} JOIN v309_images i ON i.table_name='{table}' AND i.row_id={alias}.id SET {','.join(assignments)} WHERE {alias}.tenant_id=1 AND i.current_image<>i.after_image;\n")
        parts.append(f" IF EXISTS(SELECT 1 FROM {table} {alias} JOIN v309_images i ON i.table_name='{table}' AND i.row_id={alias}.id WHERE NOT({image(alias,cols)} <=> i.after_image)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 {table} postcondition failed'; END IF;\n")
    from source_review_revision import generate as revision
    parts.append(revision())
    from generate_provider_identity_sql import body as provider_identity_revision
    parts.append(provider_identity_revision())
    parts.append("""
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V309','Media assisted source classification',SHA2('V309__media_assisted_source.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V309','Media assisted source classification',SHA2('V309__media_assisted_source.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309');
 UPDATE zsjos_schema_version SET checksum=COALESCE(@zsjos_v309_file_checksum,SHA2('lead-source-confirmed-v309-r2',256)),description='Media source classification and confirmed source review r2' WHERE version='V309';
 UPDATE zsjos_module_schema_version SET checksum=COALESCE(@zsjos_v309_file_checksum,SHA2('lead-source-confirmed-v309-r2',256)),description='Media source classification and confirmed source review r2' WHERE module_code='core' AND version='V309';
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v309_apply();
DROP PROCEDURE zsjos_v309_apply;
""")
    return ''.join(parts)

if __name__=='__main__':
    (ROOT/'script/sql/mysql/migrations/V309__media_assisted_source.sql').write_text(generate(),encoding='utf-8')
