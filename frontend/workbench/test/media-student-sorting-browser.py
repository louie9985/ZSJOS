# -*- coding: utf-8 -*-
"""Real media page with synthetic transport; exercises sorting without business writes."""
import os
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('SORT_TEST_BASE', 'http://127.0.0.1:5174')
out = Path(gettempdir()) / 'zsjos-media-student-sort'
out.mkdir(exist_ok=True)
with sync_playwright() as pw:
    browser = pw.chromium.launch(channel='chrome', headless=True)
    try:
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/media-student-sorting.html')
        cards = page.locator('.media-students-item')
        expect(cards).to_have_count(20, timeout=90000)

        def choose(direction=None, field='学员姓名'):
            page.get_by_role('button', name='选择学员排序', exact=True).click()
            if direction:
                page.locator('.ant-dropdown:visible .ant-dropdown-menu-submenu-title').filter(has_text=field).click()
                page.get_by_role('menuitem', name=direction, exact=True).click()
            else:
                page.get_by_role('menuitem', name='默认排序', exact=True).click()

        def assert_order(desc=False):
            expected = page.evaluate('''desc => mediaSortStudents.filter(x=>x.inServicePeriod).sort((a,b)=>
              a.name.localeCompare(b.name,'zh-CN')*(desc?-1:1)||b.personId-a.personId)[0].name''', desc)
            expect(cards.first.locator('strong')).to_have_text(expected)

        page.locator('.media-students-scroll').evaluate('e => { e.scrollTop=e.scrollHeight }')
        expect(cards).to_have_count(40)
        # Unsaved-editor guard must reject a sort without making any request.
        page.evaluate('mediaSortFixture.allowNavigation=false')
        before = page.evaluate('mediaSortFixture.queries.length')
        choose('升序')
        expect(cards).to_have_count(40)
        assert page.evaluate('mediaSortFixture.queries.length') == before
        page.evaluate('mediaSortFixture.allowNavigation=true')
        choose('升序')
        page.wait_for_function('mediaSortFixture.queries.at(-1).sortOrder === "ascend" && mediaSortFixture.queries.at(-1).pageNo === 1')
        expect(cards).to_have_count(20)
        assert_order()
        page.locator('.media-students-scroll').evaluate('e => { e.scrollTop=e.scrollHeight }')
        expect(cards).to_have_count(40)
        assert page.evaluate('mediaSortFixture.queries.at(-1).pageNo === 2 && mediaSortFixture.queries.at(-1).sortField === "name"')
        choose('降序')
        expect(cards).to_have_count(20)
        assert_order(True)
        page.get_by_role('button', name='刷新学员', exact=True).click()
        assert_order(True)
        page.screenshot(path=str(out / 'desktop.png'), full_page=True)
        # A delayed old response must not overwrite the latest chosen sort.
        page.evaluate('mediaSortFixture.nextDelay=800')
        choose('升序')
        page.wait_for_function('mediaSortFixture.queries.at(-1).sortOrder === "ascend"')
        choose('降序')
        assert_order(True)
        page.wait_for_timeout(900)
        assert_order(True)
        # The existing advanced-filter POST carries sorting as well.
        page.get_by_role('button', name='高级筛选', exact=True).click()
        page.locator('.advanced-filter-template-select').click()
        page.locator('.ant-select-item-option').filter(has_text='个人 · 运营甲').click()
        page.locator('.advanced-filter-footer button').filter(has_text='应用筛选').click()
        page.wait_for_function('mediaSortFixture.queries.at(-1).method === "post" && mediaSortFixture.queries.at(-1).sortOrder === "descend"')
        page.get_by_role('tab', name='非服务期', exact=True).click()
        expect(cards).to_have_count(0)
        assert page.evaluate('mediaSortFixture.queries.at(-1).sortField === "name"')
        page.get_by_role('tab', name='服务期', exact=True).click()
        expect(cards).to_have_count(20)
        # List errors retain sorting and support retry.
        page.evaluate('mediaSortFixture.failure="list"')
        choose('升序')
        expect(page.get_by_role('button', name='重试加载学员', exact=True)).to_be_visible()
        page.evaluate('mediaSortFixture.failure=""')
        page.get_by_role('button', name='重试加载学员', exact=True).click()
        expect(cards).to_have_count(20)
        assert page.evaluate('mediaSortFixture.queries.at(-1).sortOrder === "ascend"')
        choose()
        page.wait_for_function('!mediaSortFixture.queries.at(-1).sortField && !mediaSortFixture.queries.at(-1).sortOrder')
        # Narrow layout and collapsed rail retain the same visible entry.
        page.set_viewport_size({'width': 390, 'height': 844})
        page.get_by_role('button', name='收起学员列表', exact=True).click()
        expect(page.get_by_role('button', name='选择学员排序', exact=True)).to_be_visible()
        choose('升序', '学员编号')
        page.wait_for_function('mediaSortFixture.queries.at(-1).sortField === "personNo"')
        page.get_by_role('button', name='展开学员列表', exact=True).click()
        expect(cards.first).to_be_visible()
        button = page.get_by_role('button', name='选择学员排序', exact=True).bounding_box()
        assert button and button['x'] >= 0 and button['x'] + button['width'] <= 390
        page.screenshot(path=str(out / 'mobile.png'), full_page=True)
        assert not errors, errors
        print('PASS media sorting: desktop/mobile, global response order, reset, pagination, guard, stale requests, filters, empty, retry, collapsed rail')
    except Exception:
        page.screenshot(path=str(out / 'failure.png'), full_page=True)
        raise
    finally:
        browser.close()
