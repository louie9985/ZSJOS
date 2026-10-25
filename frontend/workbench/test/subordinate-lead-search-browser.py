# -*- coding: utf-8 -*-
"""Real Chrome + real pages, isolated synthetic HTTP responses only."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('ZSJOS_UI_TEST_URL', 'http://127.0.0.1:5174')
OUT = Path(gettempdir()) / 'zsjos-subordinate-lead-search'
OUT.mkdir(exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def last():
        return page.evaluate('searchFixture.requests.at(-1)')

    def count():
        return page.evaluate('searchFixture.requests.length')

    def open_sales():
        page.goto(BASE + '/test/subordinate-lead-search.html')
        page.get_by_text('测试销售5', exact=True).click()
        page.get_by_role('tab', name='名下客资').click()
        expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()

    def search(value):
        box = page.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号')
        box.fill(value)
        box.press('Enter')

    def template():
        page.get_by_role('button', name='高级筛选', exact=True).last.click()
        page.locator('.advanced-filter-template-select').click()
        page.get_by_text('个人 · 根和子组', exact=True).click()

    def apply():
        page.get_by_role('button', name='应用筛选', exact=True).click()
        expect(page.locator('.advanced-filter-drawer')).not_to_be_visible()

    open_sales()
    page.locator('.ant-pagination-item-2').click()
    expect(page.get_by_role('button', name='客资22', exact=True)).to_be_visible()
    search('  SEARCH-42  ')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    assert last()['pageNo'] == 1 and last()['keyword'] == 'SEARCH-42'
    before = count()
    page.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号').press('Enter')
    page.wait_for_function('n => searchFixture.requests.length === n + 1', arg=before)
    page.wait_for_timeout(150)
    assert count() == before + 1
    before = count()
    page.locator('.subordinate-sales-detail-pane').get_by_role('button', name='搜索', exact=True).click()
    page.wait_for_function('n => searchFixture.requests.length === n + 1', arg=before)
    page.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号').fill('')
    expect(page.get_by_role('button', name='客资41', exact=True)).to_be_visible()
    assert last()['pageNo'] == 1 and not last().get('keyword')

    # Cross-page selection survives only while query conditions are unchanged.
    page.locator('.ant-table-tbody tr[data-row-key] input[type=checkbox]').first.check()
    expect(page.locator('.business-table-batch')).to_contain_text('已选 1 条')
    toolbar = page.locator('[data-table-key="subordinate-sales-page-1"] .business-table-toolbar')
    expect(toolbar.get_by_role('button', name=re.compile(r'批量操作$'))).to_be_enabled()
    expect(toolbar.get_by_role('button', name='名下客资', exact=True)).to_be_visible()
    expect(toolbar.get_by_role('button', name='回收待处理', exact=True)).to_be_visible()
    expect(toolbar.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号')).to_be_visible()
    expect(toolbar.get_by_role('button', name='选择名下客资排序')).to_be_visible()
    expect(page.locator('.subordinate-batch-bar')).to_have_count(0)
    toolbar.get_by_role('button', name=re.compile(r'批量操作$')).click()
    expect(page.get_by_role('menuitem', name=re.compile(r'转\s*派$'))).to_have_attribute('aria-disabled', 'false')
    expect(page.get_by_role('menuitem', name=re.compile(r'恢\s*复$'))).to_have_attribute('aria-disabled', 'true')
    expect(page.get_by_role('menuitem', name=re.compile(r'释放至公海池$'))).to_have_attribute('aria-disabled', 'true')
    page.screenshot(path=str(OUT / 'batch-menu.png'), full_page=True)
    page.get_by_role('menuitem', name=re.compile(r'转\s*派$')).click()
    expect(page.get_by_role('dialog', name='批量转派客资')).to_be_visible()
    page.get_by_role('dialog', name='批量转派客资').get_by_role('button', name=re.compile(r'取\s*消')).click()
    page.mouse.move(0, 0)
    page.locator('.ant-pagination-item-2').click()
    expect(page.get_by_role('button', name='客资22', exact=True)).to_be_visible()
    expect(page.locator('.business-table-batch')).to_contain_text('已选 1 条')
    search('SEARCH-42')
    expect(page.locator('.business-table-batch')).to_contain_text('已选 0 条')

    # Out-of-order successes and failures cannot replace current query state.
    page.evaluate('searchFixture.nextDelay=900')
    search('SEARCH-1')
    page.wait_for_function("searchFixture.requests.at(-1).keyword === 'SEARCH-1'")
    search('SEARCH-42')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.wait_for_timeout(1100)
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.evaluate("searchFixture.nextDelay=900; searchFixture.nextFailure='error'")
    search('SEARCH-2')
    page.wait_for_function("searchFixture.requests.at(-1).keyword === 'SEARCH-2'")
    search('SEARCH-42')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.wait_for_timeout(1100)
    expect(page.get_by_text('查询失败，请重试', exact=True)).to_have_count(0)

    # An obsolete finally must not stop the latest request's loading indicator.
    page.evaluate('searchFixture.nextDelay=400')
    search('SEARCH-1')
    page.wait_for_function("searchFixture.requests.at(-1).keyword === 'SEARCH-1'")
    page.evaluate('searchFixture.nextDelay=1200')
    search('SEARCH-42')
    page.wait_for_function("searchFixture.requests.at(-1).keyword === 'SEARCH-42'")
    page.wait_for_timeout(600)
    expect(page.locator('.ant-spin-spinning')).to_be_visible()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_have_count(0)
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()

    # A latest error clears stale rows and disables batch actions; retry recovers.
    page.locator('.ant-table-tbody tr[data-row-key] input[type=checkbox]').first.check()
    page.evaluate("searchFixture.nextFailure='error'; searchFixture.nextDelay=600")
    page.get_by_role('tab', name='概览', exact=True).click()
    page.get_by_role('tab', name='名下客资', exact=True).click()
    expect(page.locator('.business-table-batch').get_by_role('button', name=re.compile(r'批量操作$'))).to_be_disabled()
    expect(page.get_by_text('查询失败，请重试', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_have_count(0)
    page.locator('.ant-alert').filter(has_text='查询失败，请重试').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.evaluate("searchFixture.nextFailure='denied'")
    search('SEARCH-42')
    expect(page.get_by_text('无权查询客资', exact=True)).to_be_visible()
    search('SEARCH-42')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    search('missing')
    expect(page.locator('.ant-table-tbody').get_by_role('button')).to_have_count(0)
    search('')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()

    # Cancel is inert; applying/filter-tag deletion normalizes empty child groups.
    template()
    before = count()
    page.get_by_role('button', name=re.compile(r'取\s*消')).click()
    assert count() == before
    page.locator('.ant-pagination-item-2').click()
    expect(page.get_by_role('button', name='客资22', exact=True)).to_be_visible()
    template(); apply()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    assert last()['method'] == 'post' and last()['pageNo'] == 1
    page.locator('.advanced-filter-tags .ant-tag').filter(has_text='42').locator('.ant-tag-close-icon').click()
    expect(page.get_by_role('button', name='客资41', exact=True)).to_be_visible()
    assert last()['advancedFilter']['groups'] == []
    page.locator('.advanced-filter-tags .ant-tag-close-icon').click()
    page.wait_for_function("searchFixture.requests.at(-1).method === 'get'")
    template(); apply()
    search('SEARCH-42')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.get_by_role('button', name='清空全部', exact=True).click()
    page.wait_for_function("searchFixture.requests.at(-1).method === 'get'")
    assert last()['keyword'] == 'SEARCH-42'

    # Sales remount clears query/view/selection and preserves sorting, with no old query.
    page.locator('th').filter(has_text='姓名').click()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.get_by_role('button', name='回收待处理', exact=True).click()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    template(); apply()
    page.evaluate('searchFixture.nextDelay=900')
    search('SEARCH-1')
    page.wait_for_function("searchFixture.requests.at(-1).keyword === 'SEARCH-1'")
    before = count()
    page.get_by_text('测试销售6', exact=True).click()
    expect(page.get_by_role('tab', name='概览', exact=True)).to_have_attribute('aria-selected', 'true')
    assert count() == before
    page.get_by_role('tab', name='名下客资', exact=True).click()
    expect(page.get_by_role('button', name='客资01', exact=True)).to_be_visible()
    assert '/6/' in last()['url'] and last()['pageNo'] == 1 and not last().get('keyword') and not last().get('advancedFilter') and last()['recycledOnly'] is False and last()['sortField'] == 'submittedName'
    page.wait_for_timeout(1100)
    expect(page.get_by_role('button', name='客资01', exact=True)).to_be_visible()

    # A late task error is isolated from the lead tab and its result.
    page.evaluate("searchFixture.nextDelay=900; searchFixture.nextFailure='error'")
    page.get_by_role('tab', name='待跟进任务').click()
    page.wait_for_function("searchFixture.requests.at(-1).url.endsWith('/tasks')")
    page.get_by_role('tab', name='名下客资', exact=True).click()
    expect(page.get_by_role('button', name='客资01', exact=True)).to_be_visible()
    page.wait_for_timeout(1100)
    expect(page.get_by_text('待办查询失败', exact=True)).to_have_count(0)
    page.screenshot(path=str(OUT / 'desktop.png'), full_page=True)

    page.set_viewport_size({'width': 390, 'height': 844})
    search('SEARCH-42')
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    template(); apply()
    page.evaluate("searchFixture.nextFailure='error'")
    search('SEARCH-42')
    expect(page.get_by_text('查询失败，请重试', exact=True)).to_be_visible()
    page.locator('.ant-alert').filter(has_text='查询失败，请重试').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_role('button', name='客资42', exact=True)).to_be_visible()
    page.screenshot(path=str(OUT / 'mobile.png'), full_page=True)

    # Shared component behavior on both actual consumer pages.
    for surface in ('lead', 'media'):
        page.set_viewport_size({'width': 1440, 'height': 1000})
        page.goto(BASE + '/test/subordinate-lead-search.html?surface=' + surface)
        page.get_by_role('button', name='高级筛选', exact=True).wait_for()
        template()
        before = count()
        page.get_by_role('button', name=re.compile(r'取\s*消')).click()
        assert count() == before
        template(); apply()
        page.wait_for_function("searchFixture.requests.at(-1).method === 'post'")
        page.locator('.advanced-filter-tags .ant-tag').filter(has_text='42').locator('.ant-tag-close-icon').click()
        page.wait_for_function('searchFixture.requests.at(-1).advancedFilter.groups.length === 0')
        page.get_by_role('button', name='清空全部', exact=True).click()
        page.wait_for_function("searchFixture.requests.at(-1).method === 'get'")
    assert not errors, errors
    browser.close()
print('PASS: search/page/repeat/clear, selection, stale success/error, retry/denied/empty, filter groups, sales reset/sort, tasks, desktop/mobile, shared Lead Management + Media Students')
