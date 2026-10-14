# -*- coding: utf-8 -*-
"""Real components, synthetic transport only; no authenticated business writes."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'order-void-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch()
    for frontend, port in [('workbench', 5174), ('admin', 5189)]:
        if os.environ.get('ORDER_VOID_FRONTEND') and os.environ['ORDER_VOID_FRONTEND'] != frontend: continue
        for width in (1440, 390):
            page = browser.new_page(viewport={'width': width, 'height': 1000})
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            def open_order(scenario='assigned'):
                page.goto(f'http://127.0.0.1:{port}/test/order-void.html?scenario={scenario}')
                if frontend == 'admin':
                    page.get_by_role('button', name='查看', exact=True).click()
                elif width < 768:
                    page.locator('.sales-order-list-item').click()
            open_order('denied')
            expect(page.get_by_role('button', name='作废订单', exact=True)).to_have_count(0)
            open_order()
            page.get_by_role('button', name='作废订单', exact=True).click()
            dialog = page.get_by_role('dialog').filter(has=page.get_by_role('button', name='确认作废', exact=True)).last
            confirm = dialog.get_by_role('button', name='确认作废', exact=True)
            confirm.click()
            expect(dialog).to_contain_text('请填写作废原因')
            dialog.locator('textarea').fill('部分退费重新录单')
            page.wait_for_timeout(450)
            page.screenshot(path=str(out / f'{frontend}-{width}-confirm.png'), full_page=True)
            confirm.click()
            if frontend == 'workbench':
                expect(dialog).to_contain_text('测试网络失败，请重试')
                confirm.click()
            page.wait_for_function("document.documentElement.dataset.voided === 'true'")
            expect(page.get_by_role('button', name='作废订单', exact=True)).to_have_count(0)
            expect(page.get_by_text('订单已作废，不计入业绩', exact=True).last).to_be_visible()
            expect(confirm).to_be_hidden()
            page.wait_for_timeout(450)
            page.screenshot(path=str(out / f'{frontend}-{width}-voided.png'), full_page=True)
            assert not errors, errors
            print('PASS', frontend, width, 'permission, required reason, confirmation, invalidation; React retry idempotency', flush=True)
            page.close()
    browser.close()
