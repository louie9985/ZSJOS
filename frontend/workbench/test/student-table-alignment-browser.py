# -*- coding: utf-8 -*-
"""Compare actual header/body geometry with narrow persisted student columns."""
import os
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('TABLE_TEST_BASE', 'http://127.0.0.1:5174')
output = Path(tempfile.gettempdir()) / 'zsjos-table-alignment'
output.mkdir(exist_ok=True)


def aligned(table):
    measurements = table.evaluate('''root => {
      const headers = [...root.querySelectorAll('thead tr:last-child th')];
      const cells = [...root.querySelector('tr.ant-table-row').children];
      return headers.map((h, i) => {
        const a = h.getBoundingClientRect(), b = cells[i].getBoundingClientRect();
        return {index: i, width: Math.abs(a.width-b.width), left: Math.abs(a.left-b.left)};
      });
    }''')
    assert measurements and all(m['width'] < 1 and m['left'] < 1 for m in measurements), measurements


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    errors = []
    for width in (2200, 1440, 1280, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/student-table-completion.html?long=1', wait_until='domcontentloaded')
        if width == 390:
            expect(page.locator('#student-avatar-list button.lead-inbox-item')).to_have_count(2)
            page.goto(base + '/test/business-table.html?width=1', wait_until='domcontentloaded')
            table = page.locator('[data-table-key="fixture-main"]')
        else:
            page.evaluate('''localStorage.setItem('crm-student-management-table-widths',
              JSON.stringify({name:80,personNo:160,mobile:120,wechatId:120,courses:32,
                className:80,ownerUserName:150,acceptanceStatus:60,serviceStatus:60}))''')
            page.reload(wait_until='domcontentloaded')
            table = page.locator('[data-table-key="registration-pages-1"]')
        expect(table.locator('tr.ant-table-row').first).to_be_visible()
        page.wait_for_timeout(500)
        aligned(table)
        holder = table.locator('.ant-table-body')
        for position in (400, 10000, 0):
            holder.evaluate('(el, x) => el.scrollLeft = x', position)
            page.wait_for_timeout(100)
            aligned(table)
        if width > 390:
            # Resize a visible column; header/body geometry must settle together.
            cell = table.locator('thead th').nth(1).bounding_box()
            page.mouse.move(cell['x'] + cell['width'] - 2, cell['y'] + cell['height']/2)
            page.mouse.down()
            page.mouse.move(cell['x'] + cell['width'] + 58, cell['y'] + cell['height']/2, steps=5)
            page.mouse.up()
            page.wait_for_timeout(300)
            aligned(table)
            page.reload(wait_until='domcontentloaded')
            expect(table.locator('tr.ant-table-row')).to_have_count(2)
            page.wait_for_timeout(300)
            aligned(table)
        page.screenshot(path=str(output / f'alignment-{width}.png'), full_page=True)
        print('PASS header/body alignment, scroll and preferences:', width)
        page.close()
    assert not errors, errors
    browser.close()
