# -*- coding: utf-8 -*-
"""Real Chrome checks of the production page with synthetic HTTP transport only."""
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

output = Path(tempfile.mkdtemp(prefix='exam-delete-browser-'))
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        page.set_default_timeout(10000)
        page.clock.install(time='2026-10-08T01:00:00Z')
        page.goto('http://127.0.0.1:5251/test/exam-delete.html?fail=1')
        page.get_by_role('button', name='2026年10月7日考期安排', exact=True).click()
        dialog = page.get_by_role('dialog')
        expired = dialog.locator('.ant-list-item').filter(has_text='过期草稿')
        expect(expired).to_have_count(1)
        expired.get_by_role('button', name=re.compile('删除草稿$')).click()
        expect(page.get_by_text('确认删除这条考期草稿？')).to_be_visible()
        page.clock.run_for(500)
        page.screenshot(path=str(output / f'confirm-{width}.png'), animations='disabled')
        page.locator('.ant-popconfirm').get_by_role('button', name=re.compile(r'取\s*消')).click()
        assert page.evaluate('window.examDeleteFixture.requests().length') == 0
        expired.get_by_role('button', name=re.compile('删除草稿$')).click()
        page.locator('.ant-popconfirm').get_by_role('button', name=re.compile(r'删\s*除$')).click()
        expect(page.get_by_text('当前考期状态不允许该操作', exact=True)).to_be_visible()
        expect(dialog.get_by_text('过期草稿', exact=True)).to_be_visible()
        expired.get_by_role('button', name=re.compile('删除草稿$')).click()
        page.locator('.ant-popconfirm').get_by_role('button', name=re.compile(r'删\s*除$')).click()
        expect(dialog.get_by_text('过期草稿', exact=True)).to_have_count(0)
        expect(dialog).not_to_be_visible()
        expect(page.get_by_role('button', name=re.compile('过期草稿，'))).to_have_count(0)
        page.get_by_role('button', name='2026年10月8日考期安排', exact=True).click()
        expect(dialog.get_by_role('button', name=re.compile('删除草稿$'))).to_have_count(2)
        published = dialog.locator('.ant-list-item').filter(has_text='已发布考期')
        expect(published).to_have_count(1)
        expect(published.get_by_role('button', name=re.compile('删除草稿$'))).to_have_count(0)
        page.clock.run_for(500)
        if width == 390:
            for record in dialog.locator('.exam-schedule-record').all():
                meta = record.locator('.ant-list-item-meta').bounding_box()
                actions = record.locator('.exam-schedule-actions').bounding_box()
                assert meta and actions and actions['y'] >= meta['y'] + meta['height'] - 1
                assert meta['width'] > 240
        page.screenshot(path=str(output / f'day-{width}.png'), animations='disabled')
        dialog.locator('.ant-modal-close').click()
        expect(page.get_by_text('当前考期状态不允许该操作', exact=True)).not_to_be_visible(timeout=10000)
        expect(page.get_by_text('考期草稿已删除', exact=True)).not_to_be_visible(timeout=10000)
        page.get_by_role('button', name=re.compile('多日考试安排$')).click()
        drawer = page.get_by_role('dialog', name='多日考试安排')
        drawer.get_by_role('button', name=re.compile('删除草稿$')).click()
        page.locator('.ant-popconfirm').get_by_role('button', name=re.compile(r'删\s*除$')).click()
        expect(drawer.get_by_text('多日草稿', exact=True)).to_have_count(0)
        page.screenshot(path=str(output / f'empty-multi-{width}.png'), animations='disabled')
        drawer.locator('.ant-drawer-close').click()
        expect(page.locator('[data-schedule-id="3"]')).to_have_count(0)
        requests = page.evaluate('window.examDeleteFixture.requests()')
        assert requests == [{'method': 'delete', 'url': '/zsjos/exam-calendar/delete/1'}] * 2 + [
            {'method': 'delete', 'url': '/zsjos/exam-calendar/delete/3'}], requests
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        print('PASS cancellation, rejection/retry, expired draft, published guard, multi-day removal, HTTP contract', width, flush=True)
        page.goto('http://127.0.0.1:5251/test/exam-delete.html?readonly=1')
        page.get_by_role('button', name='2026年10月8日考期安排', exact=True).click()
        expect(page.get_by_role('button', name=re.compile('删除草稿$'))).to_have_count(0)
        assert page.evaluate('window.examDeleteFixture.requests().length') == 0
        print('PASS query-only has no delete action', width, flush=True)
        page.close()
    browser.close()
print('Screenshots:', output, flush=True)
