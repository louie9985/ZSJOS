# UTF-8. Isolated real-page fixture; no real accounts or business writes.
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        for layout in ('split', 'table'):
            page = browser.new_page(viewport={'width': width, 'height': 1000})
            errors = []
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.goto(f'http://127.0.0.1:5174/test/lead-submission-default-sort.html?layout={layout}')
            cards = page.locator('.lead-inbox-item')
            if layout == 'split' or width == 390:
                expect(cards).to_have_count(3)
                assert ['KZ-TEST-' + str(i) in cards.nth(n).get_attribute('aria-label') for n, i in enumerate([1, 3, 2])] == [True]*3
                expect(cards.nth(2)).to_have_class(__import__('re').compile('.*has-pending-tasks.*'))
                cards.nth(2).click()
                if width == 390:
                    page.get_by_role('button', name='关闭', exact=True).click()
                assert 'KZ-TEST-1' in cards.first.get_attribute('aria-label')
                page.evaluate('window.taskFixture.completed = true; window.taskFixture.invalidate()')
                expect(cards.nth(2)).not_to_have_class(__import__('re').compile('.*has-pending-tasks.*'))
                assert 'KZ-TEST-1' in cards.first.get_attribute('aria-label')
            else:
                rows = page.locator('.ant-table-tbody tr.ant-table-row')
                expect(rows).to_have_count(3)
                for n, i in enumerate([1, 3, 2]):
                    expect(rows.nth(n)).to_contain_text(f'KZ-TEST-{i}')
            assert not errors, errors
            page.close()
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        page.goto('http://127.0.0.1:5174/test/lead-submission-default-sort.html?calendar=1')
        page.locator('.lead-calendar-count').first.click()
        page.wait_for_function("window.taskFixture.calendarQuery?.sort === 'submittedAt'")
        assert page.evaluate('window.taskFixture.calendarQuery.direction') == 'desc'
        expect(page.locator('.lead-calendar-toolbar')).to_contain_text('客资提交时间')
        expect(page.locator('.lead-calendar-toolbar')).to_contain_text('最新优先')
        page.locator('.ant-modal-close').click()
        page.get_by_role('button', name=__import__('re').compile('搜索')).first.click()
        expect(page.locator('.ant-drawer')).to_contain_text('客资提交时间从新到旧')
        page.close()
    browser.close()
print('PASS: default submission order, ID ties, reminder indicators, read/update stability; desktop/mobile split/table')
