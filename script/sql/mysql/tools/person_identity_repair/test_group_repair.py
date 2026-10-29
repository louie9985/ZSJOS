# -*- coding: utf-8 -*-
import datetime
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from test_calendar_notifications import query,table
from repair import helpers,fetch
from group_repair import prepare_group,apply_group,restore_group,capture
from prepare import prepare_automatic

db='historical_student_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
query(None,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
query(db,''.join(table(t) for t in ['zsjos_person','zsjos_person_contact_claim','zsjos_person_contact_alias','zsjos_person_merge_event'])+
      "CREATE TABLE zsjos_lead(id bigint PRIMARY KEY,tenant_id bigint,person_id bigint,identity_origin_person_id bigint NULL,status varchar(32),owner_user_id bigint,deleted bit DEFAULT b'0');"
      "CREATE TABLE zsjos_order(id bigint PRIMARY KEY,tenant_id bigint,person_id bigint,identity_origin_person_id bigint NULL,student_name varchar(100),creator varchar(64),deleted bit DEFAULT b'0');"
      "CREATE TABLE zsjos_opportunity(id bigint PRIMARY KEY,tenant_id bigint,person_id bigint,lead_id bigint,status varchar(32),owner_user_id bigint);"
      "INSERT INTO zsjos_person(id,person_no,name,mobile,wechat_id,identity_status,first_seen_at,last_seen_at,tenant_id) VALUES(1,'TEST1','测试甲','13800000001','common','lead',NOW(),NOW(),1),(2,'TEST2','测试乙','13800000002','common','lead',NOW(),NOW(),1),(3,'TEST3','测试丙','13800000003','common','lead',NOW(),NOW(),1);"
      "INSERT INTO zsjos_lead(id,tenant_id,person_id,status,owner_user_id) VALUES(10,1,1,'closed',100),(20,1,2,'valid',200),(30,1,3,'closed',300);"
      "INSERT INTO zsjos_opportunity VALUES(10,1,1,10,'lost',100),(20,1,2,20,'following',200),(30,1,3,30,'won',300);")
c=helpers.connect(db)
try:
    plan=prepare_automatic(c,db,[1,2,3]);assert plan['rule']['targetPersonId']==2
    rehearsal=apply_group(c,plan)
    assert not rehearsal['committed'] and rehearsal['aliasCount']==6
    with c.cursor() as q:state,_=capture(q,[1,2,3],plan['key']);assert helpers.digest(state)==plan['fingerprint']
    c.rollback()
    try:apply_group(c,plan,execute=True,fail_after='zsjos_opportunity')
    except ValueError as e:assert str(e)=='Injected group failure'
    else:raise AssertionError('Expected transactional failure')
    result=apply_group(c,plan,execute=True)
    assert apply_group(c,plan,execute=True)['replay']
    with c.cursor() as q:
        assert fetch(q,'SELECT COUNT(*) n FROM zsjos_lead WHERE person_id=2')[0]['n']==3
        assert fetch(q,"SELECT COUNT(*) n FROM zsjos_person_contact_alias WHERE person_id=2 AND contact_type='mobile'")[0]['n']==3
        assert fetch(q,'SELECT mobile FROM zsjos_person WHERE id=2')[0]['mobile']=='13800000002'
        assert fetch(q,"SELECT p.id FROM zsjos_person p WHERE p.tenant_id=1 AND p.deleted=0 AND EXISTS(SELECT 1 FROM zsjos_person_contact_alias a WHERE a.tenant_id=p.tenant_id AND a.person_id=p.id AND a.deleted=0 AND a.contact_type='mobile' AND a.contact_value='13800000001')")==[{'id':2}]
        assert not fetch(q,"SELECT p.id FROM zsjos_person p WHERE p.tenant_id=2 AND p.deleted=0 AND EXISTS(SELECT 1 FROM zsjos_person_contact_alias a WHERE a.tenant_id=p.tenant_id AND a.person_id=p.id AND a.deleted=0 AND a.contact_value='13800000001')")
        assert fetch(q,'SELECT owner_user_id FROM zsjos_opportunity ORDER BY id')==[{'owner_user_id':100},{'owner_user_id':200},{'owner_user_id':300}]
        q.execute("UPDATE zsjos_lead SET status='suspended' WHERE id=20")
    c.commit()
    try:restore_group(c,plan,result)
    except ValueError as e:assert str(e)=='After-image changed; recovery refused'
    else:raise AssertionError('Recovery must not overwrite later edits')
    with c.cursor() as q:q.execute("UPDATE zsjos_lead SET status='valid' WHERE id=20")
    c.commit()
    assert restore_group(c,plan,result,execute=True)['restored']
    print('atomic three-source merge, contact retention, rollback, replay, owner/state preservation, guarded recovery PASS',db)
finally:c.close()
