# UTF-8. Synthetic transport browser checks, no live writes.
from pathlib import Path
import re
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    base = 'http://localhost:5174/test/production-ticket-flow.html'
    page.goto(base + '?deep')
    page.get_by_role('button', name='提交成品', exact=True).click()
    dialog = page.get_by_role('dialog')
    dialog.get_by_placeholder('备注（可选）').fill('成品交付备注')
    dialog.get_by_placeholder('请输入链接').fill('https://example.com/finished')
    dialog.get_by_role('button', name='确认提交').click()
    page.locator('.media-feature-detail-pane .media-ticket-detail').get_by_text('成品交付备注', exact=True).wait_for()
    assert page.locator('.media-ticket-detail a[href="https://example.com/finished"]').count() == 1
    detail = page.locator('.media-feature-detail-pane .media-ticket-detail').bounding_box()
    actions = page.locator('.media-feature-detail-pane .media-ticket-action-bar').bounding_box()
    assert actions['x'] >= detail['x'] + detail['width']
    page.screenshot(path=str(Path.home() / 'AppData/Local/Temp/production-ticket-desktop.png'), full_page=True)
    page.get_by_role('tab', name='公共池', exact=True).click()
    page.get_by_role('button', name=re.compile(r'抢\s*单')).click()
    page.get_by_text('公共池暂无可抢工单', exact=True).wait_for()
    page.goto(base + '?assignment')
    page.get_by_role('dialog').get_by_role('button', name=re.compile(r'^接\s*单$')).click()
    page.get_by_role('dialog').wait_for(state='hidden')
    page.get_by_role('button', name='开始制作', exact=True).wait_for()
    assert page.locator('.attachment-card').count() > 0
    assert page.locator('.resource-link-card').count() > 0
    page.set_viewport_size({'width': 390, 'height': 844})
    detail = page.locator('.media-feature-detail-pane .media-ticket-detail').bounding_box()
    actions = page.locator('.media-feature-detail-pane .media-ticket-action-bar').bounding_box()
    assert actions['y'] < detail['y']
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.screenshot(path=str(Path.home() / 'AppData/Local/Temp/production-ticket-mobile.png'), full_page=True)
    assert not errors, errors
    print('PASS: deep link, completion URL/remark, claim, global accept refresh, shared resources, desktop/mobile grid')
    browser.close()
