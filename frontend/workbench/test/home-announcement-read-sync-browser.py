# UTF-8. Synthetic API responses only; production React components in Chromium.
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:5197/test/home-announcement-integration.html?regression'

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        def home():
            close = page.locator('.ant-drawer-close:visible')
            if close.count():
                close.click()
                expect(page.locator('.ant-drawer-mask:visible')).to_have_count(0)
            page.get_by_role('button', name='返回首页', exact=True).click()
            expect(page.locator('.home-announcement-panel')).to_be_visible()
        def row(id):
            return page.locator(f'.home-announcement-item[data-announcement-id="{id}"]')
        def failure(value):
            page.evaluate('(value) => window.announcementFixture.setFailure(value)', value)
        def read(id):
            row(id).click()
            expect(page.locator('.announcement-detail').filter(visible=True)).to_be_visible()
            page.wait_for_function('''() => [...document.querySelectorAll('.announcement-detail')].some(e =>
                e.getClientRects().length && (!e.querySelector('.ant-badge') || e.parentElement.querySelector('.ant-alert')))''')
        page.goto(BASE)
        expect(row(1)).to_be_visible()
        # Retained home and two loaded pages survive detail navigation.
        page.get_by_role('button', name='加载更多', exact=True).click()
        expect(page.locator('.home-announcement-item')).to_have_count(20)
        row(11).scroll_into_view_if_needed()
        before = page.locator('.home-announcement-list').evaluate('(e) => e.scrollTop')
        calls = page.evaluate('window.announcementFixture.listCalls')
        read(11)
        home()
        expect(row(11).locator('.home-announcement-read')).to_be_visible()
        expect(page.locator('.home-announcement-item')).to_have_count(20)
        assert page.evaluate('window.announcementFixture.listCalls') == calls
        assert abs(page.locator('.home-announcement-list').evaluate('(e) => e.scrollTop') - before) < 2
        expect(page.locator('.home-announcement-count')).to_have_text('未读 14')
        # Repeated reading cannot decrement the authoritative count twice.
        read(11)
        home()
        expect(page.locator('.home-announcement-count')).to_have_text('未读 14')
        # Failed write retains unread; retry succeeds.
        failure('read')
        read(13)
        expect(page.get_by_role('button', name='重试记录')).to_be_visible()
        home()
        expect(row(13).locator('.home-announcement-unread')).to_be_visible()
        read(13)
        failure('')
        page.get_by_role('button', name='重试记录').click()
        home()
        expect(row(13).locator('.home-announcement-read')).to_be_visible()
        # Summary failure does not undo a successful write.
        failure('summary')
        read(15)
        home()
        expect(row(15).locator('.home-announcement-read')).to_be_visible()
        expect(page.get_by_role('button', name='重试未读数')).to_be_visible()
        failure('')
        page.get_by_role('button', name='重试未读数').click()
        expect(page.locator('.home-announcement-count')).to_have_text('未读 12')
        # An in-flight second page captured unread before the acknowledgement.
        page.goto(BASE)
        expect(row(1)).to_be_visible()
        page.evaluate('window.announcementFixture.delay = true')
        page.get_by_role('button', name='加载更多', exact=True).click()
        page.get_by_role('button', name='打开第十一条').click()
        expect(page.locator('.announcement-detail').filter(visible=True)).to_be_visible()
        page.wait_for_function('window.announcementFixture.reads === 1')
        home()
        page.evaluate('window.announcementFixture.release()')
        expect(row(11).locator('.home-announcement-read')).to_be_attached()
        expect(page.locator('.home-announcement-item')).to_have_count(20)
        # Account key and permission revocation clear confirmed IDs.
        page.get_by_role('button', name='切换账号', exact=True).click()
        expect(row(1)).to_be_visible()
        page.get_by_role('button', name='加载更多', exact=True).click()
        expect(row(11).locator('.home-announcement-unread')).to_be_attached()
        read(11)
        home()
        page.get_by_role('button', name='切换权限').click()
        expect(page.get_by_text('暂无公告查看权限', exact=True)).to_be_visible()
        # Reset synthetic server state while disabled to detect leaked confirmations.
        page.evaluate('window.announcementFixture.resetReads()')
        page.get_by_role('button', name='切换权限').click()
        expect(row(1)).to_be_visible()
        page.get_by_role('button', name='加载更多', exact=True).click()
        expect(row(11).locator('.home-announcement-unread')).to_be_attached()
        # Uncached navigation continues to fetch current server state.
        page.goto(BASE + '&uncached')
        expect(row(1)).to_be_visible()
        read(1)
        home()
        expect(row(1).locator('.home-announcement-read')).to_be_visible()
        page.goto('http://127.0.0.1:5197/test/notice-reading.html')
        page.get_by_role('button', name='隐藏正文', exact=True).click()
        page.evaluate('() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))')
        assert page.evaluate('window.noticeFixture.reads') == 0
        assert not errors, errors
        page.close()
        print(f'PASS {width}: retained pagination/scroll, retry, summary, stale response, identity, uncached')
    browser.close()
