# -*- coding: utf-8 -*-
"""Production readers with synthetic images/API data; desktop and mobile Chromium."""
import argparse
import json
import struct
import threading
import zlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--fixtures', type=Path, required=True)
parser.add_argument('--react-origin', default='http://127.0.0.1:5174')
parser.add_argument('--vue-origin', default='http://127.0.0.1:5198')
args = parser.parse_args()
out = args.fixtures.resolve()

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(out / 'h5-dist'), **kw)
    def log_message(self, *_):
        pass
    def do_GET(self):
        if self.path == '/server':
            self.send_response(200)
            self.send_header('Content-Type', 'text/html;charset=UTF-8')
            self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; img-src https: data:; base-uri 'none'")
            self.end_headers()
            self.wfile.write((out / 'server.html').read_bytes())
        else:
            if self.path.startswith('/notice/share'): self.path = '/index.html'
            super().do_GET()

server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'
def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
pixels = b''.join(b'\0' + b''.join(bytes((30 + x // 2, 70 + y // 2, 180)) for x in range(400)) for y in range(240))
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 400, 240, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for client in ['react', 'vue', 'h5', 'server']:
            for width in [1440, 390]:
                context = browser.new_context(viewport={'width': width, 'height': 900}, has_touch=width == 390, java_script_enabled=client != 'server')
                page = context.new_page()
                errors = []
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.route('https://notice-image.test/**', lambda route: route.fulfill(content_type='image/png', body=png))
                if client == 'h5':
                    data = {'code': 0, 'data': {'title': '公告图片', 'content': (out / 'content.html').read_text(encoding='utf-8'), 'publishTime': 1791532800000, 'attachments': []}}
                    page.route('**/public-api/system/notice-share/get', lambda route: route.fulfill(json=data))
                url = {'react': args.react_origin + '/test/notice-images.html', 'vue': args.vue_origin + '/test/notice-images.html', 'h5': base + '/notice/share#token=' + 'a' * 43, 'server': base + '/server'}[client]
                page.goto(url)
                body = page.locator({'react': '.announcement-rich-text--tables', 'vue': '.notice-image-content', 'h5': '.notice-public-body', 'server': '.content'}[client])
                images = body.locator('img[src]')
                expect(images).to_have_count(2)
                expect(body.locator('a[href="https://notice-image.test/link"]')).to_have_count(1)
                images.evaluate_all('(images) => images.forEach(image => { image.style.width="160px"; image.style.height="100px" })')
                if client == 'server':
                    expect(page.locator('script')).to_have_count(0)
                    images.nth(1).click()
                    gallery = page.locator('.notice-image-preview:target')
                    expect(gallery.locator('img')).to_have_attribute('src', 'https://notice-image.test/2.png')
                    gallery.get_by_role('link', name='上一张').click()
                    expect(gallery.locator('img')).to_have_attribute('src', 'https://notice-image.test/1.png')
                    gallery.get_by_role('link', name='下一张').click()
                    expect(gallery.locator('img')).to_have_attribute('src', 'https://notice-image.test/2.png')
                    expect(gallery.get_by_role('link', name='查看原图')).to_have_attribute('rel', 'noopener noreferrer')
                    expect(gallery.locator('img')).to_have_js_property('naturalWidth', 400)
                    page.screenshot(path=str(out / f'{client}-{width}.png'))
                    gallery.get_by_role('link', name='关闭', exact=True).click()
                    expect(gallery).to_have_count(0)
                else:
                    images.nth(1).focus()
                    expect(images.nth(1)).to_be_focused()
                    images.nth(1).press('Enter')
                    gallery = page.locator({'react': '.ant-image-preview', 'vue': '.el-image-viewer__wrapper', 'h5': '.van-image-preview'}[client]).last
                    expect(gallery).to_be_visible()
                    if client == 'react':
                        expect(gallery.locator('.ant-image-preview-img')).to_have_attribute('src', 'https://notice-image.test/2.png')
                        page.keyboard.press('ArrowLeft')
                        expect(gallery.locator('.ant-image-preview-img')).to_have_attribute('src', 'https://notice-image.test/1.png')
                        gallery.get_by_role('button', name='zoomIn', exact=True).click()
                        expect(gallery.locator('.ant-image-preview-img')).not_to_have_attribute('style', 'transform: translate3d(0px, 0px, 0px) scale3d(1, 1, 1) rotate(0deg);')
                    elif client == 'vue':
                        expect(gallery.locator('.el-image-viewer__img')).to_have_attribute('src', 'https://notice-image.test/2.png')
                        gallery.locator('.el-image-viewer__prev').click()
                        expect(gallery.locator('.el-image-viewer__img')).to_have_attribute('src', 'https://notice-image.test/1.png')
                    else:
                        expect(gallery.locator('.van-image-preview__index')).to_have_text('2 / 2')
                        if width == 390:
                            page.wait_for_timeout(400)  # Swipe after the opening transition.
                            session = context.new_cdp_session(page)
                            session.send('Input.dispatchTouchEvent', {'type': 'touchStart', 'touchPoints': [{'x': 60, 'y': 450}]})
                            for x in range(80, 340, 20):
                                session.send('Input.dispatchTouchEvent', {'type': 'touchMove', 'touchPoints': [{'x': x, 'y': 450}]})
                                page.wait_for_timeout(30)
                            session.send('Input.dispatchTouchEvent', {'type': 'touchEnd', 'touchPoints': []})
                            expect(gallery.locator('.van-image-preview__index')).to_have_text('1 / 2')
                    page.wait_for_timeout(400)  # Capture the completed preview transition.
                    page.screenshot(path=str(out / f'{client}-{width}.png'))
                    if client == 'h5': gallery.locator('.van-image-preview__close-icon').click()
                    else: page.keyboard.press('Escape')
                    expect(gallery).not_to_be_visible()
                    images.nth(0).click()
                    expect(gallery).to_be_visible()
                    if client in ['react', 'vue']:
                        page.get_by_role('button', name='更换正文').evaluate('(button) => button.click()')
                        expect(gallery).not_to_be_visible()
                    else:
                        page.evaluate("location.hash = 'token=' + 'b'.repeat(43)")
                        expect(gallery).not_to_be_visible()
                    if client == 'react':
                        expect(page.locator('#other img[role=button]')).to_have_count(0)
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
                assert not errors, (client, errors)
                print('PASS', client, width, 'click/keyboard/gallery/close/reset/layout', flush=True)
                context.close()
        browser.close()
finally:
    server.shutdown()
    server.server_close()
