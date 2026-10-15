# -*- coding: utf-8 -*-
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(os.environ['TEMP']) / 'work-order-upload-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto('http://127.0.0.1:5281/test/work-order-upload.html')
        expect(page.get_by_text('单个附件不超过100MB；上传繁忙时请稍后重试')).to_be_visible()
        upload = page.locator('input[type=file]')
        upload.set_input_files({'name': 'oversized.bin', 'mimeType': 'application/octet-stream', 'buffer': b'x'})
        expect(page.get_by_text('附件不能为空且不能超过100MB')).to_be_visible()
        assert page.evaluate('window.workOrderUploadMetrics.requests') == 0
        upload.set_input_files({'name': 'boundary.bin', 'mimeType': 'application/octet-stream', 'buffer': b'x'})
        expect(page.get_by_test_id('file-count')).to_have_text('1')
        assert page.evaluate('window.workOrderUploadMetrics.requests') == 1
        page.evaluate('window.workOrderUploadMetrics.busy=true')
        upload.set_input_files({'name': 'retry.bin', 'mimeType': 'application/octet-stream', 'buffer': b'x'})
        expect(page.get_by_text('工单附件上传繁忙，请稍后重试', exact=True)).to_be_visible()
        expect(page.get_by_test_id('file-count')).to_have_text('1')
        page.evaluate('window.workOrderUploadMetrics.busy=false')
        upload.set_input_files({'name': 'retry.bin', 'mimeType': 'application/octet-stream', 'buffer': b'x'})
        expect(page.get_by_test_id('file-count')).to_have_text('2')
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        page.screenshot(path=str(out / f'work-order-upload-{width}.png'), full_page=True)
        page.close()
    browser.close()
print('PASS real picker: oversize request blocked, boundary accepted, busy preserved, retry succeeds; 1440/390')
