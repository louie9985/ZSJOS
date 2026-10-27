#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prepare review-only exception comparisons without changing import decisions."""
import argparse
from collections import defaultdict
import json
from pathlib import Path

import openpyxl


def prepare(workbook_path, source_path):
    wb = openpyxl.load_workbook(workbook_path)
    records = []
    for si, sheet in enumerate(wb.worksheets[:3]):
        for row in sheet.iter_rows(min_row=2, values_only=True):
            if not row[0]:
                continue
            values = list(row[1:45])
            records.append(dict(sheet=row[45], row=int(row[46]), sheetIndex=si, values=values,
                                reason=row[0], mobile=str(values[1]).strip()))
    groups = defaultdict(list)
    for record in records:
        groups[record['mobile']].append(record)
    comparison = []
    for group_index, group in enumerate(sorted((rs for rs in groups.values() if len(rs) > 1),
                                              key=lambda rs: min((r['sheetIndex'], r['row']) for r in rs)), 1):
        group.sort(key=lambda r: (r['sheetIndex'], r['row']))
        fields = [(0, '姓名'), (3, '课程'), (4, '规格'), (5, '班级'), (6, '规划师')]
        differences = [(index, label) for index, label in fields
                       if len({str(r['values'][index]).strip() for r in group}) > 1]
        refs = '、'.join(f"{r['sheet'].split('-')[0]}第{r['row']}行" for r in group)
        detail = '；'.join(label + '不同：' + ' ↔ '.join(dict.fromkeys(str(r['values'][index]).strip() for r in group))
                          for index, label in differences)
        for r in group:
            original_reason = r['reason']
            primary = '同手机号规格或规划师冲突' in original_reason or original_reason == '姓名/联系方式待确认'
            r['reason'] = f"对比组{group_index:02d}｜" + (detail if primary else original_reason + '；关联记录差异：' + detail)
            r['reason'] += '\n关联记录：' + refs
            r['reviewGroup'] = group_index
            comparison.append(r)
    ordered = []
    for si in range(3):
        rows = [r for r in records if r['sheetIndex'] == si]
        for r in rows:
            if r.get('reviewGroup'):
                r['reviewOrder'] = min(x['row'] for x in rows if x.get('reviewGroup') == r['reviewGroup'])
            else:
                r['reviewOrder'] = r['row']
        ordered.extend(sorted(rows, key=lambda r: (r['reviewOrder'], r['row'])))
    assert len(ordered) == len(records)
    for r in records:
        if '同手机号规格或规划师冲突' in r['reason']:
            raise ValueError('Conflict row has no counterpart; inspect source before exporting')
    return dict(source=str(source_path.resolve()), exceptions=ordered, comparisonRows=comparison,
                duplicates=[{}] * 5, reviewGroups=len({r['reviewGroup'] for r in comparison}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workbook', type=Path, required=True)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    result = prepare(a.workbook, a.source)
    with a.output.open('x', encoding='utf-8') as out:
        json.dump(result, out, ensure_ascii=False)
    print(json.dumps({'exceptions': len(result['exceptions']), 'comparisonRows': len(result['comparisonRows']), 'reviewGroups': result['reviewGroups']}))
