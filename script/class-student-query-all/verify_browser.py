# -*- coding: utf-8 -*-
"""Real React/Vue components, synthetic transport only; never uses business accounts."""
from pathlib import Path
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import re
import subprocess
import threading
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--clients', nargs='+', choices=('react', 'vue'), default=['react', 'vue'])
parser.add_argument('--views', nargs='+', choices=('classes', 'students'), default=['classes', 'students'])
parser.add_argument('--personal-routing-only', action='store_true')
args = parser.parse_args()
clients = args.clients
out = root / 'frontend/workbench/tmp/class-student-query-all/browser'
subprocess.run(['node', str(Path(__file__).with_name('prepare_browser.cjs'))], cwd=root, check=True)

class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass

server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(out)))
threading.Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'

def open_student(page, client, width):
    if client == 'vue':
        page.get_by_role('button', name='详情', exact=True).click()
    elif width < 600:
        page.locator('#student-avatar-list button.lead-inbox-item').first.click()
    else:
        page.locator('tr.ant-table-row').first.click()
    expect(page.get_by_text('全范围课程', exact=True).filter(visible=True).first).to_be_visible()
    page.wait_for_function("fixture.calls.some(c=>c.url.endsWith('/lifecycle'))")
    if client == 'vue':
        # A zero-height lifecycle panel can still overflow its visible loading spinner.
        expect(page.locator('.el-loading-mask')).to_have_count(0)
    else:
        expect(page.locator('.ant-skeleton').filter(visible=True)).to_have_count(0)
    if client == 'vue':
        page.wait_for_function('Math.abs(document.querySelector(".el-drawer").getBoundingClientRect().right-innerWidth)<1')

def verify_no_extras(page, expected_write=None):
    assert page.evaluate('fixture.unexpected.length === 0'), page.evaluate('fixture.unexpected')
    writes = page.evaluate('fixture.writes')
    if expected_write:
        assert len(writes) == 1 and writes[0]['method'] == 'post' and writes[0]['url'] == expected_write, writes
        if expected_write.endswith('/close'):
            assert writes[0]['data']['reason'] == '权限回归测试'
            assert writes[0]['data']['version'] == 1 and writes[0]['data']['idempotencyKey']
    else:
        assert not writes, writes
    assert page.evaluate('fixture.calls.every(c => !c.query?.readScope)'), 'query-all must use server scope, not tenant ALL'

