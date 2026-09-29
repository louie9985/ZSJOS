# -*- coding: utf-8 -*-
"""Browser contract checks against isolated synthetic withdrawal export fixtures."""
import json
import re
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, expect


def button(page, label):
    return page.locator("button").filter(has_text=re.compile(r"^\s*" + r"\s*".join(map(re.escape, label)) + r"\s*$"))

def run():
    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel="chrome", headless=True)
        try:
            for client, port in [("workbench", 5174), ("admin", 5189)]:
                page = browser.new_page(viewport={"width": 1440, "height": 1000})
                errors = []
                page.on("pageerror", lambda error: errors.append(str(error)))
                base = f"http://127.0.0.1:{port}/test/withdrawal-export.html"
                page.goto(base)
                expect(page.get_by_text("TEST-1", exact=True)).to_be_visible(timeout=90000)
                export = button(page, '导出')
                expect(export).to_be_visible()
                keyword = page.get_by_placeholder("提现单号 / 银行流水号").first
                keyword.fill("TEST")
                keyword.press("Enter")
                export.click()
                expect(page.get_by_text("将导出符合当前筛选条件的全部提现记录，包含完整银行卡号、开户信息及审核和打款信息。", exact=True)).to_be_visible()
                button(page, '取消').last.click()
                assert page.evaluate("exportFixture.calls.length") == 0
                export.click()
                expect(button(page, '加入导出队列')).to_be_visible()
                page.wait_for_timeout(400)
                page.screenshot(path=str(Path(tempfile.gettempdir()) / f"withdrawal-export-{client}-desktop.png"))
                page.set_viewport_size({"width": 390, "height": 844})
                page.wait_for_timeout(400)
                page.screenshot(path=str(Path(tempfile.gettempdir()) / f"withdrawal-export-{client}-mobile.png"))
                page.set_viewport_size({"width": 1440, "height": 1000})
                page.evaluate("exportFixture.fail = true")
                button(page, '加入导出队列').click()
                page.wait_for_function("exportFixture.calls.length === 1")
                expect(page.get_by_text("导出任务创建失败，请重试", exact=True).first).to_be_visible()
                expect(export).not_to_have_class(re.compile(".*(?:is-loading|ant-btn-loading).*"))
                page.evaluate("exportFixture.fail = false; exportFixture.delay = 500")
                export.click()
                button(page, '加入导出队列').click()
                page.wait_for_function("exportFixture.calls.length === 2")
                request = page.evaluate("exportFixture.calls[1]")
                assert request["url"] == "/zsjos/export-task"
                assert request["body"]["exportType"] == "withdrawal"
                filters = json.loads(request["body"]["filterJson"])
                assert filters["keyword"] == "TEST", filters
                assert "pageNo" not in filters and "pageSize" not in filters
                button(page, '查看导出任务').click()
                expect(page.get_by_text("EXP-TEST", exact=True)).to_be_visible(timeout=15000)
                expect(button(page, '下载')).to_be_visible()
                for suffix in ["no-export", "no-query", "own"]:
                    page.goto(base + "?" + suffix)
                    expect(page.get_by_text("TEST-1", exact=True)).to_be_visible(timeout=30000)
                    expect(button(page, '导出')).to_have_count(0)
                page.goto(base + "?admin-scope")
                expect(page.get_by_text("TEST-1", exact=True)).to_be_visible(timeout=30000)
                expect(button(page, '导出')).to_be_visible()
                assert not errors, errors
                print(f"PASS {client}: confirmation, cancel, filter payload, failure/retry, task navigation and permission matrix", flush=True)
                page.close()
        finally:
            browser.close()


if __name__ == "__main__":
    run()
