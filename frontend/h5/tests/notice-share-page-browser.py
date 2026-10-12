# -*- coding: utf-8 -*-
"""Inspect real Controller-exported HTML with JavaScript disabled; no live business writes."""
import argparse
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--fixtures', type=Path, required=True)
args = parser.parse_args()
fixtures = args.fixtures.resolve()
for name in ['notice.html', 'invalid.html', 'error.html', 'file-error.html']:
    assert (fixtures / name).is_file(), f'Run NoticeSharePageControllerTest first: missing {name}'
state = {'failed': False, 'revoked': False, 'file_missing': False}
received_referrers = []
api = '/public-api/system/notice-share'


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        request = urlsplit(self.path)
        query = parse_qs(request.query)
        if request.path == '/download':
            received_referrers.append(self.headers.get('Referer'))
            self.send_response(200)
            self.send_header('Content-Type', 'text/html;charset=UTF-8')
            self.end_headers()
            self.wfile.write('<h1>选中附件</h1>'.encode('utf-8'))
            return
        if query.get('token') != ['a' * 43] or state['revoked']:
            status, name = 410, 'invalid.html'
        elif state['failed']:
            status, name = 503, 'error.html'
        elif request.path == api + '/file':
            if state['file_missing'] or query.get('attachmentId') != ['101']:
                status, name = 404, 'file-error.html'
            else:
                self.send_response(302)
                self.send_header('Location', '/download')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('Referrer-Policy', 'no-referrer')
                self.end_headers()
                return
        else:
            assert request.path == api + '/page', request.path
            status, name = 200, 'notice.html'
        self.send_response(status)
        self.send_header('Content-Type', 'text/html;charset=UTF-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.end_headers()
        self.wfile.write((fixtures / name).read_bytes())


server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f'http://127.0.0.1:{server.server_port}{api}/page?token=' + 'a' * 43
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for width in [1440, 390]:
            state.update(failed=False, revoked=False, file_missing=False)
            context = browser.new_context(viewport={'width': width, 'height': 960}, java_script_enabled=False)
            page = context.new_page()
            response = page.goto(url)
            assert response.status == 200
            title = '中世健考试安排 <2026> & "说明"'
            expect(page).to_have_title(title)
            expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            expect(page.get_by_text('请及时查看考试安排。', exact=True)).to_be_visible()
            expect(page.locator('meta[property="og:title"]')).to_have_attribute('content', title)
            expect(page.get_by_role('table')).to_be_visible()
            expect(page.get_by_role('link', name='打开／下载附件')).to_have_count(1)
            assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1')
            page.screenshot(path=str(fixtures / f'page-{width}.png'), full_page=True)
            with page.expect_popup() as popup:
                page.get_by_role('link', name='打开／下载附件').click()
            file_page = popup.value
            expect(file_page.get_by_role('heading', name='选中附件')).to_be_visible()
            file_page.close()
            state['file_missing'] = True
            with page.expect_popup() as popup:
                page.get_by_role('link', name='打开／下载附件').click()
            file_page = popup.value
            expect(file_page.get_by_role('heading', name='分享附件不可用')).to_be_visible()
            expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
            state['file_missing'] = False
            file_page.get_by_role('link', name='重试', exact=True).click()
            expect(file_page.get_by_role('heading', name='选中附件')).to_be_visible()
            file_page.close()
            state['failed'] = True
            assert page.reload().status == 503
            expect(page.get_by_role('heading', name='暂时无法加载公告')).to_be_visible()
            state['failed'] = False
            page.get_by_role('link', name='重试', exact=True).click()
            expect(page).to_have_title(title)
            state['revoked'] = True
            assert page.reload().status == 410
            expect(page).to_have_title('分享内容已失效')
            expect(page.get_by_text('请及时查看考试安排。', exact=True)).to_have_count(0)
            context.close()
            print('PASS', width, 'no-JS title/metadata/body; attachment navigation and retry; revoked and transient errors')
        assert received_referrers and all(value is None for value in received_referrers)
        browser.close()
finally:
    server.shutdown()
    server.server_close()
