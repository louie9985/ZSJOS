# -*- coding: utf-8 -*-
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
out = Path(tempfile.gettempdir()) / 'zsjos-sales-course-full-labels'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto('http://127.0.0.1:5174/test/sales-course-full-labels.html')
        expect(page.locator('output')).to_have_text('course::0')
        page.wait_for_timeout(500)
        expect(page.locator('.sales-order-course-picker .ant-select-content')).to_have_count(3)
        for label in page.locator('.sales-order-course-picker .ant-select-content').all():
            assert label.evaluate('(el) => getComputedStyle(el).whiteSpace === "normal" && el.scrollWidth <= el.clientWidth + 1 && el.scrollHeight <= el.clientHeight + 1'), label.inner_text()
        page.get_by_role('combobox').last.click()
        popup = page.locator('.sales-order-course-popup:visible')
        expect(popup.locator('.ant-select-item-option')).to_have_count(12)
        for label in popup.locator('.ant-select-item-option-content').all():
            assert label.evaluate('(el) => getComputedStyle(el).whiteSpace === "normal" && el.scrollWidth <= el.clientWidth + 1 && el.scrollHeight <= el.clientHeight + 1'), label.inner_text()
        popup.locator('.ant-select-item-option').last.click()
        expect(page.locator('output')).to_have_text('course::11')
        page.keyboard.press('Escape')
        page.wait_for_timeout(300)
        page.screenshot(path=str(out / f'selected-{width}.png'), full_page=True)
        page.get_by_role('combobox').last.click()
        expect(page.locator('.sales-order-course-popup:visible')).to_be_visible()
        page.wait_for_timeout(300)
        page.screenshot(path=str(out / f'popup-{width}.png'), full_page=True)
        page.keyboard.press('Escape')
        page.get_by_role('combobox').first.click()
        category = page.locator('.sales-order-course-popup:visible .ant-cascader-menu-item-content')
        expect(category).to_have_count(1)
        assert category.evaluate('(el) => getComputedStyle(el).whiteSpace === "normal" && el.scrollWidth <= el.clientWidth + 1')
        page.keyboard.press('Escape')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        print('PASS', width)
        page.close()
    browser.close()
