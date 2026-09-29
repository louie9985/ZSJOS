# -*- coding: utf-8 -*-
"""Real Chrome with isolated synthetic transport; no live business calls."""
import re, tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
OUT = Path(tempfile.gettempdir()) / "workbench-pagination-browser"
OUT.mkdir(exist_ok=True)
BASE = "http://127.0.0.1:5196/test/"
with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={"width": width, "height": 960})
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.goto(BASE + "notice-reading.html")
        page.get_by_role("tab", name="阅读情况", exact=True).click()
        expect(page.get_by_text("员工1", exact=True)).to_be_visible()
        assert page.evaluate("window.noticeFixture.summaries") == 1
        page.locator(".ant-pagination-item-2").click()
        expect(page.get_by_text("员工21", exact=True)).to_be_visible()
        assert page.evaluate("window.noticeFixture.summaries") == 1
        assert page.evaluate("window.noticeFixture.queries.length") == 2
        page.get_by_role("tab", name="未读", exact=True).click()
        expect(page.get_by_text("员工1", exact=True)).to_be_visible()
        assert page.evaluate("window.noticeFixture.summaries") == 1
        page.get_by_role("button", name=re.compile(r"刷.*新")).last.click()
        page.wait_for_function("window.noticeFixture.summaries === 2")
        page.evaluate("window.noticeFixture.fail = true")
        page.get_by_role("button", name=re.compile(r"刷.*新")).last.click()
        expect(page.get_by_text("统计加载失败", exact=True).first).to_be_visible()
        page.evaluate("window.noticeFixture.fail = false")
        page.get_by_role("button", name=re.compile(r"重.*试")).first.click()
        expect(page.get_by_text("80.0%", exact=True)).to_be_visible()
        page.evaluate("window.noticeFixture.denied = true")
        page.get_by_role("button", name=re.compile(r"刷.*新")).last.click()
        expect(page.get_by_text("80.0%", exact=True)).to_have_count(0)
        page.goto(BASE + "media-lead.html?paging=1")
        page.locator("[aria-label=查看本月客资明细]").click()
        dialog = page.get_by_role("dialog")
        expect(dialog.get_by_text("KZ-PAGE-1", exact=True)).to_be_visible()
        page.locator(".ant-modal .ant-pagination-item-2").click()
        expect(dialog.get_by_text("KZ-PAGE-21", exact=True)).to_be_visible()
        assert page.evaluate("window.mediaLeadDetailQueries.at(-1).params.pageNo") == 2
        assert page.evaluate("window.mediaLeadDetailQueries.length") == 2
        page.evaluate("window.mediaLeadPaging.fail = true")
        page.locator(".ant-modal .ant-pagination-item-3").click()
        expect(dialog.get_by_text("明细加载失败", exact=True)).to_be_visible()
        page.evaluate("window.mediaLeadPaging.fail = false")
        dialog.get_by_role("button", name=re.compile(r"重.*试")).click()
        expect(dialog.get_by_text("KZ-PAGE-41", exact=True)).to_be_visible()
        page.evaluate("window.mediaLeadPaging.delay = 500")
        page.locator(".ant-modal .ant-pagination-item-2").click()
        page.locator(".ant-modal-close").click()
        page.evaluate("window.mediaLeadPaging.delay = 0")
        page.locator("[aria-label=查看本月客资明细]").click()
        expect(dialog.get_by_text("KZ-PAGE-1", exact=True)).to_be_visible()
        page.wait_for_timeout(650)
        expect(dialog.get_by_text("KZ-PAGE-21", exact=True)).to_have_count(0)
        page.screenshot(path=str(OUT / f"media-pagination-{width}.png"))
        assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1")
        page.goto(BASE + "order-pagination.html")
        expect(page.get_by_text("ORDER-PAGE-1", exact=True).first).to_be_visible()
        assert page.evaluate("window.orderPagination.counts") == 1
        if width == 1440:
            assert page.evaluate("window.orderPagination.pages.length") == 1
            page.locator(".ant-pagination-item-2").click()
            expect(page.get_by_text("ORDER-PAGE-21", exact=True).first).to_be_visible()
            assert page.evaluate("window.orderPagination.pages.length") == 2
            assert page.evaluate("window.orderPagination.counts") == 1
            page.evaluate("window.orderPagination.fail = true")
            page.locator(".ant-pagination-item-3").click()
            expect(page.get_by_text("订单列表暂时不可用", exact=True).first).to_be_visible()
            page.evaluate("window.orderPagination.fail = false")
            page.get_by_role("button", name=re.compile(r"刷.*新")).first.click()
            expect(page.get_by_text("ORDER-PAGE-41", exact=True).first).to_be_visible()
            assert page.evaluate("window.orderPagination.counts") == 2
        else:
            assert page.evaluate("window.orderPagination.cursors") == 1
            assert page.evaluate("window.orderPagination.pages.length") == 0
        page.screenshot(path=str(OUT / f"order-pagination-{width}.png"))
        assert not errors, errors
        print("PASS", width, "notice summary reuse/refresh/denied; media paging/retry/stale cancellation")
        page.close()
    browser.close()
print("Screenshots:", OUT)
