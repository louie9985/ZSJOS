# -*- coding: utf-8 -*-
"""Standalone student writer guards against missing claims and cross-field duplicates."""
import datetime
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from test_calendar_notifications import query,table
import seed_student
from repair import helpers,fetch

db='historical_student_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
query(None,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
query(db,table('zsjos_person')+table('zsjos_person_contact_claim')+table('zsjos_person_no_daily_counter')+
      "INSERT INTO zsjos_person(id,person_no,name,mobile,wechat_id,identity_status,first_seen_at,last_seen_at,tenant_id) VALUES(1,'TEST1','测试甲','13800000001',NULL,'lead',NOW(),NOW(),1),(2,'TEST2','测试乙',NULL,'13800000002','lead',NOW(),NOW(),1);")
conn=helpers.connect(db)
now=datetime.datetime.now()
try:
    with conn.cursor() as cur:
        pid,reused=seed_student.ensure_person(cur,'测试甲','13800000001',1,now,now.strftime('%Y%m%d%H%M%S'))
        assert (pid,reused)==(1,True)
        assert fetch(cur,"SELECT person_id FROM zsjos_person_contact_claim WHERE contact_value='13800000001'")[0]['person_id']==1
    conn.rollback()
    with conn.cursor() as cur:
        try: seed_student.ensure_person(cur,'测试乙','13800000002',1,now,now.strftime('%Y%m%d%H%M%S'))
        except seed_student.SeedError: pass
        else: raise AssertionError('Cross-field identity incorrectly reused')
    conn.rollback()
    with conn.cursor() as cur:
        pid,reused=seed_student.ensure_person(cur,'测试乙','13800000003',1,now,now.strftime('%Y%m%d%H%M%S'))
        assert not reused and pid not in (1,2)
        assert fetch(cur,'SELECT person_id FROM zsjos_person_contact_claim WHERE contact_value=%s',('13800000003',))[0]['person_id']==pid
    conn.rollback()
    print('missing claim reuse, cross-field rejection, same-name-only no merge, bound new claim PASS',db)
finally:conn.close()
