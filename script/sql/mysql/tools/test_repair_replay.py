# -*- coding: utf-8 -*-
"""Retained MySQL fixtures for direct GUI replay. No shared database writes or deletions."""
import hashlib
import json
import re
import sys
import uuid
from pathlib import Path
from order_history_repair import connect, ident, normalize, schema
from test_calendar_notifications import query

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql/migrations'
ORDER = [296,297,298,299,300,301,302,303,305,304,306,307,309,308,310]


def source(n):
    text = next(SQL.glob(f'V{n}__*.sql')).read_text(encoding='utf-8')
    return re.sub(r'(?im)^SOURCE\s+([^;]+);', lambda m: (ROOT / m[1].strip()).read_text(encoding='utf-8'), text)


def tables():
    mentioned = set(re.findall(r'\b(?:FROM|JOIN|UPDATE|INTO|TABLE(?: IF NOT EXISTS)?)\s+`?([a-z_][a-z_0-9]*)',
                              '\n'.join(source(n) for n in ORDER), re.I))
    c = connect()
    with c.cursor() as q:
        q.execute('SHOW TABLES')
        existing = {next(iter(row.values())) for row in q.fetchall()}
    c.close()
    return sorted(mentioned & existing)


def clone():
    name = 'repair_replay_' + uuid.uuid4().hex[:12]
    selected = tables()
    c = connect()
    with c.cursor() as q:
        q.execute('CREATE DATABASE ' + ident(name) + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        q.execute('SET FOREIGN_KEY_CHECKS=0')
        for t in selected:
            q.execute('CREATE TABLE ' + ident(name) + '.' + ident(t) + ' LIKE `ruoyi-vue-pro`.' + ident(t))
            q.execute("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND extra NOT LIKE '%%VIRTUAL GENERATED%%' AND extra NOT LIKE '%%STORED GENERATED%%' ORDER BY ordinal_position", (t,))
            cols = ','.join(ident(row['COLUMN_NAME']) for row in q.fetchall())
            q.execute('INSERT INTO ' + ident(name) + '.' + ident(t) + '(' + cols + ') SELECT ' + cols + ' FROM `ruoyi-vue-pro`.' + ident(t))
    c.close()
    print('Retained isolated schema:', name, flush=True)
    return name, selected


def signature(name, selected):
    c = connect(name)
    result = {}
    with c.cursor() as q:
        kinds = schema(q)
        for t in selected:
            q.execute('SELECT * FROM ' + ident(t))
            rows = sorted(json.dumps(normalize(r, kinds[t]), sort_keys=True, ensure_ascii=True, default=str) for r in q.fetchall())
            result[t] = hashlib.sha256('\n'.join(rows).encode()).hexdigest()
    c.close()
    return result


def execute(name, n, expect=None):
    output, errors = query(name, source(n), force=True)
    if expect:
        assert expect in errors, (n, errors[:600])
    else:
        assert not errors, (n, errors[:1600])
    return output


def fault_case(name, selected, n, table, predicate, column, expression, expected):
    baseline = signature(name, selected)
    c = connect(name)
    with c.cursor() as q:
        q.execute('SELECT * FROM ' + ident(table) + ' WHERE ' + predicate + ' LIMIT 1')
        row = q.fetchone()
        assert row is not None, (n, table, column)
        preserve_time = ',update_time=update_time' if 'update_time' in row else ''
        q.execute('UPDATE ' + ident(table) + ' SET ' + ident(column) + '=' + expression + preserve_time + ' WHERE id=%s', (row['id'],))
    mutated = signature(name, selected)
    assert baseline != mutated
    try:
        execute(name, n, expected)
        assert mutated == signature(name, selected), 'Rejected replay changed persistent state'
    finally:
        with c.cursor() as q:
            q.execute('UPDATE ' + ident(table) + ' SET ' + ident(column) + '=%s' + preserve_time + ' WHERE id=%s', (row[column], row['id']))
        c.close()
    assert baseline == signature(name, selected)
    print('PASS fault/no-write:', n, table, column, flush=True)


def fault_tests(name, selected):
    cases = [
        (298, 'zsjos_lead', "tenant_id=1 AND deleted=0", 'invalid_reason', "'wechat_add_failed'", 'normalization incomplete'),
        (299, 'zsjos_order', "tenant_id=1 AND payment_method='xiaoe_online'", 'payment_method', "'nopay'", 'V299 later V300'),
        (301, 'zsjos_performance_attribution', "id IN (SELECT b.row_id FROM zsjos_data_repair_backup b JOIN zsjos_performance_attribution p ON p.id=b.row_id JOIN zsjos_lead l ON l.id=p.lead_id WHERE b.repair_key='performance-source-group-20261010' AND p.source_group='self' AND l.source_type IN ('sales_self_sourced','education_self_sourced') AND COALESCE(p.org_source,'')<>'frozen')", 'source_group', "'inbound'", 'recompute incomplete'),
        (303, 'zsjos_order', "tenant_id=1 AND deleted=0", 'submitter_user_id', '232', 'repoint incomplete'),
        (304, 'zsjos_data_repair_backup', "repair_key='legacy-deal-state-v304' AND column_name='__after__'", 'column_name', "'__hidden__'", 'evidence incomplete'),
        (304, 'zsjos_lead', "id IN (SELECT row_id FROM zsjos_data_repair_backup WHERE repair_key='legacy-deal-state-v304' AND table_name='zsjos_lead' AND column_name='__after__')", 'status', "'suspended'", 'V304'),
        (305, 'zsjos_data_repair_backup', "repair_key='order-history-v305' AND table_name='__plan__'", 'column_name', "'__hidden__'", 'lacks reviewed plan'),
        (305, 'zsjos_data_repair_backup', "repair_key='order-history-v305' AND table_name='__plan__'", 'old_value', "JSON_SET(CAST(old_value AS JSON),'$.counts.\"update:zsjos_order\"',9999)", 'counts differ'),
        (305, 'zsjos_order', "id IN (SELECT row_id FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305' AND table_name='zsjos_order' AND column_name='__after__')", 'total_amount', 'total_amount+1', 'business fields conflict'),
        (305, 'zsjos_data_repair_backup', "repair_key='order-identity-v306' AND table_name='zsjos_order_approval_round' AND column_name='__after__' AND row_id IN (SELECT row_id FROM (SELECT row_id FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305' AND table_name='zsjos_order_approval_round' AND column_name='__inserted__') scoped)", 'column_name', "'__hidden__'", 'later repair evidence incomplete'),
        (306, 'zsjos_data_repair_backup', "repair_key='order-identity-v306' AND column_name='__before__'", 'column_name', "'__hidden__'", 'V306'),
        (306, 'zsjos_order', "formal_owner_identity='sales' AND id IN (SELECT row_id FROM zsjos_data_repair_backup WHERE repair_key='order-identity-v306' AND table_name='zsjos_order')", 'formal_owner_identity', "'education'", 'before/after conflict'),
    ]
    for case in cases:
        fault_case(name, selected, *case)
    for n in (298, 305, 306):
        baseline = signature(name, selected)
        query(name, f"UPDATE zsjos_module_schema_version SET version='fixture-hidden-{n}' WHERE module_code='core' AND version='V{n}';")
        mutated = signature(name, selected)
        # V306 can repair a missing ledger only after verifying all original images;
        # on the evolved database, it must refuse the strict recovery path.
        execute(name, n, 'ERROR')
        assert mutated == signature(name, selected)
        query(name, f"UPDATE zsjos_module_schema_version SET version='V{n}' WHERE module_code='core' AND version='fixture-hidden-{n}';")
        assert baseline == signature(name, selected)
        print('PASS missing ledger/no-write:', n, flush=True)


def empty_v305(name):
    empty = 'repair_empty_' + uuid.uuid4().hex[:12]
    query(None, f'CREATE DATABASE `{empty}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    selected = ['zsjos_schema_version', 'zsjos_module_schema_version', 'zsjos_data_repair_backup', 'zsjos_order']
    for table in selected:
        query(empty, f'CREATE TABLE `{table}` LIKE `{name}`.`{table}`;')
    baseline = signature(empty, selected)
    execute(empty, 305, 'prerequisites')
    assert baseline == signature(empty, selected), 'Failed preparation registered success'
    query(empty, "INSERT INTO zsjos_schema_version(version,description) VALUES('V304','fixture');"
          "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V304','fixture','fixture','fixture');")
    execute(empty, 305)
    first = signature(empty, selected)
    execute(empty, 305)
    assert first == signature(empty, selected)
    print('PASS V305 empty first/repeat and prepare failure cannot register; retained:', empty, flush=True)


def initial_v306():
    from order_owner_identity_completion import rows_snapshot, scoped_diff
    name, selected = clone()
    c = connect(name)
    key = 'order-identity-v306'
    with c.cursor() as q:
        q.execute("SELECT table_name,row_id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND column_name='__before__'", (key,))
        for row in q.fetchall():
            image = json.loads(row['old_value'])
            column = 'formal_owner_identity' if row['table_name'] == 'zsjos_order' else 'order_snapshot'
            value = image['identity'] if column == 'formal_owner_identity' else json.dumps(image['snapshot'], ensure_ascii=False)
            q.execute('UPDATE ' + ident(row['table_name']) + ' SET ' + ident(column) + '=%s,version=%s,updater=%s,update_time=%s WHERE id=%s',
                      (value, image['version'], image['updater'], image['update_time'], row['row_id']))
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key='fixture-retained-v306' WHERE repair_key=%s", (key,))
        for table in ['zsjos_schema_version', 'zsjos_module_schema_version']:
            q.execute('UPDATE ' + ident(table) + " SET version='fixture-retained-V306' WHERE version='V306'")
    baseline = signature(name, selected)
    before = rows_snapshot(c)
    query(name, "UPDATE zsjos_module_schema_version SET version='fixture-hidden-V305' WHERE module_code='core' AND version='V305';")
    hidden = signature(name, selected)
    execute(name, 306, 'V306 requires V305')
    assert hidden == signature(name, selected)
    query(name, "UPDATE zsjos_module_schema_version SET version='V305' WHERE version='fixture-hidden-V305';")
    query(name, "CREATE TRIGGER fixture_v306_fail BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture second ledger failure';")
    execute(name, 306, 'fixture second ledger failure')
    assert baseline == signature(name, selected), 'Failed V306 advanced state or a success ledger'
    query(name, 'DROP TRIGGER fixture_v306_fail;')
    execute(name, 306)
    assert scoped_diff(before, rows_snapshot(c)) == {'zsjos_order': 286, 'zsjos_order_approval_round': 286}
    after = signature(name, selected)
    execute(name, 306)
    assert after == signature(name, selected)
    with c.cursor() as q:
        q.execute("SELECT row_id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name='zsjos_order' AND column_name='__before__' LIMIT 1", (key,))
        row = q.fetchone()
        old = json.loads(row['old_value'])
        q.execute('UPDATE zsjos_order SET formal_owner_identity=%s,version=%s,updater=%s,update_time=%s WHERE id=%s',
                  (old['identity'], old['version'], old['updater'], old['update_time'], row['row_id']))
    execute(name, 306)
    assert after == signature(name, selected), 'V306 partial recovery differs from saved result'
    with c.cursor() as q:
        q.execute('UPDATE zsjos_order SET version=version+1,update_time=update_time WHERE id=%s', (row['row_id'],))
    later = signature(name, selected)
    execute(name, 306)
    assert later == signature(name, selected), 'V306 replay overwrote later unrelated version'
    c.close()
    print('PASS V306 initial/repeat/second-ledger rollback/recovery/later-version preservation:', name, flush=True)


def main():
    name, selected = clone() if len(sys.argv) == 1 else (sys.argv[1], tables())
    before = signature(name, selected)
    for n in ORDER:
        print('Replay', n, flush=True)
        execute(name, n)
    after = signature(name, selected)
    assert before == after, 'Changed tables: ' + str([t for t in before if before[t] != after[t]])
    for n in ORDER:
        execute(name, n)
    assert after == signature(name, selected), 'Second replay changed stored rows'
    print('PASS exact GUI order twice; business/evidence/ledger SHA-256 unchanged', flush=True)
    fault_tests(name, selected)
    empty_v305(name)
    initial_v306()


if __name__ == '__main__':
    main()
