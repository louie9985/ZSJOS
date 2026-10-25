# -*- coding: utf-8 -*-
"""Exercise production picker with tiny file bodies and simulated metadata sizes."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

def pick(page, files):
    page.locator('input[type=file]').evaluate('''(input, entries) => {
      const transfer = new DataTransfer();
      for (const [name, size] of entries) {
        const file = new File(['text'], name, {type: 'text/plain'});
        Object.defineProperty(file, 'size', {value: size}); transfer.items.add(file);
      }
      input.files = transfer.files; input.dispatchEvent(new Event('change', {bubbles: true}));
    }''', files)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    page.set_default_timeout(60000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        for mode in ['', '?immediate']:
            page.goto(f'http://127.0.0.1:5219/test/positioning-budget.html{mode}', wait_until='domcontentloaded')
            pick(page, [['a.txt', 700 * 1024**2], ['b.txt', 324 * 1024**2], ['c.txt', 1]])
            expect(page.locator('.positioning-attachment-item')).to_have_count(2)
            expect(page.get_by_text('附件总大小不能超过 1GB，请移除部分文件后重试', exact=True)).to_be_visible()
            page.get_by_role('button', name='移除 b.txt', exact=True).click()
            expect(page.locator('.positioning-attachment-item')).to_have_count(1)
            pick(page, [['d.txt', 324 * 1024**2]])
            expect(page.locator('.positioning-attachment-item')).to_have_count(2)
            pick(page, [['empty.txt', 0]])
            expect(page.get_by_text('附件不能为空，且须有有效的文件大小', exact=True)).to_be_visible()
            assert page.locator('body').evaluate('(el) => el.scrollWidth <= innerWidth')
            page.screenshot(path=str(Path(os.environ['TEMP']) / f'positioning-budget-{width}{"-immediate" if mode else ""}.png'), full_page=True)
    assert not errors, errors
    browser.close()
    print('PASS: 1GiB group boundary, multi-selection, removal, empty file; pending/immediate; 1440/390')
