"""UTF-8. Local, explicitly scoped current-organization compatibility backfill.

Requires V276. This is not a bootstrap/migration: historical affiliation cannot
be reconstructed. A frozen private manifest is retained outside the repository.
Existing snapshots, orders, accounts and permissions are never updated.
Rollback requires separate approval and removal of only the recorded inserted IDs;
later business records must not be removed. Never rerun by widening the manifest.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
from test_positioning_single_source import query

DB = 'ruoyi-vue-pro'
TABLE = 'zsjos_performance_attribution'
MARKER = 'current-org-backfill-20260923'
OUT = Path('D:/ZSJ-OS-backups/sales-performance-order-backfill-20260923')
FIELDS = ['fact_type', 'fact_id', 'user_id', 'user_name', 'dept_id', 'dept_name',
          'center_id', 'center_name', 'lead_id', 'source_group', 'creator', 'updater', 'tenant_id']


def candidates():
    # Disabled accounts retain affiliation; soft-deleted/missing organizations do not.
    sql = """SELECT JSON_OBJECT('fact_type','ORDER','fact_id',o.id,
      'user_id',u.id,'user_name',u.nickname,'dept_id',d.id,'dept_name',d.name,
      'center_id',c.id,'center_name',c.name,'lead_id',o.lead_id,
      'source_group',IF(o.order_type='repurchase','repurchase','unknown'),
      'creator','current-org-backfill-20260923','updater','current-org-backfill-20260923',
      'tenant_id',1) FROM zsjos_order o
      JOIN system_users u ON u.id=o.formal_sales_user_id AND u.tenant_id=o.tenant_id AND u.deleted=0
      JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=o.tenant_id AND d.deleted=0
      JOIN zsjos_performance_org m ON m.dept_id=d.id AND m.tenant_id=o.tenant_id AND m.deleted=0
      JOIN system_dept c ON c.id=m.center_id AND c.tenant_id=o.tenant_id AND c.deleted=0
      WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='effective'
      AND NOT EXISTS(SELECT 1 FROM zsjos_performance_attribution a
        WHERE a.tenant_id=1 AND a.fact_type='ORDER' AND a.fact_id=o.id AND a.deleted=0)
      ORDER BY o.id;"""
    return [json.loads(line) for line in query(DB, sql).splitlines() if line]


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    return "CONVERT(0x" + value.encode('utf-8').hex() + " USING utf8mb4)"


def insert_sql(rows):
    statements = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
    for row in rows:
        statements.append(f"INSERT INTO {TABLE} ({','.join(FIELDS)}) SELECT " +
                          ','.join(literal(row[field]) for field in FIELDS) +
                          f" WHERE NOT EXISTS(SELECT 1 FROM {TABLE} WHERE tenant_id=1 " +
                          f"AND fact_type='ORDER' AND fact_id={row['fact_id']} AND deleted=0);")
    return '\n'.join(statements + ['COMMIT;'])


def snapshot(db, where):
    # HEX makes persisted Chinese bytes verifiable independently of terminal encoding.
    return query(db, f"SELECT fact_id,user_id,dept_id,center_id,HEX(user_name),HEX(dept_name),"
                 f"HEX(center_name),source_group,creator FROM {TABLE} WHERE {where} ORDER BY fact_id;")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = OUT / 'manifest.json'
    if manifest.exists():
        rows = json.loads(manifest.read_text(encoding='utf-8'))
    else:
        rows = candidates()
        assert len(rows) == 263, f'Candidate scope changed: {len(rows)}; inspect before proceeding'
        manifest.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    assert len(rows) == 263 and len({r['fact_id'] for r in rows}) == 263
    assert all(r['tenant_id'] == 1 and r['creator'] == MARKER for r in rows)
    ids = ','.join(str(r['fact_id']) for r in rows)
    scope = f"tenant_id=1 AND fact_type='ORDER' AND fact_id IN ({ids}) AND deleted=0"
    sql = insert_sql(rows)
    (OUT / 'frozen-backfill.sql').write_text(sql, encoding='utf-8')
    controlled = 'performance_backfill_verify_' + dt.datetime.now().strftime('%Y%m%d%H%M%S')
    query(DB, f'CREATE DATABASE `{controlled}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    query(controlled, f'CREATE TABLE {TABLE} LIKE `{DB}`.{TABLE};')
    query(controlled, sql)
    first = snapshot(controlled, scope)
    assert len(first.splitlines()) == 263
    query(controlled, sql)
    assert first == snapshot(controlled, scope), 'Not repeatable'
    assert query(controlled, f'SELECT COUNT(*) FROM {TABLE};').strip() == '263'
    print('PASS controlled exact-scope insertion, repeatability and persisted UTF-8:', controlled)
    if not args.apply:
        return
    assert candidates() == rows, 'Live candidates changed; do not apply a stale manifest'
    orders_before = query(DB, 'SELECT * FROM zsjos_order ORDER BY id;')
    attribution_before = query(DB, f'SELECT * FROM {TABLE} ORDER BY id;')
    (OUT / 'orders-before.tsv').write_text(orders_before, encoding='utf-8')
    (OUT / 'attribution-before.tsv').write_text(attribution_before, encoding='utf-8')
    query(DB, sql)
    assert snapshot(DB, scope) == first, 'Local result differs from verified frozen values'
    assert query(DB, 'SELECT * FROM zsjos_order ORDER BY id;') == orders_before, 'Order rows changed'
    assert query(DB, f"SELECT * FROM {TABLE} WHERE NOT ({scope}) ORDER BY id;") == attribution_before
    query(DB, sql)
    assert snapshot(DB, scope) == first, 'Local repeatability mismatch'
    totals = query(DB, f"SELECT COUNT(*),SUM(o.total_amount) FROM zsjos_order o "
                   f"JOIN {TABLE} a ON a.fact_type='ORDER' AND a.fact_id=o.id AND a.tenant_id=o.tenant_id AND a.deleted=0 "
                   f"WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='effective' AND o.id IN ({ids});")
    expected = query(DB, f"SELECT COUNT(*),SUM(total_amount) FROM zsjos_order WHERE tenant_id=1 AND deleted=0 "
                     f"AND status='effective' AND id IN ({ids});")
    assert totals == expected
    (OUT / 'verification.txt').write_text('Controlled database: ' + controlled + '\n' +
        'PASS 263 exact frozen snapshots; totals match; original orders/existing snapshots unchanged; repeatable; UTF-8 HEX matches.\n' +
        'Count / amount: ' + totals, encoding='utf-8')
    print('PASS local: 263 snapshots, amounts match, original orders and prior snapshots unchanged')


if __name__ == '__main__':
    main()
