# -*- coding: utf-8 -*-
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

out = Path(gettempdir()) / 'zsjos-appeal-cycles-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in (1440, 390):
        page.set_viewport_size({'width': width, 'height': 950})
        for mode in ('new', 'next', 'pending', 'final'):
            page.goto('http://127.0.0.1:5174/test/lead-appeal-cycles.html?mode=' + mode)
            expect(page.get_by_text('历史判定', exact=True)).to_be_visible()
            if mode in ('new', 'next'):
                round_no = 1 if mode == 'new' else 2
                page.get_by_role('button', name=f'发起第 {round_no} 次申诉').click()
                expect(page.get_by_text('本轮由销售直属部门负责人处理' if mode == 'new' else '本轮由质控部门处理', exact=True)).to_be_visible()
                page.get_by_placeholder('填写申诉理由').fill('针对本次新的无效判定申请复核')
                page.wait_for_timeout(350)
                page.screenshot(path=str(out / f'{mode}-{width}.png'), full_page=True)
                page.get_by_role('button', name='提交申诉', exact=True).click()
                page.get_by_role('button', name='确认执行', exact=True).click()
                page.wait_for_function('window.submitted !== undefined')
                assert page.evaluate('window.submitted.invalidEventId') == 20
            else:
                expect(page.get_by_role('button', name='发起第', exact=False)).to_have_count(0)
                if mode == 'final': expect(page.get_by_text('董事长已终审维持无效，本次无效判定不再允许申诉。')).to_be_visible()
    assert not errors, errors
    browser.close()
print('PASS Workbench appeal cycles desktop/mobile')
