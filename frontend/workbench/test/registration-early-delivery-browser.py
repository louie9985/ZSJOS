# -*- coding: utf-8 -*-
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width':width,'height':960})
        errors=[]
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://127.0.0.1:5174/test/registration-early-delivery.html')
        expect(page.locator('.lead-inbox-item')).to_have_count(2)
        if width == 390:
            # Existing mobile pool shows the product list; detail actions are desktop-only.
            expect(page.locator('.lead-inbox-item').first).to_contain_text('履约产品1')
        else:
            expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_disabled()
            page.get_by_role('button',name='保存分班',exact=True).click()
            expect(page.get_by_text('已建立交付服务，报名任务仍需财务审核通过后完成',exact=True)).to_be_visible()
            expect(page.get_by_role('button',name='保存分班',exact=True)).to_have_count(0)
            expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_disabled()
            assert page.evaluate('window.registrationRows[1].deliveryEstablished') is False
            assert page.evaluate('window.registrationRows[0].deliveries[0].acceptanceStatus') == 'accepted'
            page.evaluate('window.approveFinance()')
            page.locator('.lead-inbox-item').nth(1).click()
            page.locator('.lead-inbox-item').first.click()
            expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_enabled()
            page.get_by_role('button',name='完成报名履约',exact=True).click()
            expect(page.locator('.lead-inbox-item')).to_have_count(1)
            assert page.evaluate('window.registrationRows[0].deliveries[0].serviceRelationId') == 1001
        page.screenshot(path=f'frontend/workbench/tmp/registration-early-{width}.png',full_page=True)
        assert not errors, errors
        page.close()
    page=browser.new_page(viewport={'width':1440,'height':960})
    page.goto('http://127.0.0.1:5174/test/registration-early-delivery.html?state=denied')
    expect(page.get_by_role('button',name='保存分班',exact=True)).to_have_count(0)
    expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_disabled()
    page.goto('http://127.0.0.1:5174/test/registration-early-delivery.html?state=error')
    page.get_by_role('button',name='保存分班',exact=True).click()
    expect(page.get_by_text('保存分班失败，请重试',exact=True)).to_be_visible()
    assert page.evaluate('window.registrationRows[0].deliveryEstablished') is False
    expect(page.get_by_role('button',name='保存分班',exact=True)).to_be_enabled()
    browser.close()
print('PASS Workbench desktop flow, mobile list, permission denial, save failure/retry, sibling isolation')
