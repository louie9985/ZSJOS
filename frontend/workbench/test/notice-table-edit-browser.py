# -*- coding: utf-8 -*-
"""Run against local Vite. Actual announcement editor; clipboard and memory saves only."""
import json
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('NOTICE_TABLE_TEST_URL', 'http://127.0.0.1:5174')
OUT = Path(tempfile.mkdtemp(prefix='notice-table-edit-'))
MERGED = '<table style="width:600px"><tr><td colspan="2" style="width:600px">考试安排</td></tr><tr><td style="width:200px">考试日期</td><td style="width:400px">报名截止时间</td></tr></table>'
WORD = '<table style="width:450pt"><tr style="height:30pt"><td style="width:150pt">中文甲</td><td style="width:300pt">中文乙考试安排</td></tr><tr><td>甲</td><td>乙</td></tr></table>'
PERCENT = '<table style="width:100%"><colgroup><col style="width:25%"><col style="width:75%"></colgroup><tr><td>中文甲</td><td>中文乙考试安排</td></tr></table>'
ROWSPAN = '<table style="width:600px"><tr><td rowspan="2" style="width:100px">跨行</td><td colspan="2" style="width:500px">合并标题</td></tr><tr><td style="width:200px">中列</td><td style="width:300px">末列</td></tr></table>'


def paste(page, html):
    page.evaluate('''async html => navigator.clipboard.write([new ClipboardItem({
        'text/html': new Blob([html], {type:'text/html'}),
        'text/plain': new Blob(['剪贴板表格'], {type:'text/plain'})
    })])''', html)
    page.keyboard.press('Control+v')
    page.wait_for_timeout(250)


def widths(editor):
    return editor.locator('col').evaluate_all('(xs) => xs.map(x => Number(x.width))')


def fresh(page):
    page.get_by_role('button', name='新建空正文', exact=True).click()
    editor = page.locator('[data-slate-editor]')
    editor.click()
    # Menu state follows Slate selection asynchronously.
    page.keyboard.insert_text('公告测试')
    page.wait_for_timeout(300)
    page.keyboard.press('Enter')
    return editor


