# -*- coding: utf-8 -*-
"""Portable V309 development revision, restricted to the user-confirmed source manifest."""
import json
from pathlib import Path

KEY = 'lead-source-confirmed-v309-r2'
MANIFEST_PATH = Path(__file__).with_name('source_review_manifest.json')
CONTRIBUTION = ['contribution_user_id_snapshot','contribution_user_name_snapshot',
                'contribution_supervisor_user_id_snapshot','contribution_supervisor_name_snapshot',
                'contribution_dept_id_snapshot','contribution_dept_name_snapshot']
LEAD_COLUMNS = ['lead_no','tenant_id','deleted','source_type','submission_source_type','source_user_id','source_dept_id',
                'source_provider_user_id','source_provider_recorded','provider_owner_type','provider_owner_id',
                'provider_owner_name_snapshot',*CONTRIBUTION,'counted_at','owner_user_id','owner_identity',
                'version','updater','update_time']
SPECS = {
    'zsjos_lead': ('l', LEAD_COLUMNS),
    'zsjos_order_approval_round': ('r', ['tenant_id','deleted','order_id','order_snapshot','version','updater','update_time']),
    'zsjos_performance_attribution': ('p', ['tenant_id','deleted','lead_id','fact_type','fact_id','source_group',
                                         'user_id','dept_id','center_id','org_source','updater','update_time']),
}


def image(alias, columns):
    def value(k):
        if k in ('counted_at','update_time'): return f"DATE_FORMAT({alias}.{k},'%Y-%m-%d %H:%i:%s')"
        if k in ('deleted','source_provider_recorded'): return f'CAST({alias}.{k} AS UNSIGNED)'
        if k == 'order_snapshot': return f'CAST({alias}.{k} AS JSON)'
        return f'{alias}.{k}'
    return 'JSON_OBJECT('+','.join("'"+k+"',"+value(k) for k in columns)+')'


