# -*- coding: utf-8 -*-
"""Real Chrome, shared React page with isolated HTTP; never mutates live business data."""
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-subordinate-lead-management'
OUT.mkdir(exist_ok=True)
BASE = 'http://127.0.0.1:5174/test/subordinate-lead-management.html'

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 1000})
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def open_sales(suffix=''):
        page.goto(BASE + suffix)
        page.get_by_text('测试销售5', exact=True).click()
        page.get_by_role('tab', name='名下客资').click()
        expect(page.locator('.lead-management-table').get_by_text('销售5客资42', exact=True)).to_be_visible()

    def last():
        return page.evaluate("scopedFixture.requests.filter(r=>/\/leads(?:\/search-page)?$/.test(r.url)).at(-1)")

    def search(value):
        box = page.get_by_placeholder('搜索客资编号 / 姓名 / 手机号 / 微信号')
        box.fill(value)
        box.press('Enter')

    def group(name, label):
        page.get_by_role('group', name=name, exact=True).get_by_role('button', name=label, exact=True).click()

    open_sales('?deep=1')
    table = page.locator('.lead-management-table')
    expect(page.get_by_role('dialog')).to_have_count(0)
    for column in ('客资编号', '姓名', '手机号', '微信号', '来源', '提交人', '负责人', '分类', '渠道'):
        expect(table.get_by_role('columnheader', name=column, exact=True)).to_have_count(1)
    expect(page.get_by_role('group', name='客资业务归类').get_by_role('button')).to_have_count(6)
    page.screenshot(path=str(OUT / 'desktop.png'), full_page=True)
    group('客资业务归类', '有效客资')
    expect(table.get_by_text('销售5客资03', exact=True)).to_be_visible()
    assert last()['inboxGroup'] == 'valid' and last()['audience'] == 'management'
    group('客资业务归类', '全部客资')
    group('快捷条件', '今日待跟进')
    expect(table.get_by_text('销售5客资42', exact=True)).to_be_visible()
    assert last()['inboxQuick'] == 'today'
    group('当前环节', '已挂起')
    expect(table.get_by_text('销售5客资42', exact=True)).to_be_visible()
    assert last()['inboxStage'] == 'suspended' and last()['inboxQuick'] == 'today'
    group('当前环节', '回收待处理')
    expect(table.get_by_text('销售5客资41', exact=True)).to_be_visible()
    group('当前环节', '全部')
    group('快捷条件', '全部')
    page.locator('.ant-pagination-item-2').click()
    expect(table.get_by_text('销售5客资21', exact=True)).to_be_visible()
    table.get_by_role('columnheader', name='姓名', exact=True).click()
    expect(table.get_by_text('销售5客资01', exact=True)).to_be_visible()
    assert last()['sortField'] == 'submittedName' and last()['pageNo'] == 1
    table.locator('.ant-table-tbody tr[data-row-key] input[type=checkbox]').first.check()
    expect(page.locator('.business-table-batch')).to_contain_text('已选 1 条')
    page.locator('.ant-pagination-item-2').click()
    expect(table.get_by_text('销售5客资21', exact=True)).to_be_visible()
    expect(page.locator('.business-table-batch')).to_contain_text('已选 1 条')
    search('S5-42')
    expect(table.get_by_text('销售5客资42', exact=True)).to_be_visible()
    expect(page.locator('.business-table-batch')).to_contain_text('已选 0 条')
    page.get_by_role('button', name=re.compile(r'详\s*细')).click()
    dialog = page.get_by_role('dialog')
    expect(dialog).to_be_visible()
    expect(dialog).to_contain_text('S5-42')
    dialog.get_by_role('button', name='关闭', exact=True).click()
    table.locator('.ant-table-tbody tr[data-row-key] input[type=checkbox]').first.check()
    page.get_by_role('button', name=re.compile(r'批量操作$')).click()
    expect(page.get_by_role('menuitem')).to_have_count(1)
    page.get_by_role('menuitem', name=re.compile(r'转\s*派$')).click()
    dialog = page.get_by_role('dialog', name='批量转派客资')
    expect(dialog).to_be_visible()
    dialog.get_by_label('目标销售').click()
    page.get_by_text('目标销售7', exact=True).click()
    dialog.get_by_label('操作原因').fill('隔离夹具转派验证')
    dialog.get_by_role('button', name=re.compile(r'确\s*定')).click()
    expect(page.get_by_role('dialog', name='批量操作完成')).to_be_visible()
    command = page.evaluate("scopedFixture.requests.find(r=>r.url.endsWith('/batch-transfer'))")
    assert command['leadIds'] == [542] and command['targetUserId'] == 7 and command['idempotencyKey']
    page.get_by_role('dialog', name='批量操作完成').get_by_role('button', name=re.compile(r'关\s*闭')).click()
    expect(table.get_by_text('销售5客资42', exact=True)).to_have_count(0)
    search('')
    expect(table.get_by_text('销售5客资01', exact=True)).to_be_visible()
    page.evaluate("scopedFixture.nextFailure='error'")
    search('S5-1')
    expect(page.get_by_text('模拟查询失败', exact=True)).to_be_visible()
    page.locator('.ant-alert').filter(has_text='模拟查询失败').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(table.get_by_text('销售5客资01', exact=True)).to_be_visible()
    page.evaluate("scopedFixture.nextFailure='denied'")
    search('S5-2')
    expect(page.get_by_text('无权查询客资', exact=True)).to_be_visible()
    page.locator('.lead-filter-actions').get_by_role('button').click()
    expect(table.get_by_text('销售5客资02', exact=True)).to_be_visible()
    search('missing')
    expect(table.get_by_text('当前筛选下暂无客资', exact=True)).to_be_visible()
    # In-flight old-sales responses and global assignment notifications cannot append another salesperson.
    page.evaluate('scopedFixture.nextDelay=900')
    search('S5-3')
    page.get_by_text('测试销售6', exact=True).click()
    page.get_by_role('tab', name='名下客资').click()
    expect(table.get_by_text('销售6客资42', exact=True)).to_be_visible()
    page.wait_for_timeout(1100)
    assert '/6/leads' in last()['url'] and not last().get('keyword') and not last().get('inboxStage')
    page.evaluate("window.dispatchEvent(new CustomEvent('zsjos-lead-inbox-unseen',{detail:{leadIds:[501]}}))")
    page.wait_for_timeout(1900)
    expect(table.get_by_text(re.compile('销售5客资'))).to_have_count(0)
    assert not page.evaluate("scopedFixture.requests.some(r=>r.url==='/zsjos/lead/get' && r.id===501)")
    assert not page.evaluate("scopedFixture.requests.some(r=>/^\/zsjos\/lead\/(page|search-page)$/.test(r.url))")

    # Responsive full page, filter intersection and advanced filter POST on the same shared component.
    open_sales('?layout=split')
    page.set_viewport_size({'width': 390, 'height': 844})
    group('当前环节', '已挂起')
    group('快捷条件', '今日待跟进')
    expect(table.get_by_text('销售5客资42', exact=True)).to_be_visible()
    page.get_by_role('button', name='高级筛选', exact=True).last.click()
    drawer = page.get_by_role('dialog', name='高级筛选')
    drawer.locator('.advanced-filter-template-select').click()
    page.get_by_text('个人 · 客资42', exact=True).click()
    drawer.get_by_role('button', name='应用筛选', exact=True).click()
    expect(drawer).not_to_be_visible()
    expect(table.get_by_text('销售5客资42', exact=True)).to_be_visible()
    page.wait_for_function("scopedFixture.requests.filter(r=>r.url.endsWith('/leads/search-page')).length>0")
    assert last()['method'] == 'post' and last()['inboxQuick'] == 'today' and last()['inboxStage'] == 'suspended'
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(OUT / 'mobile.png'), full_page=True)
    # Same source remains usable through the standalone page.
    page.set_viewport_size({'width': 1600, 'height': 1000})
    page.goto(BASE + '?general=1')
    expect(table.get_by_text('销售6客资42', exact=True)).to_be_visible()
    group('客资业务归类', '有效客资')
    expect(table.get_by_text('销售5客资03', exact=True)).to_be_visible()
    assert page.evaluate("scopedFixture.requests.some(r=>r.url==='/zsjos/lead/page')")
    # Server-projected editable detail is reused too; closing its drawer preserves the draft.
    open_sales('?dirty=1')
    table.get_by_role('button', name=re.compile(r'详\s*细')).first.click()
    dialog = page.get_by_role('dialog')
    dialog.get_by_label('跟进备注', exact=True).fill('保留当前销售的未提交跟进')
    dialog.get_by_role('button', name='关闭', exact=True).click()
    page.once('dialog', lambda d: d.dismiss())
    page.get_by_text('测试销售6', exact=True).click()
    expect(table.get_by_text('销售5客资42', exact=True)).to_be_visible()
    page.once('dialog', lambda d: d.dismiss())
    page.get_by_role('tab', name='概览', exact=True).click()
    expect(page.get_by_role('tab', name='名下客资')).to_have_attribute('aria-selected', 'true')
    table.get_by_role('button', name=re.compile(r'详\s*细')).first.click()
    expect(page.get_by_role('dialog').get_by_label('跟进备注', exact=True)).to_have_value('保留当前销售的未提交跟进')
    page.get_by_role('dialog').get_by_role('button', name='关闭', exact=True).click()
    page.once('dialog', lambda d: d.accept())
    page.get_by_text('测试销售6', exact=True).click()
    page.get_by_role('tab', name='名下客资').click()
    expect(table.get_by_text('销售6客资42', exact=True)).to_be_visible()
    open_sales('?denied=1')
    table.locator('.ant-table-tbody tr[data-row-key] input[type=checkbox]').first.check()
    expect(page.get_by_role('button', name=re.compile(r'批量操作$'))).to_be_disabled()
    page.evaluate('scopedFixture.profileFailure=true')
    page.locator('.lead-filter-actions').get_by_role('button').click()
    expect(page.get_by_text('模拟筛选方案失败', exact=True)).to_be_visible()
    page.evaluate('scopedFixture.profileFailure=false')
    page.get_by_role('button',name='重试筛选方案',exact=True).click()
    expect(page.get_by_role('group', name='客资业务归类')).to_be_visible()
    expect(page.get_by_text('模拟筛选方案失败', exact=True)).to_have_count(0)
    assert not errors, errors
    print('PASS: shared source/full filters and columns, fixed sales API, page/sort/selection, detail, supervisor transfer/result, retry/denied/empty, sales switch and late responses, realtime isolation, mobile/advanced POST, dirty form guards, missing command permission, profile retry and general-page compatibility')
    browser.close()
