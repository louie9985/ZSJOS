# -*- coding: utf-8 -*-
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

out = Path(gettempdir()) / 'zsjos-account-delete-review'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.set_default_navigation_timeout(60000)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        for case in ['', '?summary']:
            page.goto('http://127.0.0.1:5199/test/media-account-delete-review.html' + case, wait_until='domcontentloaded')
            expect(page.get_by_text('删除账号 · 测试账号', exact=True)).to_be_visible()
            expect(page.get_by_text('MA-TEST-210', exact=True)).to_be_visible()
            expect(page.get_by_text('所属编导（审批人）', exact=True)).to_be_visible()
            expect(page.get_by_text('所属编导', exact=True)).to_be_visible()
            expect(page.get_by_text('抄送人', exact=True)).to_have_count(0)
            expect(page.get_by_text('47', exact=True)).to_have_count(0)
            expect(page.get_by_text('525', exact=True)).to_have_count(0)
            expect(page.get_by_text('pending', exact=True)).to_have_count(0)
            expect(page.get_by_text('申请补充信息', exact=True)).to_have_count(0)
            page.wait_for_timeout(250)
            page.screenshot(path=str(out / f'detail-{width}{"-summary" if case else ""}.png'), full_page=True)
        print('PASS readable snapshot and hidden routing IDs', width, flush=True)
    page.goto('http://127.0.0.1:5199/test/media-account-delete-review.html?denied')
    expect(page.get_by_text('暂无查看本次删除申请的权限', exact=True)).to_be_visible()
    expect(page.get_by_text('MA-TEST-210', exact=True)).to_have_count(0)
    expect(page.get_by_text('47', exact=True)).to_have_count(0)
    assert not errors, errors
    browser.close()
    print('PASS denied view, no raw identifiers, no page errors', flush=True)
