# -*- coding: utf-8 -*-
"""Production My Materials UI with synthetic HTTP; never deletes live records."""
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

out = Path(gettempdir()) / 'zsjos-material-draft-delete'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    page.route_web_socket(re.compile(r'ws://.*'), lambda socket: None)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    def start(extra=''):
        page.goto('http://127.0.0.1:5174/test/material-draft.html?delete&view=mine' + extra)
        expect(page.get_by_role('button', name='验收草稿', exact=True)).to_be_visible()
    def confirm(): return page.get_by_role('dialog', name='确认删除草稿？')
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        for kind in ['', '&account']:
            start(kind)
            page.get_by_role('button', name='删除草稿', exact=True).click()
            expect(confirm()).to_contain_text('TEST-42')
            assert page.locator('html').get_attribute('data-deletes') is None
            confirm().get_by_role('button', name=re.compile(r'取\s*消')).click()
            expect(page.get_by_role('button', name='验收草稿', exact=True)).to_be_visible()
            # Detail deletion uses the same command and version; failure leaves the drawer and record intact.
            page.get_by_role('button', name='验收草稿', exact=True).click()
            drawer = page.get_by_role('dialog', name='查看爆款拆解')
            expect(drawer.get_by_role('button', name='删除草稿', exact=True)).to_be_visible()
            drawer.get_by_role('button', name='删除草稿', exact=True).click()
            page.evaluate("document.documentElement.dataset.deleteError = '草稿已被修改，请刷新后重试'")
            confirm().get_by_role('button', name='确认删除', exact=True).click()
            expect(confirm()).to_contain_text('草稿已被修改，请刷新后重试')
            expect(drawer).to_be_visible()
            expect(page.locator('html')).to_have_attribute('data-deletes', '1')
            page.screenshot(path=str(out / f'failure-{width}{"-account" if kind else ""}.png'))
            page.evaluate('delete document.documentElement.dataset.deleteError')
            confirm().get_by_role('button', name='确认删除', exact=True).click()
            expect(confirm().get_by_role('button', name=re.compile(r'取\s*消'))).to_be_disabled()
            expect(page.get_by_text('草稿已删除', exact=True)).to_be_visible()
            expect(drawer).to_have_count(0)
            expect(page.get_by_role('button', name='验收草稿', exact=True)).to_have_count(0)
            expect(page.get_by_text('暂无素材', exact=True)).to_be_visible()
            expect(page.locator('html')).to_have_attribute('data-deletes', '2')
            page.screenshot(path=str(out / f'deleted-{width}{"-account" if kind else ""}.png'))
        for denied in ['&no-action', '&no-delete-permission']:
            start(denied)
            expect(page.get_by_role('button', name='删除草稿', exact=True)).to_have_count(0)
            page.get_by_role('button', name='验收草稿', exact=True).click()
            expect(page.get_by_role('dialog', name='查看爆款拆解')).to_be_visible()
            expect(page.get_by_role('button', name='删除草稿', exact=True)).to_have_count(0)
        start()
        page.get_by_role('button', name='验收草稿', exact=True).click()
        page.get_by_role('button', name='继续编辑', exact=True).click()
        page.get_by_role('textbox', name='验收标题', exact=True).fill('保留未保存正文')
        expect(page.locator('.material-library-item').get_by_role('button', name='删除草稿', exact=True)).to_be_disabled()
        expect(page.get_by_role('dialog', name='查看爆款拆解').get_by_role('button', name='删除草稿', exact=True)).to_have_count(0)
    start()
    page.evaluate("document.documentElement.dataset.refreshError = '1'")
    page.get_by_role('button', name='删除草稿', exact=True).click()
    confirm().get_by_role('button', name='确认删除', exact=True).click()
    expect(page.get_by_text('删除后列表刷新失败', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='验收草稿', exact=True)).to_have_count(0)
    page.evaluate('delete document.documentElement.dataset.refreshError')
    page.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_text('暂无素材', exact=True)).to_be_visible()
    assert not errors, errors
    browser.close()
print('PASS: My Materials draft deletion, both types, desktop/mobile, confirmation, retry, permissions, editor safety, refresh failure')