try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for view in ('classes', 'students'):
            page = browser.new_page(viewport={'width': 1440, 'height': 960})
            page.goto(f'{base}/react.html?view={view}&class-my=1')
            expected_path = '/zsjos/delivery-class/group/my-page' if view == 'classes' else '/zsjos/delivery-class/my-page'
            page.wait_for_function('(url)=>fixture.calls.some(c=>c.url===url)', arg=expected_path)
            assert not page.evaluate("fixture.calls.some(c=>['/zsjos/delivery-class/group/page','/zsjos/delivery-class/page','/zsjos/delivery-class/filter-context'].includes(c.url))")
            verify_no_extras(page)
            print('PASS react', view, 'query-my + query-all uses personal class endpoint', flush=True)
            page.close()
        if args.personal_routing_only:
            clients = []
        for client in clients:
            for width in (1440, 390):
                for view in args.views:
                    page = browser.new_page(viewport={'width': width, 'height': 960})
                    errors = []
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    page.route('**/admin-api/**', lambda route: route.abort())
                    for mode in ('read', 'feature', 'object', 'allowed'):
                        expected_write = None
                        page.goto(f'{base}/{client}.html?view={view}&mode={mode}')
                        if view == 'students':
                            open_student(page, client, width)
                            assert not page.evaluate("fixture.calls.some(c=>c.url.includes('/lead/'))"), 'student query-all is not lead permission'
                            if mode in ('read', 'object'):
                                assert not page.evaluate("fixture.calls.some(c=>c.url.includes('/delivery-class/'))"), 'no class query permission'
                            expect(page.get_by_role('tab', name='学员档案')).to_have_count(0)
                            if mode != 'allowed':
                                for label in ('关闭服务', '修改信息', '普通跟进', '调班'):
                                    expect(page.get_by_role('button', name=re.compile(re.escape(label) + '$')).filter(visible=True)).to_have_count(0)
                            else:
                                expect(page.get_by_role('button', name='关闭服务', exact=True).filter(visible=True).first).to_be_visible()
                                if client == 'react':
                                    expect(page.get_by_role('button', name=re.compile('普通跟进$')).filter(visible=True).first).to_be_visible()
                                    transfer = page.get_by_role('button', name=re.compile('调班$')).filter(visible=True)
                                    if transfer.count():
                                        expect(transfer.first).to_be_visible()
                                    else:
                                        page.get_by_role('button', name=re.compile('更多$')).filter(visible=True).first.click()
                                        expect(page.get_by_role('menuitem', name=re.compile('调班$'))).to_be_visible()
                                        page.keyboard.press('Escape')
                                expected_write = '/zsjos/student/service/1/close'
                                page.evaluate('(url)=>fixture.allowWrite=url', expected_write)
                                page.get_by_role('button', name='关闭服务', exact=True).filter(visible=True).first.click()
                                dialog = page.get_by_role('dialog', name='关闭服务', exact=True)
                                dialog.locator('textarea').fill('权限回归测试')
                                dialog.get_by_role('button', name=re.compile('确\\s*认'), exact=True).click()
                                page.wait_for_function('fixture.writes.length === 1')
                        else:
                            expect(page.get_by_text('全范围班级', exact=True).filter(visible=True).first).to_be_visible()
                            if client == 'react':
                                assert page.evaluate("fixture.calls.some(c=>c.url==='/zsjos/delivery-class/group/page')")
                                expect(page.get_by_role('button', name='查看学员', exact=True)).to_have_count(0)
                            if mode != 'allowed':
                                for label in ('编辑', '结课', '调整归组'):
                                    expect(page.get_by_role('button', name=label, exact=True).filter(visible=True)).to_have_count(0)
                            else:
                                page.get_by_role('button', name='编辑', exact=True).filter(visible=True).first.click()
                                page.wait_for_function("fixture.calls.some(c=>c.url.endsWith('/homeroom-candidates'))")
                                page.reload()
                                expected_write = '/zsjos/delivery-class/11/complete'
                                page.evaluate('(url)=>fixture.allowWrite=url', expected_write)
                                page.get_by_role('button', name='结课', exact=True).filter(visible=True).first.click()
                                page.get_by_role('button', name=re.compile('确\\s*认|确\\s*定'), exact=True).filter(visible=True).last.click()
                                page.wait_for_function('fixture.writes.length === 1')
                            if client == 'vue' and mode != 'allowed':
                                page.locator('.el-table__row').first.click()
                                expect(page.locator('.el-drawer')).to_contain_text('全范围学员')
                                expect(page.locator('.el-loading-mask')).to_have_count(0)
                                page.wait_for_function('Math.abs(document.querySelector(".el-drawer").getBoundingClientRect().right-innerWidth)<1')
                                for label in ('直接调班', '申请调班'):
                                    expect(page.get_by_role('button', name=label, exact=True)).to_have_count(0)
                        verify_no_extras(page, expected_write)
                        if mode == 'read':
                            page.screenshot(path=str(out / f'{client}-{view}-read-{width}.png'), full_page=True, animations='disabled')
                    page.goto(f'{base}/{client}.html?view={view}&mode=none')
                    expect(page.get_by_text('暂无班级查询权限' if view == 'classes' else '暂无学员查询权限', exact=True)).to_be_visible()
                    assert page.evaluate('fixture.calls.length === 0')
                    page.goto(f'{base}/{client}.html?view={view}&empty=1')
                    expect(page.get_by_text(re.compile('暂无.*班级|暂无.*学员|当前筛选下暂无学员')).filter(visible=True).first).to_be_visible()
                    verify_no_extras(page)
                    page.goto(f'{base}/{client}.html?view={view}&error=1')
                    expect(page.get_by_text('班级列表测试失败' if view == 'classes' else '学员列表测试失败', exact=True).filter(visible=True).first).to_be_visible()
                    page.evaluate('fixture.fail=false')
                    page.get_by_role('button', name=re.compile('重\\s*试|重新加载')).filter(visible=True).first.click()
                    expect(page.get_by_text('全范围班级' if view == 'classes' else '全范围学员', exact=True).filter(visible=True).first).to_be_visible()
                    verify_no_extras(page)
                    if client == 'react' and view == 'classes' and width == 1440:
                        page.goto(f'{base}/react.html?view=classes&create-only=1')
                        expect(page.get_by_role('button', name='创建班级组', exact=True)).to_have_count(0)
                        page.get_by_role('button', name=re.compile('创建班级$')).click()
                        dialog = page.get_by_role('dialog', name='创建班级', exact=True)
                        dialog.get_by_label('班级名称', exact=True).fill('独立创建权限回归')
                        dialog.get_by_role('combobox').click()
                        page.get_by_text('测试规划师甲', exact=True).filter(visible=True).last.click()
                        page.get_by_text('测试规划师乙', exact=True).filter(visible=True).last.click()
                        page.keyboard.press('Escape')
                        expect(dialog.get_by_text('所属班级组', exact=True)).to_have_count(0)
                        expected_write = '/zsjos/delivery-class/group/create-classes'
                        page.evaluate('(url)=>fixture.allowWrite=url', expected_write)
                        dialog.get_by_role('button', name='确认创建 2 个班级', exact=True).click()
                        page.wait_for_function('fixture.writes.length === 1')
                        verify_no_extras(page, expected_write)
                        payload = page.evaluate('fixture.writes[0].data')
                        assert payload['groupMode'] == 'NONE' and payload['homeroomUserIds'] == [7, 8], payload
                        assert not page.evaluate("fixture.calls.some(c=>c.url.endsWith('/group/options'))")
                    assert not errors, errors
                    print('PASS', client, view, width, 'read/function/object/allowed, expected mock write, missing base, empty, error/retry', flush=True)
                    page.close()
        browser.close()
finally:
    server.shutdown()
    server.server_close()
