#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read the supplied OOXML log export without emitting payloads or personal data.

The .xls export is actually OOXML. Output contains only worksheet row numbers,
stable issue keys, counts and the source digest. This never edits the workbook.
"""
import argparse
import collections
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}


def rows(path):
    with zipfile.ZipFile(path) as archive:
        shared = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            shared = [''.join(item.itertext()) for item in ET.fromstring(archive.read('xl/sharedStrings.xml'))]
        sheet = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
        for row in sheet.findall('.//m:sheetData/m:row', NS):
            values = [''] * 23
            for cell in row:
                index = 0
                for letter in re.match('[A-Z]+', cell.attrib['r'])[0]:
                    index = index * 26 + ord(letter) - 64
                value = cell.find('m:v', NS)
                text = value.text if value is not None else ''
                if cell.attrib.get('t') == 's':
                    text = shared[int(text)]
                elif cell.attrib.get('t') == 'inlineStr':
                    text = ''.join(node.text or '' for node in cell.findall('.//m:t', NS))
                values[index - 1] = text
            yield int(row.attrib['r']), values


def issue(row):
    url, kind, root = row[6], row[11], row[13]
    if 'Deadlock' in kind:
        return 'task_deadlock'
    if 'HttpMessageNotWritableException' in kind:
        return 'connection_reset' if 'reset' in root.lower() else 'broken_pipe'
    if 'RedisResponseTimeoutException' in root:
        return 'redis_timeout'
    if 'ConnectException' in root:
        return 'redis_connection'
    if 'TimeoutException' in root:
        return 'other_timeout'
    if '/sales-performance/' in url:
        return 'performance_sql'
    if '/work-order/file/upload' in url:
        return 'upload_memory'
    if '/notice/create' in url:
        return 'notice_capacity'
    if '/notice/update' in url:
        return 'notice_attachments'
    if '/gift-config/' in url:
        return 'gift_duplicate'
    if '/demo01-contact/' in url:
        return 'demo_table'
    if 'FlowableIllegalArgumentException' in kind:
        return 'approval_process_filter'
    if '/lead/appeal/' in url:
        return 'appeal_null'
    if '/sales-order/' in url:
        if '/resubmit' in url:
            return 'order_gift_payload'
        if '/supervisor-confirmation/' in url:
            return 'supervisor_paging'
        return 'order_round_null' if 'NullPointerException' in kind else 'order_vouchers'
    if '/eam/asset/get' in url:
        return 'asset_detail'
    if '/eam/asset/export' in url:
        return 'asset_export'
    if '/eam/category/' in url:
        return 'asset_category_import'
    if '/content/version/file/' in url:
        return 'upload_url_capacity'
    if '/hrm/employee/get-import-template' in url:
        return 'excel_area' if 'UnsupportedOperationException' in root else 'excel_empty_dictionary'
    if '/system/social-user/bind' in url:
        return 'wecom_rebind' if 'DuplicateKeyException' in kind else 'openid_capacity'
    if '/student/my' in url:
        return 'student_query_parameters'
    if '/part-api/zsjos/auth/' in url:
        return 'partner_wecom_configuration'
    if '/lead-complaint/' in url:
        return 'complaint_attachment_null'
    if '/lead/create' in url:
        return 'person_number'
    if '/work-order/scene/' in url:
        return 'v184_scene'
    if '/auth/refresh-token' in url:
        return 'refresh_missing_user'
    if '/system/user/import' in url:
        return 'import_mobile'
    if '/infra/file/avatar/' in url:
        return 'file_master_configuration'
    if '/auth/social-auth-redirect' in url:
        return 'auth_extension_null' if 'NullPointerException' in kind else 'admin_wecom_configuration'
    if '/hrm/home/' in url:
        return 'hrm_tables'
    if '/bpm/form/' in url:
        return 'bpm_form_json'
    raise ValueError('Unclassified row; inspect privately before extending the mapping')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    records = list(rows(args.source))[1:]
    assert len(records) == 451 and len({row[0] for _, row in records}) == 451
    groups = collections.defaultdict(list)
    for row_number, row in records:
        groups[issue(row)].append(row_number)
    result = {
        'source_sha256': hashlib.sha256(args.source.read_bytes()).hexdigest(),
        'record_count': len(records),
        'empty_trace_ids': sum(not row[1] for _, row in records),
        'stack_lengths': dict(collections.Counter(len(row[14]) for _, row in records)),
        'issues': {key: {'count': len(values), 'worksheet_rows': values} for key, values in sorted(groups.items())},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'records': len(records), 'issues': len(groups), 'counts': {key: len(values) for key, values in sorted(groups.items())}}))


if __name__ == '__main__':
    main()
