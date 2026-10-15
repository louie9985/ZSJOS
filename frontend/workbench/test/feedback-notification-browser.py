# -*- coding: utf-8 -*-
"""Real action/detail components, synthetic API only; no live employee messages."""
import os
import re
from pathlib import Path
import tempfile
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('WORKBENCH_TEST_URL', 'http://localhost:5174')
out = Path(tempfile.gettempdir()) / 'feedback-notification-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 900})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        url = base + '/test/feedback-notification.html'
        for button in ['点击反馈消息', '查看业务']:
            page.goto(url)
            page.get_by_role('button', name=button, exact=False).click()
            expect(page).to_have_url(re.compile(r'/zsjos/feedback[?]feedbackId=73$'))
            expect(page.get_by_text('指定反馈详情验证', exact=True)).to_be_visible()
            expect(page.get_by_text('已修复目标问题', exact=True)).to_be_visible()
            assert '/zsjos/feedback/73' in page.evaluate('window.feedbackNotificationFixture.requests')
        page.screenshot(path=str(out / f'feedback-{width}.png'), full_page=True)
        for failure, label in [('forbidden', '无权查看该反馈'), ('missing', '反馈不存在')]:
            page.goto(url + '?failure=' + failure)
            page.get_by_role('button', name='查看业务', exact=False).click()
            expect(page.get_by_text(label, exact=True)).to_be_visible()
            expect(page.get_by_text('指定反馈详情验证', exact=True)).to_have_count(0)
            page.evaluate('window.feedbackNotificationFixture.failure = ""')
            page.get_by_role('button', name=re.compile('重.*试')).click()
            expect(page.get_by_text('指定反馈详情验证', exact=True)).to_be_visible()
        page.goto(url + '?no-read=1')
        page.get_by_role('button', name='查看业务', exact=False).click()
        expect(page.get_by_text('暂无反馈记录', exact=True)).to_be_visible()
        assert '/zsjos/feedback/73' not in page.evaluate('window.feedbackNotificationFixture.requests')
        page.goto(url + '?invalid=1')
        expect(page.get_by_role('button', name='查看业务', exact=False)).to_have_count(0)
        page.get_by_role('button', name='点击反馈消息', exact=True).click()
        expect(page).to_have_url(re.compile(r'/messages/all[?]messageId=11$'))
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: desktop/mobile message action, detail action, exact feedback, denial/missing retry, permission gate, invalid ID')
print(out)
