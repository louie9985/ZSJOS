# -*- coding: utf-8 -*-
"""Real Chromium component/route checks with synthetic HTTP only; owns ports 5197-5199."""
import json
import os
import re
import socket
import subprocess
import tempfile
import time
import urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(tempfile.gettempdir()) / 'notice-public-share-browser'
OUT.mkdir(exist_ok=True)
processes = []
logs = []
def server(project, port):
    with socket.socket() as probe:
        assert probe.connect_ex(('127.0.0.1', port)) != 0, f'Port {port} is owned by another process'
    log = open(OUT / f'{project}-vite.log', 'w', encoding='utf-8'); logs.append(log)
    cwd = ROOT / 'frontend' / project
    process = subprocess.Popen(['node', 'node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', str(port), '--strictPort'],
        cwd=cwd, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    processes.append(process)
    for _ in range(100):
        if process.poll() is not None: raise RuntimeError(f'{project} fixture server failed; inspect {log.name}')
        try:
            urllib.request.urlopen(f'http://127.0.0.1:{port}/', timeout=1)
            return
        except Exception: time.sleep(.3)
    raise RuntimeError(f'{project} fixture server timeout')

try:
    server('workbench', 5197); server('admin', 5198); server('h5', 5199)
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for client, port in [('react', 5197), ('vue', 5198)]:
            for width in [1440, 390]:
                page = browser.new_page(viewport={'width': width, 'height': 960}, accept_downloads=True)
                errors = []
                page.on('pageerror', lambda e: errors.append(str(e)))
                base = f'http://127.0.0.1:{port}/test/notice-share.html'
                def open_detail(suffix=''):
                    page.goto(base + suffix)
                    if client == 'vue': page.get_by_role('button', name='打开公告', exact=True).click()
                open_detail()
                try:
                    expect(page.get_by_role('button', name='对外分享', exact=True)).to_be_visible(timeout=15000)
                except Exception:
                    page.screenshot(path=str(OUT / f'{client}-failure.png'), full_page=True)
                    print('Fixture errors:', errors, 'Body:', page.locator('body').inner_text()[:1500], flush=True)
                    raise
                page.get_by_role('button', name='对外分享', exact=True).click()
                expect(page.get_by_role('button', name='开启分享', exact=True)).to_be_visible()
                expect(page.get_by_text('原内部接收范围：1 个部门，0 名指定人员。', exact=True)).to_be_visible()
                expect(page.get_by_role('checkbox', name='课程介绍.pdf')).not_to_be_checked()
                page.get_by_text('课程介绍.pdf', exact=True).last.click()
                expect(page.get_by_role('checkbox', name='课程介绍.pdf')).to_be_checked()
                page.get_by_role('button', name='开启分享', exact=True).click()
                expect(page.get_by_role('button', name='下载二维码', exact=True)).to_be_visible()
                expect(page.get_by_role('checkbox', name='课程介绍.pdf')).to_be_disabled()
                assert page.evaluate('window.shareFixture.selected.join()') == '101'
                old = page.get_by_role('textbox', name='公开链接').input_value()
                with page.expect_download() as download:
                    page.get_by_role('button', name='下载二维码', exact=True).click()
                download.value.save_as(OUT / f'{client}-{width}-qr.png')
                assert (OUT / f'{client}-{width}-qr.png').stat().st_size > 100
                page.screenshot(path=str(OUT / f'{client}-{width}.png'), full_page=True)
                assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1')
                page.get_by_role('button', name='关闭分享', exact=True).click()
                page.get_by_role('button', name=re.compile(r'^(确\s*定|OK|Yes)$')).last.click()
                expect(page.get_by_role('button', name='开启分享', exact=True)).to_be_visible()
                expect(page.get_by_role('checkbox', name='课程介绍.pdf')).not_to_be_checked()
                page.get_by_role('button', name='开启分享', exact=True).click()
                expect(page.get_by_role('textbox', name='公开链接')).not_to_have_value(old)
                assert not any('mark-read' in x for x in page.evaluate('window.shareFixture.calls'))
                open_detail('?denied=1')
                expect(page.get_by_role('button', name='对外分享', exact=True)).to_have_count(0)
                open_detail()
                page.evaluate('window.shareFixture.fail = true')
                page.get_by_role('button', name='对外分享', exact=True).click()
                expect(page.get_by_text('分享配置加载失败', exact=True).first).to_be_visible()
                page.evaluate('window.shareFixture.fail = false')
                page.get_by_role('button', name='刷新重试', exact=True).click()
                expect(page.get_by_role('button', name='开启分享', exact=True)).to_be_visible()
                assert not errors, errors
                page.close()
                print('PASS', client, width, 'selected files/QR download/revoke/reopen/denied/retry/no read writes', flush=True)
        for width in [1440, 390]:
            page = browser.new_page(viewport={'width': width, 'height': 960})
            state = {'fail': False, 'invalid': False, 'file_fail': False}
            requests = []
            def handle(route):
                req = route.request; requests.append(req.url)
                assert 'authorization' not in req.headers
                assert 'tenant-id' not in req.headers
                assert req.headers.get('x-notice-share-token') == 'a' * 43
                assert 'token=' not in req.url
                if state['invalid']: result = {'code': 1002008007, 'msg': '分享内容已失效'}
                elif state['fail']: result = {'code': 500, 'msg': '服务暂不可用'}
                elif 'attachment-url' in req.url:
                    result = {'code': 1002008011, 'msg': '分享附件不可用'} if state['file_fail'] else {'code': 0, 'data': 'https://files.example.com/guide.pdf'}
                else:
                    result = {'code': 0, 'data': {'title': '中世健公开课程说明', 'content': '<p>欢迎学员和合作伙伴了解课程。</p><table><tr><td>课程介绍</td></tr></table>', 'publishTime': 1791410400000,
                        'attachments': [{'id': 101, 'fileName': '课程介绍.pdf', 'fileSize': 1024, 'mimeType': 'application/pdf'}]}}
                route.fulfill(status=200, content_type='application/json', body=json.dumps(result, ensure_ascii=False))
            page.route('**/public-api/system/notice-share/**', handle)
            url = 'http://127.0.0.1:5199/notice/share#token=' + 'a' * 43
            page.goto(url)
            expect(page.get_by_role('heading', name='中世健公开课程说明', exact=True)).to_be_visible()
            state['file_fail'] = True
            page.get_by_role('button', name='获取文件', exact=True).click()
            expect(page.get_by_text('分享附件不可用', exact=True)).to_be_visible()
            expect(page.get_by_role('heading', name='中世健公开课程说明', exact=True)).to_be_visible()
            state['file_fail'] = False
            page.get_by_role('button', name='重试', exact=True).click()
            expect(page.get_by_role('link', name='打开／下载附件')).to_be_visible()
            page.screenshot(path=str(OUT / f'h5-{width}.png'), full_page=True)
            assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1')
            state['fail'] = True; page.reload()
            expect(page.get_by_role('heading', name='暂时无法加载公告')).to_be_visible()
            state['fail'] = False
            page.get_by_role('button', name='重试', exact=True).click()
            expect(page.get_by_role('heading', name='中世健公开课程说明', exact=True)).to_be_visible()
            state['invalid'] = True; page.reload()
            expect(page.get_by_role('heading', name='分享内容已失效')).to_be_visible()
            assert '/login' not in page.url
            assert not any('mark-read' in x for x in requests)
            page.close()
            print('PASS H5', width, 'anonymous/header token/files/failure/retry/revoked/no login', flush=True)
        browser.close()
    print('Screenshots:', OUT, flush=True)
finally:
    for process in processes:
        process.terminate()
    for process in processes:
        try: process.wait(timeout=10)
        except subprocess.TimeoutExpired: process.kill()
    for log in logs: log.close()
