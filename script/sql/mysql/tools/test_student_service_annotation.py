# -*- coding: utf-8 -*-
"""Retained isolated MySQL fixtures. No application data or real grants are modified."""
import datetime
from test_calendar_notifications import SQL, query, table

MIGRATION = (SQL / 'migrations/V352__student_service_annotation.sql').read_text(encoding='utf-8')
PREFIX = 'annotation_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
V351 = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V351','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V351','fixture','fixture','fixture');"
PARENT = "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES (73020,'学员管理','zsjos:student:query-my',2,6735,'my-students');"

def versions(db):
    return query(db,"SELECT * FROM zsjos_schema_version WHERE version='V352'; SELECT * FROM zsjos_module_schema_version WHERE version='V352';")[0]

def schema(db):
    return query(db,"SELECT column_name,column_type,is_nullable,IFNULL(column_default,'NULL'),extra,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_service_annotation' ORDER BY ordinal_position; SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_service_annotation' ORDER BY index_name,seq_in_index;")[0]

def run():
    for case in ['initial','prerequisite','parent_missing','ledger_failure','marker_only','menu_conflict']:
        db=PREFIX+'_'+case
        query(None,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup=''.join(table(n) for n in ['system_menu','system_role_menu','zsjos_schema_version','zsjos_module_schema_version'])
        setup+='CREATE TABLE zsjos_service_relation(id BIGINT PRIMARY KEY);'
        if case!='prerequisite': setup+=V351
        if case!='parent_missing': setup+=PARENT
        setup+="INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (101,73020,1);"
        if case=='marker_only':
            setup+="INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V352','existing','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V352','existing','preserve','fixture');"
        if case=='ledger_failure': setup+="CREATE TRIGGER annotation_ledger_failure BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        if case=='menu_conflict': setup+="INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (90001,'冲突项','zsjos:student-service-annotation:read',3,0);"
        query(db,setup)
        grants=query(db,'SELECT * FROM system_role_menu ORDER BY id;')[0]
        before=versions(db)
        _,error=query(db,MIGRATION,force=True)
        if case in ['prerequisite','parent_missing','ledger_failure','menu_conflict']:
            assert 'ERROR' in error,(case,error)
            assert versions(db)==before
            if case=='prerequisite': query(db,V351)
            if case=='parent_missing': query(db,PARENT)
            if case=='ledger_failure': query(db,'DROP TRIGGER annotation_ledger_failure;')
            if case=='menu_conflict': query(db,'UPDATE system_menu SET parent_id=73020 WHERE id=90001;')
            query(db,MIGRATION)
        else: assert not error,error
        assert len(versions(db).splitlines())==2
        if case=='marker_only': assert versions(db)==before
        before=versions(db); structure=schema(db)
        query(db,MIGRATION)
        assert versions(db)==before and schema(db)==structure
        assert query(db,'SELECT * FROM system_role_menu ORDER BY id;')[0]==grants
        labels=query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:student-service-annotation:update';")[0]
        assert labels=='修改学员批注'.encode().hex().upper()
        query(db,"INSERT INTO zsjos_student_service_annotation(service_relation_id,proxy_student,proxy_student_label,agent_name,service_needed,service_needed_label,version,tenant_id) VALUES (11,'true','是','测试代理人','false','否',1,1),(12,NULL,NULL,NULL,NULL,NULL,0,1);")
        before_data=query(db,'SELECT * FROM zsjos_student_service_annotation ORDER BY id;')[0]
        query(db,MIGRATION)
        assert query(db,'SELECT * FROM zsjos_student_service_annotation ORDER BY id;')[0]==before_data
        assert query(db,'SELECT HEX(agent_name) FROM zsjos_student_service_annotation WHERE service_relation_id=11;')[0]=='测试代理人'.encode().hex().upper()
        _,error=query(db,'INSERT INTO zsjos_student_service_annotation(service_relation_id,tenant_id) VALUES (11,1);',force=True)
        assert 'Duplicate' in error
        assert query(db,'UPDATE zsjos_student_service_annotation SET version=2 WHERE service_relation_id=11 AND version=0; SELECT ROW_COUNT();')[0]=='0'
        print('PASS',case,'replay/recovery/UTF8/grants/per-service uniqueness/version')
    print('Retained isolated prefix:',PREFIX)

if __name__=='__main__': run()
