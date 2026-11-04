# -*- coding: utf-8 -*-
"""Execute V362 only in new retained MySQL fixtures; never migrate the configured database."""
import datetime
import re
from pathlib import Path
import pymysql
import yaml

ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/migrations/V362__exam_material_collection.sql').read_text(encoding='utf-8')
docs = yaml.safe_load_all((ROOT / 'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8'))
config = next(x['spring']['datasource']['dynamic']['datasource']['master'] for x in docs
              if x and 'datasource' in x.get('spring', {}))
address = re.search(r'jdbc:mysql://([^:/]+):(\d+)/([^?]+)', config['url'])
SOURCE = address[3]
connection = pymysql.connect(host=address[1], port=int(address[2]), user=config['username'],
    password=str(config['password']), database=SOURCE, charset='utf8mb4', autocommit=True)
stamp = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
prefix = 'exam_collection_verify_' + stamp
tables = re.findall(r'CREATE TABLE IF NOT EXISTS (\w+)', SQL)

def query(db, sql, args=None):
    if db != SOURCE and not db.startswith(prefix):
        raise ValueError('Outside isolated fixture scope')
    if db == SOURCE and not sql.lstrip().upper().startswith(('SELECT ', 'SHOW ', 'CREATE DATABASE ')):
        raise ValueError('Application database is read-only')
    connection.select_db(db)
    with connection.cursor() as cursor:
        cursor.execute(sql, args)
        result = cursor.fetchall()
        while cursor.nextset():
            pass
        return result

