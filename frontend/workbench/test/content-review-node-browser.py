# UTF-8. Synthetic transport; test the production page and message resolver in real Chrome.
import os
import re
from pathlib import Path
from urllib.request import urlopen
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'node_modules/.cache/content-review-node'
OUT.mkdir(parents=True, exist_ok=True)
BASE = os.environ.get('REVIEW_NODE_URL', 'http://127.0.0.1:5197')
URL = BASE + '/zsjos/material-library/content-review'
HTML = urlopen(BASE + '/test/content-review-node.html').read().decode('utf-8')

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('**/zsjos/material-library/content-review*', lambda route:
                   route.fulfill(content_type='text/html', body=HTML)
                   if route.request.resource_type == 'document' else route.continue_())
        page.goto(URL + '?batchId=42')
        detail = page.locator('.content-review-detail-pane')
        expect(detail).to_contain_text('审批编号：CR-NODE-42')
        expect(detail).not_to_contain_text('审批编号：CR-NODE-1')
        page.get_by_role('button', name='打开历史未通过消息', exact=True).click()
        expect(page).to_have_url(URL + '?batchId=41')
        expect(detail).to_contain_text('历史冻结意见')
        page.go_back()
        expect(detail).to_contain_text('审批编号：CR-NODE-42')
        page.go_forward()
        expect(detail).to_contain_text('审批编号：CR-NODE-41')
        page.reload()
        expect(detail).to_contain_text('历史冻结作品')
        page.get_by_role('button', name='模拟结果弹窗', exact=True).click()
        page.locator('.ant-notification-notice').click()
        expect(detail).to_contain_text('审批编号：CR-NODE-42')
        page.evaluate('window.nodeMessageFixture.delay = 300')
        page.get_by_role('button', name='打开历史未通过消息', exact=True).click()
        page.evaluate('window.nodeMessageFixture.delay = 0')
        page.get_by_role('button', name='打开终审通过消息', exact=True).click()
        page.wait_for_timeout(400)
        expect(page).to_have_url(URL + '?batchId=42')
        expect(detail).to_contain_text('审批编号：CR-NODE-42')
        # A failed list cannot hide a directly authorized target.
        page.evaluate('window.nodeMessageFixture.listError = true')
        page.get_by_role('button', name='刷新内容审核', exact=True).click()
        expect(detail).to_contain_text('审批编号：CR-NODE-42')
        # Exact target fails independently and never displays the previous successful detail.
        for flag, text in [('denied', '当前账号无权查看该审批批次'), ('missing', '关联审批批次不存在或已失效'),
                           ('temporary', '审批批次加载失败，请稍后重试')]:
            page.evaluate(f'window.nodeMessageFixture.{flag} = true')
            page.get_by_role('button', name='刷新内容审核', exact=True).click()
            expect(detail).to_contain_text(text)
            expect(detail).not_to_contain_text('当前通过作品')
            page.evaluate(f'window.nodeMessageFixture.{flag} = false')
            detail.get_by_role('button', name=re.compile(r'重\s*试')).click()
            expect(detail).to_contain_text('审批编号：CR-NODE-42')
        page.screenshot(path=str(OUT / f'node-{width}.png'), full_page=True)
        assert not errors, errors
        page.goto(URL + '?batchId=-1')
        expect(page.locator('.content-review-detail-pane')).to_contain_text('内容审批链接无效')
        assert page.evaluate('window.nodeMessageFixture.gets') == []
        page.close()
    browser.close()
print('PASS desktop/mobile notification/list/link/history/reload/list failure/denied/missing/retry/invalid target')
