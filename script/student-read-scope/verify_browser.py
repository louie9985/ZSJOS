# -*- coding: utf-8 -*-
"""Browser contract checks against real React/Vue pages with isolated API fixtures."""
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import re
import subprocess
import threading
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[2]
out = root / 'frontend/workbench/tmp/student-read-scope/browser'
subprocess.run(['node', str(Path(__file__).with_name('prepare_browser.cjs'))], cwd=root, check=True)

class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass

server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(out)))
threading.Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for client in ('react', 'vue'):
            for width in (1440, 390):
                page = browser.new_page(viewport={'width': width, 'height': 960})
                errors = []
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.route('**/admin-api/**', lambda route: route.abort())
                page.goto(f'{base}/{client}.html?legacy=1')
                card = page.locator('.student-service-card').filter(visible=True) if client == 'react' else page.locator('.el-drawer')
                if client == 'react':
                    if width == 390:
                        page.locator('#student-avatar-list button.lead-inbox-item').first.click()
                    else:
                        page.locator('tr.ant-table-row').first.click()
                expect(card).to_contain_text('本人课程')
                assert page.evaluate('fixture.details.every(id=>id===1)')
                assert page.evaluate('fixture.requests.every(q=>!q.readScope && !q.params?.readScope)')
                if client == 'react':
                    expect(page.get_by_role('button', name=re.compile('普通跟进')).filter(visible=True).first).to_be_visible()
                page.evaluate('navigateToService(2)')
                expect(page.get_by_text('无权查看该课程', exact=True).filter(visible=True).first).to_be_visible()
                if client == 'react':
                    expect(page.locator('.student-service-card').filter(visible=True)).to_have_count(0)
                    expect(page.get_by_role('button', name=re.compile('普通跟进')).filter(visible=True)).to_have_count(0)
                else:
                    expect(page.locator('.el-drawer')).not_to_contain_text('本人课程')
                if client == 'vue':
                    page.wait_for_function('Math.abs(document.querySelector(".el-drawer").getBoundingClientRect().right-innerWidth)<1')
                page.screenshot(path=str(out / f'{client}-denied-{width}.png'), full_page=True, animations='disabled')
                page.goto(f'{base}/{client}.html?denied=1')
                expect(page.get_by_text('无权查看该课程', exact=True).filter(visible=True).first).to_be_visible()
                if client == 'react':
                    expect(page.locator('.student-service-card').filter(visible=True)).to_have_count(0)
                    page.goto(f'{base}/{client}.html?class=1')
                    page.wait_for_function('fixture.requests.some(q=>q.classId===51)')
                    expect(page.locator('.student-service-card').filter(visible=True)).to_have_count(0)
                assert not errors, errors
                print('PASS', client, width, 'personal/legacy, forbidden deep link, stale detail/action removal', flush=True)
                page.close()
        browser.close()
finally:
    server.shutdown()
    server.server_close()
