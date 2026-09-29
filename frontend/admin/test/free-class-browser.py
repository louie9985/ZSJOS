# -*- coding: utf-8 -*-
"""Admin production component with intercepted synthetic API responses."""
import json
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.gettempdir()) / 'free-exam-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        requests, errors = [], []
        page.on('pageerror', lambda error: errors.append(str(error)))
        def respond(route):
            request = route.request
            if request.method == 'OPTIONS':
                route.fulfill(status=204, headers={'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*'})
                return
            if request.url.split('?')[0].endswith('/exam-options'):
                data = [{'id': 7, 'scheduleType': 'EXACT', 'displayName': '自由考试 · 2099-10-10'}]
            elif request.url.split('?')[0].endswith('/homeroom-candidates'):
                data = [{'id': 8, 'name': '测试班主任'}]
            elif request.url.endswith('/create'):
                requests.append(request.post_data_json)
                data = 10
            else:
                data = {'list': [], 'total': 0}
            route.fulfill(json={'code': 0, 'data': data, 'msg': ''}, headers={'Access-Control-Allow-Origin': '*'})
        page.route('**/zsjos/delivery-class/**', respond)
        page.route('**/zsjos/class-transfer/**', respond)
        page.goto('http://127.0.0.1:5186/test/free-class.html')
        page.get_by_role('button', name='创建班级').click(timeout=90000)
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_label('班级名称')).to_have_value('')
        expect(dialog.get_by_text('产品', exact=True)).to_have_count(0)
        dialog.get_by_label('班级名称').fill('管理端自由班级')
        dialog.locator('.el-form-item').filter(has_text='考期').locator('.el-select').click()
        page.get_by_role('option', name='自由考试 · 2099-10-10').click()
        dialog.locator('.el-form-item').filter(has_text='班主任').locator('.el-select').click()
        page.get_by_role('option', name='测试班主任').click()
        dialog.get_by_label('班级名称').click()
        assert dialog.bounding_box()['x'] >= 0
        assert dialog.bounding_box()['width'] <= width
        page.screenshot(path=str(out / f'admin-class-{width}.png'), full_page=True)
        dialog.get_by_role('button', name='保存').click()
        expect(dialog).not_to_be_visible()
        assert requests == [{'className': '管理端自由班级', 'examScheduleId': 7, 'homeroomUserId': 8}], requests
        assert not errors, errors
        page.close()
    browser.close()
print('PASS Admin manual class name, independent exam and homeroom choices at 1440/390')
