# -*- coding: utf-8 -*-
"""V307 fail-fast SQL runner. Local application must be separately authorized."""
import argparse
from pathlib import Path
from order_history_repair import connect, statements

SQL = Path(__file__).resolve().parents[4] / 'script/sql/mysql/migrations/V307__lead_provider_snapshot_repair.sql'
KEY = 'lead-provider-v307'


def run(conn):
    with conn.cursor() as cur:
        for statement in statements(SQL.read_text(encoding='utf-8')):
            cur.execute(statement)


def audit(conn):
    with conn.cursor() as q:
        q.execute("""SELECT COUNT(*) total,
          SUM(provider_owner_name_snapshot IS NOT NULL) named,
          SUM(provider_owner_name_snapshot IS NULL) unnamed
          FROM zsjos_lead WHERE tenant_id=1 AND deleted=0""")
        result = {k: int(v or 0) for k, v in q.fetchone().items()}
        q.execute("SELECT COUNT(*) n FROM zsjos_data_repair_backup WHERE repair_key=%s", (KEY,))
        result['backupRows'] = q.fetchone()['n']
        q.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V307'")
        result['globalLedger'] = q.fetchone()['n']
        q.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE module_code='core' AND version='V307'")
        result['moduleLedger'] = q.fetchone()['n']
        return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['audit', 'apply'])
    parser.add_argument('--database', default='ruoyi-vue-pro')
    args = parser.parse_args()
    connection = connect(database=args.database)
    try:
        if args.mode == 'apply':
            run(connection)
        print(audit(connection))
    finally:
        connection.close()
