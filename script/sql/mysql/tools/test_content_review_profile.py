#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V316 focused MySQL verification; retains isolated schemas."""
import datetime
import json
import re
import subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V316__content_review_account_profile.sql').read_text(encoding='utf-8')
PREFIX = 'cr316_' + datetime.datetime.now().strftime('%m%d%H%M%S')
def run(database, sql, fail=False):
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 -N -B'
    result = subprocess.run(['docker','exec','-i','yudao-mysql','sh','-c',command + (' --force' if fail else '')], input=('SET NAMES utf8mb4;' + ('USE `' + database + '`;' if database else '') + sql).encode(), capture_output=True)
    if not fail and (result.returncode or b'ERROR' in result.stderr):
        raise RuntimeError(result.stderr.decode())
    return result
def query(database, sql):
    return run(database, sql).stdout.decode().strip()
def main():
    source = (SQL / 'schema/core.sql').read_text(encoding='utf-8') + (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    def table(name):
        match = re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', source)
        assert match, name
        return match[0]
    prereq = "INSERT INTO zsjos_schema_version(version,description) VALUES('V315','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V315','fixture','fixture','fixture');"
    for case in ['initial','partial','prerequisite','ledger_failure']:
        name = PREFIX + '_' + case
        query(None, 'CREATE DATABASE `' + name + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        query(name, ''.join(table(t) for t in ['zsjos_media_account','zsjos_content_review_batch','zsjos_schema_version','zsjos_module_schema_version']))
        if case != 'prerequisite': query(name, prereq)
        query(name, "INSERT INTO zsjos_media_account(id,account_no,ownership_type,tenant_id,student_person_id,detail_values_json,detail_snapshot_json,primary_problem_code_label_snapshot) VALUES(1,'fixture','STUDENT',1,7,JSON_OBJECT('product_goal','目标','work_format','产品形式','publish_rhythm','weekly'),JSON_ARRAY(JSON_OBJECT('key','publish_rhythm','displayValue','每周三条')),'瓶颈');")
        profile = {'id': 1, 'productGoal': '保留原值'}
        if case == 'partial': profile['productFormLabel'] = '先前已补'
        context = json.dumps({'accountSnapshots':[profile],'untouched':'keep'},ensure_ascii=False).replace("'","''")
        query(name, "INSERT INTO zsjos_content_review_batch(id,batch_no,account_id,student_person_id,account_ids_json,operator_user_id,director_user_id,status,current_stage,version,tenant_id,context_snapshot_json) VALUES(1,'fixture',1,7,JSON_ARRAY(1),10,11,'COMPLETED','FINAL',1,1,'" + context + "');")
        if case == 'ledger_failure': query(name, "CREATE TRIGGER reject316 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';")
        before = query(name,'SELECT context_snapshot_json,version FROM zsjos_content_review_batch;')
        result = run(name,MIGRATION,True)
        if case in ['prerequisite','ledger_failure']:
            assert b'ERROR' in result.stderr, result.stderr
            assert query(name,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V316';") == '0'
            assert query(name,'SELECT context_snapshot_json,version FROM zsjos_content_review_batch;') == before
            query(name, prereq if case == 'prerequisite' else 'DROP TRIGGER reject316;')
            run(name,MIGRATION)
        else: assert b'ERROR' not in result.stderr, result.stderr.decode()
        data = json.loads(query(name,'SELECT context_snapshot_json FROM zsjos_content_review_batch;'))
        profile = data['accountSnapshots'][0]
        assert profile['productGoal'] == '保留原值' and profile['publishFrequency'] == '每周三条' and profile['bottleneckLabel'] == '瓶颈'
        assert profile['productFormLabel'] == ('先前已补' if case == 'partial' else '产品形式')
        assert data['untouched'] == 'keep' and profile['profileBackfill']['publishFrequency']['source'] == 'CURRENT_ACCOUNT'
        after = query(name,'SELECT * FROM zsjos_content_review_batch;')
        run(name,MIGRATION)
        assert query(name,'SELECT * FROM zsjos_content_review_batch;') == after
        assert query(name,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V316';") == '1'
        assert query(name,"SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(context_snapshot_json,'$.accountSnapshots[0].publishFrequency'))) FROM zsjos_content_review_batch;") == '每周三条'.encode().hex().upper()
        print('PASS',case,name,flush=True)
if __name__ == '__main__': main()

