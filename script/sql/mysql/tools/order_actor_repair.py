# -*- coding: utf-8 -*-
"""Reviewed V312 manifest generator and fail-fast local SQL runner.

freeze is read-only against MySQL; it writes identifier/hash-only source metadata.
No passwords or complete business payloads are emitted.
"""
import argparse
import hashlib
import json
from pathlib import Path

from order_history_repair import connect, ident, statements

ROOT = Path(__file__).resolve().parents[4]
MANIFEST = Path(__file__).with_name('order_actor_repair_manifest.json')
SQL = ROOT / 'script/sql/mysql/migrations/V312__order_actor_snapshot_repair.sql'
KEY = 'order-actors-v312'
CORRECT = {
    22: 'OR202606040001 OR202609120002 OR202609120003',
    23: 'OR202607220003',
    61: 'OR202607290003 OR202607310005 OR202609080003',
    41: 'OR202608180002', 294: 'OR202608200002',
    60: 'OR202608240007 OR202609090005',
    17: 'OR202608280005 OR202608310004 OR202608310009 OR202609020001 OR202609020002 OR202609070002 OR202609070005',
    291: 'OR202609140007', 400: 'OR202609030004',
    30: 'OR202609160008', 15: 'OR202608280008',
}
KEEP = {
    400: 'OD202609221103100002',
    309: 'OR202607080001 OR202607120006 OR202607140001 OR202607300001 OR202607310001',
    37: 'OR202608280011 OR202609010002 OR202609010006',
    30: 'OR202609040001 OR202609100001 OR202609110007 OR202609160007',
    27: 'OR202609040009 OR202609110005 OR202609110012 OR202609130003 OR202609160015 OR202609170018 OR202609180006 OR202609190002 OR202609190003 OR202609190004',
}
MISSING = {
    60: 'OD202609211342490013 OD202609211344150014 OD202609211429220016 OD202609211429550017 OD202609211430220018 OD202609211435200019 OD202609211437270020 OD202609211713390022',
    294: 'OD202609211349340015', 51: 'OD202609211645550021',
    61: 'OD202609211718510023 OR202609180018 OR202609180021',
    291: 'OR202609180017 OR202609180020',
}
# Historical imported rounds identified in the read-only audit, never a live BPM starter.
ROUND_ONLY = ('OR202609110002 OR202609160012 OR202609160017 OR202609160018 OR202609160019 '
    'OR202609170008 OR202609170010 OR202609170014 OR202609170015 OR202609180007 OR202609180009 '
    'OR202609180011 OR202609180012 OR202609180013 OR202609180014 OR202609180015 OR202609180016 '
    'OR202609180019 OR202609180022 OR202609180023 OR202609180024 OR202609180026 OR202609190001 '
    'OR202609200005 OR202609200013 OR202609200016 OR202609200017 OR202609200019 OR202609200020 '
    'OR202609200021 OR202609200022 OR202609200025 OR202609200026 OR202609200027 OR202609200030').split()


def freeze():
    targets = {n: (uid, group) for group, values in [('correct', CORRECT), ('keep', KEEP), ('missing', MISSING)]
               for uid, numbers in values.items() for n in numbers.split()}
    for n in ROUND_ONLY:
        targets.setdefault(n, (None, 'round'))
    c = connect()
    with c.cursor() as q:
        q.execute('START TRANSACTION READ ONLY')
        q.execute("SELECT id,nickname,dept_id,SHA2(nickname,256) name_sha FROM system_users WHERE tenant_id=1 AND deleted=0 AND status=0")
        users = {r['id']: r for r in q.fetchall()}
        records = []
        for no, (target, group) in sorted(targets.items()):
            q.execute("""SELECT o.id,o.order_no,o.status,o.submitter_user_id,o.formal_sales_user_id,
                o.formal_owner_identity,o.submitter_center_type,o.current_approval_round_id,
                SHA2(CAST(o.imported_actor_snapshot AS CHAR),256) imported_sha,
                r.round_no,r.submitted_by_user_id,r.process_definition_key,r.process_instance_id,
                SHA2(r.order_snapshot,256) snapshot_sha,
                r.order_snapshot->>'$.submitter.name' snapshot_name
                FROM zsjos_order o LEFT JOIN zsjos_order_approval_round r
                ON r.id=o.current_approval_round_id AND r.order_id=o.id AND r.tenant_id=1 AND r.deleted=0
                WHERE o.tenant_id=1 AND o.deleted=0 AND o.order_no=%s""", (no,))
            rows = q.fetchall()
            assert len(rows) == 1, 'Manifest order missing or duplicated'
            row = rows[0]
            target = target or row['submitter_user_id']
            assert target in users
            formal = target if no in ('OR202606040001','OR202609030004','OR202608280008','OR202609160008') else row['formal_sales_user_id']
            repair_round = (group != 'keep' and row['process_definition_key'] == 'legacy-finance-review'
                            and not row['process_instance_id'] and row['submitted_by_user_id'] != target)
            if group == 'round':
                assert repair_round and row['snapshot_name'] == users[target]['nickname']
            row.pop('snapshot_name'); row.pop('id'); row.pop('current_approval_round_id')
            row.update(target=target, formal_target=formal, group=group, repair_round=repair_round,
                       name_sha=users[target]['name_sha'])
            records.append(row)
        assert sum(r['repair_round'] for r in records) == 51
        q.execute("""SELECT a.user_id,a.dept_id,a.center_id,a.assignment_id,a.org_source,
             SHA2(a.user_name,256) name_sha FROM zsjos_performance_attribution a JOIN zsjos_order o
             ON o.id=a.fact_id AND o.tenant_id=a.tenant_id WHERE o.order_no='OR202609160008'
             AND o.tenant_id=1 AND o.deleted=0 AND a.fact_type='ORDER' AND a.deleted=0""")
        attrs = q.fetchall(); assert len(attrs) == 1 and attrs[0]['user_id'] == 37
    c.rollback(); c.close()
    MANIFEST.write_text(json.dumps({'version':312,'orders':records,'attribution':attrs[0]}, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    print('Frozen manifest:', len(records), 'orders; 51 imported round references')


def literal(value):
    if value is None: return 'NULL'
    if isinstance(value, bool): return '1' if value else '0'
    if isinstance(value, int): return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def run(c):
    with c.cursor() as q:
        for stmt in statements(SQL.read_text(encoding='utf-8')):
            q.execute(stmt)
    print('V312 SQL completed')


def main():
    p = argparse.ArgumentParser(); p.add_argument('command', choices=['freeze','generate','preflight','apply','verify','apply-local-reviewed'])
    p.add_argument('--database', default='ruoyi-vue-pro'); a = p.parse_args()
    if a.command == 'freeze': freeze(); return
    if a.command == 'generate':
        from order_actor_repair_sql import generate
        generate(); return
    if a.command == 'apply-local-reviewed':
        if a.database != 'ruoyi-vue-pro': raise ValueError('Reviewed target is the local development database')
        from order_actor_repair_local import apply_local
        apply_local(); return
    c = connect(a.database)
    try:
        if a.command == 'preflight':
            from order_actor_repair_verify import preflight
            print(json.dumps(preflight(c),ensure_ascii=True))
        elif a.command == 'apply': run(c)
        else:
            from order_actor_repair_verify import verify
            print(json.dumps(verify(c),ensure_ascii=True))
    finally: c.close()


if __name__ == '__main__': main()
