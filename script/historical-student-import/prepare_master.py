#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prepare one maintained, per-person exception master without database operations."""
import argparse
from collections import defaultdict
import json
from pathlib import Path

import openpyxl

from import_students import read_source, save_private

HEADERS = ['异常原因', '姓名', '手机号', '课程规格', '班级', '规划师', '待确认内容', '原表行号', '处理意见']


def joined(values):
    return '\n'.join(dict.fromkeys(str(v) for v in values if v is not None and str(v)))


def duplicate_order(rows):
    # Adjacency is for human comparison, never an identity merge or a reuse decision.
    links = defaultdict(list)
    for index, row in enumerate(rows):
        for column in (1, 2):
            value = str(row[column] or '').strip()
            if value:
                links[(column, value)].append(index)
    seen, order = set(), []
    for start in range(len(rows)):
        if start in seen:
            continue
        pending, group = [start], set()
        while pending:
            index = pending.pop()
            if index in group:
                continue
            group.add(index)
            for column in (1, 2):
                value = str(rows[index][column] or '').strip()
                pending.extend(other for other in links[(column, value)] if other not in group)
        seen.update(group)
        order.extend(sorted(group))
    return order


def prepare(plan_path, master, output):
    plan = json.loads(plan_path.read_text(encoding='utf-8'))
    source = read_source(plan['source'])
    source_rows = [r for key in ('candidates', 'exceptions', 'duplicates') for r in source[key]]
    old_notes, notes_by_ref = {}, {}
    if master.exists():
        previous = openpyxl.load_workbook(master, data_only=False)
        sheet = previous.worksheets[0]
        if [c.value for c in sheet[1]] == HEADERS:
            for row in sheet.iter_rows(min_row=2, values_only=True):
                if row[8]:
                    old_notes[(str(row[1]), str(row[2]))] = str(row[8])
                    for ref in str(row[7]).splitlines():
                        notes_by_ref[ref] = str(row[8])
        previous.close()
    source_by_ref = {(r['sheetIndex'], r['row']): r for r in source_rows}
    # Keep alternatives as individual rows so the user can select the actual record to retain.
    groups = [((r['name'], r['mobile']), [r]) for r in plan['exceptions']]
    rows, memberships = [], []
    for key, records in groups:
        reasons, details, refs = [], [], []
        for r in records:
            why = r['reason']
            original_why = source_by_ref[(r['sheetIndex'], r['row'])].get('reason', why)
            reference = f"{r['sheet'].split('-')[0]}第{r['row']}行"
            refs.append(reference)
            if '同人依据' in why:
                reasons.append('表格姓名与系统档案姓名不一致')
                comparison = why.split('\n', 1)[1] if '\n' in why else ''
                details.append(comparison + '\n没有订单或服务记录能确认是同一人，请核实姓名和号码。')
            elif '多档案' in why:
                reasons.append('同一号码对应多个系统人员档案')
                comparison = why.split('\n', 1)[1] if '\n' in why else ''
                details.append(comparison + '\n请确认应使用哪个人员档案，不能自动合并。')
            elif '代理信息' in why:
                reasons.append('课程规格填成了代理信息')
                details.append(f"当前填写“{r['sku']}”，请提供实际报读的课程规格。")
            elif '规格未配置' in why:
                reasons.append('系统未配置这项课程规格')
                details.append('请核实应使用的规格；当前按要求不新增配置。')
            elif original_why == '姓名/联系方式待确认':
                reasons.append('表内姓名或手机号存在冲突')
                others = [v for v in source_rows if (v['name'] == r['name'] or v['mobile'] == r['mobile']) and (v['sheet'], v['row']) != (r['sheet'], r['row'])]
                same_phone = [v for v in others if v['mobile'] == r['mobile'] and v['name'] != r['name']]
                if same_phone:
                    details.append('同一手机号还有不同姓名：' + joined(v['name'] for v in same_phone) + '。请确认姓名或手机号是否填错。')
                else:
                    details.append('同名记录填写了不同号码：' + joined([r['mobile'], *(v['mobile'] for v in others)]) + '。请确认是同名不同人还是号码有误。')
            elif '规格冲突' in original_why or '规格或规划师冲突' in original_why:
                reasons.append('同一学员填写了不同课程规格')
                partners = [v for v in plan['exceptions'] if v['mobile'] == r['mobile'] and (v['sheetIndex'],v['row']) != (r['sheetIndex'],r['row'])]
                details.append(joined(f"对照{v['sheet'].split('-')[0]}第{v['row']}行：{v['sku']}；规划师：{v['sourcePlanner']}。" for v in partners)
                               + '\n请确认保留本条、对照记录，还是两条都保留。')
            else:
                reasons.append(why.split('\n')[0])
                details.append(why)
        note = joined(notes_by_ref.get(ref) for ref in refs) or old_notes.get(key, '')
        rows.append([joined(reasons), key[0], key[1], joined(r['sku'] for r in records),
                     joined(r['className'] for r in records), joined(r['sourcePlanner'] for r in records),
                     joined(details), joined(refs), note])
        memberships.append([(r['sheetIndex'], r['row']) for r in records])
    assert sum(map(len, memberships)) == len(plan['exceptions'])
    order = duplicate_order(rows)
    rows = [rows[index] for index in order]
    memberships = [memberships[index] for index in order]
    save_private(output, dict(headers=HEADERS, rows=rows, memberships=memberships,
                             sourceRecordCount=len(plan['exceptions']), masterPath=str(master.resolve())))
    print(json.dumps(dict(masterRows=len(rows), sourceRecords=len(plan['exceptions']), worksheetCount=1)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--master', type=Path, required=True)
    parser.add_argument('--output-json', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.plan, args.master, args.output_json)
