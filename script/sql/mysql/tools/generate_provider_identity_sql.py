# -*- coding: utf-8 -*-
"""One generator for the independently executable repair and the V309 development chain."""
import json
from provider_identity_repair import KEY, MANIFEST, SQL, FIELDS, image, literal, digest


def body():
    m = json.loads(MANIFEST.read_text(encoding='utf-8'))
    manifest_sha = digest(m)
    inserts = ',\n'.join(f"('{r['table']}',{r['id']},'{r['sha']}',CAST({literal(r['patch'])} AS JSON),CAST({literal(r['provenance'])} AS JSON))" for r in m['rows'])
    lead_ids = ','.join(str(r['id']) for r in m['rows'] if r['table'] == 'zsjos_lead')
    text = f"""
 provider_identity_block: BEGIN
 DECLARE pir_count INT;
 DECLARE pir_saved INT;
 DECLARE pir_now DATETIME(6) DEFAULT NOW();
 DECLARE pir_path VARCHAR(200);
 DECLARE pir_keys JSON;
 DECLARE pir_index INT DEFAULT 0;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V308') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V308') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair requires both V308 ledgers'; END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_order','zsjos_order_approval_round','zsjos_data_repair_backup') AND engine='InnoDB')<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair requires transactional tables'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair requires LONGTEXT evidence'; END IF;
 DROP TEMPORARY TABLE IF EXISTS pir_plan;
 CREATE TEMPORARY TABLE pir_plan(table_name VARCHAR(64),row_id BIGINT,expected_sha CHAR(64),patch JSON,provenance JSON,
  current_image JSON,before_image JSON,after_image JSON,verified_after JSON,PRIMARY KEY(table_name,row_id)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 INSERT INTO pir_plan(table_name,row_id,expected_sha,patch,provenance) VALUES {inserts};
 SELECT COUNT(*) INTO pir_count FROM zsjos_lead WHERE tenant_id=1 AND id IN ({lead_ids}) FOR UPDATE;
 SELECT COUNT(*) INTO pir_saved FROM zsjos_data_repair_backup WHERE repair_key='{KEY}';
 IF pir_count=0 THEN
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE tenant_id=1) OR pir_saved<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair targets missing'; END IF;
  LEAVE provider_identity_block;
 END IF;
 IF pir_count<>16 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair partial manifest'; END IF;
 IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id AND o.tenant_id=r.tenant_id
  WHERE r.tenant_id=1 AND r.deleted=0 AND o.deleted=0 AND o.lead_id IN ({lead_ids}) AND JSON_TYPE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile'))='OBJECT'
  AND NOT EXISTS(SELECT 1 FROM pir_plan p WHERE p.table_name='zsjos_order_approval_round' AND p.row_id=r.id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair approval scope changed'; END IF;
"""
    for table in FIELDS:
        text += f" SELECT COUNT(*) INTO pir_count FROM {table} x JOIN pir_plan p ON p.table_name='{table}' AND p.row_id=x.id WHERE x.tenant_id=1 FOR UPDATE;\n"
        text += f" UPDATE pir_plan p JOIN {table} x ON p.table_name='{table}' AND p.row_id=x.id AND x.tenant_id=1 SET p.current_image={image(table)};\n"
    text += f"""
 IF EXISTS(SELECT 1 FROM pir_plan WHERE current_image IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair protected target missing'; END IF;
 IF pir_saved=0 THEN
  IF EXISTS(SELECT 1 FROM pir_plan WHERE SHA2(CAST(current_image AS CHAR CHARACTER SET utf8mb4),256)<>expected_sha) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair reviewed before image changed'; END IF;
"""
    # Match current organization at first application, not during replay of frozen repair evidence.
    for r in m['rows']:
        if r['table'] == 'zsjos_lead' and r['provenance']['leadNo'] not in ('LD202609180061', 'LD202609200039'):
            text += f""" IF (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='confirmed-provider-v308'
 AND table_name='zsjos_lead' AND row_id={r['id']} AND column_name='__after__'
 AND JSON_EXTRACT(CAST(old_value AS JSON),'$.provider_owner_id')=CAST({r['patch']['provider_owner_id']} AS JSON))<>1 THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair V308 confirmation missing'; END IF;
"""
    for uid, org in m['providers'].items():
        expr = "JSON_OBJECT('id',u.id,'dept_id',d.id,'dept_name',d.name,'leader_user_id',d.leader_user_id,'supervisor_name',s.nickname,'identity_sha',SHA2(CONCAT(u.id,':',COALESCE(u.mobile,'')),256))"
        text += f""" IF NOT EXISTS(SELECT 1 FROM system_users u LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=1 AND d.deleted=0
 LEFT JOIN system_users s ON s.id=d.leader_user_id AND s.tenant_id=1 AND s.deleted=0
 WHERE u.tenant_id=1 AND u.deleted=0 AND u.id={uid} AND {expr}=CAST({literal(org)} AS JSON)
 AND (SELECT COUNT(*) FROM system_users same WHERE same.tenant_id=1 AND same.deleted=0 AND same.mobile=u.mobile)=1) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair current organization changed'; END IF;
"""
    text += """ UPDATE pir_plan SET before_image=current_image,after_image=JSON_SET(current_image,
 '$.version',CAST(current_image->>'$.version' AS UNSIGNED)+1,'$.updater','provider-identity-20261011',
 '$.update_time',DATE_FORMAT(pir_now,'%Y-%m-%d %H:%i:%s.%f'));
"""
    # Apply only known patch keys; JSON_SET preserves unrelated and null-valued snapshot keys.
    paths = sorted({k for r in m['rows'] for k in r['patch']})
    for key in paths:
        text += f" UPDATE pir_plan SET after_image=JSON_SET(after_image,'$.{key}',JSON_EXTRACT(patch,'$.\"{key}\"')) WHERE JSON_CONTAINS_PATH(patch,'one','$.\"{key}\"');\n"
    text += f"""
 ELSE
  IF pir_saved<>{len(m['rows'])*3+1} OR
   (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='{KEY}' AND table_name='__repair__' AND row_id=0 AND column_name='__manifest__' AND old_value='{manifest_sha}')<>1
   OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='{KEY}' GROUP BY table_name,row_id,column_name HAVING COUNT(*)<>1)
   OR EXISTS(SELECT 1 FROM pir_plan p WHERE (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name=p.table_name AND b.row_id=p.row_id AND b.column_name IN ('__before__','__after__','__provenance__'))<>3) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair incomplete or different evidence'; END IF;
  UPDATE pir_plan p JOIN zsjos_data_repair_backup b ON b.repair_key='{KEY}' AND b.table_name=p.table_name AND b.row_id=p.row_id AND b.column_name='__before__'
   JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
   SET p.before_image=CAST(b.old_value AS JSON),p.after_image=CAST(a.old_value AS JSON);
  IF EXISTS(SELECT 1 FROM pir_plan p JOIN zsjos_data_repair_backup b ON b.repair_key='{KEY}' AND b.table_name=p.table_name AND b.row_id=p.row_id AND b.column_name='__provenance__'
   WHERE p.provenance<>CAST(b.old_value AS JSON) OR SHA2(CAST(p.before_image AS CHAR CHARACTER SET utf8mb4),256)<>p.expected_sha) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair evidence does not match manifest'; END IF;
 END IF;
 UPDATE pir_plan SET verified_after=JSON_SET(before_image,'$.version',CAST(before_image->>'$.version' AS UNSIGNED)+1,
  '$.updater','provider-identity-20261011','$.update_time',after_image->'$.update_time');
"""
    for key in paths:
        text += f" UPDATE pir_plan SET verified_after=JSON_SET(verified_after,'$.{key}',JSON_EXTRACT(patch,'$.\"{key}\"')) WHERE JSON_CONTAINS_PATH(patch,'one','$.\"{key}\"');\n"
    text += f"""
 IF EXISTS(SELECT 1 FROM pir_plan WHERE NOT(verified_after <=> after_image) OR JSON_TYPE(after_image->'$.update_time')<>'STRING') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair after evidence differs from approved patch'; END IF;
 IF EXISTS(SELECT 1 FROM pir_plan WHERE current_image<>before_image AND current_image<>after_image) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair later edit conflicts'; END IF;
"""
    for column, expr in [('__before__', 'before_image'), ('__after__', 'after_image'), ('__provenance__', 'provenance')]:
        text += f" INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT '{KEY}',table_name,row_id,'{column}',CAST({expr} AS CHAR CHARACTER SET utf8mb4) FROM pir_plan WHERE pir_saved=0;\n"
    for table in FIELDS:
        cols = sorted({k.split('.')[0] for r in m['rows'] if r['table'] == table for k in r['patch']} | {'version','updater','update_time'})
        assigns = ','.join(f"x.`{col}`=" + (f"p.after_image->'$.{col}'" if col == 'order_snapshot' else f"CAST(p.after_image->>'$.{col}' AS UNSIGNED)" if col == 'source_provider_recorded' else f"NULLIF(p.after_image->>'$.{col}','null')") for col in cols)
        text += f" UPDATE {table} x JOIN pir_plan p ON p.table_name='{table}' AND p.row_id=x.id SET {assigns} WHERE x.tenant_id=1 AND p.current_image<>p.after_image;\n"
        text += f" IF EXISTS(SELECT 1 FROM {table} x JOIN pir_plan p ON p.table_name='{table}' AND p.row_id=x.id WHERE {image(table)}<>p.after_image) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Provider repair postcondition failed: {table}'; END IF;\n"
    text += f""" INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
 SELECT '{KEY}','__repair__',0,'__manifest__','{manifest_sha}' WHERE pir_saved=0;
 DROP TEMPORARY TABLE pir_plan;
 END;
"""
    return text


def generate():
    return """-- UTF-8. Independently apply ONLY the approved 16 Leads after both V308 ledgers.
-- Shared database apply requires separate target approval. No deletes, grants, orders or sales attribution updates.
-- Organization is the reviewed current provider organization, not historical organization.
-- Full V309 calls the same body; this entry does not mark V309 complete or run its other repairs.
-- Before/after/provenance and completion receipt commit together; later edits/partial evidence fail closed.
-- Repeat is no-op; partial restoration accepts only exact saved before/after images. Restore needs separate review.
-- DDL creates only this helper procedure; transaction rollback protects DML, not procedure DDL.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS zsjos_provider_identity_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_provider_identity_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
 START TRANSACTION;
""" + body() + """
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_provider_identity_apply();
DROP PROCEDURE zsjos_provider_identity_apply;
"""


if __name__ == '__main__':
    SQL.write_text(generate(), encoding='utf-8')
