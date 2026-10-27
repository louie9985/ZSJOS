#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build a private, review-only exception plan with exact name/contact comparisons."""
import argparse
from collections import Counter
import json
from pathlib import Path

from exception_review import prepare as review_groups
from import_students import connect, fetch, save_private


def audit(plan_path, comparison_source, output):
    plan = json.loads(plan_path.read_text(encoding='utf-8'))
    report = dict(plan, reportOnly=True)
    report['exceptions'] = [dict(r) for r in plan['exceptions']]
    prior = review_groups(comparison_source, Path(plan['source']))
    source_groups = {(r['sheetIndex'], r['row']): r for r in prior['exceptions'] if r.get('reviewGroup')}
    surviving = Counter(source_groups[(r['sheetIndex'], r['row'])]['reviewGroup']
                        for r in report['exceptions'] if (r['sheetIndex'], r['row']) in source_groups)
    comparison = []
    conn = connect(plan['target']['db'])
    try:
        with conn.cursor() as q:
            for r in report['exceptions']:
                key = (r['sheetIndex'], r['row'])
                group = source_groups.get(key)
                if group and surviving[group['reviewGroup']] > 1:
                    r.update(reason=group['reason'], reviewGroup=group['reviewGroup'], reviewOrder=group['reviewOrder'])
                    comparison.append(r)
                if '同人依据' not in r['reason'] and '多档案' not in r['reason']:
                    continue
                people = fetch(q, 'SELECT id,name,mobile,wechat_id FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND (mobile=%s OR wechat_id=%s) ORDER BY id', (r['mobile'], r['mobile']))
                claims = fetch(q, 'SELECT person_id FROM zsjos_person_contact_claim WHERE tenant_id=1 AND contact_value=%s', (r['mobile'],))
                for claim in claims:
                    if claim['person_id'] is not None and not any(p['id'] == claim['person_id'] for p in people):
                        people.extend(fetch(q, 'SELECT id,name,mobile,wechat_id FROM zsjos_person WHERE tenant_id=1 AND id=%s', (claim['person_id'],)))
                pairs = []
                for p in people:
                    fields = [title for field, title in [('mobile','手机号'),('wechat_id','微信号')] if p[field] == r['mobile']]
                    pairs.append('库中姓名：' + p['name'] + '（命中' + '/'.join(fields) + '）')
                r['reason'] += '\n表格姓名：' + r['name'] + '\n' + '\n'.join(pairs)
    finally:
        conn.rollback()
        conn.close()
    report['comparisonRows'] = sorted(comparison, key=lambda r: (r['reviewGroup'], r['sheetIndex'], r['row']))
    report['reviewGroups'] = len({r['reviewGroup'] for r in comparison})
    save_private(output, report)
    print(json.dumps(dict(exceptions=len(report['exceptions']), comparisonRows=len(comparison), reviewGroups=report['reviewGroups'], reportOnly=True)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--previous-exceptions', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    audit(args.plan, args.previous_exceptions, args.output)
