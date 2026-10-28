# -*- coding: utf-8 -*-
"""Real React components and Chrome, isolated synthetic API transport only."""
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('ZSJOS_UI_TEST_URL', 'http://127.0.0.1:5174') + '/test/my-operations.html'
OUT = Path(__file__).resolve().parents[1] / 'tmp' / 'my-operations'
OUT.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.route('**/admin-api/**', lambda route: route.abort('blockedbyclient'))
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    def visit(query=''):
        page.goto(BASE + query, wait_until='networkidle')
    def select_member(name='测试运营甲'):
        page.get_by_role('button', name=name).click()
        expect(page.get_by_role('tab', name='名下客资', exact=True)).to_have_attribute('aria-selected', 'true')
    def requests():
        return page.evaluate('myOperationsFixture.requests')

    visit('?denied')
    expect(page.get_by_text('暂无我的运营查看权限')).to_be_visible()
    assert requests() == [], 'Unauthorized page must not load members or dictionaries'
    visit('?empty')
    expect(page.get_by_text('暂无关联运营，请检查编导与运营关联配置')).to_be_visible()
    visit('?error')
    expect(page.get_by_text('运营团队暂时不可用')).to_be_visible()
    page.get_by_role('button', name=re.compile('重\s*试')).click()
    expect(page.get_by_role('button', name='测试运营甲')).to_be_visible()
    expect(page.get_by_role('button', name='测试运营乙')).to_contain_text('停用')
    page.get_by_label('搜索运营姓名').fill('不存在')
    page.get_by_label('搜索运营姓名').press('Enter')
    expect(page.get_by_text('没有匹配的运营')).to_be_visible()

    visit()
    select_member()
    expect(page.get_by_text('KZ-OPERATIONS-21', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='批量操作', exact=True)).to_have_count(0)
    expect(page.locator('.ant-table-selection-column')).to_have_count(0)
    page.get_by_role('button', name='今日提交', exact=True).click()
    page.wait_for_function("myOperationsFixture.requests.some(r => r.body?.inboxQuick === 'today')")
    page.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号').fill('KZ-TEST')
    page.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号').press('Enter')
    page.wait_for_function("myOperationsFixture.requests.some(r => r.body?.keyword === 'KZ-TEST')")
    page.screenshot(path=str(OUT / 'desktop.png'), full_page=True, animations='disabled')
    page.get_by_role('button', name=re.compile('详\s*细')).click()
    expect(page.locator('.ant-drawer-body')).to_contain_text('代录归属客资')
    page.wait_for_function("myOperationsFixture.requests.some(r => r.url.endsWith('/21/leads/210'))")
    expect(page.locator('.ant-drawer-body').get_by_role('button', name='编辑')).to_have_count(0)
    page.screenshot(path=str(OUT / 'desktop-detail.png'), full_page=True, animations='disabled')
    page.locator('.ant-drawer-close').click()
    select_member('测试运营乙')
    expect(page.get_by_text('KZ-OPERATIONS-22', exact=True)).to_be_visible()
    expect(page.get_by_text('KZ-OPERATIONS-21', exact=True)).to_have_count(0)
    assert not any(r['url'].startswith('/zsjos/lead/') for r in requests()), 'Embedded list/detail must use scoped operations API'

    visit('?overlap')
    select_member()
    page.get_by_role('button', name=re.compile('详\s*细')).click()
    page.wait_for_function("myOperationsFixture.requests.some(r => r.url.endsWith('/210/context'))")
    page.locator('.ant-drawer-body').get_by_role('tab', name='申诉记录', exact=True).click()
    expect(page.locator('.ant-drawer-body').get_by_role('button', name=re.compile('发起.*申诉'))).to_have_count(0)
    expect(page.locator('.ant-drawer-body').get_by_role('button', name=re.compile('转派|协助|编辑'))).to_have_count(0)
    page.locator('.ant-drawer-body').get_by_role('tab', name=re.compile('跟进记录')).click()
    expect(page.locator('.ant-drawer-body').get_by_role('button', name='新增跟进')).to_have_count(0)
    page.locator('.ant-drawer-close').click()

    visit('?lead-error')
    select_member()
    expect(page.get_by_text('无权查看该运营的客资', exact=True)).to_be_visible()
    page.evaluate('myOperationsFixture.failLeads = false')
    page.locator('.lead-filter-actions button').click()
    expect(page.get_by_text('KZ-OPERATIONS-21', exact=True)).to_be_visible()

    page.set_viewport_size({'width': 390, 'height': 844})
    visit()
    expect(page.get_by_role('button', name='测试运营甲')).to_be_visible()
    page.screenshot(path=str(OUT / 'mobile-members.png'), full_page=True, animations='disabled')
    select_member()
    expect(page.get_by_role('button', name='返回运营团队')).to_be_visible()
    expect(page.get_by_role('complementary', name='运营成员')).not_to_be_visible()
    expect(page.get_by_text('KZ-OPERATIONS-21', exact=True)).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'No viewport overflow'
    page.screenshot(path=str(OUT / 'mobile-leads.png'), full_page=True, animations='disabled')
    page.get_by_role('button', name=re.compile('详\s*细')).click()
    expect(page.locator('.ant-drawer-body')).to_contain_text('代录归属客资')
    page.screenshot(path=str(OUT / 'mobile-detail.png'), full_page=True, animations='disabled')
    page.locator('.ant-drawer-close').click()
    page.get_by_role('button', name='返回运营团队').click()
    expect(page.get_by_role('button', name='测试运营乙')).to_be_visible()
    assert not errors, errors
    assert all(r['method'] == 'get' or r['url'].endswith('/leads/search-page') for r in requests())
    print('PASS: allowed/denied, empty/search-empty, retry, scope switch, quick/keyword filters, read-only detail, desktop/mobile layout')
    browser.close()
