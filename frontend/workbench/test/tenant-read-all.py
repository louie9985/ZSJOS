# -*- coding: utf-8 -*-
"""Actual dual-frontend components with synthetic transport; no live business writes."""
from pathlib import Path
from tempfile import gettempdir
import re
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    for port in [5185, 5186]:
        for view in (["calendar", "tasks", "feedback", "workorders", "withdrawal", "students"] if port == 5185 else ["calendar", "tasks", "withdrawal", "students"]):
            print(f"Checking {port} {view}", flush=True)
            page = browser.new_page(viewport={"width": 1280, "height": 900})
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.goto(f"http://localhost:{port}/test/tenant-read-all.html?view={view}")
            if view == "feedback":
                page.get_by_text("我的记录", exact=True).click()
            scope = page.get_by_role("combobox", name="查看范围", exact=True)
            expect(scope).to_be_visible()
            scope.press("Enter") if port == 5186 else scope.click()
            page.get_by_text("全部（只读）", exact=True).last.click()
            page.wait_for_function("tenantReadFixture.requests.some(r => r.params.readScope === 'ALL')")
            expect(page.get_by_role("button", name="申请提现", exact=True)).to_have_count(0)
            expect(page.get_by_role("button", name="撤销", exact=True)).to_have_count(0)
            expect(page.get_by_role("button", name="新增日程", exact=True)).to_have_count(0)
            expect(page.get_by_role("button", name="新建日程", exact=True)).to_have_count(0)
            scope.press("Enter") if port == 5186 else scope.click()
            page.get_by_text("指定人员（只读）", exact=True).last.click()
            page.wait_for_function("tenantReadFixture.requests.some(r => r.url.includes('simple-list') && String(r.params.includeDisabled) === 'true')")
            person = page.get_by_role("combobox", name="指定人员", exact=True)
            if person.count() == 0:
                person = page.get_by_role("combobox").nth(1)
            person.press("Enter") if port == 5186 else person.click()
            page.get_by_text("停用测试人员", exact=True).last.click()
            page.wait_for_function("tenantReadFixture.requests.some(r => r.params.readScope === 'USER' && r.params.targetUserId === 20)")
            for width in [1280, 390]:
                page.set_viewport_size({"width": width, "height": 900})
                expect(scope).to_be_visible()
                page.screenshot(path=str(Path(gettempdir()) / f"tenant-read-{port}-{view}-{width}.png"), full_page=True)
            page.evaluate("tenantReadFixture.mode = 'error'")
            scope.press("Enter") if port == 5186 else scope.click()
            page.get_by_text("全部（只读）", exact=True).last.click()
            expect(page.get_by_text("测试读取失败", exact=True).first).to_be_visible()
            page.evaluate("tenantReadFixture.mode = 'success'")
            for _ in range(3):
                if page.get_by_text("测试读取失败", exact=True).count() == 0:
                    break
                retry = page.get_by_role("button", name=re.compile(r"重\s*试|重新加载"))
                if retry.count() == 0:
                    break
                retry.first.click()
                page.wait_for_timeout(200)
            expect(page.get_by_text("测试读取失败", exact=True)).to_have_count(0, timeout=8000)
            assert page.evaluate("tenantReadFixture.writes") == 0
            assert not errors, errors
            page.goto(f"http://localhost:{port}/test/tenant-read-all.html?view={view}&ordinary")
            page.wait_for_function("window.tenantReadFixture && tenantReadFixture.requests.length > 0")
            expect(page.get_by_role("combobox", name="查看范围", exact=True)).to_have_count(0)
            page.close()
    browser.close()
print("PASS: both frontends calendar/tasks/withdrawal and workbench feedback/workorders SELF/ALL/USER, disabled-user selection, ordinary scope hidden, desktop/mobile, zero writes")
