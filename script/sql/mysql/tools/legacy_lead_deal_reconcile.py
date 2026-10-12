# -*- coding: utf-8 -*-
"""V304 read-only preflight and recovery inspection; explicit local apply with backup.

Uses the existing local Docker MySQL credential internally, never prints credentials.
Other environments execute the portable SQL via their established UTF-8 migration runner.
"""
import argparse
import datetime as dt
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION_PATH = SQL / 'migrations/V304__legacy_lead_deal_state_reconcile.sql'
MIGRATION = MIGRATION_PATH.read_text(encoding='utf-8')
KEY = 'legacy-deal-state-v304'
LOCAL_DB = 'ruoyi-vue-pro'
TABLES = ['zsjos_person', 'zsjos_lead', 'zsjos_opportunity', 'zsjos_order',
          'zsjos_lead_intended_product', 'zsjos_business_task', 'zsjos_lead_follow_up_record',
          'zsjos_opportunity_follow_up_record', 'zsjos_lead_aging_pool_cycle',
          'zsjos_lead_public_sea_record', 'zsjos_order_approval_round',
          'zsjos_data_repair_backup', 'zsjos_schema_version', 'zsjos_module_schema_version']


def ident(value):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', value):
        raise ValueError('Invalid database/table identifier')
    return '`' + value + '`'


def query(database, sql, force=False):
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B --raw'
    if force:
        command += ' --force'
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c', command],
        input=('SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;\n' +
               (f'USE {ident(database)};\n' if database else '') + sql).encode('utf-8'),
        capture_output=True)
    out, err = result.stdout.decode('utf-8'), result.stderr.decode('utf-8')
    if result.returncode or 'ERROR' in err:
        if force:
            return out.strip(), err
        raise RuntimeError(err.strip() + ('\n' + out.strip() if out.strip() else ''))
    return out.strip(), err


def section(name):
    value = MIGRATION.split('-- BEGIN V304 ' + name + '\n', 1)[1].split('-- END V304 ' + name, 1)[0].strip()
    return value.replace('  ;\n  INSERT INTO zsjos_v304_conflicts\n', '  UNION ALL\n')


def preflight_sql():
    return ('WITH zsjos_v304_target AS (' + section('TARGET SELECT') + '), conflicts AS (' +
            section('CONFLICT SELECT') + ') SELECT l.lead_no,o.order_no,l.status,'
            "COALESCE(p.status,'MISSING'),t.effective_at,COALESCE(c.reason,'READY') "
            'FROM zsjos_v304_target t LEFT JOIN zsjos_lead l ON l.id=t.lead_id '
            'LEFT JOIN zsjos_order o ON o.id=t.order_id '
            'LEFT JOIN zsjos_opportunity p ON p.id=t.opportunity_id '
            'LEFT JOIN conflicts c ON c.lead_id=t.lead_id ORDER BY l.lead_no,c.reason;')


def preflight(database):
    exists = query(database, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                   "AND table_name='zsjos_data_repair_backup';")[0] == '1'
    if not exists:
        # Pure SELECT counterpart of the optional backup table, without any DDL.
        sql = preflight_sql().replace('WITH zsjos_v304_target AS (',
            "WITH zsjos_data_repair_backup AS (SELECT '' repair_key,'' table_name,0 row_id,"
            "'' column_name,'' old_value WHERE FALSE), zsjos_v304_target AS (")
    else:
        sql = preflight_sql()
    rows = query(database, sql)[0]
    prerequisites = query(database, "SELECT (SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V303'),"
                         "(SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V303');")[0]
    return {'database': database, 'v303_ledgers': prerequisites, 'rows': rows,
            'ready': prerequisites == '1\t1' and all(x.endswith('\tREADY') for x in rows.splitlines())}


def json_rows(database, table):
    columns = query(database, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                    f"AND table_name='{table}' ORDER BY ordinal_position;")[0].splitlines()
    if not columns:
        return {}
    pairs = ','.join("'" + c + "'," + (f'CAST({ident(c)} AS UNSIGNED)' if c == 'deleted' else ident(c))
                     for c in columns)
    keys = ['id'] if 'id' in columns else [c for c in ('module_code', 'version') if c in columns]
    raw = query(database, f'SELECT JSON_OBJECT({pairs}) FROM {ident(table)} ORDER BY ' + ','.join(keys) + ';')[0]
    return {'/'.join(str(row[k]) for k in keys): row for row in map(json.loads, raw.splitlines())}


def snapshot(database):
    raw_columns = query(database, 'SELECT table_name,column_name FROM information_schema.columns '
                        'WHERE table_schema=DATABASE() AND table_name IN (' +
                        ','.join("'" + t + "'" for t in TABLES) + ') ORDER BY table_name,ordinal_position;')[0]
    columns = {t: [] for t in TABLES}
    for line in raw_columns.splitlines():
        table, column = line.split('\t')
        columns[table].append(column)
    statements, keys = [], {}
    for table, names in columns.items():
        if not names:
            continue
        keys[table] = ['id'] if 'id' in names else [c for c in ('module_code', 'version') if c in names]
        pairs = ','.join("'" + c + "'," + (f'CAST({ident(c)} AS UNSIGNED)' if c == 'deleted' else ident(c)) for c in names)
        statements.append(f"SELECT '{table}',JSON_OBJECT({pairs}) FROM {ident(table)} ORDER BY " + ','.join(keys[table]) + ';')
    result = {t: {} for t in TABLES}
    read_sql = 'SET TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION READ ONLY;\n' + '\n'.join(statements) + '\nCOMMIT;'
    for line in query(database, read_sql)[0].splitlines():
        table, encoded = line.split('\t', 1)
        row = json.loads(encoded)
        result[table]['/'.join(str(row[k]) for k in keys[table])] = row
    return result


