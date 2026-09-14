#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Synthetic API acceptance for the current account positioning surface."""
import json, re
from pathlib import Path
from playwright.sync_api import sync_playwright

OUTPUT = Path(__file__).resolve().parents[1] / 'output/account-positioning-acceptance'
OUTPUT.mkdir(parents=True, exist_ok=True)
fields = [dict(key='goal', label='承接产品目标', type='textarea', group='POSITIONING',
               description='明确本次产品目标', ownerType='DIRECTOR', enabled=True, required=False,
               requiredForComplete=False, sort=10, searchable=False)]

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        state = {'version': 0, 'values': {}, 'submissions': [], 'keys': [], 'history_fail': True}
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))

        def route(r):
            url = r.request.url
            data = []
            if '/profile/positioning/versions' in url:
                if state['history_fail']:
                    state['history_fail'] = False
                    r.fulfill(status=500, content_type='application/json', body='{}')
                    return
                data = {'list': state['submissions'], 'total': len(state['submissions'])}
            elif '/profile/positioning/submit' in url:
                command = r.request.post_data_json
                assert command['version'] == state['version'] and command['configVersionId'] == 8
                assert command['idempotencyKey'] and set(command['changes']) <= {'goal'}
                state['keys'].append(command['idempotencyKey'])
                state['values'].update(command['changes'])
                state['version'] += 1
                snapshots = [dict(key='goal', label='承接产品目标', value=state['values'].get('goal'), displayValue=state['values'].get('goal'))]
                state['submissions'].insert(0, dict(id=state['version'], title='定位卡正式提交', kind='POSITIONING',
                    snapshots=snapshots, files=[], resultVersion=state['version'], operatedAt='2026-09-11T16:00:00',
                    operatedBy='测试编导', positioning=dict(configVersionId=8, fields=fields, values=snapshots)))
                data = state['version']
            elif '/profile/history' in url:
                data = {'list': [], 'total': 0}
            elif '/profile' in url and r.request.method == 'PUT':
                state['values'].update(r.request.post_data_json['changes'])
                state['version'] += 1
                data = state['version']
            elif '/profile' in url:
                data = dict(account=dict(id=777, accountNo='TEST-777', version=state['version']),
                    config=dict(id=8, versionNo=1, fields=fields), values=state['values'], snapshots=[],
                    editableFields=['goal'], missingFields=[], missingByOwner={}, sourceNotes={}, files={},
                    canViewHistory=True, canSubmitPositioning=True)
            r.fulfill(content_type='application/json', body=json.dumps({'code': 0, 'data': data}))

        page.route('**/admin-api/**', route)
        page.goto('http://127.0.0.1:5174/test/account-positioning.html')
        page.get_by_role('button', name='历史定位卡', exact=True).click()
        dialog = page.get_by_role('dialog')
        try:
            dialog.get_by_role('button', name=re.compile(r'^重\s*试$')).click(timeout=5000)
        except Exception:
            (OUTPUT / 'failure.html').write_text(page.content(), encoding='utf-8')
            (OUTPUT / 'errors.json').write_text(json.dumps(errors, ensure_ascii=False), encoding='utf-8')
            page.screenshot(path=str(OUTPUT / 'failure.png'))
            raise
        dialog.get_by_text('暂无正式提交的定位卡').wait_for()
        dialog.get_by_role('button', name='Close', exact=True).click()
        page.get_by_role('button', name=re.compile(r'^维\s*护$')).click()
        editor = page.get_by_role('dialog')
        editor.get_by_role('textbox', name='承接产品目标').fill('草稿计划')
        editor.get_by_role('button', name=re.compile(r'^保\s*存$')).click()
        assert not state['submissions'], 'Draft must not create a formal version'
        editor.get_by_role('textbox', name='承接产品目标').fill('正式计划')
        editor.get_by_role('button', name='正式提交定位卡').click()
        page.get_by_text('定位卡已提交，历史版本已保存', exact=True).wait_for()
        assert len(state['submissions']) == 1
        page.screenshot(path=str(OUTPUT / f'editor-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Document overflow'
        editor.get_by_role('button', name=re.compile(r'^返\s*回$')).click()
        page.get_by_role('button', name='历史定位卡', exact=True).click()
        page.get_by_text('第 1 次提交', exact=False).click()
        page.get_by_role('dialog').get_by_text('正式计划', exact=True).wait_for()
        assert not page.get_by_role('dialog').get_by_role('textbox').count(), 'History must be read-only'
        page.screenshot(path=str(OUTPUT / f'history-{width}.png'), full_page=True)
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: desktop/mobile draft, submit, history, empty and retry')
