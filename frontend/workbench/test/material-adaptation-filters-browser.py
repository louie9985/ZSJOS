# -*- coding: utf-8 -*-
"""Dictionary-backed material filters against the real page with isolated transport."""
import json
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

artifacts = Path(os.environ['MATERIAL_BROWSE_ARTIFACTS']).resolve()
base = 'http://material-browse-check.local/preview.html?adaptation'
long_label = '用于检查完整显示的超长账号类型字典选项'


def fulfill(route):
    name = route.request.url.split('.local/', 1)[-1].split('?', 1)[0]
    file = artifacts / name
    if file.is_file() and file.parent == artifacts:
        kind = 'text/html' if file.suffix == '.html' else 'text/css' if file.suffix == '.css' else 'application/javascript'
        route.fulfill(path=str(file), content_type=kind + '; charset=utf-8')
    else:
        route.fulfill(status=404, body='Missing fixture resource')


def choose(page, field, label):
    page.get_by_role('combobox', name=field, exact=True).click()
    page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter(has_text=label).click()


def request(page, **values):
    page.wait_for_function('(values) => { const req = JSON.parse(document.documentElement.dataset.request || "{}"); return Object.entries(values).every(([key, value]) => req[key] === value) }', arg=values)
    return json.loads(page.locator('html').get_attribute('data-request'))


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    page.route('http://material-browse-check.local/**', fulfill)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    for width in [1440, 1280, 1024, 390, 320]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(base)
        expect(page.locator('.material-library-item').first).to_be_visible()
        for label in ['适配账号类型', '适配业务定位', '适配账号期段']:
            control = page.get_by_role('combobox', name=label, exact=True)
            expect(control).to_be_enabled()
        assert page.locator('.material-library-adaptation-select').evaluate_all('els => els.every(e => { const p = e.querySelector(".ant-select-placeholder"); return !p || p.scrollWidth <= p.clientWidth + 1 })')
        if width > 768:
            centers = page.locator('.material-library-toolbar > *').evaluate_all('els => els.map(e => {const r=e.getBoundingClientRect(); return r.y+r.height/2})')
            assert max(centers) - min(centers) < 3, (width, centers)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        choose(page, '适配账号类型', long_label)
        request(page, accountType='persona-a', pageNo=1)
        expect(page.locator('.material-library-item')).to_have_count(12)
        label = page.locator('.material-library-filter-label')
        expect(label).to_have_text(long_label)
        assert label.evaluate('e => { const r=e.getBoundingClientRect(), p=e.closest(".ant-select").getBoundingClientRect(); return e.scrollHeight<=e.clientHeight+1 && e.scrollWidth<=e.clientWidth+1 && r.bottom<=p.bottom+1 && r.right<=p.right+1 }'), width
        expect(page.locator('.ant-select-dropdown:visible')).to_have_count(0)
        page.screenshot(path=str(artifacts / f'adaptation-long-label-{width}.png'))
        choose(page, '适配业务定位', '业务定位乙')
        choose(page, '适配账号期段', '账号期段甲')
        request(page, accountType='persona-a', profession='profession-b', accountStage='stage-a', pageNo=1)
        expect(page.locator('.material-library-item')).to_have_count(4)
        for tab in ['收藏', '我的素材', '全部']:
            page.get_by_role('tab', name=tab, exact=True).click()
            request(page, accountType='persona-a', profession='profession-b', accountStage='stage-a', pageNo=1)
            page.wait_for_function('(personal) => document.documentElement.dataset.listUrl === (personal ? "/zsjos/material/page" : "/zsjos/material/browse/page")', arg=tab == '我的素材')
        choose(page, '适配账号期段', '账号期段乙')
        expect(page.get_by_text('暂无素材', exact=True)).to_be_visible()
        page.get_by_role('button', name='更多素材操作').click()
        page.get_by_text('清空适配筛选', exact=True).click()
        page.wait_for_function('() => {const r=JSON.parse(document.documentElement.dataset.request);return !r.accountType && !r.profession && !r.accountStage}')
        expect(page.locator('.material-library-item').first).to_be_visible()
        page.locator('.material-library-layout-switch [title="紧凑布局"]').click()
        expect(page.locator('.material-library-compact')).to_be_visible()
    # Pagination must retain selected conditions, and a new filter must reset to page one.
    page.set_viewport_size({'width': 1440, 'height': 700})
    page.goto(base)
    choose(page, '适配业务定位', '业务定位甲')
    page.locator('.material-library-load-more').scroll_into_view_if_needed()
    request(page, profession='profession-a', pageNo=2)
    choose(page, '适配账号期段', '账号期段乙')
    request(page, profession='profession-a', accountStage='stage-b', pageNo=1)
    # Failed dictionaries stay disabled until the existing retry succeeds.
    page.goto(base + '&dictError')
    expect(page.get_by_role('combobox', name='适配账号类型', exact=True)).to_be_disabled()
    page.locator('.ant-alert').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_role('combobox', name='适配账号类型', exact=True)).to_be_enabled()
    choose(page, '适配账号类型', long_label)
    request(page, accountType='persona-a')
    assert not errors, errors
    browser.close()
print('PASS adaptation filters: full labels, desktop single row, mobile, combinations, tabs, empty/reset, pagination, dictionary retry')
