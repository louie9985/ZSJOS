# -*- coding: utf-8 -*-
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUTPUT = Path(os.environ.get('TEMP', '.')) / 'zsjos-class-batch-browser'
OUTPUT.mkdir(parents=True, exist_ok=True)
BASE = 'http://127.0.0.1:5174/test/delivery-class-batch.html'

def open_form(page):
    page.get_by_role('button', name='创建班级').click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_label('班级名称')).to_be_enabled()
    return dialog

def fill_form(page, dialog):
    dialog.get_by_label('班级名称').fill('  同名测试班级  ')
    date = dialog.get_by_placeholder('选择日期', exact=True)
    date.fill('2026-12-01')
    date.press('Enter')
    dialog.get_by_label('班主任', exact=True).click()
    page.locator('.ant-select-item-option').filter(has_text='测试规划师甲').click()
    page.locator('.ant-select-item-option').filter(has_text='测试规划师乙').click()
    dialog.get_by_label('班级名称').click()
    expect(page.locator('.ant-select-dropdown:visible')).to_have_count(0)
    expect(dialog.get_by_role('button', name='确认创建 2 个班级')).to_be_visible()

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE)
        dialog = open_form(page)
        dialog.get_by_role('button', name='创建班级', exact=True).click()
        expect(dialog.get_by_text('请选择至少一位班主任')).to_be_visible()
        assert page.evaluate('window.classBatch.rows.length') == 0
        fill_form(page, dialog)
        page.screenshot(path=str(OUTPUT / f'create-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        dialog.get_by_role('button', name='确认创建 2 个班级').click()
        expect(dialog).not_to_be_visible()
        expect(page.locator('.delivery-class-card')).to_have_count(2)
        rows = page.evaluate('window.classBatch.rows')
        assert [row['homeroomUserId'] for row in rows] == [20, 21]
        assert {row['className'] for row in rows} == {'同名测试班级'}
        assert {row['startDate'] for row in rows} == {'2026-12-01'}
        assert len({row['classNo'] for row in rows}) == 2
        assert page.evaluate("window.classBatch.calls.filter(c => c.url.endsWith('/batch-create')).length") == 1
        page.get_by_role('button', name='编辑班级').first.click()
        expect(dialog.get_by_label('班主任', exact=True)).to_be_enabled()
        expect(dialog.locator('.ant-select-multiple')).to_have_count(0)
        dialog.get_by_label('班级名称').fill('单班修改')
        dialog.get_by_role('button', name=re.compile(r'^保\s*存$')).click()
        expect(dialog).not_to_be_visible()
        calls = page.evaluate("window.classBatch.calls.filter(c => c.method === 'put')")
        assert calls[0]['data']['homeroomUserId'] == 20
        assert 'homeroomUserIds' not in calls[0]['data']
        assert page.evaluate('window.classBatch.rows.length') == 2
        assert not errors, errors
        page.close()

    page = browser.new_page(viewport={'width': 1440, 'height': 960})
    page.goto(BASE + '?state=error')
    dialog = open_form(page)
    fill_form(page, dialog)
    dialog.get_by_role('button', name='确认创建 2 个班级').click()
    expect(page.get_by_text('班主任不可用，请重新选择', exact=True)).to_be_visible()
    expect(dialog.get_by_label('班级名称')).to_have_value('  同名测试班级  ')
    assert page.evaluate('window.classBatch.rows.length') == 0
    page.evaluate('window.classBatch.allowSave()')
    dialog.get_by_role('button', name='确认创建 2 个班级').click()
    expect(dialog).not_to_be_visible()
    expect(page.locator('.delivery-class-card')).to_have_count(2)
    page.goto(BASE + '?state=denied')
    expect(page.get_by_text('暂无班级', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='创建班级')).to_have_count(0)
    page.goto(BASE + '?state=empty')
    dialog = open_form(page)
    dialog.get_by_label('班主任', exact=True).click()
    expect(page.locator('.ant-select-item-option')).to_have_count(0)
    page.goto(BASE + '?state=candidate-error')
    page.get_by_role('button', name='创建班级').click()
    expect(page.get_by_text('班主任加载失败，请重试', exact=True)).to_be_visible()
    expect(page.get_by_role('dialog')).not_to_be_visible()
    browser.close()
print('PASS: desktop/mobile multi-create, required selection, isolated transport, edit single owner, failure/retry, denied and candidate states')
print(OUTPUT)
