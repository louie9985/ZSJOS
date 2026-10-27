# -*- coding: utf-8 -*-
"""Header interaction/request checks against the existing isolated class fixture on 5224.
Global result ordering is covered by DeliveryClassGroupServiceTest, not this adapter.
"""
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ.get('TEMP', '.')) / 'zsjos-class-sort'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto('http://127.0.0.1:5224/test/delivery-class-groups.html')
        page.get_by_role('button', name='测试表格布局').click()
        table = page.locator('[data-table-key="delivery-class-management"]')
        if width == 390:
            # The existing responsive contract always uses cards on narrow screens.
            expect(page.locator('.delivery-group-card')).to_have_count(4)
            expect(table).to_have_count(0)
            assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
            page.screenshot(path=str(out / 'cards-390.png'), full_page=True)
            assert not errors, errors
            page.close()
            continue
        expect(table).to_be_visible()
        page.evaluate("""() => {
          const f = window.classGroupsFixture;
          const sample = f.rows.find(r => r.id === 200);
          for(let i=0;i<20;i++) f.rows.push({...sample,id:1000+i,className:'分页班级'+i});
        }""")
        for field, label in [('name', '班级名称'), ('date', '考期'), ('count', '服务数量')]:
            header = table.locator('th.ant-table-column-has-sorters').filter(has_text=label)
            for direction, aria in [('asc', 'ascending'), ('desc', 'descending'), (None, None)]:
                page.evaluate('window.classGroupsFixture.calls.length=0')
                header.click()
                page.wait_for_function("""([field, direction]) => window.classGroupsFixture.calls.some(c =>
                  c.url.endsWith('/group/page') && c.params.pageNo === 1 &&
                  (direction ? c.params.sortField === field && c.params.sortOrder === direction : !c.params.sortField && !c.params.sortOrder))""", arg=[field, direction])
                expect(table.locator('.ant-spin-spinning')).to_have_count(0)
                if aria:
                    expect(header).to_have_attribute('aria-sort', aria)
                else:
                    assert header.get_attribute('aria-sort') is None
            # Returning to a previous sort must reset its saved page to 1.
            header.click()
            table.locator('.ant-pagination-item-2').click()
            page.wait_for_function("window.classGroupsFixture.calls.some(c => c.url.endsWith('/group/page') && c.params.pageNo === 2)")
            header.click()
            header.click()
        # Child paging uses the same sort, and a root failure remains retryable.
        table.locator('th.ant-table-column-has-sorters').filter(has_text='班级名称').click()
        table.locator('button.delivery-group-name').filter(has_text='批量测试').click()
        page.wait_for_function("window.classGroupsFixture.calls.some(c => c.url.endsWith('/children') && c.params.sortField === 'name' && c.params.sortOrder === 'asc')")
        expect(table.get_by_role('button', name='加载更多小班（12/15）')).to_be_visible()
        table.get_by_role('button', name='加载更多小班（12/15）').click()
        page.wait_for_function("window.classGroupsFixture.calls.some(c => c.url.endsWith('/children') && c.params.pageNo === 2 && c.params.sortField === 'name')")
        page.evaluate('window.classGroupsFixture.failRoot=true')
        table.locator('th.ant-table-column-has-sorters').filter(has_text='考期').click()
        expect(table.get_by_text('班级组查询失败，请重试', exact=True)).to_be_visible()
        page.evaluate('window.classGroupsFixture.failRoot=false')
        table.get_by_role('button', name=re.compile(r'重\s*试')).click()
        expect(table.get_by_text('班级组查询失败，请重试', exact=True)).not_to_be_visible()
        page.screenshot(path=str(out / f'sorting-{width}.png'), full_page=True)
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: desktop header cycles, paging reset, child sort propagation, retry; mobile card fallback')
