# -*- coding: utf-8 -*-
"""Real React component checks against the existing local Vite server; no real messages."""
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
OUT = Path(gettempdir()) / 'zsjos-wecom-assignment-browser'
OUT.mkdir(exist_ok=True)
URL = 'http://127.0.0.1:5174/test/lead-assignment-browser.html'
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1280, 'height': 900})
    page.goto(URL + '?assignmentLeadId=1&assignmentHistoryId=20')
    expect(page.get_by_role('dialog')).to_be_visible()
    expect(page.get_by_text('KZ-TEST-001')).to_be_visible()
    page.screenshot(path=str(OUT / 'desktop.png'))
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(page.get_by_role('dialog')).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(OUT / 'mobile.png'))
    page.locator('.assignment-accept-btn').click()
    assert page.evaluate('assignmentFixture.accepted') == 20
    page.goto(URL + '?assignmentLeadId=1&assignmentHistoryId=19')
    expect(page.get_by_text('本次派单已失效')).to_be_visible()
    expect(page.get_by_role('dialog')).not_to_be_visible()
    page.screenshot(path=str(OUT / 'expired.png'))
    page.evaluate('assignmentFixture.error = true')
    page.get_by_role('button', name='重试').first.click()
    expect(page.get_by_text('待接客资查询暂不可用')).to_be_visible()
    page.evaluate('assignmentFixture.error = false')
    page.get_by_role('button', name='重试').first.click()
    expect(page.get_by_text('本次派单已失效')).to_be_visible()
    page.goto(URL + '?assignmentLeadId=1&assignmentHistoryId=20&denied=1')
    expect(page.get_by_text('无权接单', exact=True)).to_be_visible()
    expect(page.get_by_role('dialog')).not_to_be_visible()
    browser.close()
print('PASS: valid/old-round/error/retry/unauthorized; desktop/mobile; round forwarded to accept')
print('Screenshots:', OUT)
