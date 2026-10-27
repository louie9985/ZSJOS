# -*- coding: utf-8 -*-
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, expect
out = Path(tempfile.gettempdir()) / 'disabled-sku-selection-20261027'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto('http://127.0.0.1:5174/test/disabled-sku-selection.html')
        chosen = page.get_by_test_id('selection')
        expect(chosen).to_have_text('course::ax')
        page.get_by_role('combobox').nth(2).click()
        popup = page.locator('.sales-order-course-popup:visible')
        expect(popup.get_by_text('已停用历史等级', exact=True)).to_have_count(0)
        popup.get_by_text('乙级', exact=True).click()
        expect(chosen).to_have_text('EMPTY')
        expect(page.get_by_text('当前规格组合不可用，请重新选择', exact=True)).to_be_visible()
        page.get_by_role('button', name='提交测试', exact=True).click()
        expect(page.get_by_test_id('submitted')).to_have_text('未提交')
        expect(page.get_by_text('请选择成交课程', exact=True)).to_be_visible()
        page.keyboard.press('Escape')
        page.screenshot(path=str(out / f'invalid-{width}.png'), full_page=True)
        page.get_by_role('combobox').nth(3).click()
        page.locator('.sales-order-course-popup:visible').get_by_text('Y班', exact=True).click()
        expect(chosen).to_have_text('course::by')
        page.get_by_role('button', name='提交测试', exact=True).click()
        expect(page.get_by_test_id('submitted')).to_have_text('course::by')
        page.get_by_role('button', name='移除乙级SKU', exact=True).click()
        expect(chosen).to_have_text('EMPTY')
        page.get_by_role('button', name='清空启用SKU', exact=True).click()
        expect(chosen).to_have_text('EMPTY')
        page.get_by_role('button', name='查看历史订单', exact=True).click()
        expect(page.get_by_text('历史课程 / 历史停用规格', exact=True)).to_be_visible()
        expect(chosen).to_have_text('course::retired')
        page.get_by_role('button', name='重新选择课程', exact=True).click()
        expect(chosen).to_have_text('EMPTY')
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: desktop/mobile disabled options, stale SKU clearing, required validation, reselection, catalog refresh, empty and historical snapshot')
