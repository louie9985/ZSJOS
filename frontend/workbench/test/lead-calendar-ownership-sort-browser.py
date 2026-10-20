# -*- coding: utf-8 -*-
"""Real Chrome with synthetic responses; verifies sort controls and API parameters."""
import datetime as dt
import os
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright, expect

OUT = Path('D:/ZSJ-OS-backups/lead-calendar-ownership-20261018')
OUT.mkdir(parents=True, exist_ok=True)
day = dt.date.today().isoformat()
stamp = int(dt.datetime.now().timestamp() * 1000)
queries = []
errors = []

def handler(route):
    parsed = urlparse(route.request.url)
    q = parse_qs(parsed.query)
    data = []
    if parsed.path.endswith('/lead-follow-up-calendar/days'):
        data = [dict(date=day, count=3)]
    elif parsed.path.endswith('/lead-follow-up-calendar/cards'):
        queries.append(q)
        ids = [1, 2, 3]
        if q.get('sort') == ['ownershipStartedAt']:
            ids = [2, 1, 3] if q.get('direction') == ['asc'] else [1, 2, 3]
        data = dict(total=3, list=[dict(lead=dict(id=i, leadNo=f'KZ-TEST-{i:03}',
            submittedName=f'测试客资{i}', status='valid', assignmentStatus='owned',
            submittedAt=stamp, availableActions=[], attachments=[], intendedProducts=[]),
            deadline=stamp+i*60000, canReadFollowUp=False) for i in ids])
    route.fulfill(json=dict(code=0, data=data))

def choose(page, name, label):
    page.get_by_role('combobox', name=name, exact=True).click()
    page.locator('.ant-select-dropdown:visible').get_by_text(label, exact=True).click()

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width, height in [(1600, 1100), (390, 844)]:
        page = browser.new_page(viewport=dict(width=width, height=height))
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.route('**/admin-api/**', handler)
        page.goto(os.getenv('CALENDAR_TEST_URL', 'http://127.0.0.1:5174/test/lead-calendar.html'))
        page.locator('.lead-calendar-count').click()
        titles = page.locator('.lead-calendar-card h5')
        expect(titles).to_have_count(3)
        choose(page, '客资排序字段', '归属开始时间')
        expect(titles.first).to_have_text('测试客资2')
        assert queries[-1]['sort'] == ['ownershipStartedAt']
        assert queries[-1]['direction'] == ['asc'] and queries[-1]['pageNo'] == ['1']
        choose(page, '排序方向', '最新优先')
        expect(titles.first).to_have_text('测试客资1')
        assert queries[-1]['direction'] == ['desc']
        expect(titles.last).to_have_text('测试客资3')
        page.get_by_role('combobox', name='客资排序字段', exact=True).click()
        expect(page.locator('.ant-select-dropdown:visible').get_by_text('归属开始时间', exact=True)).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.screenshot(path=str(OUT / f'ownership-sort-{width}.png'), full_page=True)
        page.keyboard.press('Escape')
        choose(page, '客资排序字段', '跟进截止时间')
        choose(page, '排序方向', '最早优先')
        assert queries[-1]['sort'] == ['deadline']
        page.close()
    browser.close()
assert not errors, errors
print('PASS: desktop/mobile ownership sort controls, asc/desc parameters, page reset, existing sort and layout')
