# -*- coding: utf-8 -*-
"""Verify real withdrawal UI using the isolated synthetic transport fixture."""
import json
import os
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
from importlib.util import spec_from_file_location, module_from_spec

spec = spec_from_file_location('export_checks', Path(__file__).with_name('withdrawal-export-browser.py'))
helpers = module_from_spec(spec)
spec.loader.exec_module(helpers)
button = helpers.button

with sync_playwright() as pw:
    browser = pw.chromium.launch(channel='chrome', headless=True)
    try:
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        base = os.environ.get('WITHDRAWAL_TEST_BASE', 'http://127.0.0.1:5174') + '/test/withdrawal-export.html?status-tabs'
        page.goto(base)
        all_tab = page.get_by_role('tab', name='全部', exact=True)
        expect(page.get_by_role('tab', name='待打款', exact=True)).to_be_enabled(timeout=90000)
        expect(page.get_by_role('tab')).to_have_text(['全部', '待审核', '待打款', '已打款', '已驳回', '已撤销'])
        expect(all_tab).to_have_attribute('aria-selected', 'true')
        expect(page.get_by_text('TEST-1', exact=True)).to_be_visible()
        assert not page.evaluate('exportFixture.queries.at(-1).status')
        page.locator('.ant-pagination-item-2').click()
        page.wait_for_function('exportFixture.queries.at(-1).pageNo === 2')
        expect(page.get_by_text('TEST-11', exact=True)).to_be_visible()
        page.get_by_role('tab', name='待打款', exact=True).click()
        page.wait_for_function("exportFixture.queries.at(-1).status === 'approved' && exportFixture.queries.at(-1).pageNo === 1")
        expect(page.get_by_text('TEST-1', exact=True)).to_be_visible()
        expect(page.locator('.ant-pagination-item-1')).to_have_class(__import__('re').compile('.*ant-pagination-item-active.*'))
        page.locator('tr[data-row-key="1"] input[type=checkbox]').check()
        expect(button(page, '批量登记打款（1）')).to_be_enabled()
        page.get_by_role('tab', name='已打款', exact=True).click()
        expect(page.get_by_text('TEST-13', exact=True)).to_be_visible()
        expect(button(page, '批量登记打款（0）')).to_be_disabled()
        keyword = page.get_by_placeholder('提现单号 / 银行流水号').first
        keyword.fill('TEST')
        keyword.press('Enter')
        page.wait_for_function("exportFixture.queries.at(-1).keyword === 'TEST' && exportFixture.queries.at(-1).status === 'paid'")
        button(page, '导出').click()
        button(page, '加入导出队列').click()
        page.wait_for_function('exportFixture.calls.length === 1')
        exported = json.loads(page.evaluate('exportFixture.calls[0].body.filterJson'))
        assert exported['status'] == 'paid' and exported['keyword'] == 'TEST', exported
        expect(button(page, '查看导出任务')).to_be_visible()
        page.keyboard.press('Escape')
        expect(button(page, '查看导出任务')).to_have_count(0)
        page.evaluate("exportFixture.listDelay.approved = 800")
        page.get_by_role('tab', name='待打款', exact=True).click()
        page.wait_for_function("exportFixture.queries.at(-1).status === 'approved'")
        page.get_by_role('tab', name='已打款', exact=True).click()
        expect(page.get_by_text('TEST-13', exact=True)).to_be_visible()
        page.wait_for_timeout(1000)
        expect(page.get_by_text('TEST-1', exact=True)).to_have_count(0)
        expect(page.get_by_text('TEST-13', exact=True)).to_be_visible()
        page.get_by_role('tab', name='已驳回', exact=True).click()
        expect(page.get_by_text('当前筛选下暂无提现记录', exact=True)).to_be_visible()
        page.evaluate('exportFixture.loadError = true')
        page.get_by_role('tab', name='待审核', exact=True).click()
        expect(page.get_by_role('alert').filter(has_text='提现列表加载失败')).to_be_visible()
        page.evaluate('exportFixture.loadError = false')
        button(page, '重试').click()
        expect(page.get_by_role('alert').filter(has_text='提现列表加载失败')).to_have_count(0)
        all_tab.click()
        expect(page.get_by_text('TEST-1', exact=True)).to_be_visible()
        assert not page.evaluate('exportFixture.queries.at(-1).status')
        assert page.evaluate('exportFixture.queries.at(-1).keyword') == 'TEST'
        for width, height, label in [(1440, 1000, 'desktop'), (390, 844, 'mobile')]:
            page.set_viewport_size({'width': width, 'height': height})
            page.wait_for_timeout(350)
            expect(all_tab).to_be_visible()
            page.screenshot(path=str(Path(tempfile.gettempdir()) / f'withdrawal-status-tabs-{label}.png'))
        page.set_viewport_size({'width': 1440, 'height': 1000})
        page.goto(base + '&catalog-error')
        expect(page.get_by_role('alert').filter(has_text='筛选选项加载失败')).to_be_visible()
        expect(page.get_by_role('tab', name='全部', exact=True)).to_have_attribute('aria-disabled', 'true')
        page.evaluate('exportFixture.catalogError = false')
        button(page, '重试').click()
        expect(page.get_by_role('tab', name='待打款', exact=True)).to_be_enabled()
        page.goto(base + '&own')
        expect(page.get_by_text('TEST-1', exact=True)).to_be_visible()
        expect(page.get_by_role('tab')).to_have_count(0)
        expect(page.get_by_text('提现状态', exact=True)).to_be_visible()
        assert not errors, errors
        print('PASS: server status tabs, first-page reset, selection clearing, keyword/export filters, race protection, empty/error/retry, catalog retry, own-view preservation, desktop/mobile screenshots')
    finally:
        browser.close()
