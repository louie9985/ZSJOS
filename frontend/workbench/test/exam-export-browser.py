# -*- coding: utf-8 -*-
"""Real Chrome production UI with synthetic HTTP; no authenticated acceptance claim."""
import json
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

OUT = Path(tempfile.mkdtemp(prefix='exam-export-browser-'))
BASE = 'http://127.0.0.1:5174'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440,390]:
        page = browser.new_page(viewport={'width':width,'height':1000})
        page.route('**/*', lambda route: route.continue_() if urlparse(route.request.url).netloc == '127.0.0.1:5174' and '/admin-api/' not in route.request.url else route.abort())
        page.clock.install(time='2026-10-14T01:00:00Z')
        page.goto(BASE + '/test/exam-export.html?fail=1')
        page.get_by_role('button',name=re.compile('下载 Excel$')).click()
        dialog = page.get_by_role('dialog')
        fields = dialog.locator('input')
        expect(fields.nth(0)).to_have_value('2026-10-01')
        expect(fields.nth(1)).to_have_value('2026-10-31')
        fields.nth(0).fill('2026-09-28'); fields.nth(0).press('Enter')
        fields.nth(1).fill('2026-11-02'); fields.nth(1).press('Enter')
        dialog.get_by_text('时间区间',exact=True).click()
        page.clock.run_for(500)
        page.screenshot(path=str(OUT / f'range-{width}.png'),animations='disabled')
        box=dialog.bounding_box(); assert box['x'] >= 0 and box['x']+box['width'] <= width
        dialog.get_by_role('button',name='生成 Excel',exact=True).click()
        page.clock.run_for(600)
        expect(dialog.get_by_text('没有导出权限',exact=True)).to_be_visible()
        dialog.get_by_role('button',name='生成 Excel',exact=True).click()
        page.clock.run_for(600)
        expect(dialog.get_by_text('导出任务已创建',exact=True)).to_be_visible()
        expect(dialog.get_by_role('link',name='导出任务')).to_have_attribute('href','/zsjos/export-task')
        requests=page.evaluate('window.examExportRequests')
        assert len(requests)==2 and requests[1]['exportType']=='exam_calendar'
        assert json.loads(requests[1]['filterJson']) == {'rangeStart':'2026-09-28','rangeEnd':'2026-11-02'}
        page.screenshot(path=str(OUT / f'success-{width}.png'),animations='disabled')
        for permission in ['zsjos:exam-calendar:export','zsjos:export:query','zsjos:exam-calendar:query']:
            page.goto(BASE+'/test/exam-export.html?denied='+permission)
            expect(page.get_by_role('button',name=re.compile('下载 Excel$'))).to_have_count(0)
        page.goto(BASE+'/test/exam-export.html?tasks=1')
        expect(page.get_by_text('考期日历',exact=True)).to_be_visible()
        expect(page.get_by_role('button',name=re.compile('下载$'))).to_be_visible()
        page.close()
    browser.close()
print('PASS desktop/mobile date range, failure/retry, request contract, permission gates and task download entry')
print('Screenshots:', OUT)
