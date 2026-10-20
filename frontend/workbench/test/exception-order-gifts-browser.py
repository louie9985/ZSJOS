# -*- coding: utf-8 -*-
"""Exercise production gift components against isolated transport, never live APIs."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

artifacts = Path(os.environ['TEMP']) / 'exception-workbench-gifts-20261020'
artifacts.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        for clear in (False, True):
            page.goto('http://127.0.0.1:5301/test/order-gifts.html')
            page.get_by_role('button', name='编辑礼品', exact=True).click()
            modal = page.get_by_role('dialog', name='补正成交', exact=True)
            expect(modal.get_by_label('礼品邮寄地址', exact=True)).to_have_value('历史收件地址')
            gifts = modal.locator('.ant-form-item').filter(has=page.locator('label[for="giftItems"]'))
            expect(gifts.get_by_text('原礼品', exact=False).first).to_be_visible()
            if clear:
                gifts.locator('.ant-select-selection-item-remove').click()
            else:
                modal.get_by_label('礼品邮寄地址', exact=True).fill('编辑后的地址')
                modal.get_by_label('礼品邮寄地址', exact=True).scroll_into_view_if_needed()
                page.screenshot(path=str(artifacts / f'gifts-{width}.png'), animations='disabled')
            modal.get_by_role('button', name='重新提交审批', exact=True).click()
            page.get_by_role('button', name='确认执行', exact=True).click()
            page.wait_for_function('window.orderGiftsFixture.requests.length === 1')
            payload = page.evaluate('window.orderGiftsFixture.requests[0]')
            assert payload['giftItems'] == ([] if clear else ['old'])
            if not clear:
                assert payload['giftShippingAddress'] == '编辑后的地址'
        page.goto('http://127.0.0.1:5301/test/order-gifts.html?invalid=1')
        page.get_by_role('button', name='编辑礼品', exact=True).click()
        expect(page.get_by_role('alert').filter(has_text='成交配置加载失败')).to_be_visible()
        assert page.evaluate('window.orderGiftsFixture.requests.length') == 0
        assert not errors, errors
        page.close()
    browser.close()
print('PASS Workbench gift history/address/edit/clear/corrupt at 1440/390')
