# -*- coding: utf-8 -*-
"""Verify HRM V004 in new hrm_trial_verify_* schemas on local MySQL 8; retain fixtures.

The existing ruoyi-vue-pro schema is read only, used for prerequisite table definitions.
No service changes, business rows copied, or database drops. Docker credentials stay inside the container.
"""
from pathlib import Path
import json
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[4]
PREFIX = 'hrm_trial_verify_' + str(time.time_ns())[-12:]


def sql(text, db=None, check=True):
    assert db is None or db.startswith(PREFIX), 'Writes are restricted to this run fixtures'
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B'
    if db:
        command += ' ' + db
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c', command],
                            input=('SET NAMES utf8mb4;\n' + text).encode(), capture_output=True)
    if check and result.returncode:
        raise AssertionError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8').strip() if check else result


assert sql('SELECT VERSION();').startswith('8.'), 'MySQL 8 required'
prerequisites = []
for name in ['system_dict_type', 'system_dict_data', 'system_notify_template', 'system_notify_rule',
             'infra_job', 'zsjos_schema_version', 'zsjos_module_schema_version']:
    # SHOW CREATE is the only operation addressing the real development database.
    ddl = sql('SHOW CREATE TABLE `ruoyi-vue-pro`.`' + name + '`;').split('\t', 1)[1]
    ddl = ddl.replace('\\n', '\n').replace('\\t', '\t')
    prerequisites.append(re.sub(r'AUTO_INCREMENT=\d+', '', ddl) + ';')

schema = (ROOT / 'script/sql/mysql/schema/hrm.sql').read_text(encoding='utf-8')
# Reproduce V003 prerequisite shape without modifying historical migrations.
old_schema = re.sub(r'^    (trial_days|planned_trial_end_time|actual_trial_end_time) .*\n', '', schema, flags=re.M)
old_schema = old_schema.replace('company_age decimal(10,2)', 'company_age int')
v002 = (ROOT / 'script/sql/mysql/migrations/hrm/V002__hrm_dictionary.sql').read_text(encoding='utf-8')
v003 = (ROOT / 'script/sql/mysql/migrations/hrm/V003__manual_employee_lifecycle.sql').read_text(encoding='utf-8')
v004 = (ROOT / 'script/sql/mysql/migrations/hrm/V004__employee_trial_and_decimal_tenure.sql').read_text(encoding='utf-8')
columns_query = """SELECT column_name,column_type,is_nullable,COALESCE(column_default,'NULL')
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hrm_employee' ORDER BY column_name;"""


def no_success(db):
    assert sql("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='HRM_V004';", db) == '0'
    assert sql("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='hrm' AND version='V004';", db) == '0'


results = []
for scenario in ['fresh', 'upgrade', 'partial', 'prerequisite_failure', 'execution_failure', 'ledger_failure']:
    db = PREFIX + '_' + scenario
    sql('CREATE DATABASE ' + db + ' CHARACTER SET utf8mb4;')
    sql('\n'.join(prerequisites) + '\n' + (schema if scenario == 'fresh' else old_schema), db)
    sql(v002, db)
    sql(v003, db)
    sql("INSERT INTO hrm_employee(name,mobile,type,status,entry_status,company_age,tenant_id,legacy_date_source) "
        "VALUES('合成验证员工','13800000001',2,3,1,2,1,'人工维护');", db)
    before = sql('SELECT id,type,status,entry_status,company_age*100,HEX(name) FROM hrm_employee;', db)
    if scenario == 'partial':
        sql('ALTER TABLE hrm_employee ADD COLUMN trial_days int DEFAULT NULL;', db)
    if scenario == 'prerequisite_failure':
        sql("UPDATE zsjos_module_schema_version SET version='pending_V003' WHERE module_code='hrm' AND version='V003';", db)
        assert sql(v004, db, False).returncode != 0
        no_success(db)
        sql("UPDATE zsjos_module_schema_version SET version='V003' WHERE module_code='hrm' AND version='pending_V003';", db)
    if scenario == 'execution_failure':
        sql('ALTER TABLE system_dict_data RENAME COLUMN color_type TO hidden_color_type;', db)
        assert sql(v004, db, False).returncode != 0
        no_success(db)
        sql('ALTER TABLE system_dict_data RENAME COLUMN hidden_color_type TO color_type;', db)
    if scenario == 'ledger_failure':
        sql('ALTER TABLE zsjos_module_schema_version RENAME COLUMN installed_at TO hidden_installed_at;', db)
        assert sql(v004, db, False).returncode != 0
        no_success(db)
        assert sql("SELECT COUNT(*) FROM system_dict_data WHERE dict_type='hrm_employee_status' AND value='9';", db) == '0'
        sql('ALTER TABLE zsjos_module_schema_version RENAME COLUMN hidden_installed_at TO installed_at;', db)
    sql(v004, db)
    assert sql('SELECT id,type,status,entry_status,CAST(company_age*100 AS SIGNED),HEX(name) FROM hrm_employee;', db) == before.replace('.00', '')
    assert sql('SELECT COUNT(*) FROM hrm_employee WHERE trial_days IS NOT NULL OR planned_trial_end_time IS NOT NULL OR actual_trial_end_time IS NOT NULL;', db) == '0'
    assert sql("SELECT HEX(label) FROM system_dict_data WHERE dict_type='hrm_employee_status' AND value='9' AND deleted=0;", db) == '试岗'.encode().hex().upper()
    ledgers = sql("SELECT version,checksum FROM zsjos_schema_version WHERE version='HRM_V004'; SELECT version,checksum,installed_at FROM zsjos_module_schema_version WHERE module_code='hrm' AND version='V004';", db)
    sql(v004, db)
    assert ledgers == sql("SELECT version,checksum FROM zsjos_schema_version WHERE version='HRM_V004'; SELECT version,checksum,installed_at FROM zsjos_module_schema_version WHERE module_code='hrm' AND version='V004';", db)
    columns = sql(columns_query, db)
    if scenario == 'fresh':
        expected_columns = columns
    else:
        assert columns == expected_columns, 'Fresh/upgrade schema mismatch'
    sql("UPDATE hrm_employee SET trial_days=3,planned_trial_end_time='2026-10-17',actual_trial_end_time='2026-10-16',company_age=0.27;", db)
    assert sql('SELECT trial_days,company_age FROM hrm_employee;', db) == '3\t0.27'
    sql(v004, db)
    assert sql('SELECT trial_days,company_age FROM hrm_employee;', db) == '3\t0.27'
    results.append({'scenario': scenario, 'schema': db, 'result': 'passed'})
print(json.dumps(results, ensure_ascii=False, indent=2))
