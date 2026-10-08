# UTF-8. Uses existing local Vite; synthetic data only, no live business requests.
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-announcement-pagination'
OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/admin-api/**', lambda route: route.abort())
    rows = page.locator('.home-announcement-item')
    more = page.get_by_role('button', name='加载更多', exact=True)

    def refresh():
        page.get_by_role('button', name='刷新公告', exact=True).click()

    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 850})
        page.goto('http://127.0.0.1:5174/test/home-announcement-pagination.html')
        expect(rows).to_have_count(10)
        assert page.evaluate('announcementFixture.calls[0].pageSize') == 10
        panel_height = page.locator('.home-announcement-panel').evaluate('(e)=>e.clientHeight')
        more.click()
        expect(rows).to_have_count(20)
        more.click()
        expect(rows).to_have_count(23)
        expect(page.get_by_text('已加载全部公告', exact=True)).to_be_visible()
        expect(more).to_have_count(0)
        assert page.locator('.home-announcement-panel').evaluate('(e)=>e.clientHeight') == panel_height
        assert page.locator('.home-announcement-list').evaluate('(e)=>e.scrollHeight>e.clientHeight')
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.screenshot(path=str(OUT / f'announcements-{width}.png'), full_page=True)
        refresh()
        expect(rows).to_have_count(10)

        # Failed append retains the first page; retry requests the same page.
        page.evaluate('announcementFixture.fail=true')
        more.click()
        expect(page.get_by_text('模拟公告网络错误', exact=True)).to_be_visible()
        expect(rows).to_have_count(10)
        page.evaluate('announcementFixture.fail=false')
        page.locator('.home-announcement-pagination button').click()
        expect(rows).to_have_count(20)
        assert page.evaluate('announcementFixture.calls.slice(-2).every(x=>x.pageNo===2)')

        # One pending request despite repeated clicks; refresh invalidates old append.
        refresh()
        expect(rows).to_have_count(10)
        page.evaluate('announcementFixture.delay=800; announcementFixture.calls=[]')
        more.evaluate('(e)=>{e.click();e.click();e.click()}')
        page.wait_for_timeout(50)
        assert page.evaluate('announcementFixture.calls.length') == 1
        page.evaluate('announcementFixture.delay=0')
        refresh()
        expect(rows).to_have_count(10)
        page.wait_for_timeout(900)
        expect(rows).to_have_count(10)

        page.evaluate('announcementFixture.overlap=true')
        more.click()
        expect(rows).to_have_count(19)
        assert len(set(rows.evaluate_all('(es)=>es.map(e=>e.dataset.announcementId)'))) == 19
        page.evaluate('announcementFixture.overlap=false; announcementFixture.total=0')
        refresh()
        expect(page.get_by_text('暂无公告', exact=True)).to_be_visible()
        expect(more).to_have_count(0)

        page.evaluate('announcementFixture.denied=true')
        refresh()
        expect(page.get_by_text('暂无权限，请联系管理员配置对应功能权限', exact=True)).to_be_visible()
        page.evaluate('announcementFixture.denied=false; announcementFixture.total=23')
        refresh()
        expect(rows).to_have_count(10)
        page.evaluate('announcementFixture.delay=500')
        more.click()
        page.get_by_role('button', name='切换权限', exact=True).click()
        expect(page.get_by_text('暂无公告查看权限', exact=True)).to_be_visible()
        page.wait_for_timeout(600)
        expect(rows).to_have_count(0)
        expect(more).to_have_count(0)
    assert not errors, errors
    browser.close()
print('PASS: desktop/mobile paging, end, retry, duplicate-click guard, refresh race, overlap, empty and denied states')
print(OUT)
