# -*- coding: utf-8 -*-
"""Real Workbench forms with synthetic transport; no live business writes."""
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.gettempdir()) / 'free-exam-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://localhost:5175/test/free-exam.html')
        page.get_by_role('button', name='新增考期').click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_label('考期名称')).to_have_value('')
        expect(dialog.get_by_text('产品', exact=True)).to_have_count(0)
        page.get_by_role('button', name='保存草稿').click()
        expect(dialog.get_by_text('请填写考期名称', exact=True)).to_be_visible()
        dialog.get_by_label('考期名称').fill('自由命名秋季场')
        page.evaluate('window.freeExamFixture.fail = true')
        page.get_by_role('button', name='保存草稿').click()
        expect(page.get_by_text('测试保存失败')).to_be_visible()
        expect(dialog.get_by_label('考期名称')).to_have_value('自由命名秋季场')
        page.evaluate('window.freeExamFixture.fail = false')
        page.screenshot(path=str(out / f'exam-{width}.png'), full_page=True)
        page.get_by_role('button', name='保存并发布').click()
        expect(dialog).to_have_count(0)
        expect(page.get_by_text('自由命名秋季场', exact=True)).to_be_visible()
        request = page.evaluate('window.freeExamFixture.requests.at(-1)')
        assert request['scheduleName'] == '自由命名秋季场'
        assert not {'productId', 'categoryId', 'selectedAttrs'} & request.keys()
        page.goto('http://localhost:5175/test/free-exam.html?class')
        page.get_by_role('button', name='创建班级').click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_label('班级名称')).to_have_value('')
        expect(dialog.get_by_text('SKU', exact=True)).to_have_count(0)
        dialog.get_by_label('班级名称').fill('自由命名一班')
        dialog.get_by_label('考期', exact=True).click()
        page.locator('.ant-select-item-option').filter(has_text='自由考试').click()
        dialog.get_by_label('班主任', exact=True).click()
        page.locator('.ant-select-item-option').filter(has_text='测试班主任').click()
        dialog.get_by_label('班级名称').click()
        page.screenshot(path=str(out / f'class-{width}.png'), full_page=True)
        dialog.get_by_role('button', name='确 定').click()
        expect(dialog).to_have_count(0)
        assert page.evaluate('window.freeExamFixture.requests[0]') == {'className': '自由命名一班', 'examScheduleId': 7, 'homeroomUserId': 8}
        assert not errors, errors
        page.close()
    browser.close()
print('PASS Workbench free exam/class forms at 1440 and 390, required names, failure retention and no catalog fields')
