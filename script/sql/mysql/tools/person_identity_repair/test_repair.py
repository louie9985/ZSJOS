# -*- coding: utf-8 -*-
"""Synthetic local fixture: no actual customer merge or archive."""
import datetime
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from test_calendar_notifications import query,table
from repair import helpers,repair,fetch
from prepare import prepare,prepare_automatic

db='historical_student_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
query(None,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
query(db,table('zsjos_person')+table('zsjos_person_contact_claim')+table('zsjos_person_merge_event')+
      "CREATE TABLE zsjos_lead(id bigint PRIMARY KEY,tenant_id bigint,person_id bigint,identity_origin_person_id bigint NULL,status varchar(32),owner_user_id bigint,deleted bit DEFAULT b'0');"
      "CREATE TABLE zsjos_order(id bigint PRIMARY KEY,tenant_id bigint,person_id bigint,identity_origin_person_id bigint NULL,status varchar(32),submitter_user_id bigint,student_name varchar(100) DEFAULT '合并测试',creator varchar(64) DEFAULT 'fixture',deleted bit DEFAULT b'0');"
      "INSERT INTO zsjos_person(id,person_no,name,mobile,identity_status,first_seen_at,last_seen_at,tenant_id) VALUES(1,'TEST1','合并测试','13800000001','lead',NOW(),NOW(),1),(2,'TEST2','合并测试','13800000001','student',NOW(),NOW(),1);"
      "INSERT INTO zsjos_lead(id,tenant_id,person_id,identity_origin_person_id,status,owner_user_id) VALUES(10,1,1,NULL,'valid',100),(20,1,2,NULL,'closed',200);"
      "INSERT INTO zsjos_order(id,tenant_id,person_id,identity_origin_person_id,status,submitter_user_id) VALUES(30,1,1,NULL,'effective',100),(40,1,2,NULL,'effective',200);"
      "INSERT INTO zsjos_person_contact_claim(contact_value,person_id,tenant_id) VALUES('13800000001',2,1);")
conn=helpers.connect(db)
try:
    decision=prepare_automatic(conn,db,[1,2])
    assert decision['automaticIdentity']['identityDecision']=='AUTO_MERGE'
    assert not repair(conn,decision)['committed']
    with conn.cursor() as q:
        assert fetch(q,'SELECT person_id FROM zsjos_lead WHERE id=20')[0]['person_id']==2
    conn.rollback()
    try: repair(conn,decision,execute=True,inject_failure=True)
    except ValueError as e: assert str(e)=='Injected rollback verification'
    else: raise AssertionError('Failure was not injected')
    assert repair(conn,decision,execute=True)['committed']
    assert repair(conn,decision,execute=True)['replay']
    with conn.cursor() as q:
        assert fetch(q,'SELECT COUNT(*) n FROM zsjos_lead WHERE person_id=1')[0]['n']==2
        rows=fetch(q,'SELECT id,identity_origin_person_id,owner_user_id FROM zsjos_lead ORDER BY id')
        assert rows==[{'id':10,'identity_origin_person_id':1,'owner_user_id':100},{'id':20,'identity_origin_person_id':2,'owner_user_id':200}]
        assert fetch(q,'SELECT HEX(name) h FROM zsjos_person WHERE id=1')[0]['h']=='合并测试'.encode().hex().upper()
    conn.rollback()
    print('dry-run rollback, injected failure rollback, apply, replay, provenance and owner preservation, UTF-8 PASS',db)
finally:conn.close()

# No-order rule must choose the non-closed Lead's Person and reject a stale status decision.
second='historical_student_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
query(None,f'CREATE DATABASE `{second}` CHARACTER SET utf8mb4;')
query(second,table('zsjos_person')+table('zsjos_person_contact_claim')+table('zsjos_person_merge_event')+
      f'CREATE TABLE zsjos_lead LIKE `{db}`.zsjos_lead; CREATE TABLE zsjos_order LIKE `{db}`.zsjos_order;'+
      "INSERT INTO zsjos_person(id,person_no,name,mobile,identity_status,first_seen_at,last_seen_at,tenant_id) VALUES(1,'OLD','历史姓名','13800000001','lead',NOW(),NOW(),1),(2,'CURRENT','当前姓名','13800000001','lead',NOW(),NOW(),1);"
      "INSERT INTO zsjos_lead(id,tenant_id,person_id,status,owner_user_id) VALUES(10,1,1,'closed',100),(20,1,2,'valid',200);")
conn=helpers.connect(second)
try:
    decision=prepare_automatic(conn,second,[1,2])
    assert decision['targetPersonId']==2 and decision['automaticIdentity']['primaryLeadId']==20
    with conn.cursor() as cur:cur.execute("UPDATE zsjos_lead SET status='closed' WHERE id=20")
    conn.commit()
    try:repair(conn,decision)
    except ValueError as e:assert str(e)=='Automatic identity evidence changed; prepare again'
    else:raise AssertionError('Changed primary Lead status was accepted')
    with conn.cursor() as cur:cur.execute("UPDATE zsjos_lead SET status='valid' WHERE id=20")
    conn.commit()
    assert repair(conn,decision,execute=True)['committed']
    with conn.cursor() as cur:
        rows=fetch(cur,'SELECT person_id,status,owner_user_id FROM zsjos_lead ORDER BY id')
        assert rows==[{'person_id':2,'status':'closed','owner_user_id':100},{'person_id':2,'status':'valid','owner_user_id':200}]
    conn.rollback()
    print('sole non-closed Lead target, stale status rejection, source Lead history/owners preserved PASS',second)
finally:conn.close()
