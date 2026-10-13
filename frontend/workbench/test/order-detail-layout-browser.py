# UTF-8. Isolated browser context and synthetic transport; no business writes.
import os
import re
import base64
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(os.environ.get('TEMP', '.')) / 'zsjos-order-detail-layout'
OUT.mkdir(parents=True, exist_ok=True)
URL = os.environ.get('ORDER_FIXTURE_URL', 'http://127.0.0.1:5174') + '/test/order-detail-layout.html'
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={'width': 1600, 'height': 1100})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/test/fixture-voucher.png*', lambda route: route.fulfill(content_type='image/png', body=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ahtcAAAAASUVORK5CYII=')))
    page.goto(URL)
    main = page.locator('.sales-order-detail-pane .information-table')
    expect(main.locator('.sales-order-information-table')).to_have_count(4)
    expect(main.locator('.detail-field-grid')).to_have_count(0)
    expect(main.get_by_text('历史支付名称（现已停用）', exact=True)).to_be_visible()
    assert main.get_by_text('冲刺课', exact=True).evaluate("e=>e.classList.contains('ant-tag-purple')")
    groups = main.locator('.sales-order-information-table')
    assert len(groups.first.evaluate('e=>getComputedStyle(e).gridTemplateColumns').split()) == 2
    assert page.locator('.sales-order-approval-sidebar').bounding_box()['x'] > main.bounding_box()['x']
    page.screenshot(path=str(OUT / 'desktop.png'), full_page=True)
    page.get_by_role('button', name='预览图片：测试凭证.png').click()
    expect(page.locator('.ant-modal')).to_be_visible()
    page.keyboard.press('Escape')
    page.locator('.sales-order-list-item').nth(1).click()
    expect(main.get_by_text('LD-LAYOUT-1', exact=True)).to_be_visible()
    expect(main.locator('button[title="复制"]')).to_have_count(2)
    page.evaluate('window.orderLayoutFixture.failure=true')
    page.locator('.sales-order-list-item').first.click()
    expect(page.get_by_text('测试详情加载失败', exact=True)).to_be_visible()
    page.evaluate('window.orderLayoutFixture.failure=false')
    page.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(main.get_by_text('OR-LAYOUT-100', exact=True)).to_be_visible()
    for width in (1280, 390, 320):
        page.set_viewport_size({'width': width, 'height': 900})
        if width < 768:
            page.locator('.sales-order-list-item').first.click()
            scope = page.get_by_role('dialog')
            page.wait_for_function("Math.abs(document.querySelector('[role=dialog]').getBoundingClientRect().x)<2")
        else:
            scope = main
        expect(scope.locator('.sales-order-information-table').first).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        assert scope.locator('.sales-order-information-cell').evaluate_all('(cells)=>cells.every(e=>e.scrollWidth<=e.clientWidth+1)')
        if width < 768:
            assert len(scope.locator('.sales-order-information-table').first.evaluate('e=>getComputedStyle(e).gridTemplateColumns').split()) == 1
            scope.locator('.sales-order-information-table').first.scroll_into_view_if_needed()
            page.screenshot(path=str(OUT / f'mobile-{width}.png'), full_page=True)
            scope.get_by_role('button', name=re.compile(r'关\s*闭')).click()
    page.set_viewport_size({'width': 1600, 'height': 1100})
    page.evaluate("localStorage.setItem('crm-theme',JSON.stringify({preset:'default-dark'}))")
    page.reload()
    expect(main.locator('.sales-order-information-table')).to_have_count(4)
    expect(page.locator('[data-crm-dark="true"]').first).to_be_attached()
    page.screenshot(path=str(OUT / 'dark.png'), full_page=True)
    page.goto(URL + '?shared')
    expect(page.locator('.detail-field-grid')).to_have_count(4)
    expect(page.locator('.sales-order-information-table')).to_have_count(0)
    assert not errors, errors
    print('PASS: management page, snapshots, tags, profile/copy controls, image preview, retry, desktop/mobile, shared default; screenshots:', OUT)
    browser.close()
