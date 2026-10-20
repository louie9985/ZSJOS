# UTF-8. Real Edge / production components with synthetic PDF and HTTP responses.
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

def make_pdf():
    data = b'%PDF-1.4' + bytes([10])
    stream = b'BT /F1 18 Tf 30 150 Td (Notice PDF preview verified) Tj ET'
    objects = [b'<</Type /Catalog /Pages 2 0 R>>', b'<</Type /Pages /Kids [3 0 R] /Count 1>>',
               b'<</Type /Page /Parent 2 0 R /MediaBox [0 0 400 240] /Resources <</Font <</F1 4 0 R>>>> /Contents 5 0 R>>',
               b'<</Type /Font /Subtype /Type1 /BaseFont /Helvetica>>',
               b'<</Length ' + str(len(stream)).encode() + b'>>' + bytes([10]) + b'stream' + bytes([10]) + stream + bytes([10]) + b'endstream']
    offsets = [0]
    for number, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data += str(number).encode() + b' 0 obj' + bytes([10]) + obj + bytes([10]) + b'endobj' + bytes([10])
    xref = len(data)
    data += b'xref' + bytes([10]) + b'0 6' + bytes([10]) + b'0000000000 65535 f ' + bytes([10])
    for offset in offsets[1:]:
        data += f'{offset:010d} 00000 n '.encode() + bytes([10])
    return data + b'trailer <</Size 6 /Root 1 0 R>>' + bytes([10]) + b'startxref' + bytes([10]) + str(xref).encode() + bytes([10]) + b'%%EOF'

out = Path(os.environ['TEMP']) / 'notice-pdf-preview-20261018'
out.mkdir(exist_ok=True)
origin = os.environ.get('NOTICE_PDF_FIXTURE_ORIGIN', 'http://127.0.0.1:5174')
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000}, accept_downloads=True)
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.add_init_script('''window.pdfUrls={created:[],revoked:[]};
            const create=URL.createObjectURL.bind(URL), revoke=URL.revokeObjectURL.bind(URL);
            URL.createObjectURL=b=>{const u=create(b);window.pdfUrls.created.push(u);return u};
            URL.revokeObjectURL=u=>{window.pdfUrls.revoked.push(u);revoke(u)};''')
        state = {'status': 200, 'type': 'application/pdf', 'body': make_pdf()}
        page.route('**/test/notice-preview-file.pdf', lambda route: route.fulfill(status=state['status'], content_type=state['type'], body=state['body'], headers={'Content-Disposition': 'attachment; filename=notice.pdf'}))
        page.goto(origin + '/test/notice-pdf-preview.html')
        preview = page.locator('.attachment-card-actions').get_by_role('button', name=re.compile('预.*览'))
        preview.click()
        frame = page.locator('iframe[title="公告测试.pdf"]')
        expect(frame).to_be_visible()
        assert frame.get_attribute('sandbox') is None
        assert frame.get_attribute('src').startswith('blob:')
        # Native PDF viewer creates an embed; merely finding an iframe is insufficient.
        page.wait_for_function('''()=>[...document.querySelectorAll('iframe')].some(f=>{try{return !!f.contentDocument?.querySelector('embed[type="application/pdf"]')}catch{return false}})''')
        assert not any(f.url.startswith('chrome-error:') for f in page.frames)
        page.screenshot(path=str(out / f'edge-{width}.png'))
        with page.expect_download() as download:
            page.get_by_role('button', name='下载原文件').click()
        assert download.value.failure() is None
        page.get_by_role('button', name=re.compile('关.*闭')).click()
        expect(frame).to_have_count(0)
        assert page.evaluate('pdfUrls.created.every(u=>pdfUrls.revoked.includes(u))')
        state['status'] = 403
        preview.click()
        expect(page.get_by_text('附件访问已失效或无权读取，请关闭后重新预览')).to_be_visible()
        state['status'] = 200
        page.get_by_role('button', name='重试预览').click()
        expect(frame).to_be_visible()
        page.get_by_role('button', name=re.compile('关.*闭')).click()
        state['type'] = 'text/html'
        state['body'] = b'<script>window.parent.pdfAttack=true</script>'
        preview.click()
        expect(page.get_by_text('文件实际类型不是 PDF，请下载原文件核实')).to_be_visible()
        expect(frame).to_have_count(0)
        assert not page.evaluate('Boolean(window.pdfAttack)')
        page.get_by_role('button', name=re.compile('关.*闭')).click()
        state['type'] = 'application/pdf'
        state['body'] = make_pdf()
        preview.click()
        expect(frame).to_be_visible()
        page.get_by_role('button', name='切换公告').evaluate('(button)=>button.click()')
        expect(frame).to_have_count(0)
        assert page.evaluate('pdfUrls.created.every(u=>pdfUrls.revoked.includes(u))')
        assert not errors, errors
        print(f'PASS Edge {browser.version}: {width}px viewer, download, retry, HTML rejection, close/unmount cleanup', flush=True)
        page.close()
    browser.close()
print(out, flush=True)
