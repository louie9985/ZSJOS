# -*- coding: utf-8 -*-
"""Actual shared Vue label component with isolated, asynchronously loaded dictionary data."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'hrm-label-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 800})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://127.0.0.1:5296/test/hrm-manual.html?view=labels')
        expect(page.get_by_test_id('type')).to_have_text('正式', timeout=60000)
        expect(page.get_by_test_id('status')).to_have_text('正式')
        expect(page.get_by_test_id('entry')).to_have_text('在职')
        expect(page.get_by_test_id('snapshot')).to_have_text('记录时的名称')
        expect(page.get_by_test_id('unknown')).to_have_text('历史代码 999')
        expect(page.get_by_test_id('empty')).to_have_text('-')
        page.get_by_test_id('type').locator('span').hover()
        expect(page.get_by_role('tooltip')).to_have_text('历史标签未记录，按当前字典显示')
        page.get_by_test_id('snapshot').hover()
        expect(page.get_by_role('tooltip')).not_to_be_visible()
        page.evaluate('setLabelValue(2)')
        expect(page.get_by_test_id('reactive')).to_have_text('试用')
        assert page.evaluate('hrmCalls.length') == 0
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.screenshot(path=str(out / f'labels-{width}.png'), full_page=True)
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: snapshot priority, current-dictionary fallback/tooltip, unknown/empty, async/reactive, desktop/mobile; no writes')
