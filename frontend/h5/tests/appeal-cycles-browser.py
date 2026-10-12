# -*- coding: utf-8 -*-
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width':390,'height':844})
    for mode in ('new','next','pending','final'):
        page.goto('http://127.0.0.1:5199/tests/appeal-cycles.html?mode='+mode)
        expect(page.get_by_text('历史判定 · 第 3 轮',exact=False)).to_be_visible()
        if mode in ('new','next'):
            expect(page.get_by_text('发起第 '+('1' if mode=='new' else '2')+' 次申诉',exact=True)).to_be_visible()
            page.get_by_placeholder('请详细说明申诉理由').fill('针对新判定申诉')
            page.screenshot(path=str(Path(gettempdir())/'zsjos-appeal-cycles-browser'/('h5-'+mode+'.png')),full_page=True)
            page.get_by_role('button',name='提交申诉',exact=True).click()
            page.wait_for_function('window.submitted !== undefined')
            assert page.evaluate('window.submitted.invalidEventId')==20
        else:
            expect(page.get_by_role('button',name='提交申诉',exact=True)).to_have_count(0)
    browser.close()
print('PASS H5 current-cycle entry and request')
