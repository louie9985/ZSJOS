# UTF-8. Both real pages use isolated API fixtures and local intercepted files.
import base64
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'order-voucher-attachments'
out.mkdir(exist_ok=True)
png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ahtcAAAAASUVORK5CYII=')
with sync_playwright() as p:
    browser = p.chromium.launch()
    for approval in (False, True):
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('**/test/fixture-voucher.png*', lambda route: route.fulfill(content_type='image/png', body=png))
        page.route('**/test/fixture-voucher.pdf*', lambda route: route.fulfill(content_type='application/pdf', body=b'%PDF-1.4\n%%EOF'))
        url = os.environ.get('ORDER_FIXTURE_URL', 'http://127.0.0.1:5174') + '/test/order-detail-layout.html?vouchers=all' + ('&approval=1' if approval else '')
        page.goto(url)
        rows = page.locator('.business-inbox-item' if approval else '.sales-order-list-item')
        expect(rows.first).to_be_visible()
        if approval:
            rows.first.click()
        cards = page.locator('.sales-order-voucher-attachments .attachment-card')
        expect(cards).to_have_count(2)
        image = cards.filter(has=page.get_by_text('测试凭证.png', exact=True))
        pdf = cards.filter(has=page.get_by_text('测试凭证.pdf', exact=True))
        expect(image.get_by_role('button', name=re.compile('下.*载$'))).to_be_enabled()
        before = page.evaluate('window.orderLayoutFixture.reads.length')
        image.get_by_role('button', name='预览图片：测试凭证.png').click()
        expect(page.locator('.ant-modal')).to_be_visible()
        assert page.evaluate('window.orderLayoutFixture.reads.length') == before + 1
        page.keyboard.press('Escape')
        pdf.get_by_role('button', name=re.compile('预.*览$')).click()
        expect(page.locator('.ant-modal iframe')).to_be_visible()
        page.keyboard.press('Escape')
        # Capture the refreshed download target without leaving the isolated context.
        page.evaluate("window.downloads=[]; window.oldClick=HTMLAnchorElement.prototype.click; HTMLAnchorElement.prototype.click=function(){if(this.href)window.downloads.push(this.href)}")
        before = page.evaluate('window.orderLayoutFixture.reads.length')
        pdf.get_by_role('button', name=re.compile('下.*载$')).click()
        page.wait_for_function('window.downloads.length===1')
        assert f'revision={before+1}' in page.evaluate('window.downloads[0]')
        page.evaluate('HTMLAnchorElement.prototype.click=window.oldClick; window.orderLayoutFixture.failure=true')
        image.get_by_role('button', name=re.compile('预.*览$')).click()
        expect(image.get_by_text('测试详情加载失败', exact=True)).to_be_visible()
        page.evaluate('window.orderLayoutFixture.failure=false')
        image.get_by_role('button', name='重试附件').click()
        expect(image.get_by_role('button', name='预览图片：测试凭证.png')).to_be_enabled()
        rows.nth(1).click()
        expect(image.locator('img')).to_have_attribute('src', re.compile('order=101'))
        reads = page.evaluate('window.orderLayoutFixture.reads')
        assert all(r.startswith('approval:' if approval else 'management:') for r in reads)
        page.screenshot(path=str(out / f'desktop-{approval}.png'), full_page=True)
        page.set_viewport_size({'width': 390, 'height': 900})
        rows.nth(1).click()
        drawer = page.locator('.ant-drawer')
        expect(drawer.locator('.attachment-card')).to_have_count(2)
        page.wait_for_timeout(400)
        drawer.locator('.attachment-card').first.scroll_into_view_if_needed()
        assert drawer.locator('.attachment-card').evaluate_all('(els)=>els.every(e=>e.scrollWidth<=e.clientWidth+1)')
        page.screenshot(path=str(out / f'mobile-{approval}.png'), full_page=True)
        page.set_viewport_size({'width': 1440, 'height': 1000})
        page.goto(url.replace('vouchers=all', 'vouchers=missing'))
        if approval:
            rows.first.click()
        expect(image.get_by_text('凭证暂不可用，请重试或联系管理员', exact=True)).to_be_visible()
        page.evaluate("window.orderLayoutFixture.voucherMode='all'")
        image.get_by_role('button', name='重试附件').click()
        expect(image.get_by_role('button', name='预览图片：测试凭证.png')).to_be_enabled()
        page.evaluate("window.orderLayoutFixture.voucherMode='empty'")
        image.get_by_role('button', name=re.compile('预.*览$')).click()
        expect(image.get_by_text('凭证不存在或已不可访问，请刷新订单详情', exact=True)).to_be_visible()
        page.goto(url.replace('vouchers=all', 'vouchers=empty'))
        if approval:
            rows.first.click()
        expect(page.get_by_text('无缴费凭证', exact=True)).to_be_visible()
        assert not errors, errors
        page.close()
    browser.close()
print('PASS both pages: image/PDF preview, fresh download URL, retry, removed/missing/empty, order switch, isolated authorization loaders, desktop/mobile')