def drag_row(page, row, delta):
    box = row.bounding_box()
    x, y = box['x'] + 20, box['y'] + box['height'] - 1
    page.mouse.move(x, y - 8)
    page.mouse.move(x, y)
    expect(page.locator('.notice-table-row-guide')).to_be_visible()
    page.mouse.down()
    page.mouse.move(x, y + delta, steps=8)
    page.mouse.up()
    page.wait_for_timeout(200)


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    for viewport in [1440, 390]:
        page = browser.new_page(viewport={'width': viewport, 'height': 1000}, permissions=['clipboard-read', 'clipboard-write'])
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('**/*admin-api/**', lambda route: route.abort())
        page.goto(BASE + '/test/notice-tables.html')
        page.locator('[data-slate-editor]').wait_for()
        for name, html, expected in [('merged', MERGED, [200, 400]), ('word', WORD, [200, 400]), ('percent', PERCENT, [25, 75]), ('rowspan', ROWSPAN, [100, 200, 300])]:
            editor = fresh(page)
            paste(page, html)
            assert widths(editor) == expected, (name, widths(editor))
            before = editor.inner_text()
            assert '\ufffd' not in before
            if name == 'word':
                assert editor.locator('tr').first.evaluate('r=>r.style.height') == '40px'
            if name == 'percent':
                cells = editor.locator('td').evaluate_all('(xs)=>xs.map(x=>x.getBoundingClientRect().width)')
                assert abs(cells[1] / cells[0] - 3) < .03, cells
            page.get_by_role('button', name='保存到本地样例', exact=True).click()
            page.get_by_role('button', name='重新打开样例', exact=True).click()
            assert widths(editor) == expected
            assert editor.inner_text() == before
            if name == 'rowspan':
                expect(editor.locator('td[rowspan="2"]')).to_have_text('跨行')
                expect(editor.locator('td[colspan="2"]')).to_have_text('合并标题')
            if name == 'merged':
                row = editor.locator('tr').last
                before_height = row.bounding_box()['height']
                drag_row(page, row, 20)
                assert row.bounding_box()['height'] >= before_height + 15
                assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
            page.screenshot(path=str(OUT / f'{viewport}-{name}.png'), full_page=True)

        editor = fresh(page)
        page.locator('button[data-menu-key="insertTable"]').click()
        page.locator('.w-e-panel-content-table td[data-x="2"][data-y="1"]').click()
        editor.locator('th').first.click()
        page.keyboard.insert_text('报名截止时间及考试安排说明')
        page.wait_for_timeout(250)
        row = editor.locator('tr').first
        original = row.bounding_box()['height']
        assert original > 80
        drag_row(page, row, 35)
        changed = row.bounding_box()['height']
        assert changed >= original + 30, (original, changed)
        page.keyboard.press('Control+z')
        page.wait_for_timeout(150)
        assert abs(row.bounding_box()['height'] - original) < 2
        page.keyboard.press('Control+y')
        page.wait_for_timeout(150)
        assert abs(row.bounding_box()['height'] - changed) < 2
        # Cancelling a drag must not persist the DOM preview or leave document listeners.
        box = row.bounding_box()
        page.mouse.move(box['x'] + 20, box['y'] + box['height'] - 1)
        page.mouse.down()
        page.mouse.move(box['x'] + 20, box['y'] + box['height'] + 30)
        page.keyboard.press('Escape')
        page.mouse.up()
        assert abs(row.bounding_box()['height'] - changed) < 2
        page.get_by_role('button', name='保存到本地样例', exact=True).click()
        page.get_by_role('button', name='重新打开样例', exact=True).click()
        assert abs(row.bounding_box()['height'] - changed) < 2
        assert '报名截止时间及考试安排说明' in editor.inner_text()
        drag_row(page, row, -20)
        assert row.bounding_box()['height'] < changed - 15
        # Existing column interaction still uses the native table menu and resizer.
        cell = editor.locator('th').first
        cell.click()
        page.wait_for_timeout(200)
        box = cell.bounding_box()
        x, y = box['x'] + box['width'] - 1, box['y'] + 10
        page.mouse.move(x - 8, y)
        page.mouse.move(x, y)
        page.wait_for_timeout(150)
        prior = widths(editor)[0]
        page.mouse.down()
        page.mouse.move(x + 40, y, steps=8)
        page.wait_for_timeout(150)
        page.mouse.up()
        page.wait_for_timeout(150)
        assert widths(editor)[0] > prior + 30
        page.screenshot(path=str(OUT / f'{viewport}-resized.png'), full_page=True)
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), 'page overflow'
        assert not errors, errors
        print(json.dumps({'width': viewport, 'paste_roundtrip': 'PASS', 'new_row_drag_undo_redo_cancel': 'PASS', 'column_drag': 'PASS'}), flush=True)
        page.close()

    # The real announcement form validates and saves HTML through the existing service boundary.
    page = browser.new_page(viewport={'width':1440,'height':1000}, permissions=['clipboard-read','clipboard-write'])
    page.goto(BASE + '/test/notice-table-overflow.html')
    page.get_by_role('dialog').get_by_role('button', name='Close').click()
    page.get_by_role('button', name='editor', exact=True).click()
    editor = page.locator('[data-slate-editor]')
    editor.wait_for()
    editor.locator('p').last.click()
    page.keyboard.press('End')
    page.keyboard.press('Enter')
    paste(page, MERGED)
    assert widths(editor)[-2:] == [200, 400]
    dialog = page.get_by_role('dialog', name='编辑公告草稿')
    dialog.get_by_role('button', name=re.compile('保存草稿')).click()
    expect(page.locator('p[role=status]')).to_contain_text('内存保存次数：1')
    assert '考试安排' in page.get_by_test_id('saved-html').text_content()
    dialog.get_by_role('button', name=re.compile(r'关\s*闭')).click()
    page.get_by_role('button', name='editor', exact=True).click()
    expect(editor.locator('table')).to_have_count(2)
    assert widths(editor)[-2:] == [200, 400]
    page.close()
    browser.close()
print('PASS; screenshots:', OUT)
