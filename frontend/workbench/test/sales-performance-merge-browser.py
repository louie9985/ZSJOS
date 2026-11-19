# -*- coding: utf-8 -*-
"""Isolated Chromium fixture for merged target save and stale-token feedback."""
from playwright.sync_api import sync_playwright, expect
base='http://127.0.0.1:5189/test/sales-performance.html?target=1&review=1&periodStart=2026-09-01'
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':1440,'height':900})
    page.goto(base)
    floor=page.get_by_role('spinbutton',name='演示销售甲保底目标')
    expect(floor).to_have_value('150000.00')
    floor.fill('50000')
    page.get_by_placeholder('填写调整原因（必填，将保留历史修订记录）').fill('调整合并目标')
    page.get_by_role('button',name='保存 1 项修改').click()
    expect(page.get_by_text('目标已保存',exact=True)).to_be_visible()
    expect(floor).to_have_value('50000.00')
    page.goto(base+'&conflict=1')
    floor=page.get_by_role('spinbutton',name='演示销售甲保底目标')
    floor.fill('40000')
    page.get_by_placeholder('填写调整原因（必填，将保留历史修订记录）').fill('并发冲突验证')
    page.get_by_role('button',name='保存 1 项修改').click()
    expect(page.get_by_text('目标已被修改，请刷新后重试',exact=True)).to_be_visible()
    browser.close()
print('PASS fixture: smaller merged target saved and reloaded; stale-token conflict displayed')

