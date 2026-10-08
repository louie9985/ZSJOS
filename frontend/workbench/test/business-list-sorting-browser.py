# -*- coding: utf-8 -*-
"""Exercise real list pages against the isolated synthetic transport."""
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('SORT_TEST_BASE', 'http://127.0.0.1:5198')
output = Path(tempfile.gettempdir()) / 'zsjos-business-sort'
output.mkdir(exist_ok=True)

with sync_playwright() as pw:
    browser = pw.chromium.launch(channel='chrome', headless=True)
    try:
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        cases = [('student', '学员姓名', 'name'), ('order', '订单号', 'orderNo'),
                 ('cashback', '金额', 'amount'), ('withdrawal', '申请金额', 'applicationAmount')]
        for scene, title, field in cases:
            page.goto(f'{base}/test/business-list-sorting.html?scene={scene}')
            expect(page.locator('.ant-table-tbody tr[data-row-key]').first).to_be_visible(timeout=90000)
            page.locator('.ant-pagination-item-2').click()
            page.wait_for_function('sortingFixture.queries.at(-1).pageNo === 2')
            header = page.get_by_role('columnheader', name=title, exact=True)
            header.click()
            page.wait_for_function('(field) => sortingFixture.queries.at(-1).sortField === field && sortingFixture.queries.at(-1).sortOrder === "ascend" && sortingFixture.queries.at(-1).pageNo === 1', arg=field)
            expect(header).to_have_attribute('aria-sort', 'ascending')
            if scene in ['cashback', 'withdrawal']:
                expect(page.locator('.ant-table-tbody tr[data-row-key]').first).to_have_attribute('data-row-key', '65')
            page.locator('.ant-pagination-item-2').click()
            page.wait_for_function('(field) => sortingFixture.queries.at(-1).pageNo === 2 && sortingFixture.queries.at(-1).sortField === field', arg=field)
            header.click()
            page.wait_for_function('sortingFixture.queries.at(-1).sortOrder === "descend" && sortingFixture.queries.at(-1).pageNo === 1')
            expect(header).to_have_attribute('aria-sort', 'descending')
            page.screenshot(path=str(output/f'{scene}-desktop.png'), full_page=True)
            # Sorting changes while an older request is still pending must keep the newest selection.
            page.evaluate('sortingFixture.delay = 300')
            header.click()
            page.wait_for_function('!sortingFixture.queries.at(-1).sortField')
            page.evaluate('sortingFixture.delay = 0')
            header.click()
            page.wait_for_function('sortingFixture.queries.at(-1).sortOrder === "ascend"')
            page.wait_for_timeout(400)
            expect(header).to_have_attribute('aria-sort', 'ascending')
            if scene in ['cashback', 'withdrawal']:
                expect(page.locator('.ant-table-tbody tr[data-row-key]').first).to_have_attribute('data-row-key', '65')
            if scene in ['student', 'order']:
                page.locator('#switch-layout').click()
                expect(page.get_by_role('button', name='选择'+('学员' if scene=='student' else '订单')+'排序')).to_be_visible()
                page.locator('#switch-layout').click()
                expect(page.get_by_role('columnheader', name=title, exact=True)).to_have_attribute('aria-sort', 'ascending')
            header = page.get_by_role('columnheader', name=title, exact=True)
            header.click(); page.wait_for_function('sortingFixture.queries.at(-1).sortOrder === "descend"')
            header.click(); page.wait_for_function('!sortingFixture.queries.at(-1).sortField')
            page.evaluate('sortingFixture.fail = true')
            header.click()
            expect(page.get_by_text('排序列表读取失败，请重试', exact=False).first).to_be_visible()
            page.evaluate('sortingFixture.fail = false')
            page.get_by_role('button', name=re.compile(r'重\s*试')).last.click()
            expect(page.locator('.ant-table-tbody tr[data-row-key]').first).to_be_visible()
            expect(header).to_have_attribute('aria-sort', 'ascending')
            header.click(); page.wait_for_function('sortingFixture.queries.at(-1).sortOrder === "descend"')
            header.click(); page.wait_for_function('!sortingFixture.queries.at(-1).sortField')
            page.set_viewport_size({'width':390,'height':844})
            page.screenshot(path=str(output/f'{scene}-mobile.png'), full_page=True)
            if scene in ['student','order']:
                label='学员' if scene=='student' else '订单'
                page.get_by_role('button',name='选择'+label+'排序').click()
                page.locator('.ant-dropdown:visible .ant-dropdown-menu-submenu-title').filter(has_text=title).click()
                page.get_by_role('menuitem',name='降序',exact=True).click()
                page.wait_for_function('(field) => sortingFixture.queries.at(-1).sortField === field && sortingFixture.queries.at(-1).sortOrder === "descend"',arg=field)
                page.get_by_role('button',name='选择'+label+'排序').click()
                page.get_by_role('menuitem',name='默认排序',exact=True).click()
                page.wait_for_function('!sortingFixture.queries.at(-1).sortField')
            page.set_viewport_size({'width':1440,'height':1000})
            print(f'PASS {scene}: global request, page reset, directions, cancel, stale response, desktop/mobile')
        assert not errors, errors
        print(f'PASS browser acceptance; screenshots: {output}')
    finally:
        browser.close()
