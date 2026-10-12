# -*- coding: utf-8 -*-
"""Verify closed/pending distinction on real components with synthetic responses."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

output = Path(os.environ.get('TEMP', '.')) / 'zsjos-closed-lead-status'
output.mkdir(exist_ok=True)
base = 'http://127.0.0.1:5174/test/closed-lead-status.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    errors = []
    for width in [1440, 390]:
        for layout in ['split', 'table', 'overview']:
            page = browser.new_page(viewport={'width': width, 'height': 1000})
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.route('**/admin-api/**', lambda route: route.fulfill(json={'code': 0, 'data': []}))
            query = '?overview=1' if layout == 'overview' else f'?layout={layout}'
            page.goto(base + query)
            # Mobile intentionally uses the inbox even when table layout is preferred.
            region = page.locator('.lead-status-labels') if layout == 'overview' else page.locator('.ant-table-tbody') if layout == 'table' and width >= 768 else page.locator('.lead-inbox-list-pane')
            expect(region.get_by_text('已关闭', exact=True)).to_be_visible()
            expect(region.get_by_text('待判定', exact=True)).to_have_count(0)
            if layout == 'overview':
                expect(region.get_by_text('客资状态', exact=True)).to_be_visible()
            page.screenshot(path=str(output / f'{layout}-{width}.png'), full_page=True)
            page.goto(base + query + '&pending=1')
            expect(region.get_by_text('待判定', exact=True)).to_be_visible()
            expect(region.get_by_text('已关闭', exact=True)).to_have_count(0)
            print(f'PASS {layout} {width}: closed displayed; pending unchanged')
            page.close()
    assert not errors, errors
    browser.close()
print(f'Screenshots: {output}')
