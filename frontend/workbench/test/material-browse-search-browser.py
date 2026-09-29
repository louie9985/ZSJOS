# -*- coding: utf-8 -*-
import json
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = 'http://localhost:5174/test/material-browse-search.html'
ARTIFACTS = Path(os.environ.get('TEMP', '.')) / 'material-browse-search'
ARTIFACTS.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(BASE)
        cards = page.locator('.material-library-item')
        expect(cards).to_have_count(20)
        for index in range(3):
            image = cards.nth(index).locator('img')
            image.scroll_into_view_if_needed()
            expect(image).to_be_visible()
            page.wait_for_function('(img) => img.complete && img.naturalWidth > 0', arg=image.element_handle())
            ratio = image.evaluate('(img) => ({actual: img.clientWidth / img.clientHeight, original: img.naturalWidth / img.naturalHeight, gap: img.parentElement.clientWidth - img.clientWidth})')
            assert abs(ratio['actual'] - ratio['original']) < .02, ratio
            assert abs(ratio['gap']) < 2, ratio
        expect(cards.nth(3).get_by_text('暂无封面')).to_be_visible()
        cards.nth(4).scroll_into_view_if_needed()
        expect(cards.nth(4).get_by_text('图片加载失败')).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert page.locator('button button').count() == 0
        first = cards.first
        first.scroll_into_view_if_needed()
        expect(first.locator('.material-search-field')).to_have_count(0)
        toggle = first.get_by_role('button', name='展开关键词 / 可检索内容（6项）', exact=True)
        expect(toggle).to_have_attribute('aria-expanded', 'false')
        page.screenshot(path=str(ARTIFACTS / f'collapsed-{width}.png'))
        toggle.click()
        expect(first.locator('.material-search-field')).to_have_count(6)
        first.get_by_role('button', name='收起关键词 / 可检索内容', exact=True).click()
        expect(first.locator('.material-search-field')).to_have_count(0)
        toggle.click()
        expect(first.get_by_role('button', name='查找：历史分类', exact=True)).to_be_visible()
        expect(first.get_by_text('不可展示', exact=True)).to_have_count(0)
        expect(first.get_by_text('富文本 & 中文', exact=True)).to_be_visible()
        page.screenshot(path=str(ARTIFACTS / f'cards-{width}.png'))
        first.get_by_role('button', name='查找：摄影', exact=True).click()
        expect(page.get_by_role('searchbox', name='搜索素材')).to_have_value('摄影')
        expect(page.locator('.ant-drawer')).to_have_count(0)
        request = json.loads(page.locator('html').get_attribute('data-request'))
        assert request['keyword'] == '摄影' and request['pageNo'] == 1
        search = page.get_by_role('searchbox', name='搜索素材')
        search.fill('独立命中'); search.press('Enter')
        hit = cards.first.locator('.material-search-hits button').filter(has_text='作品 / 第2项 / 说明')
        expect(hit).to_be_visible()
        expect(cards.first.locator('.material-search-field')).to_have_count(0)
        hit.click()
        detail = page.get_by_role('dialog', name='查看爆款拆解', exact=True)
        expect(detail).to_be_visible()
        expect(detail.get_by_role('button', name='版本记录')).to_be_visible()
        expect(detail.locator('.material-search-content')).to_have_count(0)
        expect(detail.get_by_text('可检索内容', exact=True)).to_have_count(0)
        page.screenshot(path=str(ARTIFACTS / f'detail-{width}.png'))
        page.get_by_role('button', name='Close', exact=True).click()
        search.fill('长文命中'); search.press('Enter')
        expect(cards.first.locator('.material-search-hits mark')).to_have_text('长文命中')
        search.fill('服务端命中不可见字段'); search.press('Enter')
        expect(cards.first.get_by_text('当前可见内容中未定位到命中位置')).to_be_visible()
        search.fill('不存在'); search.press('Enter')
        expect(page.get_by_text('暂无素材', exact=True)).to_be_visible()
        search.fill(''); search.press('Enter')
        expect(cards).to_have_count(20)
        page.locator('.material-library-load-more').scroll_into_view_if_needed()
        expect(cards).to_have_count(23)
        expect(page.get_by_text('已加载全部 23 条素材')).to_be_visible()
        page.get_by_role('tab', name='我的素材', exact=True).click()
        expect(page.locator('.material-library-mine')).to_be_visible()
        expect(cards.first.locator('img')).to_be_visible()
        assert cards.first.locator('img').evaluate('(img) => img.clientWidth > 250')
        page.get_by_role('tab', name='收藏', exact=True).click()
        cards.first.get_by_role('button', name='展开关键词 / 可检索内容（6项）', exact=True).click()
        cards.first.get_by_role('button', name='查找：摄影', exact=True).click()
        page.wait_for_function('JSON.parse(document.documentElement.dataset.request).keyword === "摄影"')
        assert json.loads(page.locator('html').get_attribute('data-request'))['favorite'] is True
    page.goto(BASE)
    expect(page.get_by_role('tab')).to_have_count(3)
    expect(page.get_by_role('tab', name='推荐', exact=True)).to_have_count(0)
    expect(page.get_by_role('tab', name='全部', exact=True)).to_have_attribute('aria-selected', 'true')
    assert page.locator('html').get_attribute('data-unexpected-recommendation') is None
    page.get_by_role('combobox').first.click()
    page.locator('.ant-select-item-option-content').filter(has_text='验收素材').click()
    page.locator('.material-library-item').first.get_by_role('button', name='展开关键词 / 可检索内容（6项）', exact=True).click()
    page.locator('.material-library-item').first.get_by_role('button', name='查找：摄影', exact=True).click()
    page.wait_for_function('JSON.parse(document.documentElement.dataset.request).keyword === "摄影"')
    request = json.loads(page.locator('html').get_attribute('data-request'))
    assert request['materialTypeId'] == 2 and request['pageNo'] == 1
    assert 'accountId' not in request and 'recommendation' not in request
    page.get_by_role('button', name='close-circle', exact=True).click()
    expect(page.get_by_role('searchbox', name='搜索素材')).to_have_value('')
    page.wait_for_function('!JSON.parse(document.documentElement.dataset.request).keyword')
    page.goto(BASE + '?error')
    expect(page.get_by_text('素材加载失败（验收）', exact=True)).to_be_visible()
    page.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.locator('.material-library-item')).to_have_count(20)
    page.goto(BASE + '?denied')
    expect(page.get_by_text('无权访问素材库', exact=True)).to_be_visible()
    expect(page.locator('.material-library-item')).to_have_count(0)
    assert not errors, errors
    browser.close()
print(f'PASS: desktop/mobile original aspect ratios, fields, hits, search, pagination, error/retry/denied; screenshots: {ARTIFACTS}')
