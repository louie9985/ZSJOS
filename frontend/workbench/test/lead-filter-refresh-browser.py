# -*- coding: utf-8 -*-
"""Real components with synthetic transport; never writes business data."""
import re
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 1100})
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/admin-api/**', lambda route: route.fulfill(json={'code': 0, 'data': []}))
    for layout in ['split', 'table']:
        for slow in ['detail', 'list']:
            for deep_link in [False, True]:
                url = f'http://127.0.0.1:5174/test/lead-follow-up-refresh.html?filterRegression=1&layout={layout}&slow={slow}'
                page.goto(url + ('&leadId=1&tab=follow-ups' if deep_link else ''))
                if layout == 'table' and deep_link:
                    page.get_by_role('button', name='关闭', exact=True).click()
                page.get_by_role('button', name='今日待跟进', exact=True).click()
                listing = page.locator('.lead-management-table' if layout == 'table' else '#lead-avatar-list')
                expect(listing.get_by_text('刷新测试1', exact=True)).to_be_visible()
                if layout == 'table':
                    page.get_by_role('button', name=re.compile(r'详\s*细')).click()
                panel = page.get_by_role('dialog') if layout == 'table' else page.locator('.lead-inbox-detail-pane').first
                panel.get_by_role('radiogroup', name='跟进方式', exact=True).locator('label').first.click()
                panel.get_by_role('radiogroup', name='跟进结果', exact=True).locator('label').first.click()
                panel.get_by_label('跟进备注', exact=True).fill('筛选移出回归')
                panel.get_by_role('button', name='+1 天', exact=True).click()
                before = page.evaluate('({list: refreshFixture.listReads, detail: refreshFixture.detailReads})')
                panel.get_by_role('button', name='提交跟进', exact=True).click()
                page.wait_for_function('(before) => refreshFixture.posts === 1 && refreshFixture.listReads > before.list && refreshFixture.detailReads > before.detail', arg=before)
                expect(listing.get_by_text('刷新测试1', exact=True)).to_have_count(0)
                expect(panel.get_by_text('筛选移出回归', exact=True)).to_be_visible()
                if layout == 'table':
                    page.get_by_role('button', name='关闭', exact=True).click()
                expect(page.get_by_role('button', name='今日待跟进', exact=True)).to_have_attribute('aria-pressed', 'true')
                page.get_by_role('group', name='快捷条件', exact=True).get_by_role('button', name='全部', exact=True).click()
                expect(listing.get_by_text('刷新测试1', exact=True)).to_be_visible()
                print(f'PASS: {layout}, slow={slow}, deep_link={deep_link}; removed from filtered list, detail retained, all filter restores row')
    assert not errors, errors
    browser.close()
