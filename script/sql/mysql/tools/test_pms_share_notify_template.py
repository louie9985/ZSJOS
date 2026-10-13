# -*- coding: utf-8 -*-
"""PMS V002 scoped MySQL verification; retains isolated schemas, sends no messages.

Run: python -B script/sql/mysql/tools/test_pms_share_notify_template.py
Uses the existing local yudao-mysql container via the established SQL test helper.
"""
import datetime
import json
from pathlib import Path

from test_calendar_notifications import query, table

ROOT = Path(__file__).resolve().parents[4]
PMS = ROOT / 'script/sql/mysql/migrations/pms'
MIGRATION = (PMS / 'V002__knowledge_document_share_notify_template.sql').read_text(encoding='utf-8')
CODE = 'pms_knowledge_document_shared'
FILTER = f"code='{CODE}'"


def template(database):
    return query(database, f'SELECT id,HEX(name),HEX(nickname),HEX(title),HEX(summary),HEX(content),'
                 f'params,status,channel_code,deleted+0,create_time,update_time '
                 f'FROM system_notify_template WHERE {FILTER} ORDER BY id;')[0]


def ledger(database):
    return query(database, "SELECT module_code,version,checksum,release_version,installed_at "
                 "FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V002';")[0]


def verify_template(database):
    fields = template(database).split('\t')
    assert bytes.fromhex(fields[1]).decode('utf-8') == '知识库文档分享'
    assert bytes.fromhex(fields[2]).decode('utf-8') == '中世健消息中心'
    assert bytes.fromhex(fields[3]).decode('utf-8') == '收到知识库文档分享'
    assert bytes.fromhex(fields[4]).decode('utf-8') == '{{operatorName}}向您分享了《{{documentTitle}}》。'
    assert bytes.fromhex(fields[5]).decode('utf-8') == '{{operatorName}}向您分享了知识库文档《{{documentTitle}}》。'
    assert json.loads(fields[6]) == ['operatorName', 'documentTitle']
    assert fields[7:10] == ['0', 'in_app', '0']
    assert len(ledger(database).splitlines()) == 1


def run():
    prefix = 'pms_notify_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    cases = ['old_default', 'initial', 'partial_template', 'existing_marker', 'custom_disabled',
             'prerequisite', 'deleted', 'bad_params', 'bad_json', 'duplicate',
             'ledger_failure', 'postcondition_failure']
    for case in cases:
        database = prefix + '_' + case
        query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;')
        setup = table('system_notify_template') + table('zsjos_module_schema_version')
        # Run the real PMS predecessor, as the module runner does after Core setup.
        setup += (PMS / 'V001__pms_schema.sql').read_text(encoding='utf-8')
        if case != 'prerequisite':
            setup += "INSERT INTO zsjos_module_schema_version VALUES ('pms','V001','fixture','fixture','fixture',NOW());"
        setup += "INSERT INTO system_notify_template(name,code,nickname,title,summary,content,type,params,status) "
        setup += "VALUES ('unrelated','unrelated','fixture','title','summary','body',2,'[]',0);"
        if case in ('partial_template', 'custom_disabled', 'deleted', 'bad_params', 'bad_json', 'duplicate'):
            params = {'bad_params': '["unsupported"]', 'bad_json': 'invalid'}.get(case, '["documentTitle","route"]')
            setup += "INSERT INTO system_notify_template(name,code,nickname,title,summary,content,type,params,status,deleted) "
            setup += f"VALUES ('custom','{CODE}','fixture','custom title','custom summary','custom body',2,'{params}',"
            setup += f"{1 if case == 'custom_disabled' else 0},{1 if case == 'deleted' else 0});"
            if case == 'duplicate':
                setup += "INSERT INTO system_notify_template(name,code,nickname,title,summary,content,type,params,status) "
                setup += f"VALUES ('duplicate','{CODE}','fixture','title','summary','body',2,'[]',0);"
        if case == 'existing_marker':
            setup += "INSERT INTO zsjos_module_schema_version VALUES ('pms','V002','preserved','preserved','fixture',NOW());"
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW "
            setup += "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        if case == 'postcondition_failure':
            setup += "CREATE TRIGGER corrupt_template BEFORE INSERT ON system_notify_template FOR EACH ROW SET NEW.deleted=1;"
        query(database, setup)
        if case == 'old_default':
            query(database, "INSERT INTO system_notify_template(name,code,nickname,title,summary,content,type,params,status,creator,updater) VALUES ("
                  f"'知识库文档分享','{CODE}','中世健消息中心','收到知识库文档分享',"
                  "'有人向您分享了《{{documentTitle}}》。','有人向您分享了知识库文档《{{documentTitle}}》。查看地址：{{route}}',"
                  "2,'[\"documentTitle\",\"route\"]',0,'migration-pms-V002','migration-pms-V002');")
        before_template, before_ledger = template(database), ledger(database)
        unrelated = query(database, "SELECT HEX(CONCAT_WS('|',name,title,summary,content,update_time)) "
                          "FROM system_notify_template WHERE code='unrelated';")[0]
        _, error = query(database, MIGRATION, force=True)
        failure = case in ('prerequisite', 'deleted', 'bad_params', 'bad_json', 'duplicate',
                           'ledger_failure', 'postcondition_failure')
        if failure:
            assert 'ERROR' in error, case
            assert template(database) == before_template, case
            assert ledger(database) == before_ledger, case
            if case == 'prerequisite':
                query(database, "INSERT INTO zsjos_module_schema_version VALUES ('pms','V001','fixture','fixture','fixture',NOW());")
            elif case == 'ledger_failure':
                query(database, 'DROP TRIGGER fail_ledger;')
            elif case == 'postcondition_failure':
                query(database, 'DROP TRIGGER corrupt_template;')
            else:
                print(f'PASS {case}: failure preserves template and ledger, even with client continuation')
                continue
            query(database, MIGRATION)
            verify_template(database)
        else:
            assert not error, (case, error)
            if before_template and case != 'old_default':
                assert template(database) == before_template, case
            else:
                verify_template(database)
            if before_ledger:
                assert ledger(database) == before_ledger, case
        stable = (template(database), ledger(database))
        query(database, MIGRATION)
        assert stable == (template(database), ledger(database)), case
        assert unrelated == query(database, "SELECT HEX(CONCAT_WS('|',name,title,summary,content,update_time)) "
                                  "FROM system_notify_template WHERE code='unrelated';")[0]
        print(f'PASS {case}: template/ledger contract, recovery, zero-write replay, unrelated row preserved')
    print('Retained isolated schemas:', prefix + '_*')


if __name__ == '__main__':
    run()
