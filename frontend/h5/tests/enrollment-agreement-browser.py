"""Exercise the production H5 bundle with isolated registration API responses."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import os
import json
from pathlib import Path
from threading import Thread
from tempfile import gettempdir
from playwright.sync_api import expect, sync_playwright

class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/student-info-form'):
            self.path = '/index.html'
        super().do_GET()
    def log_message(self, *args):
        pass

dist = Path(os.environ.get('H5_DIST', Path(__file__).resolve().parents[1] / 'dist'))
server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Handler, directory=str(dist)))
Thread(target=server.serve_forever, daemon=True).start()
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='msedge')
    for width in [390, 1280]:
        page = browser.new_page(viewport={'width':width,'height':800})
        seen = []
        def fixture(route):
            url = route.request.url
            result = True
            if '/detail' in url:
                result = dict(status='DRAFT', productName='示例课程', fields=[dict(key='name',label='姓名',type='text',group='basic',enabled=True)],
                              images=[],options={},agreementHash='test-digest',agreementConsent='我已阅读并同意协议',
                              agreementBody='<p>核对课程和实际成交金额。</p>'*100)
            elif '/agreement-read' in url:
                seen.append('read')
            elif '/submit' in url:
                assert not seen
                assert 'agreementAccepted' not in route.request.post_data_json
                assert 'agreementHash' not in route.request.post_data_json
                seen.append('submit')
            route.fulfill(json={'code':0,'data':result})
        page.route('**/public-api/**', fixture)
        page.goto(f'http://127.0.0.1:{server.server_port}/student-info-form#token=fixture')
        expect(page.get_by_role('button',name='阅读协议')).to_have_count(0)
        expect(page.get_by_role('checkbox')).to_have_count(0)
        page.get_by_role('textbox').first.fill('合成学员')
        page.get_by_role('button',name='提交信息').click()
        page.locator('.van-dialog__confirm').click()
        expect(page.get_by_text('信息已提交，不可修改',exact=False).first).to_be_visible()
        assert seen == ['submit']
        page.close()
    browser.close()
server.shutdown()
