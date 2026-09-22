# UTF-8. Real page components; isolated GET adapter, no business writes.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL = 'http://127.0.0.1:5174/test/content-review-integration.html'
OUT = Path(__file__).resolve().parents[1] / 'node_modules/.cache/content-review-integration'
OUT.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={'width': 1600, 'height': 1000})
    errors, requests = [], []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.on('request', lambda r: requests.append(r.url) if '/admin-api/' in r.url else None)

    def load(suffix=''):
        page.goto(URL + suffix)
        expect(page.locator('.content-review-list-item')).to_have_count(3)

    def workspace(name):
        target = page.locator('.crm-tab-bar').get_by_role('tab', name=name, exact=True)
        # A notification/deep link can request navigation while the existing modal is open.
        if page.locator('.ant-modal:visible').count(): target.dispatch_event('click')
        else: target.click()
        expect(target).to_have_attribute('aria-selected', 'true')
        expect(page.locator('.media-students-page' if name == '媒体学员' else '.content-review-page')).to_be_visible()

    load()
    assert 'CR-DEMO' not in page.locator('.content-review-list-pane').inner_text()
    search = page.get_by_label('搜索内容审核', exact=True)
    box = page.locator('.content-review-inbox-search').bounding_box()
    title = page.locator('.content-review-inbox-title').first.bounding_box()
    assert abs(box['x'] - title['x']) < 1
    assert abs(box['width'] - title['width']) < 1
    page.screenshot(path=str(OUT / 'desktop.png'))
    script = page.locator('.content-review-field-text').filter(has_text='【全文结束').first
    assert len(script.inner_text()) == 10000
    assert script.evaluate('(el) => {el.scrollTop=el.scrollHeight; return el.scrollHeight-el.clientHeight-el.scrollTop < 2 && el.scrollWidth <= el.clientWidth+1}')
    opinion = page.locator('.content-review-decision textarea').first
    opinion.fill('内部跳转后保留的审核意见')
    page.locator('.content-review-account-link').first.click()
    expect(page.get_by_role('tab', name='小禾的轻食日记', exact=True)).to_have_attribute('aria-selected', 'true')
    page.wait_for_timeout(350)
    workspace('内容审核')
    expect(opinion).to_have_value('内部跳转后保留的审核意见')
    page.locator('.content-review-account-link').nth(1).click()
    expect(page.get_by_role('tab', name='小禾的厨房与四季生活记录', exact=True)).to_have_attribute('aria-selected', 'true')
    expect(page.locator('.crm-tab-bar').get_by_role('tab')).to_have_count(2)
    page.wait_for_timeout(350)
    workspace('内容审核')
    workspace('媒体学员')
    expect(page.get_by_role('tab', name='小禾的厨房与四季生活记录', exact=True)).to_have_attribute('aria-selected', 'true')
    page.wait_for_timeout(1000)
    page.get_by_role('button', name='维护账号表').click()
    # Simulate an internal navigation request while the editor modal is open.
    page.get_by_role('tab', name='小禾的轻食日记', exact=True).dispatch_event('click')
    expect(page.locator('.ant-modal-confirm-title').filter(has_text='账号页面有未保存修改')).to_be_visible()
    page.get_by_role('button', name='继续填写', exact=True).click()
    expect(page.locator('.ant-modal-confirm-title')).to_have_count(0)
    expect(page.get_by_role('tab', name='小禾的厨房与四季生活记录', exact=True)).to_have_attribute('aria-selected', 'true')
    page.get_by_role('tab', name='小禾的轻食日记', exact=True).dispatch_event('click')
    page.get_by_role('button', name='放弃并切换', exact=True).click()
    expect(page.get_by_role('tab', name='小禾的轻食日记', exact=True)).to_have_attribute('aria-selected', 'true')
    page.locator('.crm-tab-bar .ant-tabs-tab-remove').click()
    expect(opinion).to_have_value('内部跳转后保留的审核意见')
    print('PASS: account targeting, shared internal tab, opinions, edit guard, close fallback')

    load()
    search.fill('慢查询'); search.press('Enter')
    page.wait_for_timeout(100)
    search.fill('小禾'); search.press('Enter')
    expect(page.locator('.content-review-list-item')).to_have_count(1)
    page.wait_for_timeout(750)
    expect(page.locator('.content-review-list-item')).to_have_count(1)
    search.fill('不存在的内容'); search.press('Enter')
    expect(page.locator('.content-review-list-item')).to_have_count(0)
    load()
    page.get_by_role('button', name='筛选内容审核', exact=True).click()
    page.get_by_role('combobox', name='责任运营筛选').click()
    page.get_by_title('运营乙', exact=True).click()
    expect(page.locator('.content-review-list-item')).to_have_count(2)
    assert page.evaluate('JSON.parse(document.documentElement.dataset.reviewQuery).operatorUserId') == 30
    print('PASS: inbox alignment, long manuscript, query race, empty results, authoritative filter parameters')

    for width in [1280, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        load()
        if width == 390:
            expect(page.locator('.content-review-detail-pane')).not_to_be_visible()
            page.locator('.content-review-list-item').first.click()
            expect(page.get_by_role('button', name='返回收件箱')).to_be_visible()
        expect(page.locator('.bpm-process-panel .ant-collapse-header')).to_have_attribute('aria-expanded', 'false')
        expect(page.locator('.bpm-process-panel .ant-collapse-panel')).not_to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+1')
        page.screenshot(path=str(OUT / f'width-{width}.png'))
        if width == 390:
            page.get_by_role('button', name='返回收件箱').click()
            expect(search).to_be_visible()
    page.set_viewport_size({'width': 1600, 'height': 1000})
    load('?noMenu=1')
    expect(page.locator('.content-review-account-link')).to_have_count(0)
    for query, message in [('accountMissing', '该账号已失效'), ('accountDenied', '无权查看该学员')]:
        load('?' + query + '=1')
        page.locator('.content-review-account-link').first.click()
        expect(page.get_by_text(message, exact=False).first).to_be_visible()
    page.goto(URL + '?listError=1')
    expect(page.locator('.content-review-list-pane .ant-alert button')).to_be_visible()
    assert not errors, errors
    assert not requests, requests
    print('PASS: desktop/mobile, denied menu/object, invalid account, retry; no JS errors or business requests')
    browser.close()
