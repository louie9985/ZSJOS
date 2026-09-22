# UTF-8. Synthetic browser acceptance; no business endpoints or production data.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL = 'http://127.0.0.1:5174/test/content-review-layout.html'
ARTIFACTS = Path(__file__).resolve().parents[1] / 'node_modules/.cache/content-review-layout'
ARTIFACTS.mkdir(parents=True, exist_ok=True)

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 1000})
    errors = []
    business_requests = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.on('request', lambda request: business_requests.append(request.url)
            if any(part in request.url for part in ('/admin-api/', '/app-api/', '/part-api/')) else None)

    def load():
        page.goto(URL)
        expect(page.get_by_role('heading', name='内容审核', exact=True)).to_be_visible()
        expect(page.locator('.crp-inbox-item')).to_have_count(3)

    def scenario(label):
        page.get_by_role('combobox', name='预览场景').click()
        page.get_by_title(label, exact=True).click()

    def select(label, option):
        page.get_by_role('combobox', name=label).click()
        page.get_by_title(option, exact=True).click()

    load()
    expect(page.get_by_role('tab', name='待审批', exact=True)).to_have_attribute('aria-selected', 'true')
    assert 'CR-DEMO' not in page.locator('.crp-inbox').inner_text()
    process = page.locator('.crp-process').bounding_box()
    content = page.locator('.crp-content').bounding_box()
    assert process['x'] > content['x'] + content['width']
    page.screenshot(path=str(ARTIFACTS / 'desktop.png'), animations='disabled')

    # The fixed 180px reader must contain the exact full manuscript, and reach its last line.
    script = page.get_by_label('正文文稿 1', exact=True)
    assert len(script.inner_text()) == 10000
    script.scroll_into_view_if_needed()
    script.evaluate('(el) => { el.scrollTop = el.scrollHeight }')
    metrics = script.evaluate('''el => {
      const text = el.firstChild; const range = document.createRange();
      range.setStart(text, text.length - 19); range.setEnd(text, text.length);
      const end = range.getBoundingClientRect(); const box = el.getBoundingClientRect();
      return { remaining: el.scrollHeight - el.clientHeight - el.scrollTop,
        endVisible: end.bottom <= box.bottom + 1 && end.top >= box.top - 1,
        horizontalOverflow: el.scrollWidth - el.clientWidth };
    }''')
    assert metrics['remaining'] <= 1 and metrics['endVisible'] and metrics['horizontalOverflow'] <= 1, metrics
    page.screenshot(path=str(ARTIFACTS / 'manuscript-end.png'), animations='disabled')
    print('PASS: wide layout; 10,000 characters; end marker visible; no horizontal text clipping')

    # Account navigation shares one internal tab, preserving an unsaved review and scroll.
    opinion = page.get_by_role('textbox', name='作品 1 审核意见', exact=True)
    opinion.fill('保留这条未保存的审核意见')
    link = page.locator('.crp-profile').first.get_by_role('link')
    link.scroll_into_view_if_needed()
    before = page.locator('.crp-detail').evaluate('el => el.scrollTop')
    link.click()
    expect(page.get_by_role('region', name='媒体学员账号详情').get_by_role('heading', name='小禾的轻食日记', exact=True)).to_be_visible()
    expect(page.locator('.crp-tab-with-close')).to_have_count(1)
    page.screenshot(path=str(ARTIFACTS / 'account-tab.png'), animations='disabled')
    page.get_by_role('button', name='返回内容审核', exact=True).click()
    expect(opinion).to_have_value('保留这条未保存的审核意见')
    after = page.locator('.crp-detail').evaluate('el => el.scrollTop')
    assert abs(before - after) <= 1, (before, after)
    page.locator('.crp-profile').nth(1).get_by_role('link').click()
    expect(page.get_by_role('region', name='媒体学员账号详情').get_by_role('heading', name='小禾的厨房与四季生活记录', exact=True)).to_be_visible()
    expect(page.locator('.crp-tab-with-close')).to_have_count(1)
    page.get_by_role('button', name='维护账号资料', exact=True).click()
    page.get_by_role('textbox', name='账号维护备注').fill('尚未保存的账号备注')
    page.get_by_role('button', name='返回内容审核', exact=True).click()
    page.locator('.crp-profile').first.get_by_role('link').click()
    expect(page.get_by_role('dialog', name='账号页面有未保存修改', exact=True)).to_be_visible()
    page.get_by_role('button', name='继续编辑', exact=True).click()
    page.get_by_role('navigation', name='工作台内部标签').get_by_role('button', name='媒体学员', exact=True).click()
    expect(page.get_by_role('textbox', name='账号维护备注')).to_have_value('尚未保存的账号备注')
    page.get_by_role('button', name='关闭媒体学员标签').click()
    page.get_by_role('button', name='放弃并关闭', exact=True).click()
    expect(opinion).to_have_value('保留这条未保存的审核意见')
    expect(page.locator('.crp-tab-with-close')).to_have_count(0)
    print('PASS: shared account tab, precise target, retained review/scroll, account edit guard and close')

    load()
    for keyword in ('小禾', '小禾的厨房', '提前分装食材', 'CR-DEMO-20260922-01'):
        search = page.get_by_role('searchbox', name='搜索内容审核')
        search.fill(keyword)
        search.press('Enter')
        expect(page.locator('.crp-inbox-item').first).to_contain_text('小禾')
    page.get_by_role('button', name='重置', exact=True).click()
    page.get_by_role('button', name='筛选', exact=True).click()
    select('责任运营筛选', '运营甲')
    select('平台筛选', '小红书')
    expect(page.get_by_text('没有符合条件的批次', exact=True)).to_be_visible()
    page.get_by_role('button', name='重置', exact=True).click()
    select('审批阶段筛选', '终审')
    expect(page.locator('.crp-inbox-item')).to_have_count(1)
    expect(page.locator('.crp-inbox-item')).to_contain_text('阿川')
    page.get_by_role('button', name='重置', exact=True).click()
    page.screenshot(path=str(ARTIFACTS / 'filters.png'), animations='disabled')
    for label in ('草稿', '待修改', '待发布', '已发布', '已取消'):
        page.get_by_role('tab', name=label, exact=True).click()
        expect(page.locator('.crp-inbox-item')).to_have_count(1)
    print('PASS: search, same-account filters, stage filter, reset, status categories')

    load()
    scenario('加载中')
    expect(page.locator('.crp-inbox .ant-skeleton')).to_be_visible()
    scenario('空列表')
    expect(page.get_by_text('没有符合条件的批次', exact=True)).to_be_visible()
    scenario('加载失败')
    expect(page.get_by_text('列表加载失败', exact=True)).to_be_visible()
    page.get_by_role('button', name='重试', exact=True).click()
    expect(page.locator('.crp-inbox-item')).to_have_count(3)
    for label, text in [('账号无权限', '无权查看此账号'), ('账号已失效', '账号已失效或不再属于此学员')]:
        scenario(label)
        page.locator('.crp-profile').first.get_by_role('link').click()
        expect(page.get_by_text(text, exact=True)).to_be_visible()
        page.get_by_role('button', name='返回内容审核', exact=True).click()
    print('PASS: loading, empty, error/retry, denied and missing account')

    for return_one in (False, True):
        load()
        for index in range(3):
            work = page.get_by_role('article', name=f'作品 {index + 1}', exact=True)
            work.get_by_role('radio', name='不通过' if return_one and index == 0 else '通过', exact=True).check()
            work.get_by_role('textbox', name=f'作品 {index + 1} 审核意见').fill('演示审核意见')
            work.get_by_role('button', name='保存结论', exact=True).click()
        action = '退回运营修改' if return_one else '通过本次审批'
        page.get_by_role('button', name=action, exact=True).click()
        page.get_by_role('textbox', name='本轮审核意见').fill('演示本轮意见')
        page.get_by_role('button', name='确认（仅演示）', exact=True).click()
        if return_one:
            page.get_by_role('tab', name='待修改', exact=True).click()
            expect(page.locator('.crp-inbox-item').first).to_contain_text('小禾')
        else:
            expect(page.locator('.crp-inbox-item').first).to_contain_text('待终审')
    print('PASS: saved per-item decisions, simulated approve and return transitions')

    for width in (1280, 390):
        page.set_viewport_size({'width': width, 'height': 844})
        load()
        if width == 390:
            page.screenshot(path=str(ARTIFACTS / 'mobile-inbox.png'), animations='disabled')
            page.locator('.crp-inbox-item').first.click()
            expect(page.get_by_role('button', name='返回收件箱')).to_be_visible()
        process = page.locator('.crp-process').bounding_box()
        content = page.locator('.crp-content').bounding_box()
        assert process['y'] < content['y'], (width, process, content)
        assert page.locator('.crp-process .ant-collapse-header').get_attribute('aria-expanded') == 'false'
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1')
        page.screenshot(path=str(ARTIFACTS / f'layout-{width}.png'), animations='disabled')
        if width == 390:
            script = page.get_by_label('正文文稿 1', exact=True)
            script.scroll_into_view_if_needed()
            script.evaluate('el => { el.scrollTop = el.scrollHeight }')
            assert script.evaluate('el => el.scrollWidth <= el.clientWidth + 1')
            page.screenshot(path=str(ARTIFACTS / 'mobile-manuscript.png'), animations='disabled')
            page.get_by_role('button', name='返回收件箱').click()
            expect(page.locator('.crp-inbox')).to_be_visible()
        print(f'PASS: responsive layout {width}, process on top, no viewport overflow')

    assert not errors, errors
    assert not business_requests, business_requests
    print('PASS: zero browser runtime errors and zero business API requests')
    browser.close()
