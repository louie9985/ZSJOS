# -*- coding: utf-8 -*-
"""Real Chromium UI regression with synthetic transport; no live business writes."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

artifact_dir = Path(os.environ['MATERIAL_BROWSE_ARTIFACTS'])
base = 'http://material-browse-check.local/preview.html'

def fulfill(route):
    name = route.request.url.split('.local/', 1)[-1].split('?', 1)[0]
    file = artifact_dir / name
    if file.is_file() and file.parent == artifact_dir:
        kind = 'text/html' if file.suffix == '.html' else 'text/css' if file.suffix == '.css' else 'application/javascript'
        route.fulfill(path=str(file), content_type=kind + '; charset=utf-8')
    else:
        route.fulfill(status=404, body='Missing synthetic resource')

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    page.route('http://material-browse-check.local/**', fulfill)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(base + '?publication')
        cards = page.locator('.material-library-item')
        expect(cards).to_have_count(1)
        page.locator('.material-library-layout-switch [title="大图布局"]').click()
        page.evaluate('''() => { window.layoutRequests = 0; window.firstMaterial = document.querySelector('.material-library-item');
            new MutationObserver(records => { window.layoutRequests += records.filter(r => r.attributeName === 'data-request').length })
              .observe(document.documentElement, {attributes: true}); }''')
        page.locator('.material-library-layout-switch [title="紧凑布局"]').click()
        expect(page.locator('.material-library-compact')).to_be_visible()
        page.wait_for_timeout(200)
        assert page.evaluate('window.layoutRequests === 0 && window.firstMaterial === document.querySelector(".material-library-item")')
        columns = page.locator('.material-library-scroll').evaluate('(el) => getComputedStyle(el).gridTemplateColumns.split(" " ).length')
        assert columns == (4 if width == 1440 else 1), (width, columns)
        cover = cards.first.locator('.material-library-open').bounding_box()
        copy = cards.first.locator('.material-library-item-copy').bounding_box()
        assert cover['x'] < copy['x'] and abs(cover['y'] - copy['y']) < 2
        page.screenshot(path=str(artifact_dir / f'compact-{width}.png'))
        page.reload()
        expect(page.locator('.material-library-compact')).to_be_visible()
        expect(cards).to_have_count(1)
        page.locator('.material-library-layout-switch [title="大图布局"]').click()
        expect(page.locator('.material-library-large')).to_be_visible()
        page.screenshot(path=str(artifact_dir / f'large-{width}.png'))
        expect(cards.get_by_text('Published account', exact=True)).to_be_visible()
        for tab in ['收藏', '我的素材', '全部']:
            page.get_by_role('tab', name=tab, exact=True).click()
            expect(cards).to_have_count(1)
            page.wait_for_timeout(200)
            request = json.loads(page.locator('html').get_attribute('data-request'))
            assert bool(request.get('mine')) == (tab == '我的素材'), request
            assert bool(request.get('favorite')) == (tab == '收藏'), request
            assert 'versionStatus' not in request
        for name in ['创建爆款账号', '创建爆款内容', '继续编辑', '修改并重新送审', '撤回审批', '通过审批']:
            expect(page.get_by_role('button', name=name, exact=True)).to_have_count(0)
        expect(page.get_by_role('combobox', name='审核状态')).to_have_count(0)
        search = page.get_by_role('searchbox', name='搜索素材')
        search.fill('Published')
        search.press('Enter')
        page.wait_for_timeout(200)
        request_before = page.locator('html').get_attribute('data-request')
        page.locator('.material-library-layout-switch [title="紧凑布局"]').click()
        expect(search).to_have_value('Published')
        assert page.locator('html').get_attribute('data-request') == request_before
        cards.first.locator('.material-library-title').click()
        expect(page.get_by_text('查看爆款拆解', exact=True)).to_be_visible()
        page.wait_for_function('document.documentElement.dataset.detailRequest')
        expect(page.locator('.ant-drawer-body').get_by_text('Published account', exact=True)).to_be_visible()
        page.locator('.material-layout-summary-actions button').click()
        page.wait_for_function('document.documentElement.dataset.historyRequest')
        expect(page.get_by_text('草稿／待提交', exact=True)).to_have_count(0)
        page.screenshot(path=str(artifact_dir / f'browse-history-{width}.png'))
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.goto(base + '?denied')
        expect(page.get_by_text('无权访问素材库', exact=True)).to_be_visible()
        page.goto(base + '?error')
        page.locator('.ant-alert button').click()
        expect(cards).to_have_count(20)
    for width, columns in [(1630, 4), (1100, 3), (900, 2), (768, 1), (320, 1)]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(base)
        page.locator('.material-library-layout-switch [title="紧凑布局"]').click()
        expect(page.locator('.material-library-item')).to_have_count(20)
        actual = page.locator('.material-library-scroll').evaluate('(el) => getComputedStyle(el).gridTemplateColumns.split(" " ).length')
        assert actual == columns, (width, actual)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        page.screenshot(path=str(artifact_dir / f'compact-grid-{width}.png'))
    assert not errors, errors
    browser.close()
print('PASS effective-only browse UI, tabs, published detail/history, no management actions, denied/retry at 1440/390')
