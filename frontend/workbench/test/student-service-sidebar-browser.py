# -*- coding: utf-8 -*-
"""Real components with isolated API fixtures; no authenticated business writes."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('STUDENT_TABLE_TEST_BASE', 'http://127.0.0.1:5174')
output = Path(__file__).resolve().parents[1] / 'tmp' / 'student-service-sidebar'
output.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width, layout, long in [(1920, 'inbox', False), (1440, 'inbox', False), (1280, 'table', False), (390, 'inbox', False), (320, 'inbox', True), (1440, 'inbox', True)]:
        page = browser.new_page(viewport={'width': width, 'height': 1080})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(f'{base}/test/student-service-sidebar.html?layout={layout}' + ('&long=1' if long else ''))
        selector = 'tr.ant-table-row' if layout == 'table' else '#student-avatar-list button.lead-inbox-item'
        expect(page.locator(selector)).to_have_count(2)
        page.locator(selector).nth(1).click()
        card = page.locator('.student-service-card').filter(visible=True)
        expect(card).to_have_count(1)
        expect(card.locator('.student-service-card-course')).to_contain_text('课程2')
        expect(card.locator('.student-service-card-class')).to_contain_text('班级2')
        expect(card).to_contain_text('规划师2')
        expect(card).to_contain_text('编导2')
        expect(card).to_contain_text('未分配')
        expect(card).to_contain_text('人员姓名未提供')
        page.wait_for_timeout(400)
        expect(card.locator('.detail-field-grid')).to_have_count(0)
        aside = page.locator('.lead-overview-aside').filter(visible=True)
        expect(aside.locator('.lead-status-card + .student-service-card')).to_have_count(1)
        expect(aside.locator('.lead-status-labels')).to_contain_text('已接收')
        assert card.bounding_box()['y'] > aside.locator('.lead-status-card').bounding_box()['y']
        assert card.evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
        assert card.locator('dd').evaluate_all('(els) => els.every(el => el.scrollWidth <= el.clientWidth + 1)')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
        if width >= 1440:
            main = page.locator('.lead-overview-main').filter(visible=True).bounding_box()
            assert card.bounding_box()['x'] >= main['x'] + main['width']
        if width < 700:
            card.scroll_into_view_if_needed()
            assert card.bounding_box()['x'] >= 0
            assert card.bounding_box()['x'] + card.bounding_box()['width'] <= width + 1
        page.screenshot(path=str(output / f'{width}-{layout}{"-long" if long else ""}.png'), full_page=True)
        if width == 1440 and not long:
            page.locator(selector).first.click()
            expect(card).to_contain_text('规划师1')
            expect(card).not_to_contain_text('规划师2')
            expect(aside.locator('.lead-status-labels')).to_contain_text('待接收')
            page.evaluate('sidebarFixture.delay = 500')
            page.locator(selector).first.click()
            page.locator(selector).nth(1).click()
            expect(card).to_contain_text('规划师2')
            page.wait_for_timeout(650)
            expect(card).to_contain_text('规划师2')
            page.evaluate('sidebarFixture.fail = true')
            page.locator(selector).first.click()
            expect(page.get_by_text('服务读取失败', exact=True)).to_be_visible()
            expect(card).to_have_count(0)
        assert not errors, errors
        print('PASS sidebar placement, selected service, text wrapping', width, layout, long, flush=True)
        page.close()
    browser.close()
