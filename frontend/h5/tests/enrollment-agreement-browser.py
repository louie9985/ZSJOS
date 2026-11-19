"""Exercise the production H5 bundle with isolated registration API responses."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
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

dist = Path(__file__).resolve().parents[1] / 'dist'
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
                assert seen == ['read']
                assert route.request.post_data_json['agreementAccepted']
                assert route.request.post_data_json['agreementHash']=='test-digest'
                seen.append('submit')
            route.fulfill(json={'code':0,'data':result})
        page.route('**/public-api/**', fixture)
        page.goto(f'http://127.0.0.1:{server.server_port}/student-info-form#token=fixture')
        submit = page.get_by_role('button',name='同意协议并提交报名信息')
        expect(submit).to_be_disabled()
        page.get_by_text('我已阅读并同意协议',exact=True).click(force=True)
        expect(page.get_by_text('请先完整阅读协议，再勾选同意。')).to_be_visible()
        expect(page.get_by_role('checkbox')).not_to_be_checked()
        page.get_by_role('button',name='阅读协议',exact=True).click()
        done = page.get_by_role('button',name='我已完整阅读并同意该协议',exact=True)
        expect(done).to_be_disabled()
        done.focus()
        done.press('Enter')
        expect(page.locator('.van-toast')).to_contain_text('请向下滑动至协议末尾')
        expect(page.get_by_role('button',name='关闭',exact=True)).to_have_count(0)
        assert not seen
        page.locator('.van-toast').wait_for(state='hidden')
        page.screenshot(path=str(Path(gettempdir()) / f'agreement-reader-qa-{width}.png'))
        page.locator('.agreement-reader').evaluate('el => el.scrollTop = el.scrollHeight')
        done.click()
        expect(page.get_by_role('checkbox')).to_be_checked()
        expect(submit).to_be_enabled()
        assert seen == ['read']
        page.get_by_role('checkbox').uncheck()
        expect(submit).to_be_disabled()
        page.get_by_role('checkbox').check()
        submit.click()
        page.locator('.van-dialog__confirm').click()
        page.get_by_role('button',name='查看已确认协议').click()
        expect(page.locator('.agreement-reader')).to_be_visible()
        assert seen == ['read','submit']
        page.close()
    browser.close()
server.shutdown()
print('Agreement reading, consent, submission and archive passed on mobile and desktop.')
