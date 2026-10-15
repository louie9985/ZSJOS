# -*- coding: utf-8 -*-
"""Production flow panel, synthetic API, desktop/mobile behavior checks."""
import os
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, expect
base = os.environ.get('WORKBENCH_TEST_URL', 'http://localhost:5174')
out = Path(tempfile.gettempdir()) / 'lead-flow-history-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 900})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/lead-flow-history.html')
        expect(page.get_by_text('主管恢复客资', exact=True)).to_be_visible()
        expect(page.locator('.flow-history-node')).to_have_count(2)
        page.locator('summary').click()
        expect(page.get_by_text('已清空', exact=True)).to_be_visible()
        expect(page.get_by_text('历史未记录', exact=True)).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        page.screenshot(path=str(out / f'flow-{width}.png'), full_page=True)
        page.goto(base + '/test/lead-flow-history.html?error')
        expect(page.get_by_text('测试加载失败', exact=True)).to_be_visible()
        page.get_by_role('alert').get_by_role('button').click()
        expect(page.get_by_text('主管恢复客资', exact=True)).to_be_visible()
        page.goto(base + '/test/lead-flow-history.html?empty')
        expect(page.get_by_text('暂无流转记录', exact=True)).to_be_visible()
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: desktop/mobile expand, clear/unrecorded, long text, retry, empty, no overflow/page errors')
print(out)