def fixture(kind):
    db = prefix + '_' + kind
    query(SOURCE, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4')
    for table in ('system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version'):
        query(db, f'CREATE TABLE {table} LIKE `{SOURCE}`.{table}')
    query(db, f"INSERT INTO system_menu SELECT * FROM `{SOURCE}`.system_menu WHERE path='/zsjos' AND type=1 AND deleted=0")
    for table in ('zsjos_schema_version', 'zsjos_module_schema_version'):
        query(db, f"INSERT INTO {table} SELECT * FROM `{SOURCE}`.{table} WHERE version='V361'")
    return db

def apply(db):
    assert db.startswith(prefix) and db != SOURCE
    delimiter, pending = ';', ''
    for line in SQL.splitlines():
        if line.startswith('--'):
            continue
        if line.startswith('DELIMITER '):
            delimiter = line.split()[1]
            continue
        pending += line + '\n'
        if pending.rstrip().endswith(delimiter):
            query(db, pending.rstrip()[:-len(delimiter)])
            pending = ''

def ledgers(db):
    return [query(db, f'SELECT * FROM {table} ORDER BY version')
            for table in ('zsjos_schema_version', 'zsjos_module_schema_version')]

def verify(db):
    apply(db)
    before = ledgers(db)
    menus = query(db, 'SELECT * FROM system_menu ORDER BY id')
    structure = [query(db, f'SHOW CREATE TABLE {table}') for table in tables]
    apply(db)
    assert before == ledgers(db)
    assert menus == query(db, 'SELECT * FROM system_menu ORDER BY id')
    assert structure == [query(db, f'SHOW CREATE TABLE {table}') for table in tables]
    for table in tables:
        assert query(db, f'SELECT COUNT(*) FROM {table}') == ((0,),)
    assert query(db, "SELECT name, HEX(name) FROM system_menu WHERE permission='zsjos:exam-collection:query'") == (
        ('考试资料收集', '考试资料收集'.encode().hex().upper()),)
    assert query(db, "SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:exam-collection:%'") == ((8,),)
    assert query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V362'") == ((1,),)
    assert query(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V362' AND module_code='core'") == ((1,),)
    print('PASS initial/replay/schema/menu/UTF-8:', db)

def reject(db):
    before = ledgers(db)
    menus = query(db, 'SELECT * FROM system_menu ORDER BY id')
    try:
        apply(db)
    except pymysql.MySQLError:
        pass
    else:
        raise AssertionError('Expected migration failure')
    assert before == ledgers(db)
    assert menus == query(db, 'SELECT * FROM system_menu ORDER BY id')
    print('PASS failure does not advance ledgers:', db)

if __name__ == '__main__':
    try:
        assert query(SOURCE, 'SELECT VERSION()')[0][0].startswith('8.')
        assert query(SOURCE, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V361'") == ((1,),)
        original = ledgers(SOURCE)
        grants = query(SOURCE, 'SELECT * FROM system_role_menu ORDER BY id')
        menus = query(SOURCE, 'SELECT * FROM system_menu ORDER BY id')
        db = fixture('fresh')
        verify(db)
        db = fixture('missing')
        query(db, "UPDATE zsjos_module_schema_version SET version='fixture_missing' WHERE version='V361'")
        reject(db)
        assert query(db, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s AND table_name=%s", (db, tables[0])) == ((0,),)
        db = fixture('ledger_failure')
        query(db, "CREATE TRIGGER fixture_reject BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
        reject(db)
        # DDL survives the failed transaction; recovery must inspect and reuse it.
        assert query(db, f'SELECT COUNT(*) FROM {tables[0]}') == ((0,),)
        query(db, 'DROP TRIGGER fixture_reject')
        verify(db)
        db = fixture('partial')
        apply(db)
        query(db, f'RENAME TABLE {tables[-1]} TO fixture_retained_archive')
        verify(db)
        db = fixture('conflict')
        apply(db)
        query(db, "UPDATE system_menu SET path='fixture_conflict' WHERE permission='zsjos:exam-collection:query'")
        reject(db)
        db = fixture('roster_column')
        apply(db)
        query(db, 'ALTER TABLE zsjos_exam_collection_task RENAME COLUMN class_ids_json TO fixture_retained_class_ids')
        verify(db)
        db = fixture('index_conflict')
        apply(db)
        query(db, 'ALTER TABLE zsjos_exam_collection_binding DROP INDEX uk_identity')
        reject(db)
        query(db, 'ALTER TABLE zsjos_exam_collection_binding ADD UNIQUE KEY uk_identity (tenant_id,task_id,identity_hash)')
        verify(db)
        query(db, "INSERT INTO zsjos_exam_collection_binding (tenant_id,task_id,identity_hash,service_relation_id) VALUES (1,1,%s,10)", ('a' * 64,))
        try:
            query(db, "INSERT INTO zsjos_exam_collection_binding (tenant_id,task_id,identity_hash,service_relation_id) VALUES (1,1,%s,11)", ('a' * 64,))
        except pymysql.IntegrityError:
            pass
        else:
            raise AssertionError('Duplicate identity binding accepted')
        query(db, "INSERT INTO zsjos_exam_collection_binding (tenant_id,task_id,identity_hash,service_relation_id) VALUES (1,2,%s,11)", ('a' * 64,))
        assert query(db, 'SELECT COUNT(*) FROM zsjos_exam_collection_binding') == ((2,),)
        print('PASS identity uniqueness is scoped to task')
        assert query(db, "SELECT JSON_CONTAINS(%s,%s),JSON_CONTAINS(%s,%s)",
                     ('[{"source":"exam","id":7}]', '{"source":"exam","id":7}',
                      '[{"source":"registration","id":7}]', '{"source":"exam","id":7}')) == ((1, 0),)
        assert query(db, 'SELECT JSON_CONTAINS(%s,%s)', ('[7,8]', '7')) == ((1,),)
        print('PASS historical attachment JSON membership distinguishes sources')
        # Execute the actual Java-generated summary condition against MySQL, not a copied predicate.
        java_source = (ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/studentinfo/StudentSummaryService.java').read_text(encoding='utf-8')
        condition = re.search(r'AND EXISTS \(SELECT 1 FROM zsjos_student_info_form submitted.*?"""', java_source, re.S).group(0)[:-3].strip()
        query(db, 'CREATE TABLE fixture_exam_relations(id bigint,person_id bigint,registration_case_id bigint,tenant_id bigint)')
        query(db, 'INSERT INTO fixture_exam_relations VALUES (1,101,11,1),(2,102,12,1),(3,103,13,1),(4,104,14,1),(5,105,15,1)')
        query(db, 'CREATE TABLE zsjos_student_info_form(id bigint,registration_case_id bigint,person_id bigint,status varchar(24),tenant_id bigint,deleted int)')
        query(db, "INSERT INTO zsjos_student_info_form VALUES (1,11,101,'SUBMITTED',1,0),(2,13,103,'DRAFT',1,0)")
        query(db, 'CREATE TABLE zsjos_student_info_task(id bigint,order_item_id bigint,form_id bigint,person_id bigint,tenant_id bigint,deleted int)')
        query(db, 'CREATE TABLE zsjos_registration_case(id bigint,order_item_id bigint,tenant_id bigint,deleted int)')
        for service_id, person_id, tenant_id in [(2,102,1),(4,104,2),(5,999,1)]:
            query(db, "INSERT INTO zsjos_student_service_archive_revision(service_relation_id,person_id,tenant_id,revision,source,idempotency_key,request_hash,fields_snapshot,answers_json,attachments_json) VALUES (%s,%s,%s,1,'exam_submission','summary-fixture','synthetic','[]','synthetic-encrypted','[]')", (service_id,person_id,tenant_id))
        assert query(db, 'SELECT sr.id FROM fixture_exam_relations sr WHERE sr.tenant_id=1 '+condition+' ORDER BY sr.id') == ((1,), (2,))
        assert query(db, 'SELECT sr.id FROM fixture_exam_relations sr WHERE sr.tenant_id=1 '+condition.replace('AND EXISTS','AND NOT EXISTS',1)+' ORDER BY sr.id') == ((3,), (4,), (5,))
        print('PASS actual summary SQL recognizes exam archives, excludes draft/cross-tenant/wrong-person rows')
        assert original == ledgers(SOURCE)
        assert grants == query(SOURCE, 'SELECT * FROM system_role_menu ORDER BY id')
        assert menus == query(SOURCE, 'SELECT * FROM system_menu ORDER BY id')
        print('PASS application ledgers, menus and role grants unchanged; fixtures retained')
    finally:
        connection.close()
