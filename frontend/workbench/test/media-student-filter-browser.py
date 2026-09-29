# -*- coding: utf-8 -*-
"""Real Chromium / real MediaStudentsPage with isolated synthetic services."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('ZSJOS_UI_TEST_URL', 'http://localhost:5174')
out = Path(gettempdir()) / 'zsjos-media-student-filter'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(base + '/test/media-student-filter.html')
    cards = page.locator('.media-students-item')
    expect(cards).to_have_count(20)
    search = page.get_by_placeholder('搜索姓名或手机号')
    button = page.get_by_role('button', name='高级筛选', exact=True)
    # Same row, stable controls, equal card/toolbar edges and safe page insets.
    geometry = page.evaluate("""() => {
      const r = s => document.querySelector(s).getBoundingClientRect();
      const input = r('.advanced-filter-toolbar .ant-input-affix-wrapper'), filter = r('.advanced-filter-toolbar .ant-badge'), card = r('.media-students-item'), toolbar = r('.advanced-filter-toolbar'), workspace = r('.workspace-page');
      return { input: input.toJSON(), filter: filter.toJSON(), card: card.toJSON(), toolbar: toolbar.toJSON(), workspace: workspace.toJSON() };
    }""")
    assert geometry['input']['right'] < geometry['filter']['left']
    assert abs(geometry['input']['top'] - geometry['filter']['top']) < 2
    assert abs(geometry['toolbar']['right'] - geometry['card']['right']) < 2
    assert geometry['input']['width'] > 100
    page.screenshot(path=str(out / 'desktop.png'), full_page=True, animations='disabled')
    def template(name):
        button.click()
        page.locator('.advanced-filter-template-select').click()
        page.locator('.ant-select-item-option').filter(has_text='个人 · ' + name).click()
    template('运营甲')
    before = page.evaluate('mediaFilterFixture.queries.length')
    page.get_by_role('button', name=re.compile(r'取\s*消')).click()
    assert page.evaluate('mediaFilterFixture.queries.length') == before
    template('甲的抖音')
    page.evaluate('mediaFilterFixture.allowNavigation=false')
    before = page.evaluate('mediaFilterFixture.queries.length')
    page.locator('.advanced-filter-footer button').filter(has_text='应用筛选').click()
    expect(page.locator('.advanced-filter-drawer')).to_be_visible()
    assert page.evaluate('mediaFilterFixture.queries.length') == before
    page.evaluate('mediaFilterFixture.allowNavigation=true')
    page.locator('.advanced-filter-footer button').filter(has_text='应用筛选').click()
    expect(page.locator('.advanced-filter-drawer')).not_to_be_visible()
    expect(page.locator('.advanced-filter-tags')).to_contain_text('责任运营')
    expect(page.locator('.advanced-filter-tags')).to_contain_text('抖音')
    page.wait_for_function("mediaFilterFixture.queries.at(-1).method === 'post'")
    expect(cards).to_have_count(20)
    assert page.evaluate('mediaFilterFixture.queries.at(-1).advancedFilter.conditions.length') == 2
    page.locator('.media-students-scroll').evaluate('(node) => { node.scrollTop = node.scrollHeight }')
    expect(cards).to_have_count(40)
    assert page.evaluate('mediaFilterFixture.queries.at(-1).pageNo === 2 && mediaFilterFixture.queries.at(-1).advancedFilter.conditions.length === 2')
    # Search and tab changes preserve filters; clear only removes advanced conditions.
    search.fill('筛选学员1'); search.press('Enter')
    expect(cards).to_have_count(11)
    tabs = page.locator('.media-students-service-period-tabs')
    tabs.get_by_role('tab', name='非服务期', exact=True).click()
    expect(cards).to_have_count(0)
    assert page.evaluate('mediaFilterFixture.queries.at(-1).advancedFilter.conditions.length') == 2
    page.get_by_role('button', name='清空全部', exact=True).click()
    page.wait_for_function("mediaFilterFixture.queries.at(-1).method === 'get'")
    assert page.evaluate("mediaFilterFixture.queries.at(-1).keyword === '筛选学员1' && mediaFilterFixture.queries.at(-1).inServicePeriod === false")
    search.fill(''); search.press('Enter')
    expect(cards).to_have_count(1)
    tabs.get_by_role('tab', name='全部', exact=True).click()
    expect(cards).to_have_count(20)
    # Late old-filter response must not overwrite newer search/tab state.
    page.evaluate('mediaFilterFixture.nextDelay=900')
    search.fill('筛选学员2'); search.press('Enter')
    page.wait_for_function("mediaFilterFixture.queries.at(-1).keyword === '筛选学员2'")
    search.fill('筛选学员46'); search.press('Enter')
    expect(cards).to_have_count(1)
    expect(cards).to_contain_text('筛选学员46')
    page.wait_for_timeout(1100)
    expect(cards).to_have_count(1)
    expect(cards).to_contain_text('筛选学员46')
    # Collapse retains filter entry and active badge; template saving uses own scene/pageKey.
    template('运营甲')
    page.locator('.advanced-filter-drawer button').filter(has_text='保存模板').click()
    page.get_by_placeholder('模板名称').fill('浏览器验收模板')
    page.get_by_role('button', name=re.compile(r'确\s*定')).click()
    page.wait_for_function('mediaFilterFixture.templates.length === 3')
    assert page.evaluate("mediaFilterFixture.templates.at(-1).scene === 'media_student' && mediaFilterFixture.templates.at(-1).pageKey === 'media_students'")
    expect(page.locator('.ant-modal-content')).not_to_be_visible()
    page.locator('.advanced-filter-footer button').filter(has_text='应用筛选').click()
    page.get_by_role('button', name='收起学员列表', exact=True).click()
    expect(button).to_be_visible()
    expect(page.locator('.advanced-filter-toolbar .ant-badge-count')).to_be_visible()
    expect(search).not_to_be_visible()
    button.click()
    expect(page.get_by_text('高级筛选', exact=True).last).to_be_visible()
    page.get_by_role('button', name=re.compile(r'取\s*消')).click()
    page.get_by_role('button', name='展开学员列表', exact=True).click()
    # Error/retry uses the same filter request.
    page.evaluate("mediaFilterFixture.failure='list'")
    page.get_by_role('button', name='刷新学员', exact=True).click()
    expect(page.get_by_role('button', name='重试加载学员', exact=True)).to_be_visible()
    page.evaluate("mediaFilterFixture.failure=''")
    page.get_by_role('button', name='重试加载学员', exact=True).click()
    expect(cards).to_have_count(1)
    # Responsive drawer, toolbar and page never overflow the viewport.
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(button).to_be_visible()
    page.wait_for_function('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(out / 'mobile.png'), full_page=True, animations='disabled')
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    button.click()
    expect(page.get_by_role('button', name='应用筛选', exact=True)).to_be_visible()
    page.wait_for_function("() => { const r=document.querySelector('.advanced-filter-drawer').getBoundingClientRect(); return r.left >= 0 && r.left < 2 && r.right <= innerWidth + 1 && r.width > 300 }")
    expect(page.get_by_role('button', name='应用筛选', exact=True)).to_be_in_viewport()
    page.screenshot(path=str(out / 'mobile-drawer.png'), full_page=False)
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.get_by_role('button', name=re.compile(r'取\s*消')).click()
    # Catalog and dictionary failures are explicit and recoverable.
    page.goto(base + '/test/media-student-filter.html?catalog-error')
    button.click()
    expect(page.get_by_text('筛选字段加载失败', exact=True)).to_be_visible()
    page.evaluate("mediaFilterFixture.failure=''")
    page.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_role('button', name=re.compile(r'条\s*件$'))).to_be_visible()
    page.get_by_role('button', name=re.compile(r'取\s*消')).click()
    page.goto(base + '/test/media-student-filter.html?dict-error')
    template('甲的抖音')
    page.locator('.advanced-filter-condition').nth(1).locator('.advanced-filter-value-control .ant-select').click()
    expect(page.get_by_role('button', name='加载失败，重试', exact=True)).to_be_visible()
    page.evaluate("mediaFilterFixture.failure=''")
    page.get_by_role('button', name='加载失败，重试', exact=True).click()
    value_select = page.locator('.advanced-filter-condition').nth(1).locator('.advanced-filter-value-control .ant-select')
    expect(value_select).to_be_visible()
    value_select.click()
    expect(page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option[title=抖音]')).to_be_visible()
    assert not errors, errors
    browser.close()
print('PASS: toolbar alignment, draft/cancel/apply, templates, filters/search/tabs, pagination, stale responses, collapse, failures/retry, desktop/mobile; screenshots:', out)
