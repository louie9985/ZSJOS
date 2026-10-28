# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    for width in [390, 1440]:
        page = browser.new_page(viewport={"width": width, "height": 900})
        page.goto("http://127.0.0.1:5187/test/appeal-badges.html")
        expect(page.locator("#appeal .ant-badge-count")).to_have_attribute("title", "2")
        expect(page.locator("#workflow .ant-badge-count")).to_have_attribute("title", "2")
        page.get_by_text("查询失败", exact=True).click()
        expect(page.locator("#appeal .ant-badge-count")).to_have_count(0)
        page.get_by_text("重试", exact=True).click()
        expect(page.locator("#appeal .ant-badge-count")).to_have_attribute("title", "3")
        page.get_by_text("处理完成", exact=True).click()
        expect(page.locator("#appeal .ant-badge-count")).to_have_count(0)
        expect(page.locator("#workflow .ant-badge-count")).to_have_count(0)
        page.get_by_text("撤销权限", exact=True).click()
        page.get_by_text("读取请求数", exact=True).click()
        count = page.locator("body").get_attribute("data-calls")
        page.get_by_text("重试", exact=True).click()
        page.get_by_text("读取请求数", exact=True).click()
        assert page.locator("body").get_attribute("data-calls") == count
        expect(page.locator("#appeal .ant-badge-count")).to_have_count(0)
        page.close()
    browser.close()
print("PASS desktop/mobile: pending, failure, retry, completed, permission removal")
