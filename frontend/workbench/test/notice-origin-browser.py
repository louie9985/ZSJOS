# -*- coding: utf-8 -*-
"""Real Chromium coverage of both notice clients with isolated synthetic transport."""
import json
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(tempfile.gettempdir()) / 'notice-origin-browser'
OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for client, port in [('react', 5193), ('vue', 5194)]:
        for width in [1440, 390]:
            page = browser.new_page(viewport={'width': width, 'height': 1000})
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            base = f'http://127.0.0.1:{port}/test/notice-origin.html'
            page.goto(base + '?editor')
            form_item = page.locator('.ant-form-item' if client == 'react' else '.el-form-item')
            source = form_item.filter(has=page.locator('label').filter(has_text=re.compile('^文章来源$')))
            expect(source).to_contain_text('考务部')
            source.locator('.ant-select' if client == 'react' else '.el-select').click()
            page.locator('.ant-select-item-option' if client == 'react' else '.el-select-dropdown__item').filter(has_text='综合行政部').click()
            expect(source).to_contain_text('综合行政部')
            page.get_by_placeholder('请输入公告标题').fill('来源部门验收') if client == 'vue' else page.get_by_label('公告标题').fill('来源部门验收')
            type_item = form_item.filter(has=page.locator('label').filter(has_text=re.compile('^公告类型$')))
            type_item.locator('.ant-select' if client == 'react' else '.el-select').click()
            page.locator('.ant-select-item-option' if client == 'react' else '.el-select-dropdown__item').filter(has_text=re.compile('^公告$')).click()
            page.locator('[contenteditable=true]').first.fill('公告来源与发布人验收正文')
            page.get_by_role('button', name='保存草稿', exact=True).click()
            page.wait_for_function('window.noticeOriginFixture.writes.length === 1')
            write = page.evaluate('window.noticeOriginFixture.writes[0]')
            payload = json.loads(write['data']) if isinstance(write['data'], str) else write['data']
            assert payload['sourceDeptId'] == 20 and payload['audienceType'] == 'ALL'
            assert 'publisherName' not in payload and 'publisherId' not in payload
            source.scroll_into_view_if_needed()
            page.screenshot(path=str(OUT / f'{client}-{width}-editor.png'), full_page=True)

            page.goto(base + '?editor&error')
            expect(page.locator('.ant-alert, .el-alert').get_by_text('部门选项加载失败', exact=True)).to_be_visible()
            page.get_by_role('button', name=re.compile(r'重\s*试'), exact=True).first.click()
            expect(page.locator('.ant-alert, .el-alert').get_by_text('部门选项加载失败', exact=True)).to_have_count(0)
            expect(source).to_contain_text('考务部')
            page.goto(base + '?editor&empty')
            source.locator('.ant-select' if client == 'react' else '.el-select').click()
            expect(page.get_by_text('暂无可选部门，请联系管理员维护', exact=True)).to_be_visible()

            for suffix, audience in [('?detail', '全体员工'), ('?detail&target', '综合行政部（含子部门）'), ('?detail&legacy', '全体员工')]:
                page.goto(base + suffix)
                if client == 'vue': page.get_by_role('button', name='打开公告', exact=True).click()
                expect(page.get_by_text(audience, exact=True)).to_be_visible()
                if 'legacy' in suffix: expect(page.get_by_text('未记录', exact=True)).to_have_count(2)
                else:
                    expect(page.get_by_text('考务部', exact=True)).to_be_visible()
                    expect(page.get_by_text('测试发布人', exact=True)).to_be_visible()
                if suffix == '?detail&target': page.screenshot(path=str(OUT / f'{client}-{width}-detail.png'), full_page=True)

            if client == 'react':
                page.goto(base + '?target')
                panel = page.get_by_role('region', name='公告栏')
                expect(panel).to_contain_text('文章来源：考务部')
                expect(panel).to_contain_text('发布人：测试发布人')
                expect(panel).to_contain_text('接收部门/人员：综合行政部（含子部门）')
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                page.screenshot(path=str(OUT / f'{client}-{width}-home.png'), full_page=True)
                page.goto(base + '?center')
                search = page.get_by_placeholder('搜索标题、来源部门或发布人')
            else:
                page.goto(base)
                search = page.get_by_placeholder('标题、来源部门或发布人')
            search.fill('考务'); search.press('Enter')
            page.wait_for_function("window.noticeOriginFixture.queries.some(q => (q.keyword || q.title) === '考务')")
            expect(page.get_by_text('十月考试工作安排', exact=True).first).to_be_visible()
            search.fill('不存在'); search.press('Enter')
            page.wait_for_function("window.noticeOriginFixture.queries.at(-1).keyword === '不存在' || window.noticeOriginFixture.queries.at(-1).title === '不存在'")
            if client == 'react': expect(page.locator('.announcement-list-pane').get_by_text('十月考试工作安排', exact=True)).to_have_count(0)
            else: expect(page.locator('.el-table').get_by_text('十月考试工作安排', exact=True)).to_have_count(0)
            assert not errors, errors
            print('PASS', client, width, 'source default/change/save, retry/empty, metadata/legacy, search; screenshots', OUT, flush=True)
            page.close()
    browser.close()
