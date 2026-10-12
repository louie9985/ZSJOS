# -*- coding: utf-8 -*-
"""Exercise the real pool component with isolated responses, never live mutations."""
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

url = 'http://127.0.0.1:5174/test/registration-pool-status.html'
out = Path(os.environ['TEMP']) / 'registration-pool-status-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto(url)
        tabs = page.locator('.lead-inbox-group-tabs')
        items = page.locator('.lead-inbox-item')
        expect(tabs.get_by_role('tab', name='待处理', exact=True)).to_have_attribute('aria-selected', 'true')
        expect(items).to_have_count(20)
        assert set(items.locator('.ant-tag').all_text_contents()) == {'待处理', '处理中'}
        page.locator('.ant-pagination-next').click()
        expect(items).to_have_count(4)
        tabs.get_by_role('tab', name='已处理', exact=True).click()
        expect(items).to_have_count(20)
        assert set(items.locator('.ant-tag').all_text_contents()) == {'已完成', '已取消'}
        assert page.evaluate('window.poolRequests.at(-1).pageNo') == 1
        tabs.get_by_role('tab', name='全部', exact=True).click()
        expect(items.first).to_contain_text('TEST-1')
        expect(items.nth(2)).to_contain_text('已完成')
        page.screenshot(path=str(out / f'pool-{width}.png'), full_page=True)
        # All three controls must be physically visible without horizontal page scrolling.
        for label in ('待处理', '已处理', '全部'):
            box = tabs.get_by_role('tab', name=label, exact=True).bounding_box()
            assert box and box['x'] >= 0 and box['x'] + box['width'] <= width
        tabs.get_by_role('tab', name='待处理', exact=True).click()
        expect(items.nth(2)).to_contain_text('TEST-5')
        if width == 1440:
            page.get_by_role('button', name='完成报名履约', exact=True).click()
            expect(items.first).to_contain_text('TEST-2')
            page.get_by_role('button', name='关闭服务', exact=True).click()
            page.get_by_role('dialog').get_by_role('textbox').fill('测试关闭')
            page.get_by_role('button', name='确认关闭', exact=True).click()
            expect(items.first).to_contain_text('TEST-5')
            tabs.get_by_role('tab', name='已处理', exact=True).click()
            expect(items.first).to_contain_text('TEST-1')
            expect(items.nth(1)).to_contain_text('已取消')
        search = page.get_by_placeholder('搜索订单号、客资编号、姓名或手机号')
        search.fill('TEST-48')
        search.press('Enter')
        if width == 1440:
            expect(items).to_have_count(1)
            expect(items.first).to_contain_text('TEST-48')
        else:
            expect(items).to_have_count(0)
            tabs.get_by_role('tab', name='已处理', exact=True).click()
            expect(items).to_have_count(1)
            expect(items.first).to_contain_text('TEST-48')
        page.close()
        print(f'PASS {width}: tabs, groups, pagination reset, keyword intersection; desktop terminal refresh', flush=True)
    page = browser.new_page()
    page.goto(url + '?state=slow')
    items = page.locator('.lead-inbox-item')
    tabs = page.locator('.lead-inbox-group-tabs')
    expect(items).to_have_count(20)
    tabs.get_by_role('tab', name='已处理', exact=True).click()
    tabs.get_by_role('tab', name='待处理', exact=True).click()
    expect(items.first).to_contain_text('TEST-1')
    page.wait_for_timeout(650)
    expect(items.nth(2)).to_contain_text('TEST-5')
    page.goto(url + '?state=error')
    expect(page.get_by_text('履约列表暂不可用')).to_be_visible()
    page.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(items).to_have_count(20)
    page.goto(url + '?state=empty')
    expect(page.get_by_text('当前筛选下暂无报名履约任务')).to_be_visible()
    browser.close()
    print('PASS race, error/retry, empty', flush=True)
