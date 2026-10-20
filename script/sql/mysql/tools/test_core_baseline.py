#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify Core synchronization on retained synthetic MySQL schemas.

Uses the existing local yudao-mysql container. Never writes to the application
database, starts/stops a service, or removes verification databases. SOURCE files
are expanded from repository files before the fail-fast MySQL client executes them.
"""
import argparse
import re
import subprocess
import uuid
from types import SimpleNamespace

import zsjos_db as db


AFFECTED = (
    'zsjos_student_info_form', 'zsjos_student_info_form_config',
    'zsjos_student_info_image', 'zsjos_navigation_preference',
    'zsjos_student_archive_revision', 'zsjos_service_relation',
    'zsjos_student_contact_record', 'zsjos_student_archive_extra_revision',
    'zsjos_student_archive_attachment', 'zsjos_student_archive_extra_config',
)
VERSIONS = ('V323', 'V325', 'V327', 'V328', 'V330', 'V332', 'V335')


class Client:
    def __init__(self, database):
        if not re.fullmatch(r'[a-zA-Z0-9_-]+', database):
            raise ValueError('Invalid database name')
        self.database = database
        self.config = SimpleNamespace(release='core-baseline-verification')

    def execute(self, sql, *, expect_success=True):
        def source(match):
            path = (db.ROOT / match.group(1)).resolve()
            if db.SQL_ROOT.resolve() not in path.parents:
                raise ValueError('SOURCE outside SQL root')
            return re.sub(r'^SOURCE\s+([^;]+);\s*$', source,
                          path.read_text(encoding='utf-8'), flags=re.M | re.I)
        sql = re.sub(r'^SOURCE\s+([^;]+);\s*$', source, sql, flags=re.M | re.I)
        command = ['docker', 'exec', '-i']
        command += ['yudao-mysql', 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql '
                    '--default-character-set=utf8mb4 -uroot -N -B --raw']
        result = subprocess.run(command, input=(
            'SET NAMES utf8mb4; USE `' + self.database + '`;\n' + sql
        ).encode('utf-8'), capture_output=True)
        if expect_success and (result.returncode or b'ERROR' in result.stderr):
            raise RuntimeError(result.stderr.decode('utf-8'))
        return result

    def query(self, sql):
        return self.execute(sql).stdout.decode('utf-8').strip()

    def table_exists(self, table):
        return self.query(
            'SELECT COUNT(*) FROM information_schema.tables '
            'WHERE table_schema=DATABASE() AND table_name=' + db.sql_literal(table)
        ) == '1'

    def execute_file(self, path):
        relative = path.relative_to(db.ROOT).as_posix()
        return self.query('SOURCE ' + relative + ';')


def signature(client):
    new_tables = tuple(name for name in AFFECTED if name not in (
        'zsjos_student_info_form', 'zsjos_student_info_form_config',
        'zsjos_service_relation', 'zsjos_student_contact_record'))
    names = ','.join(db.sql_literal(name) for name in new_tables)
    changed_columns = {
        'zsjos_student_info_form': ('lead_id', 'registration_case_id', 'order_item_id',
                                  'person_id', 'product_id', 'sent_by_user_id', 'sent_at',
                                  'fields_snapshot', 'category_path_snapshot', 'product_name'),
        'zsjos_student_info_form_config': ('education_category_ids_json',),
        'zsjos_service_relation': ('first_contact_progress_json',),
        'zsjos_student_contact_record': ('study_plan_completed_at_first_contact',
                                        'checklist_snapshot_json', 'next_contact_at'),
    }
    columns = ' OR '.join(
        '(table_name=' + db.sql_literal(table) + ' AND column_name IN (' +
        ','.join(db.sql_literal(column) for column in fields) + '))'
        for table, fields in changed_columns.items())
    return client.query(
        'SELECT table_name,column_name,column_type,is_nullable,'
        "COALESCE(column_default,'<NULL>'),extra,HEX(column_comment) "
        'FROM information_schema.columns WHERE table_schema=DATABASE() '
        f'AND (table_name IN ({names}) OR {columns}) ORDER BY table_name,column_name; '
        'SELECT table_name,index_name,non_unique,seq_in_index,column_name,sub_part '
        'FROM information_schema.statistics WHERE table_schema=DATABASE() '
        f'AND (table_name IN ({names}) OR (table_name=\'zsjos_student_info_form\' '
        "AND index_name='uk_student_info_case')) ORDER BY table_name,index_name,seq_in_index"
    )


def ledgers(client):
    return client.query(
        'SELECT version,checksum FROM zsjos_schema_version ORDER BY version; '
        'SELECT module_code,version,checksum,release_version '
        'FROM zsjos_module_schema_version ORDER BY module_code,version'
    )


def verify_target(database):
    client = Client(database)
    before = (signature(client), ledgers(client))
    manifests = db.load_manifests()
    pending, blockers = db.migration_preflight(client, manifests)
    drift = db.schema_drift(client, manifests)
    assert not drift, drift
    assert not pending and not blockers, (pending, blockers)
    assert client.query(
        "SELECT GROUP_CONCAT(id ORDER BY id) FROM system_tenant WHERE deleted=b'0'"
    ) == '1'
    assert before == (signature(client), ledgers(client))
    print('PASS target read-only: zero schema drift, zero pending migrations, '
          'valid installed checksums, tenant=1, unchanged structure/ledgers', flush=True)


def create_database(name):
    # Only new, uniquely named synthetic schemas are created. Retain on failure.
    Client('mysql').query(
        f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'
    )
    print('Retained verification database:', name, flush=True)
    return Client(name)


def verify_fresh(client):
    client.execute_file(db.SQL_ROOT / 'bootstrap.sql')
    manifests = db.load_manifests()
    pending = db.pending_migrations(manifests, db.installed_versions(client))
    print('Fresh migration count:', len(pending), flush=True)
    for migration in pending:
        print('Applying', migration.module, migration.version, flush=True)
        client.execute_file(migration.path)
        db.record_migration(client, migration)
    drift = db.schema_drift(client, manifests)
    assert not drift, drift
    assert not db.pending_migrations(manifests, db.installed_versions(client))
    failures = []
    for code in db.module_order(manifests):
        output = client.execute_file(db.resolve_sql_path(manifests[code]['verify']))
        failures += [code + ': ' + line for line in output.splitlines()
                     if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)]
    print('Fresh data-grade findings:', len(failures), flush=True)
    for failure in failures:
        print(failure, flush=True)
    # Match verify_database(): data snapshots are reported separately from the
    # fail-closed schema and installed-file checksum verification above.
    if failures:
        print('WARN data-grade findings require separate business assessment; '
              'this run verifies migration execution and structure', flush=True)
    before = (signature(client), ledgers(client))
    for version in VERSIONS:
        migration = next(item for item in db.migrations_for('core', manifests['core'])
                         if item.version == version)
        client.execute_file(migration.path)
    assert before == (signature(client), ledgers(client))
    image_label = client.query(
        "SELECT HEX(table_comment) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image'"
    )
    assert image_label == '学员收集表图片关联'.encode().hex().upper()
    before_guard = ledgers(client)
    rejected = client.execute('SOURCE script/sql/mysql/bootstrap.sql;', expect_success=False)
    assert rejected.returncode != 0 or b'ERROR' in rejected.stderr
    assert before_guard == ledgers(client)
    print('PASS complete fresh chain, every module, final schema/ledgers, '
          'affected migration replay, UTF-8 HEX and nonempty bootstrap guard', flush=True)


def verify_v306(prefix):
    migration = next((db.SQL_ROOT / 'migrations').glob('V306__*.sql'))
    text = migration.read_text(encoding='utf-8')
    ddl = definitions()
    backup_ddl = (db.SQL_ROOT / 'migrations/V304__legacy_lead_deal_state_reconcile.sql').read_text(encoding='utf-8')
    ddl['zsjos_data_repair_backup'] = re.search(
        r'CREATE TABLE IF NOT EXISTS zsjos_data_repair_backup\s*\(.*?\)\s*ENGINE\s*=.*?;',
        backup_ddl, re.S | re.I).group(0)
    tables = ('zsjos_schema_version', 'zsjos_module_schema_version', 'zsjos_order',
              'zsjos_order_approval_round', 'zsjos_data_repair_backup')
    client = create_database(prefix + '_v306')
    for table in tables:
        client.query(ddl[table])
    before = ledgers(client)
    failed = client.execute(text, expect_success=False)
    assert b'V306 requires V305' in failed.stderr
    assert before == ledgers(client)
    client.query("INSERT INTO zsjos_schema_version(version,description,checksum) "
                 "VALUES('V305','fixture','fixture'); "
                 'INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) '
                 "VALUES('core','V305','fixture','fixture','fixture')")
    before = ledgers(client)
    failed = client.execute(text, expect_success=False)
    assert b'V306 requires LONGTEXT backup' in failed.stderr
    assert before == ledgers(client)
    client.query('ALTER TABLE zsjos_data_repair_backup MODIFY COLUMN old_value LONGTEXT NULL')
    before = ledgers(client)
    trigger = ('CREATE TRIGGER refuse_v306 BEFORE INSERT ON zsjos_module_schema_version '
               "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 fixture ledger failure'")
    client.query(trigger)
    failed = client.execute(text, expect_success=False)
    assert b'V306 fixture ledger failure' in failed.stderr
    assert before == ledgers(client)
    client.query('DROP TRIGGER refuse_v306')
    client.query(text)
    once = ledgers(client)
    client.query(text)
    assert once == ledgers(client)
    assert client.query('SELECT COUNT(*) FROM zsjos_order; '
                        'SELECT COUNT(*) FROM zsjos_data_repair_backup') == '0\n0'
    print('PASS V306 empty initial/replay, prerequisite and second-ledger failure/recovery', flush=True)

    # Only synthetic rows are created; use the manifest's technical identifiers
    # to exercise the existing nonempty repair path with no copied personal data.
    rows = re.findall(r"\('([^']+)',(\d+),'(sales|education)'\)", text)
    assert len(rows) == 286
    for index, (number, user, identity) in enumerate(rows, 1):
        client.query('INSERT INTO zsjos_order(id,order_no,person_id,order_type,status,'
                     'formal_sales_user_id,total_amount,payable_amount,current_approval_round_id,tenant_id) '
                     f"VALUES({index},{db.sql_literal(number)},{index},'new','effective',{user},0,0,{index},1); "
                     'INSERT INTO zsjos_order_approval_round(id,order_id,round_no,status,order_snapshot,'
                     'process_definition_key,submitted_by_user_id,submitted_at,tenant_id) '
                     f"VALUES({index},{index},1,'approved',"
                     "JSON_OBJECT('snapshotVersion',2,'order',JSON_OBJECT(),'formalSales',JSON_OBJECT()),"
                     f"'fixture',{user},NOW(),1)")
    before = ledgers(client)
    failed = client.execute(text, expect_success=False)
    assert b'completed repair evidence incomplete' in failed.stderr
    assert before == ledgers(client)
    # Hide only synthetic success markers; installed database ledgers are untouched.
    client.query("UPDATE zsjos_schema_version SET version='fixture-V306' WHERE version='V306'; "
                 "UPDATE zsjos_module_schema_version SET version='fixture-V306' WHERE version='V306'")
    before = ledgers(client)
    client.query(trigger)
    failed = client.execute(text, expect_success=False)
    assert b'V306 fixture ledger failure' in failed.stderr, failed.stderr.decode()
    assert before == ledgers(client)
    assert client.query("SELECT COUNT(*) FROM zsjos_order WHERE formal_owner_identity IS NOT NULL; "
                        'SELECT COUNT(*) FROM zsjos_data_repair_backup') == '0\n0'
    client.query('DROP TRIGGER refuse_v306')
    client.query(text)
    repaired = client.query('SELECT id,formal_owner_identity,version FROM zsjos_order ORDER BY id; '
                            'SELECT id,order_snapshot,version FROM zsjos_order_approval_round ORDER BY id; '
                            'SELECT table_name,row_id,column_name,old_value FROM zsjos_data_repair_backup '
                            'ORDER BY table_name,row_id,column_name')
    client.query(text)
    assert repaired == client.query('SELECT id,formal_owner_identity,version FROM zsjos_order ORDER BY id; '
                                    'SELECT id,order_snapshot,version FROM zsjos_order_approval_round ORDER BY id; '
                                    'SELECT table_name,row_id,column_name,old_value FROM zsjos_data_repair_backup '
                                    'ORDER BY table_name,row_id,column_name')
    assert client.query('SELECT COUNT(*) FROM zsjos_order WHERE formal_owner_identity IS NOT NULL') == '286'
    client.query("UPDATE zsjos_order SET formal_owner_identity='changed',update_time=update_time WHERE id=1")
    before = ledgers(client)
    failed = client.execute(text, expect_success=False)
    assert b'V306 before/after conflict' in failed.stderr
    assert before == ledgers(client)
    client.query('UPDATE zsjos_order SET current_approval_round_id=NULL,update_time=update_time WHERE id=1')
    failed = client.execute(text, expect_success=False)
    assert b'V306 manifest target missing or incomplete' in failed.stderr
    assert before == ledgers(client)
    print('PASS V306 nonempty 286-target initial/replay, transaction rollback/recovery, '
          'incomplete evidence, later-change and missing-round protection', flush=True)


def definitions():
    text = (db.SQL_ROOT / 'schema/core.sql').read_text(encoding='utf-8')
    # The global version table belongs to the seed, rather than schema/core.sql.
    text += (db.SQL_ROOT / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    return {match.group(1).lower(): match.group(0) for match in re.finditer(
        r'CREATE TABLE IF NOT EXISTS\s+`?([^`\s(]+)`?\s*\(.*?\)\s*ENGINE\s*=.*?;',
        text, re.S | re.I)}


def verify_upgrades(fresh, prefix):
    ddl = definitions()
    migrations = db.migrations_for('core', db.load_manifests()['core'])
    cases = {
        'V323': ('zsjos_student_info_form', 'zsjos_student_info_form_config',
                 'zsjos_student_info_form_value'),
        'V325': (),
        'V327': ('zsjos_student_info_form', 'zsjos_student_info_image'),
        'V328': ('zsjos_student_contact_record', 'zsjos_service_relation'),
        'V330': ('zsjos_student_contact_record', 'zsjos_student_contact_config_version'),
        'V332': ('zsjos_service_relation',),
        'V335': (),
    }
    target = signature(fresh)
    for version, tables in cases.items():
        if not prefix.startswith('cs_'):
            raise ValueError('Upgrade fixtures require a synthetic schema prefix')
        migration = next(item for item in migrations if item.version == version)
        client = create_database(prefix + '_' + version.lower())
        for table in ('zsjos_schema_version', 'zsjos_module_schema_version', 'system_menu', *tables):
            client.query(ddl[table])
        # Reviewed metadata comes from the just-built synthetic fresh database.
        prerequisite = {'V323': 'V322', 'V325': 'V324', 'V327': 'V324',
                        'V332': 'V327', 'V335': 'V332'}.get(version)
        for table in ('zsjos_schema_version', 'zsjos_module_schema_version', 'system_menu'):
            predicate = ''
            if table != 'system_menu':
                predicate = ' WHERE version<>' + db.sql_literal(version)
                if prerequisite:
                    predicate += ' AND version<>' + db.sql_literal(prerequisite)
            elif version == 'V328':
                predicate = " WHERE permission<>'zsjos:student:query-my'"
            client.query(f'INSERT INTO `{table}` SELECT * FROM `{fresh.database}`.`{table}`{predicate}')
        before_prerequisite = ledgers(client)
        rejected = client.execute('SOURCE ' + migration.path.relative_to(db.ROOT).as_posix() + ';',
                                  expect_success=False)
        assert rejected.returncode != 0 or b'ERROR' in rejected.stderr, version
        assert before_prerequisite == ledgers(client), version
        if prerequisite:
            for table in ('zsjos_schema_version', 'zsjos_module_schema_version'):
                client.query(f'INSERT INTO `{table}` SELECT * FROM `{fresh.database}`.`{table}` '
                             'WHERE version=' + db.sql_literal(prerequisite))
        elif version == 'V328':
            client.query(f'INSERT INTO system_menu SELECT * FROM `{fresh.database}`.system_menu '
                         "WHERE permission='zsjos:student:query-my'")
        if version == 'V323':
            client.query('ALTER TABLE zsjos_student_info_form DROP INDEX uk_student_info_case, '
                         'DROP COLUMN registration_case_id, DROP COLUMN order_item_id, '
                         'DROP COLUMN person_id, DROP COLUMN product_id, DROP COLUMN sent_by_user_id, '
                         'DROP COLUMN sent_at, DROP COLUMN fields_snapshot, DROP COLUMN category_path_snapshot, '
                         'DROP COLUMN product_name, MODIFY COLUMN lead_id BIGINT NOT NULL; '
                         'ALTER TABLE zsjos_student_info_form_config DROP COLUMN education_category_ids_json')
        elif version == 'V327':
            client.query('ALTER TABLE zsjos_student_info_image DROP COLUMN archive_only')
        elif version == 'V328':
            client.query('ALTER TABLE zsjos_student_contact_record '
                         'DROP COLUMN study_plan_completed_at_first_contact, DROP COLUMN checklist_snapshot_json; '
                         'ALTER TABLE zsjos_service_relation DROP COLUMN first_contact_progress_json')
        elif version == 'V330':
            client.query('ALTER TABLE zsjos_student_contact_record MODIFY COLUMN next_contact_at DATETIME NOT NULL; '
                         f'INSERT INTO zsjos_student_contact_config_version SELECT * FROM `{fresh.database}`.'
                         "zsjos_student_contact_config_version WHERE tenant_id=1 AND status='published'")
            client.query("UPDATE zsjos_student_contact_config_version SET creator='fixture', "
                         'study_plan_timeout_minutes=1440')
        before = ledgers(client)
        # Force a transaction failure after DDL, then recover without discarding it.
        client.query('CREATE TRIGGER refuse_core_ledger BEFORE INSERT ON zsjos_module_schema_version '
                     "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure'")
        failed = client.execute('SOURCE ' + migration.path.relative_to(db.ROOT).as_posix() + ';',
                                expect_success=False)
        assert failed.returncode != 0 or b'ERROR' in failed.stderr, version
        assert before == ledgers(client), version
        client.query('DROP TRIGGER refuse_core_ledger')
        client.execute_file(migration.path)
        if version == 'V323':
            # The schema signature is the latest baseline; this image column
            # is introduced only at V327, beyond this upgrade checkpoint.
            client.query("ALTER TABLE zsjos_student_info_image ADD COLUMN archive_only BIT(1) "
                         "NOT NULL DEFAULT b'0' COMMENT '仅档案维护使用，不改变原提交'")
        once = (signature(client), ledgers(client))
        client.execute_file(migration.path)
        assert once == (signature(client), ledgers(client)), version
        # Compare complete changed-table definitions, including defaults/comments.
        actual = signature(client)
        for line in actual.splitlines():
            assert line in target.splitlines(), (version, line)
        assert client.query('SELECT COUNT(*) FROM zsjos_schema_version WHERE version=' +
                            db.sql_literal(version)) == '1'
        assert client.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' "
                            'AND version=' + db.sql_literal(version)) == '1'
        print('PASS', version, 'missing prerequisite, prior shape, failed second ledger, preserved versions, '
              'partial-DDL recovery, replay, final structure', flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', default='ruoyi-vue-pro')
    parser.add_argument('--target-only', action='store_true')
    parser.add_argument('--upgrade-only', action='store_true',
                        help='Verify affected upgrade fixtures from installed metadata; target stays read-only')
    parser.add_argument('--v306-only', action='store_true')
    args = parser.parse_args()
    db.static_check()
    verify_target(args.target)
    if args.target_only:
        return
    # Historical migrations append their lock suffix to DATABASE(); keep the
    # fixture name short enough for MySQL's 64-character named-lock limit.
    prefix = 'cs_' + uuid.uuid4().hex[:8]
    if args.v306_only:
        verify_v306(prefix)
        verify_target(args.target)
        return
    if args.upgrade_only:
        verify_upgrades(Client(args.target), prefix)
        verify_target(args.target)
        return
    fresh = create_database(prefix + '_fresh')
    verify_fresh(fresh)
    verify_v306(prefix)
    verify_upgrades(fresh, prefix)
    assert signature(fresh) == signature(Client(args.target)), 'Target definition mismatch'
    verify_target(args.target)
    print('PASS fresh/upgrade/target complete definitions agree; verification databases retained', flush=True)


if __name__ == '__main__':
    main()
