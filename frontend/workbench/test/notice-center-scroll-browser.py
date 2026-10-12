# -*- coding: utf-8 -*-
"""Chromium interaction checks; synthetic APIs only. Run fixture Vite on 5191."""
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.gettempdir()) / 'notice-center-scroll-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto('http://127.0.0.1:5191/test/notice-center-scroll.html')
        rows = page.locator('.announcement-list-item')
        expect(rows).to_have_count(20)
        pane = page.locator('.announcement-list-pane')
        page.wait_for_timeout(400)
        assert page.evaluate('noticeCenterFixture.lists.length') == 1
        assert pane.evaluate('(e) => e.scrollHeight > e.clientHeight')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert page.evaluate('document.documentElement.scrollHeight <= innerHeight + 1')
        if width == 390:
            assert page.evaluate('noticeCenterFixture.reads.length') == 0
            rows.nth(1).click()
            expect(page.locator('.ant-drawer-body .announcement-detail')).to_be_visible()
            expect(page.locator('.ant-drawer-body')).to_contain_text('文章来源：未记录')
            page.locator('.ant-drawer-close').click()
            page.wait_for_timeout(400)
        else:
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 1')
            pane.evaluate('(e) => e.scrollTop = 60')
            before = pane.evaluate('(e) => e.scrollTop')
            rows.nth(1).click()
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 2')
            assert pane.evaluate('(e) => e.scrollTop') == before
            assert page.evaluate('noticeCenterFixture.lists.length') == 1
            assert page.evaluate('noticeCenterFixture.details.filter(id => id === 2).length') == 1
            detail = page.locator('.announcement-detail-pane')
            assert detail.evaluate('(e) => e.scrollHeight > e.clientHeight')
            detail.evaluate('(e) => e.scrollTop = 300')
            rows.nth(2).click()
            rows.nth(3).click()
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 4')
            page.wait_for_timeout(400)
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 4')
            assert detail.evaluate('(e) => e.scrollTop') == 0
            page.evaluate('noticeCenterFixture.failDetail = 5')
            rows.nth(4).click()
            expect(detail).to_contain_text('正文加载失败')
            page.evaluate('noticeCenterFixture.failDetail = 0')
            detail.get_by_role('button', name=re.compile(r'重\s*试')).click()
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 5')
            assert page.evaluate('noticeCenterFixture.lists.length') == 1
            page.evaluate("history.pushState({}, '', '?announcementId=8'); dispatchEvent(new PopStateEvent('popstate'))")
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 8')
            assert page.evaluate('noticeCenterFixture.lists.length') == 1
            page.evaluate('noticeCenterFixture.denied = true')
            rows.nth(5).click()
            expect(detail).to_contain_text('无权查看此公告')
            page.evaluate('noticeCenterFixture.denied = false')
            detail.get_by_role('button', name=re.compile(r'重\s*试')).click()
            expect(page.locator('.announcement-detail h3')).to_have_text('验收公告 6')
        page.evaluate('noticeCenterFixture.failMore = true')
        pane.evaluate('(e) => e.scrollTop = e.scrollHeight')
        expect(page.get_by_role('button', name='重试加载')).to_be_visible()
        count = page.evaluate('noticeCenterFixture.lists.length')
        page.wait_for_timeout(400)
        assert page.evaluate('noticeCenterFixture.lists.length') == count
        expect(rows).to_have_count(20)
        page.evaluate('noticeCenterFixture.failMore = false')
        page.get_by_role('button', name='重试加载').click()
        expect(rows).to_have_count(40)
        pane.evaluate('(e) => e.scrollTop = e.scrollHeight')
        expect(rows).to_have_count(60)
        pane.evaluate('(e) => e.scrollTop = e.scrollHeight')
        expect(page.get_by_text('已加载全部公告', exact=True)).to_be_visible()
        pane.evaluate('(e) => e.scrollTop = 0')
        page.screenshot(path=str(out / f'notice-{width}.png'), full_page=True)
        page.get_by_role('button', name=re.compile(r'刷\s*新')).click()
        expect(rows).to_have_count(20)
        page.evaluate('noticeCenterFixture.slowMore = true')
        pane.evaluate('(e) => e.scrollTop = e.scrollHeight')
        expect(page.get_by_text('正在加载更多', exact=True)).to_be_visible()
        page.get_by_placeholder('搜索标题、来源部门或发布人').fill('empty')
        page.get_by_placeholder('搜索标题、来源部门或发布人').press('Enter')
        expect(page.get_by_text('暂无公告', exact=True)).to_be_visible()
        page.wait_for_timeout(1000)
        expect(rows).to_have_count(0)
        page.goto('http://127.0.0.1:5191/test/notice-center-scroll.html?announcementId=21')
        expect(page.locator('.announcement-detail:visible h3')).to_have_text('验收公告 21')
        assert page.evaluate('noticeCenterFixture.details') == [21]
        assert page.evaluate('noticeCenterFixture.lists.length') == 1
        if width == 1440:
            expect(page.locator('.ant-drawer-content')).to_have_count(0)
        assert not errors, errors
        page.close()
        print('PASS', width, 'local selection, bounded scroll, lazy cursor/retry/end, empty state')
    browser.close()
print(out)
