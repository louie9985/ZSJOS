# -*- coding: utf-8 -*-
"""Run only against the dedicated ephemeral hrm-manual-verify-20261012 MySQL container."""
from pathlib import Path
import subprocess,re,json,time
ROOT=Path(__file__).resolve().parents[4]
CONTAINER='hrm-manual-verify-20261012'
def sql(text, database=None, check=True):
    args=['docker','exec','-i',CONTAINER,'mysql','-uroot','--default-character-set=utf8mb4','--batch','--skip-column-names']
    if database: args.append(database)
    result=subprocess.run(args,input=('SET NAMES utf8mb4;\n'+text).encode('utf-8'),capture_output=True)
    if check and result.returncode: raise AssertionError(result.stderr.decode('utf-8',errors='replace'))
    return result

def table(source,name):
    match=re.search(r'CREATE TABLE IF NOT EXISTS `'+name+r'` \(.*?;',source,re.S)
    if not match: raise AssertionError('Missing DDL '+name)
    return match.group(0)
base=(ROOT/'script/sql/mysql/00-bootstrap-schema.sql').read_text(encoding='utf-8')
seed=(ROOT/'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
prereq='\n'.join(table(base,n) for n in ['system_notify_template','system_notify_rule','zsjos_module_schema_version','infra_job','system_dict_type','system_dict_data'])+'\n'+table(seed,'zsjos_schema_version')
old=subprocess.check_output(['git','show','c82c8605d86aef5c8f4cd328600bc7c3efc2bf90:script/sql/mysql/schema/hrm.sql'],cwd=ROOT).decode('utf-8')
new=(ROOT/'script/sql/mysql/schema/hrm.sql').read_text(encoding='utf-8')
migration=(ROOT/'script/sql/mysql/migrations/hrm/V003__manual_employee_lifecycle.sql').read_text(encoding='utf-8')
results=[]
run_id=str(time.time_ns())[-10:]
for scenario in ['fresh','upgrade','partial','failure','execution_failure','ledger_failure']:
    db='hrm_manual_'+scenario+'_'+run_id
    sql('CREATE DATABASE IF NOT EXISTS '+db+' CHARACTER SET utf8mb4;')
    sql(prereq+'\n'+(new if scenario=='fresh' else old),db)
    sql((ROOT/'script/sql/mysql/migrations/hrm/V002__hrm_dictionary.sql').read_text(encoding='utf-8'),db)
    if scenario!='fresh':
        sql("INSERT INTO hrm_employee(id,name,mobile,type,status,entry_status,regular_time,leave_time,tenant_id) VALUES (91001,'合成试用员工','13800000001',1,2,3,'2026-10-20','2026-10-22',1),(91002,'合成正式员工','13800000002',1,1,4,'2026-09-20','2026-09-22',1);",db)
    if scenario!='fresh':
        sql("INSERT INTO hrm_employee(id,name,mobile,type,status,entry_status,regular_time,tenant_id) VALUES (91003,'合成待核实员工','13800000003',2,3,1,'2026-09-20',1);",db)
    if scenario=='partial': sql('ALTER TABLE hrm_employee ADD COLUMN planned_regular_time datetime DEFAULT NULL;',db)
    if scenario=='failure':
        sql('RENAME TABLE system_notify_rule TO missing_rule;',db)
        assert sql(migration,db,False).returncode!=0
        assert sql("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='hrm' AND version='V003';",db).stdout.strip()==b'0'
        sql('RENAME TABLE missing_rule TO system_notify_rule;',db)
    if scenario=='execution_failure':
        sql('ALTER TABLE system_notify_template RENAME COLUMN nickname TO hidden_nickname;',db)
        assert sql(migration,db,False).returncode!=0
        assert sql("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='HRM_V003';",db).stdout.strip()==b'0'
        assert sql("SELECT COUNT(*) FROM hrm_employee WHERE legacy_date_source IS NOT NULL;",db).stdout.strip()==b'0'
        sql('ALTER TABLE system_notify_template RENAME COLUMN hidden_nickname TO nickname;',db)
    if scenario=='ledger_failure':
        sql('ALTER TABLE zsjos_module_schema_version RENAME COLUMN installed_at TO hidden_installed_at;',db)
        assert sql(migration,db,False).returncode!=0
        assert sql("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='HRM_V003';",db).stdout.strip()==b'0'
        sql('ALTER TABLE zsjos_module_schema_version RENAME COLUMN hidden_installed_at TO installed_at;',db)
    sql(migration,db)
    before=sql('SELECT id,type,status,entry_status,planned_regular_time,regular_time,planned_leave_time,leave_time,HEX(legacy_date_source) FROM hrm_employee ORDER BY id;',db).stdout
    sql(migration,db)
    assert before==sql('SELECT id,type,status,entry_status,planned_regular_time,regular_time,planned_leave_time,leave_time,HEX(legacy_date_source) FROM hrm_employee ORDER BY id;',db).stdout
    assert sql("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='hrm' AND version='V003';",db).stdout.strip()==b'1'
    assert sql("SELECT COUNT(*) FROM system_notify_rule WHERE scene_code LIKE 'hrm.employee.lifecycle_%' AND tenant_id=1;",db).stdout.strip()==b'3'
    if scenario!='fresh':
        expected=b'91001\t1\t2\t3\t2026-10-20 00:00:00\tNULL\t2026-10-22 00:00:00\tNULL'
        assert expected in before,before
        assert '旧系统记录（按状态承接）'.encode('utf-8').hex().upper().encode() in before
        assert '旧系统日期待核实'.encode('utf-8').hex().upper().encode() in before
        assert sql("SELECT legacy_regular_time FROM hrm_employee WHERE id=91003;",db).stdout.strip()==b'2026-09-20 00:00:00'
    columns=sql("SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('hrm_employee','hrm_employee_change_record') ORDER BY table_name,column_name;",db).stdout
    if scenario=='fresh': expected_columns=columns
    else: assert columns==expected_columns,'fresh/upgrade schema mismatch'
    results.append(scenario+': initial/replay/state-preservation/version/UTF-8 passed')
print(json.dumps(results,ensure_ascii=False,indent=2))
