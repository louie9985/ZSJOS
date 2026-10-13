# -*- coding: utf-8 -*-
"""Exercise actual React/Vue notice editors with synthetic, isolated API responses."""
import json
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(tempfile.gettempdir()) / 'notice-recipient-tree-20261013'
OUT.mkdir(exist_ok=True)


def checkbox(page, client, title):
    if client == 'react':
        row = page.locator('.ant-tree-treenode').filter(has=page.get_by_text(title, exact=True))
        return row.locator('.ant-tree-checkbox')
    row = page.locator('.el-tree-node__content').filter(has=page.get_by_text(title, exact=True))
    return row.locator('.el-checkbox__input')


def checked(page, client, title, state):
    box = checkbox(page, client, title)
    prefix = 'ant-tree-checkbox-' if client == 'react' else 'is-'
    if state == 'checked':
        expect(box).to_have_class(re.compile(prefix + 'checked'))
    elif state == 'half':
        expect(box).to_have_class(re.compile(prefix + 'indeterminate'))
    else:
        expect(box).not_to_have_class(re.compile(prefix + '(checked|indeterminate)'))


def save(page, expected):
    before = page.evaluate('window.noticeOriginFixture.writes.length')
    page.locator('button').filter(has_text='保存草稿').click()
    page.wait_for_function('(n) => window.noticeOriginFixture.writes.length > n', arg=before)
    payload = page.evaluate('JSON.parse(window.noticeOriginFixture.writes.at(-1).data)')
    assert payload['targetDeptIds'] == [], payload
    assert sorted(payload['targetUserIds']) == sorted(expected), payload
    expect(page.get_by_text('草稿已保存', exact=True).first).to_be_visible()


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for client, port in [('react', os.getenv('NOTICE_REACT_PORT', '5174')), ('vue', os.getenv('NOTICE_VUE_PORT', '5194'))]:
        for width in [1440, 390]:
            page = browser.new_page(viewport={'width': width, 'height': 1000})
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            base = f'http://127.0.0.1:{port}/test/notice-origin.html?editor&recipient-tree'
            try:
                # Legacy department drafts expand before any user interaction or save.
                page.goto(base + '&draft')
                expect(page.get_by_text('已选 4 人', exact=False)).to_be_visible(timeout=60000)
                for name in ['测试中心', '测试一部', '测试二部', '测试小组', '中心员工', '一部员工', '二部员工', '小组员工']:
                    checked(page, client, name, 'checked')
                disabled = page.get_by_text(re.compile('^禁用员工（'))
                row = page.locator('.ant-tree-treenode' if client == 'react' else '.el-tree-node__content').filter(has=disabled)
                expect(row.locator('.ant-tree-checkbox' if client == 'react' else '.el-checkbox__input')).to_have_class(re.compile('disabled'))
                save(page, [101, 102, 103, 104])
                # Cancel a complete branch; its descendants stay excluded after saving/reopening.
                checkbox(page, client, '测试一部').click()
                checked(page, client, '测试中心', 'half')
                checked(page, client, '一部员工', 'empty')
                checked(page, client, '小组员工', 'empty')
                save(page, [101, 103])
                page.goto(base + '&saved')
                checked(page, client, '测试中心', 'half')
                checked(page, client, '一部员工', 'empty')
                checked(page, client, '小组员工', 'empty')
                # Recheck the center, then exclude one deep employee only.
                checkbox(page, client, '测试中心').click()
                checked(page, client, '小组员工', 'checked')
                checkbox(page, client, '小组员工').click()
                checked(page, client, '测试中心', 'half')
                checked(page, client, '测试一部', 'half')
                checkbox(page, client, '未分配员工').click()
                search = page.get_by_placeholder('搜索部门或用户（匹配项高亮）' if client == 'react' else '搜索部门或用户', exact=True)
                search.fill('二部'); search.fill('')
                checked(page, client, '一部员工', 'checked')
                checked(page, client, '小组员工', 'empty')
                save(page, [101, 102, 103, 106])
                page.goto(base + '&saved')
                checked(page, client, '测试一部', 'half')
                checked(page, client, '小组员工', 'empty')
                checked(page, client, '未分配员工', 'checked')
                expect(page.locator('.w-e-text-container')).to_be_visible()
                page.wait_for_timeout(500)  # Let modal/loading/checkbox transitions settle for visual evidence.
                page.screenshot(path=str(OUT / f'{client}-{width}-partial.png'), full_page=True, animations='disabled')
                # Clear all selections, reject an empty TARGET, then allow reselecting.
                checkbox(page, client, '测试中心').click()
                checkbox(page, client, '测试中心').click()
                checkbox(page, client, '未分配员工').click()
                checked(page, client, '测试中心', 'empty')
                before = page.evaluate('window.noticeOriginFixture.writes.length')
                page.get_by_role('button', name='保存草稿', exact=True).click()
                expect(page.get_by_text('请选择接收员工' if client == 'react' else '请至少选择一名接收员工', exact=True)).to_be_visible()
                assert page.evaluate('window.noticeOriginFixture.writes.length') == before
                checkbox(page, client, '测试二部').click()
                save(page, [103])
                # New editor also conducts parent/child selection after choosing TARGET.
                page.goto(base)
                page.get_by_text('指定部门/用户', exact=True).first.click()
                checkbox(page, client, '测试中心').click()
                checked(page, client, '小组员工', 'checked')
                # Failed options must block saving; retry restores the legacy selection.
                page.goto(base + '&draft&error')
                expect(page.get_by_text('部门选项加载失败', exact=True).first).to_be_visible()
                page.get_by_role('button', name=re.compile('重\s*试')).first.click()
                checked(page, client, '测试中心', 'checked')
                assert not errors, errors
                print('PASS', client, width, 'cascade/partial/exclusion/disabled/search/clear/save/reopen/legacy/retry', flush=True)
            except Exception:
                page.screenshot(path=str(OUT / f'{client}-{width}-failure.png'), full_page=True)
                print(page.locator('body').inner_text()[-3500:], flush=True)
                raise
            finally:
                page.close()
    browser.close()
print('Screenshots:', OUT)
