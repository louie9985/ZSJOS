# UTF-8. Reuses the isolated approval fixture; never submits approval commands.
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'order-approval-layout-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch()
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://127.0.0.1:5174/test/order-approval-status.html')
        rows = page.locator('.business-inbox-item')
        expect(rows.first).to_be_visible()
        for index in (0, 1):
            rows.nth(index).click()
            pane = page.get_by_role('dialog') if width == 390 else page.locator('.business-inbox-detail-pane')
            if width == 390:
                page.wait_for_function("Math.abs(document.querySelector('[role=dialog]').getBoundingClientRect().x)<2")
            main = pane.locator('.information-table')
            expect(main.locator('.sales-order-information-table')).to_have_count(4)
            expect(main.locator('.detail-field-grid')).to_have_count(0)
            expect(main.get_by_text('首购', exact=True)).to_have_class(re.compile('ant-tag-blue'))
            expect(main.get_by_text('待审核', exact=True)).to_have_class(re.compile('ant-tag-gold'))
            expect(pane.get_by_role('button', name=re.compile('通.*过$'))).to_have_count(1 if index == 0 else 0)
            expect(pane.get_by_role('button', name=re.compile('驳.*回$'))).to_have_count(1 if index == 0 else 0)
            expect(main.get_by_text('处理结果', exact=True)).to_have_count(index)
            expect(main.get_by_text('处理时间', exact=True)).to_have_count(index)
            expect(main.get_by_text('审批意见', exact=True)).to_have_count(index)
            expect(pane.locator('.sales-order-approval-rail')).to_have_count(1)
            assert main.locator('.sales-order-information-cell').evaluate_all('(cells)=>cells.every(e=>e.scrollWidth<=e.clientWidth+1)')
            if width == 390:
                main.locator('.sales-order-information-table').first.scroll_into_view_if_needed()
            page.screenshot(path=str(out / f'approval-{width}-{index}.png'), full_page=True)
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            if width == 390:
                pane.get_by_role('button', name=re.compile('关.*闭')).click()
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: approval table/tags, pending/done fields and action gating, desktop/mobile; screenshots:', out)
