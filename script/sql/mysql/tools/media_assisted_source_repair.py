# -*- coding: utf-8 -*-
"""V309 local, nondeleting source correction. audit is read-only; apply requires target authorization."""
import argparse
import hashlib
from pathlib import Path
from order_history_repair import connect, statements
from generate_media_assisted_source_sql import KEY, MANIFEST

SQL=Path(__file__).resolve().parents[4]/'script/sql/mysql/migrations/V309__media_assisted_source.sql'

def run(c):
    with c.cursor() as q:
        q.execute('SET @zsjos_v309_file_checksum=%s',(hashlib.sha256(SQL.read_bytes()).hexdigest(),))
        for s in statements(SQL.read_text(encoding='utf-8')): q.execute(s)

def audit(c):
    with c.cursor() as q:
        q.execute('SELECT source_type,COUNT(*) n,SUM(source_user_id=source_provider_user_id) matching_submitter FROM zsjos_lead WHERE tenant_id=1 AND lead_no IN ('+','.join(['%s']*len(MANIFEST))+') GROUP BY source_type',tuple(n for n,_ in MANIFEST))
        result={'leads':q.fetchall()}
        q.execute('SELECT table_name,COUNT(*) n FROM zsjos_data_repair_backup WHERE repair_key=%s GROUP BY table_name',(KEY,))
        result['evidence']=q.fetchall()
        q.execute("SELECT version FROM zsjos_schema_version WHERE version='V309'");result['ledger']=q.fetchall()
        return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['audit','apply']);p.add_argument('--database',default='ruoyi-vue-pro');a=p.parse_args()
    c=connect(database=a.database)
    try:
        if a.mode=='apply':run(c)
        print(audit(c))
    finally:c.close()
