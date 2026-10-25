# -*- coding: utf-8 -*-
"""Production class editors, isolated synthetic transport; no business writes."""
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(os.environ['TEMP']) / 'zsjos-optional-exam-browser'
OUT.mkdir(exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto('http://127.0.0.1:5174/test/delivery-class-groups.html')
        page.get_by_role('button', name='测试卡片布局').click()
        page.get_by_role('button', name=re.compile('创建班级$')).click()
        dialog = page.get_by_role('dialog')
        dialog.get_by_label('班级名称', exact=True).fill('稍后补考期')
        dialog.get_by_label('班主任', exact=True).click()
        page.locator('.ant-select-item-option').filter(has_text='测试规划师甲').click()
        dialog.get_by_label('班级名称', exact=True).click()
        expect(dialog.get_by_placeholder('选择日期', exact=True)).to_have_value('')
        dialog.get_by_role('button', name=re.compile('确认创建 1')).click()
        expect(dialog).not_to_be_visible()
        payload = page.evaluate("classGroupsFixture.calls.find(c=>c.url.endsWith('/create-classes')).data")
        assert 'startDate' not in payload, payload
        card = page.locator('.delivery-class-card').filter(has_text='稍后补考期')
        expect(card.get_by_text('未设置', exact=True)).to_be_visible()
        card.get_by_role('button', name='编辑', exact=True).click()
        date = dialog.get_by_placeholder('选择日期', exact=True)
        expect(date).to_have_value('')
        date.fill('2026-12-05'); date.press('Enter')
        dialog.get_by_role('button', name=re.compile(r'保\s*存')).click()
        expect(dialog).not_to_be_visible()
        payload = page.evaluate("classGroupsFixture.calls.filter(c=>c.method==='put').at(-1).data")
        assert payload['startDate'] == '2026-12-05' and payload['preserveExamDate'] is False
        expect(card.get_by_text('2026-12-05', exact=True)).to_be_visible()
        card.get_by_role('button', name='编辑', exact=True).click()
        dialog.get_by_label('班级名称', exact=True).fill('稍后补考期改名')
        dialog.get_by_role('button', name=re.compile(r'保\s*存')).click()
        expect(dialog).not_to_be_visible()
        assert page.evaluate("classGroupsFixture.calls.filter(c=>c.method==='put').at(-1).data.preserveExamDate") is True
        page.screenshot(path=str(OUT / f'workbench-{width}.png'), full_page=True)
        assert not errors, errors
        page.close()

        page = browser.new_page(viewport={'width': width, 'height': 1000})
        rows, writes, errors = [], [], []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.route('**/admin-api/**', lambda route: route.fulfill(json={'code': 0, 'data': []}, headers={'Access-Control-Allow-Origin': '*'}))
        def respond(route):
            req = route.request
            headers = {'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*'}
            if req.method == 'OPTIONS':
                route.fulfill(status=204, headers=headers); return
            path = req.url.split('?')[0]
            if path.endswith('/homeroom-candidates'):
                data = [{'id': 20, 'name': '测试规划师甲'}]
            elif path.endswith('/create'):
                value = req.post_data_json; writes.append(value)
                rows.append(dict(value, id=100, classNo='BJ-TEST', status='SERVING', systemClass=False, version=0, studentCount=0, availableActions=['UPDATE', 'COMPLETE']))
                data = 100
            elif req.method == 'PUT':
                value = req.post_data_json; writes.append(value); rows[0].update(value); data = True
            else:
                data = {'list': rows, 'total': len(rows)}
            route.fulfill(json={'code': 0, 'data': data}, headers=headers)
        page.route('**/zsjos/delivery-class/**', respond)
        page.route('**/zsjos/class-transfer/**', respond)
        page.goto('http://127.0.0.1:5231/test/free-class.html')
        page.get_by_role('button', name='创建班级').click(timeout=90000)
        dialog = page.get_by_role('dialog')
        dialog.get_by_label('班级名称').fill('管理端稍后补考期')
        dialog.locator('.el-form-item').filter(has_text='班主任').locator('.el-select').click()
        page.get_by_role('option', name='测试规划师甲').click()
        dialog.get_by_role('button', name='保存', exact=True).click()
        expect(dialog).not_to_be_visible()
        assert 'startDate' not in writes[-1], writes
        page.get_by_role('button', name='编辑', exact=True).first.click()
        date = dialog.get_by_placeholder('可暂不填写，后续补填')
        date.fill('2026-12-06'); date.press('Enter')
        dialog.get_by_label('班级名称').click()
        dialog.get_by_role('button', name='保存', exact=True).click()
        expect(dialog).not_to_be_visible()
        assert writes[-1]['startDate'] == '2026-12-06' and writes[-1]['preserveExamDate'] is False, writes
        page.get_by_role('button', name='编辑', exact=True).first.click()
        dialog.get_by_label('班级名称').fill('管理端改名')
        dialog.get_by_role('button', name='保存', exact=True).click()
        expect(dialog).not_to_be_visible()
        assert writes[-1]['preserveExamDate'] is True, writes
        page.screenshot(path=str(OUT / f'admin-{width}.png'), full_page=True)
        assert not errors, errors
        page.close()
    browser.close()
print('PASS Workbench/Admin no-date create, later fill, unchanged-date preserve at 1440/390')
