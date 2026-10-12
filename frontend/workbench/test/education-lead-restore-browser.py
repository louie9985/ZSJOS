# -*- coding: utf-8 -*-
"""Real Workbench restore flow with synthetic HTTP only; no real lead mutations."""
import re
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:5174/test/education-lead-restore.html?leadId=1'

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.set_default_timeout(15000)
    for source in ['', '&partner=1']:
        page.goto(BASE + source + '&denied=1')
        expect(page.get_by_text('客资已挂起', exact=True)).to_be_visible()
        expect(page.get_by_role('button', name=re.compile(r'恢复$'))).to_have_count(0)
        assert page.evaluate('window.restoreFixture.posts.length') == 0

        page.goto(BASE + source + '&error=1')
        page.get_by_role('button', name=re.compile(r'恢复$')).click()
        dialog = page.get_by_role('dialog', name='恢复原负责人')
        dialog.get_by_role('button', name='确认处理', exact=True).click()
        expect(page.get_by_text('请填写处置理由', exact=True)).to_be_visible()
        assert page.evaluate('window.restoreFixture.posts.length') == 0
        dialog.get_by_placeholder('填写本次处置理由').fill('主管核实后恢复原教务继续跟进')
        for attempt in range(2):
            dialog.get_by_role('button', name='确认处理', exact=True).click()
            page.locator('.ant-popconfirm').get_by_role('button', name='确认执行', exact=True).click()
            if attempt == 0:
                expect(page.get_by_text('负责人状态已变化，请刷新后重试', exact=True)).to_be_visible()
                expect(dialog.get_by_placeholder('填写本次处置理由')).to_have_value('主管核实后恢复原教务继续跟进')
            else:
                expect(dialog).not_to_be_visible()
        expect(page.get_by_role('button', name=re.compile(r'恢复$'))).to_have_count(0)
        expect(page.get_by_text('客资已挂起', exact=True)).to_have_count(0)
        assert page.evaluate('window.restoreFixture.valid')
        posts = page.evaluate('window.restoreFixture.posts')
        assert len(posts) == 2 and posts[0]['idempotencyKey'] == posts[1]['idempotencyKey']
        assert posts[1]['reason'] == '主管核实后恢复原教务继续跟进'
        print('PASS', 'partner specified' if source else 'education self sourced')
    browser.close()
