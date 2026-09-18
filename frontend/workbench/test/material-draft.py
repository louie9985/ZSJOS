# -*- coding: utf-8 -*-
"""Real page/form browser flow with synthetic API transport; no live writes."""
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    def open_draft(query=''):
        page.goto('http://127.0.0.1:5174/test/material-draft.html' + query)
        page.get_by_role('tab', name='我的素材').click()
        page.get_by_role('button', name='验收草稿').click()
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        for query in ['', '?account']:
            open_draft(query)
            expect(page.get_by_role('heading', name='账号详情' if query else '作品详情')).to_be_visible()
            page.get_by_role('dialog').get_by_role('button', name='继续编辑').click()
            page.get_by_role('dialog').get_by_role('textbox').fill('继续编辑后的草稿')
            page.screenshot(path=str(Path(gettempdir()) / f'material-draft-{width}{"-account" if query else ""}.png'))
            page.get_by_role('button', name='保存草稿').click()
            if not query:
                expect(page.locator('.ant-modal-confirm-title').filter(has_text='草稿保存成功')).to_be_visible()
                page.get_by_role('button', name='知道了').click()
            expect(page.get_by_role('dialog')).to_have_count(0)
            expect(page.locator('html')).to_have_attribute('data-writes', '1')
            page.get_by_role('button', name='继续编辑后的草稿').click()
            page.get_by_role('dialog').get_by_role('button', name='继续编辑').click()
            expect(page.get_by_role('dialog').get_by_role('textbox')).to_have_value('继续编辑后的草稿')
            page.get_by_role('button', name='提交审批').click()
            expect(page.locator('html')).to_have_attribute('data-submitted', '42')
            expect(page.locator('html')).to_have_attribute('data-writes', '2')
    for query in ['?no-permission', '?no-action']:
        open_draft(query)
        expect(page.get_by_role('heading', name='作品详情')).to_be_visible()
        expect(page.get_by_role('dialog').get_by_role('button', name='继续编辑')).to_have_count(0)
    open_draft('?no-submit')
    page.get_by_role('dialog').get_by_role('button', name='继续编辑').click()
    expect(page.get_by_role('button', name='保存草稿')).to_be_visible()
    expect(page.get_by_role('button', name='提交审批')).to_have_count(0)
    open_draft('?error')
    expect(page.get_by_text('验收详情失败')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_role('button', name='继续编辑')).to_have_count(0)
    page.get_by_role('dialog').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_role('dialog').get_by_role('button', name='继续编辑')).to_be_visible()
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        page.goto('http://127.0.0.1:5174/test/material-draft.html?standalone')
        save = page.get_by_role('button', name='保存草稿')
        expect(save).to_be_disabled()
        page.get_by_role('textbox').fill('   ')
        expect(save).to_be_disabled()
        page.get_by_role('textbox').fill('已填写的拆解')
        expect(save).to_be_enabled()
        save.click()
        expect(page.locator('.ant-modal-confirm-title').filter(has_text='草稿保存成功')).to_be_visible()
        link = page.get_by_role('link', name='前往我的素材')
        expect(link).to_have_attribute('href', '/zsjos/material-library/browse?view=mine')
        link.scroll_into_view_if_needed()
        print('modal bounds', width, page.locator('.ant-modal-confirm').bounding_box(), flush=True)
        page.screenshot(path=str(Path(gettempdir()) / f'material-draft-success-{width}.png'), animations='disabled')
        # Render the real library in the isolated fixture at the actual destination URL.
        html = page.request.get('http://127.0.0.1:5174/test/material-draft.html').text().replace('./material-draft.tsx', '/test/material-draft.tsx')
        page.route('**/zsjos/material-library/browse?view=mine', lambda route: route.fulfill(body=html, content_type='text/html'))
        link.click()
        expect(page).to_have_url('http://127.0.0.1:5174/zsjos/material-library/browse?view=mine')
        expect(page.get_by_role('tab', name='我的素材')).to_have_attribute('aria-selected', 'true')
        page.unroute('**/zsjos/material-library/browse?view=mine')
    page.goto('http://127.0.0.1:5174/test/material-draft.html?standalone&save-error')
    page.get_by_role('textbox').fill('保存失败保留内容')
    page.get_by_role('button', name='保存草稿').click()
    expect(page.get_by_text('验收保存失败')).to_be_visible()
    expect(page.get_by_role('textbox')).to_have_value('保存失败保留内容')
    expect(page.locator('.ant-modal-confirm-title').filter(has_text='草稿保存成功')).to_have_count(0)
    assert not errors, errors
    browser.close()
    print('PASS: content/account reopen, save existing ID, submit, permissions, retry, desktop/mobile')
