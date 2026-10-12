# -*- coding: utf-8 -*-
"""Real Workbench pages, synthetic transport; does not alter business data."""
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
OUT=Path(gettempdir())/'zsjos-lead-appeal-visibility'
OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page(); page.set_default_timeout(15000)
    errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    def open_detail(entry='management',width=1440,query=''):
        page.set_viewport_size({'width':width,'height':1000})
        page.goto('http://127.0.0.1:5174/test/lead-appeal-visibility.html?leadId=1&entry='+entry+query)
        if entry=='subordinate':
            page.locator('.subordinate-sales-item').click()
            page.get_by_role('tab',name='名下客资',exact=True).click()
            expect(page.get_by_text('申诉中',exact=True).first).to_be_visible()
            page.get_by_role('button',name='改判测试客资',exact=True).click()
        expect(page.get_by_text('KZ-OV-TEST',exact=True).first).to_be_visible()
    for entry in ('management','subordinate'):
        for width in (1440,390):
            open_detail(entry,width)
            detail=page.locator('.lead-inbox-detail:visible').last
            expect(detail.get_by_text('申诉处理中 · 第 1 轮 · 待负责人主管审核',exact=True)).to_be_visible()
            expect(detail.get_by_text('当前判定：已判无效 · 原判联系不上',exact=True)).to_be_visible()
            expect(detail.get_by_role('button',name='去处理申诉',exact=True)).to_be_visible()
            expect(detail.get_by_role('tab',name='申诉记录 · 处理中',exact=True)).to_be_visible()
            page.wait_for_timeout(350)  # Allow Drawer entrance animation to finish before visual capture.
            page.screenshot(path=str(OUT/f'{entry}-{width}.png'),full_page=True)
            detail.get_by_role('button',name='查看申诉',exact=True).click()
            expect(detail.get_by_role('tab',name='申诉记录 · 处理中',exact=True)).to_have_attribute('aria-selected','true')
            detail.get_by_role('tab',name='概览',exact=True).click()
            detail.get_by_role('button',name='去处理申诉',exact=True).click()
            expect(page).to_have_url('http://127.0.0.1:5174/zsjos/appeals')
            assert page.evaluate('history.state.usr.appealId')==101
            print('PASS',entry,width,flush=True)
    open_detail(query='&denied=1')
    expect(page.get_by_role('button',name='去处理申诉',exact=True)).to_have_count(0)
    expect(page.get_by_role('button',name='查看申诉',exact=True)).to_be_visible()
    for status,label in [('quality_reviewing','待质控审核'),('chairman_reviewing','待董事长终审')]:
        open_detail(query='&denied=1&round=2&status='+status)
        expect(page.get_by_text('申诉处理中 · 第 2 轮 · '+label,exact=True)).to_be_visible()
    for status in ('none','upheld','overturned','withdrawn'):
        open_detail(query='&denied=1&status='+status)
        expect(page.get_by_role('button',name='去处理申诉',exact=True)).to_have_count(0)
        expect(page.get_by_role('tab',name='申诉记录',exact=True)).to_be_visible()
        expect(page.get_by_text('申诉中',exact=True)).to_have_count(0)
    open_detail(query='&layout=table')
    page.get_by_role('button',name='关闭',exact=True).click()
    expect(page.locator('.lead-management-table').get_by_text('申诉中',exact=True)).to_be_visible()
    print('PASS management table badge',flush=True)
    assert not errors,errors
    print('PASS completed/absent/denied/higher-round states; no browser errors',flush=True)
    browser.close()
