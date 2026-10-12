# -*- coding: utf-8 -*-
"""Synthetic UI transport plus actual server-exported public HTML; no shared services or data."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import cv2
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--fixtures', type=Path, required=True)
args = parser.parse_args()
fixtures = args.fixtures.resolve()

class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Handler, directory=str(fixtures)))
threading.Thread(target=server.serve_forever, daemon=True).start()
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for width in [1440, 390]:
            context = browser.new_context(viewport={'width': width, 'height': 960}, permissions=['clipboard-read', 'clipboard-write'])
            page = context.new_page()
            page.goto('http://127.0.0.1:5298/test/pms-share.html')
            page.get_by_role('button', name='打开分享', exact=True).click()
            dialog = page.get_by_role('dialog').filter(has_text='分享文档')
            expect(dialog.get_by_role('button', name='复制链接')).to_be_visible()
            page.get_by_role('button', name='复制链接', exact=True).click()
            copied = page.evaluate('navigator.clipboard.readText()')
            assert copied == 'https://public.example.com/education/public-api/pms/kb/document-share/page?token=' + 'a'*31 + '1'
            download = dialog.get_by_role('button', name='下载二维码')
            expect(download).to_be_enabled()
            with page.expect_download() as event:
                download.click()
            assert event.value.suggested_filename.endswith('.png')
            qr_path = fixtures / f'qr-{width}.png'
            event.value.save_as(qr_path)
            qr_text, _, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imread(str(qr_path)))
            assert qr_text == copied
            box = dialog.bounding_box()
            assert box['x'] >= 0 and box['x'] + box['width'] <= width + 1
            page.screenshot(path=str(fixtures / f'dialog-{width}.png'), full_page=True)
            dialog.get_by_role('button', name='关闭分享', exact=True).click()
            page.get_by_role('button', name='确定', exact=True).click()
            expect(dialog).not_to_be_visible()
            page.get_by_role('button', name='打开分享', exact=True).click()
            dialog.get_by_role('button', name='开启分享', exact=True).click()
            expect(dialog.get_by_role('button', name='复制链接')).to_be_visible()
            dialog.get_by_role('button', name='复制链接').click()
            assert page.evaluate('navigator.clipboard.readText()').endswith('2')
            dialog.get_by_role('button', name='取 消', exact=True).click()
            page.evaluate('pmsShareFixture.missing = true')
            page.get_by_role('button', name='打开分享', exact=True).click()
            expect(dialog.get_by_text('公开分享地址暂不可用', exact=False)).to_be_visible()
            expect(dialog.get_by_role('button', name='复制链接')).to_have_count(0)
            expect(dialog.get_by_role('button', name='关闭分享')).to_be_enabled()
            dialog.get_by_role('button', name='取 消', exact=True).click()
            page.evaluate('pmsShareFixture.fail = true')
            page.get_by_role('button', name='打开分享', exact=True).click()
            expect(dialog.get_by_text('分享配置加载失败', exact=True)).to_be_visible()
            expect(dialog.get_by_role('button', name='开启分享')).to_be_disabled()
            page.evaluate('pmsShareFixture.fail = false; pmsShareFixture.missing = false')
            dialog.get_by_role('button', name='重试', exact=True).click()
            expect(dialog.get_by_role('button', name='复制链接')).to_be_visible()
            context.close()

            context = browser.new_context(viewport={'width': width, 'height': 960}, java_script_enabled=False)
            page = context.new_page()
            base_url = f'http://127.0.0.1:{server.server_port}/'
            page.goto(base_url + 'document.html')
            expect(page).to_have_title('中世健知识文档 <2026> & 说明')
            expect(page.get_by_text('请查看课程说明。')).to_be_visible()
            expect(page.get_by_role('table')).to_be_visible()
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
            page.screenshot(path=str(fixtures / f'page-{width}.png'), full_page=True)
            for name, title in [('file', '课程说明.pdf'), ('invalid', '分享内容已失效'), ('error', '暂时无法加载文档'), ('file-error', '分享文件不可用')]:
                page.goto(base_url + name + '.html')
                expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            context.close()
        browser.close()
    print('PASS: copy/download/reopen/configuration/retry and no-JS public HTML at 1440/390')
finally:
    server.shutdown()
    server.server_close()
