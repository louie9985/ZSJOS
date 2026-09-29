#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only cashback candidate-query profile. Never prints names, IDs, credentials or raw plans.

Uses the existing MySQL container's root environment internally. Measures SQL candidate selection,
not authenticated endpoint latency. No DDL, data writes, grants, or version-ledger changes.
"""
import argparse
import json
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='yudao-mysql')
    parser.add_argument('--database', default='ruoyi-vue-pro')
    parser.add_argument('--tenant', type=int, default=1)
    parser.add_argument('--samples', type=int, default=20)
    args = parser.parse_args()
    if args.tenant < 0 or not 1 <= args.samples <= 100:
        parser.error('tenant must be nonnegative; samples must be between 1 and 100')

    def sql(statement):
        command = ['docker', 'exec', '-i', args.container, 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names "$@"',
                   '--', '--database', args.database]
        result = subprocess.run(command, input=statement.encode('utf-8'), capture_output=True)
        if result.returncode:
            raise RuntimeError('Read-only MySQL profiling failed; inspect local connection configuration (payload suppressed).')
        return result.stdout.decode('utf-8')

    scope = f'c.tenant_id={args.tenant} AND c.deleted=0'
    setup = ('SET NAMES utf8mb4; START TRANSACTION READ ONLY; '
             'SET @needle=(SELECT LOWER(p.name) FROM zsjos_cashback c JOIN zsjos_partner p '
             'ON p.id=c.partner_id AND p.tenant_id=c.tenant_id AND p.deleted=0 '
             f'WHERE {scope} AND p.name IS NOT NULL AND p.name<>\'\' ORDER BY c.id LIMIT 1); ')
    # LOCATE treats %, _ and backslash literally; Java performs the final exact normalization in production.
    match = ('(EXISTS (SELECT 1 FROM zsjos_partner p WHERE p.id=c.partner_id AND p.tenant_id=c.tenant_id '
             'AND p.deleted=0 AND LOCATE(@needle,LOWER(p.name))>0) '
             'OR EXISTS (SELECT 1 FROM zsjos_lead l WHERE l.id=c.lead_id AND l.tenant_id=c.tenant_id '
             'AND l.deleted=0 AND LOCATE(@needle,LOWER(l.submitted_name))>0) '
             'OR EXISTS (SELECT 1 FROM zsjos_order o WHERE o.id=c.order_id AND o.tenant_id=c.tenant_id '
             'AND o.deleted=0 AND LOCATE(@needle,LOWER(o.student_name))>0))')
    projection = 'SELECT c.id,c.lead_id,c.order_id,c.partner_id,c.beneficiary_user_id FROM zsjos_cashback c WHERE '
    queries = {'old_unfiltered_references': projection + scope,
               'new_name_candidates_first_batch': projection + scope + ' AND ' + match + ' AND c.id>0 ORDER BY c.id LIMIT 256'}
    output = {'scope': 'candidate SQL only; real business data read-only; excludes API/permission/projection/network time',
              'mysql_version': sql('SELECT VERSION();').strip(), 'samples': args.samples}
    counts = sql(setup + 'SELECT COUNT(*) FROM zsjos_cashback c WHERE ' + scope + '; SELECT COUNT(*) FROM zsjos_cashback c WHERE ' + scope + ' AND ' + match + '; ROLLBACK;').splitlines()
    output['scoped_cashbacks'], output['representative_name_candidates'] = map(int, counts)
    for label, query in queries.items():
        times = []
        for _ in range(args.samples):
            plan = sql(setup + 'EXPLAIN ANALYZE ' + query + '; ROLLBACK;')
            timing = re.search(r'actual time=[\d.]+\.\.([\d.]+)', plan)
            if not timing:
                raise RuntimeError('MySQL did not return an actual execution timing; raw plan suppressed.')
            times.append(float(timing.group(1)))
        times.sort()
        output[label] = {'server_p50_ms': times[(len(times)-1)//2],
                         'server_p95_ms': times[min(len(times)-1, int(len(times)*.95))]}
    output['cashback_indexes'] = sql("SELECT INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='zsjos_cashback' GROUP BY INDEX_NAME ORDER BY INDEX_NAME;").splitlines()
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
