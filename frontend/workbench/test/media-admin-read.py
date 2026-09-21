# -*- coding: utf-8 -*-
"""Browser regression against the existing local Vite server; no business writes."""
from pathlib import Path
from tempfile import gettempdir
import re
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page(viewport={"width": 1280, "height": 900})
    page.goto("http://127.0.0.1:5174/test/media-admin-read.html")
    expect(page.get_by_role("heading", name="学员管理", exact=True)).to_be_visible()
    expect(page.get_by_text("暂无课程服务", exact=True)).to_be_visible()
    page.evaluate("mediaAdminReadFixture.mode = 'error'")
    page.get_by_role("button", name="查看保留草稿 · PC-TEST").click()
    expect(page.get_by_text("草稿读取失败", exact=True)).to_be_visible()
    page.evaluate("mediaAdminReadFixture.mode = 'success'")
    page.get_by_role("button", name=re.compile(r"重\s*试")).click()
    expect(page.get_by_text("历史定位草稿内容", exact=True)).to_be_visible()
    expect(page.get_by_role("button", name="保存草稿", exact=True)).to_have_count(0)
    for width in [1280, 390]:
        page.set_viewport_size({"width": width, "height": 900})
        page.screenshot(path=str(Path(gettempdir()) / f"media-admin-draft-{width}.png"), full_page=True)
        assert page.locator(".ant-modal").bounding_box()["width"] <= width
    assert page.evaluate("mediaAdminReadFixture.writes") == 0
    print("PASS: full-read heading, orphan draft, error/retry, read-only content, desktop/mobile, zero writes")
    browser.close()
