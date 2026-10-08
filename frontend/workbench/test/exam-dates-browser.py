# -*- coding: utf-8 -*-
"""Production components with isolated transport, fixed business dates, real Chrome."""
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(tempfile.mkdtemp(prefix='exam-dates-browser-'))
URL = 'http://127.0.0.1:5237/test/exam-dates.html'

def open_day(page, name):
    page.get_by_role('button', name=re.compile(name + '，')).first.click()
    return page.locator('.exam-day-detail')

def date_input(editor, value):
    field = editor.locator('input[placeholder="请选择日期"]')
    field.fill(value)
    field.press('Enter')
    editor.get_by_text('考期名称', exact=True).click()

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width, zone in [(1440, 'America/Los_Angeles'), (390, 'Asia/Shanghai')]:
        page = browser.new_page(viewport={'width': width, 'height': 1000}, timezone_id=zone)
        page.clock.set_fixed_time('2026-10-08T01:00:00Z')
        page.goto(URL)
        detail = open_day(page, '过期草稿')
        expect(detail.get_by_role('button', name=re.compile('发布$')).first).to_be_disabled()
        expect(detail.get_by_text('考期已过期，请先编辑日期')).to_be_visible()
        detail.get_by_role('button', name=re.compile('编辑$')).first.click()
        editor = page.get_by_role('dialog', name='编辑考期', exact=True)
        expect(editor.locator('input[placeholder="请选择日期"]')).to_have_value('2026-10-07')
        editor.get_by_role('button', name='保存草稿').click()
        expect(editor.get_by_text('单日日期或多日结束日期不能早于北京时间今天')).to_be_visible()
        assert page.evaluate('window.examDatesFixture.counters().updates') == 0
        editor.screenshot(path=str(OUT / f'expired-{width}.png'))
        date_input(editor, '2026-10-08')
        editor.get_by_role('button', name='保存并发布').click()
        expect(editor).not_to_be_visible()
        assert page.evaluate('window.examDatesFixture.counters().updates') == 1
        assert page.evaluate('window.examDatesFixture.counters().publishes') == 1
        page.screenshot(path=str(OUT / f'rescheduled-{width}.png'))
        print('PASS expired draft rescue and Shanghai dates', width, zone, flush=True)
        page.close()

    page = browser.new_page(viewport={'width': 1440, 'height': 1000}, timezone_id='America/Los_Angeles')
    page.clock.set_fixed_time('2026-10-08T15:59:59Z')
    page.goto(URL)
    detail = open_day(page, '今天草稿')
    expect(detail.get_by_role('button', name=re.compile('发布$')).first).to_be_enabled()
    page.clock.set_fixed_time('2026-10-08T16:00:00Z')
    expect(detail.get_by_role('button', name=re.compile('发布$')).first).to_be_disabled()
    print('PASS Shanghai midnight updates open detail', flush=True)
    page.close()

    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.clock.set_fixed_time('2026-10-08T01:00:00Z')
    page.goto(URL + '?fail=1')
    page.get_by_role('button', name=re.compile('新增考期$')).click()
    editor = page.get_by_role('dialog', name='新增考期', exact=True)
    editor.get_by_label('考期名称', exact=True).fill('重试草稿')
    editor.get_by_role('button', name='保存并发布', exact=True).click()
    expect(page.get_by_text('草稿已保存，发布未成功：模拟发布失败')).to_be_visible()
    editor.get_by_role('button', name='保存并发布', exact=True).click()
    expect(editor).not_to_be_visible()
    assert page.evaluate('window.examDatesFixture.counters()') == {'creates': 1, 'updates': 1, 'publishes': 2}
    print('PASS publish retry retains saved ID', flush=True)
    page.close()

    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.clock.set_fixed_time('2026-10-08T01:00:00Z')
    page.goto(URL)
    detail = open_day(page, '过期草稿')
    detail.get_by_role('button', name=re.compile('编辑$')).nth(1).click()
    editor = page.get_by_role('dialog', name='编辑考期', exact=True)
    expect(editor.locator('input[placeholder="开始日期"]')).to_have_value('2026-10-01')
    editor.get_by_role('button', name='保存并发布', exact=True).click()
    expect(editor).not_to_be_visible()
    assert page.evaluate('window.examDatesFixture.rows().find(r => r.id === 3).recordStatus') == 'PUBLISHED'
    print('PASS ongoing multi-day reschedule and publish', flush=True)
    page.close()

    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.clock.set_fixed_time('2026-10-08T01:00:00Z')
    page.goto(URL)
    page.get_by_role('button', name='上一月', exact=True).click()
    page.get_by_role('button', name=re.compile('新增考期$')).click()
    editor = page.get_by_role('dialog', name='新增考期', exact=True)
    expect(editor.locator('input[placeholder="请选择日期"]')).to_have_value('2026-10-08')
    editor.get_by_label('考期名称', exact=True).fill('跨日未保存草稿')
    page.clock.set_fixed_time('2026-10-08T16:00:00Z')
    editor.get_by_role('button', name='保存草稿', exact=True).click()
    expect(editor.get_by_text('单日日期或多日结束日期不能早于北京时间今天')).to_be_visible()
    assert page.evaluate('window.examDatesFixture.counters().creates') == 0
    print('PASS past month default and midnight submission validation', flush=True)
    browser.close()
print('Screenshots:', OUT)
