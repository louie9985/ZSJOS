# -*- coding: utf-8 -*-
"""Runs V309 on uniquely owned MySQL copies, never mutates source business rows."""
import datetime
import json
import uuid
import os
import pymysql
from order_history_repair import connect,ident
from media_assisted_source_repair import run,audit,KEY

TABLES=['zsjos_lead','zsjos_order','zsjos_order_approval_round','zsjos_performance_attribution',
        'zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version','system_users','zsjos_lead_assignment_history','system_dept']
BUSINESS=TABLES[:4]

def clone(empty=False):
    name='v309_test_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')+'_'+uuid.uuid4().hex[:6]
    c=connect()
    with c.cursor() as q:
        q.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        for table in TABLES:
            dest=ident(name)+'.'+ident(table)
            q.execute('CREATE TABLE '+dest+' LIKE '+ident(table))
            if table!='zsjos_data_repair_backup' and (not empty or 'schema_version' in table):
                q.execute('SHOW COLUMNS FROM '+ident(table))
                cols=','.join(ident(r['Field']) for r in q.fetchall() if 'VIRTUAL GENERATED' not in r['Extra'] and 'STORED GENERATED' not in r['Extra'])
                suffix=" WHERE version<>'V309'" if 'schema_version' in table else ''
                q.execute('INSERT INTO '+dest+' ('+cols+') SELECT '+cols+' FROM '+ident(table)+suffix)
        if not empty:
            q.execute('INSERT INTO '+ident(name)+".zsjos_data_repair_backup SELECT * FROM zsjos_data_repair_backup WHERE repair_key='confirmed-provider-v308'")
            # A developer may rerun verification after local application; reconstruct prerequisites
            # only in this isolated copy using the original repair evidence.
            q.execute("SELECT table_name,row_id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND column_name='__before__'",(KEY,))
            for r in q.fetchall():
                fields=json.loads(r['old_value'])
                vals=[json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for v in fields.values()]
                q.execute('UPDATE '+ident(name)+'.'+ident(r['table_name'])+' SET '+','.join(ident(k)+'=%s' for k in fields)+' WHERE id=%s',tuple(vals)+(r['row_id'],))
    c.close()
    result = connect(database=name)
    fixture = os.environ.get('ZSJOS_V309_PREREQUISITE_FIXTURE')
    if fixture and not empty:
        # A retained prior controlled run contains exact reviewed r2 before-images. Only
        # reconstruct the NEW isolated copy; never regenerate or weaken the manifest.
        from source_review_revision import MANIFEST_PATH, KEY as revision_key
        manifest=json.loads(MANIFEST_PATH.read_text(encoding='utf-8'))
        evidence=connect(database=fixture)
        with evidence.cursor() as q:
            q.execute("SELECT table_name,row_id,old_value,SHA2(CAST(CAST(old_value AS JSON) AS CHAR CHARACTER SET utf8mb4),256) sha FROM zsjos_data_repair_backup WHERE repair_key=%s AND column_name='__before__'",(revision_key,))
            images={(r['table_name'],r['row_id']):r for r in q.fetchall()}
        evidence.close()
        with result.cursor() as q:
            for r in manifest['rows']:
                saved=images[(r['table'],r['id'])]
                if saved['sha']!=r['sha']:raise ValueError('Retained r2 prerequisite does not match reviewed hash')
                fields=json.loads(saved['old_value'])
                values=[json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for v in fields.values()]
                q.execute('UPDATE '+ident(r['table'])+' SET '+','.join(ident(k)+'=%s' for k in fields)+' WHERE id=%s',(*values,r['id']))
    return name,result

def rows(c):
    out={}
    with c.cursor() as q:
        for table in BUSINESS:
            q.execute('SELECT * FROM '+ident(table)+' ORDER BY id')
            # New nullable schema field is not a business-row change.
            out[table]={r['id']:{k:v for k,v in r.items() if k!='submission_source_type' or v is not None} for r in q.fetchall()}
    return out

def backups(c):
    with c.cursor() as q:
        q.execute('SELECT * FROM zsjos_data_repair_backup WHERE repair_key=%s ORDER BY id',(KEY,));return q.fetchall()

def fail(c):
    try:run(c)
    except pymysql.MySQLError:return
    raise AssertionError('Expected rejection')

def restore(c,limit=None):
    with c.cursor() as q:
        q.execute("SELECT * FROM zsjos_data_repair_backup WHERE repair_key=%s AND column_name='__before__' ORDER BY table_name,row_id",(KEY,))
        for r in q.fetchall()[:limit]:
            fields=json.loads(r['old_value'])
            vals=[json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for v in fields.values()]
            q.execute('UPDATE '+ident(r['table_name'])+' SET '+','.join(ident(k)+'=%s' for k in fields)+' WHERE id=%s',tuple(vals)+(r['row_id'],))

def main():
    # V309 now includes both original source correction and the confirmed revision.
    from test_source_review_revision import main as verify_revision
    verify_revision()


if __name__=='__main__':main()