def generate():
    manifest=json.loads(MANIFEST_PATH.read_text(encoding='utf-8'))
    leads=manifest['leads']; expected=manifest['rows']
    assert len(leads)==83 and sum(r['provider'] is None for r in leads)==81
    sql=[f"""
 -- V309 revision 2: 83 individually confirmed Leads; no current-role/name inference.
 -- Separate evidence key preserves original V309 before/after records. Runs inside the same transaction.
 DROP TEMPORARY TABLE IF EXISTS v309_r2_manifest;
 CREATE TEMPORARY TABLE v309_r2_manifest(lead_no VARCHAR(64) PRIMARY KEY,provider_id BIGINT NULL,entry_type VARCHAR(40)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 INSERT INTO v309_r2_manifest VALUES
""" + ',\n'.join("('%s',%s,'%s')"%(r['lead_no'],r['provider'] if r['provider'] is not None else 'NULL',r['entry']) for r in leads)+';\n']
    sql.append("""
 SELECT COUNT(*) INTO v_count FROM zsjos_lead l JOIN v309_r2_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 FOR UPDATE;
 IF v_count NOT IN (0,83) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 partial confirmed manifest'; END IF;
 IF v_count=83 AND (NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=1 AND id=19 AND BINARY username='MoCaiQiao') OR NOT EXISTS(SELECT 1 FROM system_users WHERE tenant_id=1 AND id=527 AND BINARY username='LiangYing')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 confirmed employee identity missing'; END IF;
 IF v_count=0 AND EXISTS(SELECT 1 FROM zsjos_lead WHERE tenant_id=1 AND deleted=0) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 requires reviewed local dataset or empty fresh database'; END IF;
 DROP TEMPORARY TABLE IF EXISTS v309_r2_expected;
 CREATE TEMPORARY TABLE v309_r2_expected(table_name VARCHAR(64),row_id BIGINT,before_sha CHAR(64),PRIMARY KEY(table_name,row_id));
 INSERT INTO v309_r2_expected VALUES
"""+',\n'.join("('%s',%d,'%s')"%(r['table'],r['id'],r['sha']) for r in expected)+';\n')
    sql.append(' DROP TEMPORARY TABLE IF EXISTS v309_r2_images;\n CREATE TEMPORARY TABLE v309_r2_images(table_name VARCHAR(64),row_id BIGINT,current_image JSON,before_image JSON,after_image JSON,PRIMARY KEY(table_name,row_id));\n')
    lead_img=image('l',LEAD_COLUMNS)
    changes=["'$.source_type',IF(m.provider_id IS NULL,m.entry_type,'internal_new_media')",
             "'$.submission_source_type',IF(m.entry_type='internal_new_media',l.submission_source_type,COALESCE(l.submission_source_type,m.entry_type))",
             "'$.source_user_id',IF(m.provider_id IS NULL,l.source_user_id,m.provider_id)",
             "'$.source_provider_recorded',1","'$.source_provider_user_id',m.provider_id",
             "'$.provider_owner_type',IF(m.provider_id IS NULL,NULL,'system_user')","'$.provider_owner_id',m.provider_id",
             "'$.provider_owner_name_snapshot',IF(m.provider_id IS NULL,NULL,l.provider_owner_name_snapshot)"]
    for col in CONTRIBUTION:
        v='m.provider_id' if col=='contribution_user_id_snapshot' else 'l.provider_owner_name_snapshot' if col=='contribution_user_name_snapshot' else 'l.'+col
        changes.append(f"'$.{col}',IF(m.provider_id IS NULL,NULL,{v})")
    changes += ["'$.version',l.version+1","'$.updater','V309-r2'","'$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')"]
    sql.append(f" INSERT INTO v309_r2_images SELECT 'zsjos_lead',l.id,{lead_img},{lead_img},JSON_SET({lead_img},{','.join(changes)}) FROM zsjos_lead l JOIN v309_r2_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1;\n")
    round_img=image(*SPECS['zsjos_order_approval_round'])
    sql.append(f""" INSERT INTO v309_r2_images SELECT 'zsjos_order_approval_round',r.id,{round_img},{round_img},
 JSON_SET({round_img},'$.order_snapshot',JSON_SET(CAST(r.order_snapshot AS JSON),
 '$.leadProfile.sourceType',IF(m.provider_id IS NULL,m.entry_type,'internal_new_media'),
 '$.leadProfile.sourceLabel',IF(m.provider_id IS NOT NULL,'新媒体提交',IF(m.entry_type='sales_self_sourced','销售自拓录','教务自拓录')),
 '$.leadProfile.sourceSubjectType','ADMIN','$.leadProfile.sourceSubjectId',IF(m.provider_id IS NULL,l.source_user_id,m.provider_id),
 '$.leadProfile.sourceUserName',IF(m.provider_id IS NULL,NULL,l.provider_owner_name_snapshot)),
 '$.version',r.version+1,'$.updater','V309-r2','$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s'))
 FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id AND o.tenant_id=r.tenant_id
 JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=o.tenant_id JOIN v309_r2_manifest m ON m.lead_no=l.lead_no
 WHERE l.tenant_id=1 AND o.deleted=0 AND r.deleted=0 AND JSON_TYPE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile'))='OBJECT';
""")
    perf_img=image(*SPECS['zsjos_performance_attribution'])
    sql.append(f""" INSERT INTO v309_r2_images SELECT 'zsjos_performance_attribution',p.id,{perf_img},{perf_img},
 IF(p.source_group=IF(m.provider_id IS NULL,'self','inbound'),{perf_img},JSON_SET({perf_img},'$.source_group',IF(m.provider_id IS NULL,'self','inbound'),'$.updater','V309-r2','$.update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')))
 FROM zsjos_performance_attribution p JOIN zsjos_lead l ON l.id=p.lead_id AND l.tenant_id=p.tenant_id
 JOIN v309_r2_manifest m ON m.lead_no=l.lead_no WHERE l.tenant_id=1 AND p.deleted=0
 AND NOT(p.fact_type='ORDER' AND EXISTS(SELECT 1 FROM zsjos_order o WHERE o.id=p.fact_id AND o.tenant_id=p.tenant_id AND o.order_type='repurchase'));
""")
    sql.append(f"""
 IF v_count=83 AND (SELECT COUNT(*) FROM v309_r2_images)<>{len(expected)} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 related records changed'; END IF;
 IF EXISTS(SELECT 1 FROM v309_r2_images i LEFT JOIN v309_r2_expected e ON e.table_name=i.table_name AND e.row_id=i.row_id WHERE e.row_id IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 unreviewed target'; END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='{KEY}' GROUP BY table_name,row_id,column_name HAVING COUNT(*)<>1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 duplicate evidence'; END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b LEFT JOIN v309_r2_images i ON i.table_name=b.table_name AND i.row_id=b.row_id WHERE b.repair_key='{KEY}' AND (i.row_id IS NULL OR b.column_name NOT IN ('__before__','__after__','__provenance__'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 evidence target missing'; END IF;
 IF EXISTS(SELECT 1 FROM v309_r2_images i WHERE (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id) NOT IN (0,3)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 incomplete evidence'; END IF;
 IF v_count=83 AND EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309' AND (BINARY checksum=BINARY SHA2('{KEY}',256) OR BINARY checksum=BINARY @zsjos_v309_file_checksum)) AND (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='{KEY}')<>(SELECT COUNT(*)*3 FROM v309_r2_images) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 success lacks evidence'; END IF;
 UPDATE v309_r2_images i JOIN zsjos_data_repair_backup b ON b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__before__'
 JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
 SET i.before_image=CAST(b.old_value AS JSON),i.after_image=CAST(a.old_value AS JSON);
 IF EXISTS(SELECT 1 FROM v309_r2_images i JOIN v309_r2_expected e ON e.table_name=i.table_name AND e.row_id=i.row_id WHERE SHA2(CAST(i.before_image AS CHAR CHARACTER SET utf8mb4),256)<>e.before_sha) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 reviewed before image changed'; END IF;
 IF EXISTS(SELECT 1 FROM v309_r2_images WHERE current_image<>before_image AND current_image<>after_image) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 later edit conflicts'; END IF;
""")
    for column,expr in [('__before__','i.before_image'),('__after__','i.after_image'),('__provenance__',"JSON_OBJECT('source','user-confirmed per-business-number source review','reviewed_on','2026-10-10','revision','V309-r2')")]:
        sql.append(f" INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) SELECT '{KEY}',i.table_name,i.row_id,'{column}',CAST({expr} AS CHAR CHARACTER SET utf8mb4) FROM v309_r2_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='{KEY}' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='{column}');\n")
    protected={'lead_no','tenant_id','deleted','counted_at','owner_user_id','owner_identity','order_id','lead_id','fact_type','fact_id','user_id','dept_id','center_id','org_source','source_dept_id'}
    for table,(alias,cols) in SPECS.items():
        assignments=[]
        for col in cols:
            if col in protected:continue
            val=f"NULLIF(i.after_image->>'$.{col}','null')" if col!='order_snapshot' else "i.after_image->'$.order_snapshot'"
            if col=='source_provider_recorded': val=f'CAST({val} AS UNSIGNED)'
            assignments.append(f'{alias}.{col}={val}')
        sql.append(f" UPDATE {table} {alias} JOIN v309_r2_images i ON i.table_name='{table}' AND i.row_id={alias}.id SET {','.join(assignments)} WHERE {alias}.tenant_id=1 AND i.current_image<>i.after_image;\n")
        sql.append(f" IF EXISTS(SELECT 1 FROM {table} {alias} JOIN v309_r2_images i ON i.table_name='{table}' AND i.row_id={alias}.id WHERE NOT({image(alias,cols)} <=> i.after_image)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V309 r2 postcondition failed'; END IF;\n")
    return ''.join(sql)
