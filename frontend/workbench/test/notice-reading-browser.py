# -*- coding: utf-8 -*-
"""Isolated real Chromium checks for both announcement clients; synthetic APIs only."""
import tempfile
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.gettempdir()) / 'notice-reading-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for client, port in [('react', 5187), ('vue', 5188)]:
        for width in [1440, 390]:
            page = browser.new_page(viewport={'width': width, 'height': 960})
            errors = []
            page.on('pageerror', lambda e: errors.append(str(e)))
            base = f'http://127.0.0.1:{port}/test/notice-reading.html'
            def open_stats(suffix=''):
                page.goto(base + suffix)
                if client == 'vue':
                    page.get_by_role('button', name='打开公告', exact=True).click()
                page.get_by_role('tab', name='阅读情况', exact=True).click()
            open_stats()
            expect(page.get_by_text('80.0%', exact=True)).to_be_visible()
            expect(page.get_by_text('员工1', exact=True)).to_be_visible()
            expect(page.get_by_text('已删除', exact=True)).to_be_visible()
            if client == 'react':
                page.locator('.ant-pagination-item-2').click()
            else:
                page.locator('.el-pager li').filter(has_text='2').first.click()
            expect(page.get_by_text('员工21', exact=True)).to_be_visible()
            assert page.evaluate('window.noticeFixture.queries.at(-1).pageNo') == 2
            page.get_by_role('tab', name='未读', exact=True).click()
            expect(page.get_by_text('员工1', exact=True)).to_be_visible()
            assert page.evaluate('window.noticeFixture.queries.at(-1).scope') == 'UNREAD'
            page.get_by_placeholder('搜索姓名').fill('员工19')
            page.get_by_placeholder('搜索姓名').press('Enter')
            expect(page.get_by_text('员工19', exact=True)).to_be_visible()
            expect(page.get_by_text('员工1', exact=True)).to_have_count(0)
            page.get_by_placeholder('搜索姓名').fill('')
            page.get_by_placeholder('搜索姓名').press('Enter')
            if client == 'react':
                page.get_by_role('combobox', name='筛选部门').click()
                page.locator('.ant-select-item-option').filter(has_text='发布部门').click()
            else:
                page.locator('.el-select').click()
                page.locator('.el-select-dropdown__item').filter(has_text='发布部门').click()
            page.wait_for_function('window.noticeFixture.queries.at(-1).deptId === 10')
            page.get_by_role('tab', name='名单外阅读', exact=True).click()
            expect(page.get_by_text('当前资料（非历史快照）', exact=True).first).to_be_visible()
            expect(page.get_by_role('tab', name='名单外阅读', exact=True)).to_have_attribute('aria-selected', 'true')
            page.wait_for_timeout(300)
            page.screenshot(path=str(out / f'{client}-{width}.png'), full_page=True)
            assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1')
            page.evaluate('window.noticeFixture.fail = true')
            page.get_by_role('button', name=re.compile(r'刷\s*新')).last.click()
            expect(page.get_by_text('统计加载失败', exact=True).first).to_be_visible()
            page.evaluate('window.noticeFixture.fail = false')
            page.get_by_role('button', name=re.compile(r'重\s*试')).first.click()
            expect(page.get_by_text('80.0%', exact=True)).to_be_visible()
            page.evaluate('window.noticeFixture.denied = true')
            page.get_by_role('button', name=re.compile(r'刷\s*新')).last.click()
            expect(page.get_by_text('80.0%', exact=True)).to_have_count(0)
            expect(page.get_by_text('员工1', exact=True)).to_have_count(0)
            assert page.evaluate('window.noticeFixture.' + ('reads' if client == 'react' else 'writes')) == 0
            open_stats('?legacy=1')
            expect(page.get_by_text('无法统计应读、未读及阅读率：发布时未记录名单', exact=True)).to_be_visible()
            expect(page.get_by_role('tab', name='实际阅读人员')).to_be_visible()
            open_stats('?draft=1')
            expect(page.get_by_text('发布后生成阅读统计', exact=True)).to_be_visible()
            assert not errors, errors
            page.close()
            print('PASS', client, width, 'summary/paging/search/extra/history/draft/error/retry/denied; no management writes')
    page = browser.new_page(viewport={'width': 1440, 'height': 960})
    page.goto('http://127.0.0.1:5187/test/notice-reading.html')
    page.get_by_role('button', name='隐藏正文', exact=True).click()
    page.wait_for_timeout(300)
    assert page.evaluate('window.noticeFixture.reads') == 0
    page.get_by_role('button', name='打开员工正文', exact=True).click()
    expect(page.get_by_text('已读记录失败：写入失败', exact=True)).to_be_visible()
    expect(page.get_by_text('真实正文展示测试', exact=True)).to_be_visible()
    assert page.evaluate('window.noticeFixture.painted')
    page.get_by_role('button', name='恢复写入', exact=True).click()
    page.get_by_role('button', name='重试记录', exact=True).click()
    expect(page.get_by_text('已读记录失败：写入失败', exact=True)).to_have_count(0)
    expect(page.get_by_text('未读', exact=True)).to_have_count(0)
    assert page.evaluate('window.noticeFixture.reads') == 2
    for suffix in ['?center=1', '?center=1&detailFail=1']:
        page.set_viewport_size({'width': 390, 'height': 960})
        page.goto('http://127.0.0.1:5187/test/notice-reading.html' + suffix)
        page.wait_for_timeout(700)
        assert page.evaluate('window.noticeFixture.reads') == 0, 'Mobile list/preload must not acknowledge'
    print('PASS visible-body acknowledgement, retry, hidden-body and mobile-list no-read')
    browser.close()
print('Screenshots:', out)