def signature(snapshot_value):
    return hashlib.sha256(json.dumps(snapshot_value, ensure_ascii=True, sort_keys=True).encode()).hexdigest()


def dump(database, destination):
    command = ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 '
               '--single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF '
               '--hex-blob -uroot ' + ident(database) + ' ' + ' '.join(TABLES))
    # All shell words are repository-owned constants or validated identifiers.
    command = command.replace(ident(database), "'" + database + "'")
    result = subprocess.run(['docker', 'exec', 'yudao-mysql', 'sh', '-c', command], capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8'))
    destination.write_bytes(result.stdout)


def verify_diff(before, after):
    manifests = [json.loads(b['old_value']) | {'leadId': b['row_id']}
                 for b in after['zsjos_data_repair_backup'].values()
                 if b['repair_key'] == KEY and b['column_name'] == '__target__']
    leads = {str(x['leadId']) for x in manifests}
    orders = {str(x['orderId']) for x in manifests}
    opportunities = {str(after['zsjos_order'][oid]['opportunity_id']) for oid in orders}
    allowed = {'zsjos_lead': ({'status', 'version', 'updater', 'update_time'}, leads),
               'zsjos_order': ({'opportunity_id', 'version', 'updater', 'update_time'}, orders),
               'zsjos_opportunity': ({'status', 'won_at', 'version', 'updater', 'update_time'}, opportunities)}
    counts = {}
    for table, old in before.items():
        new = after[table]
        assert set(old).issubset(new), f'{table}: rows removed'
        changed = 0
        for row_id, old_row in old.items():
            if old_row == new[row_id]:
                continue
            fields, ids = allowed.get(table, (set(), set()))
            assert row_id in ids, f'{table}: out-of-scope change'
            assert {k for k in old_row if old_row[k] != new[row_id][k]} <= fields, f'{table}: unexpected field change'
            changed += 1
        inserted = set(new) - set(old)
        if table == 'zsjos_opportunity':
            assert inserted <= opportunities
            assert all(new[i]['creator'] == 'V304-legacy-deal-backfill' for i in inserted)
        elif table == 'zsjos_data_repair_backup':
            assert all(new[i]['repair_key'] == KEY for i in inserted)
        elif table in ('zsjos_schema_version', 'zsjos_module_schema_version'):
            assert all(new[i]['version'] == 'V304' for i in inserted)
        else:
            assert not inserted, f'{table}: unexpected insertion'
        if changed or inserted:
            counts[table] = {'updated': changed, 'inserted': len(inserted)}
    for item in manifests:
        lead = after['zsjos_lead'][str(item['leadId'])]
        order = after['zsjos_order'][str(item['orderId'])]
        opp = after['zsjos_opportunity'][str(order['opportunity_id'])]
        assert lead['status'] == opp['status'] == 'won'
        assert opp['lead_id'] == lead['id'] and opp['person_id'] == lead['person_id']
        assert opp['won_at'] == order['effective_at']
    return counts


def recovery_check(database):
    state = snapshot(database)
    key_map = {'status': 'status', 'wonAt': 'won_at', 'version': 'version', 'updater': 'updater',
               'updateTime': 'update_time', 'opportunityId': 'opportunity_id'}
    result = []
    for b in state['zsjos_data_repair_backup'].values():
        if b['repair_key'] != KEY or b['column_name'] != '__after__':
            continue
        row = state[b['table_name']].get(str(b['row_id']))
        expected = json.loads(b['old_value'])
        safe = row is not None and all(row[key_map[k]] == v for k, v in expected.items())
        result.append({'table': b['table_name'], 'internalRowId': b['row_id'], 'matchesAfterImage': safe})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['preflight', 'recovery-check', 'apply-local'])
    parser.add_argument('--database', default=LOCAL_DB)
    args = parser.parse_args()
    ident(args.database)
    if args.mode == 'recovery-check':
        print(json.dumps(recovery_check(args.database), ensure_ascii=False, indent=2))
        return
    result = preflight(args.database)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if args.mode != 'apply-local':
        return
    if args.database != LOCAL_DB:
        raise SystemExit('apply-local only accepts the approved local development database')
    if not result['ready']:
        raise SystemExit('Preflight failed; no writes performed')
    directory = ROOT / 'backups/mysql' / ('v304-' + dt.datetime.now().strftime('%Y%m%d-%H%M%S'))
    directory.mkdir(parents=True, exist_ok=False)
    dump(args.database, directory / 'before.sql')
    before = snapshot(args.database)
    (directory / 'before.json').write_text(json.dumps(before, ensure_ascii=True), encoding='utf-8')
    output = query(args.database, MIGRATION)[0]
    after = snapshot(args.database)
    counts = verify_diff(before, after)
    report = {'migrationSha256': hashlib.sha256(MIGRATION_PATH.read_bytes()).hexdigest(),
              'beforeSha256': signature(before), 'afterSha256': signature(after),
              'counts': counts, 'output': output, 'backup': str(directory)}
    (directory / 'verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
