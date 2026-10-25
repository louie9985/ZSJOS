# -*- coding: utf-8 -*-
"""Real Chrome regression against isolated fixtures on the existing Vite server."""
import os
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('TABLE_TEST_BASE', 'http://127.0.0.1:5174')
output = Path(tempfile.gettempdir()) / 'zsjos-table-width'
output.mkdir(exist_ok=True)

def width(locator):
    return locator.evaluate('(el) => el.getBoundingClientRect().width')

def header(table, title):
    return table.locator('thead th').filter(has_text=title).first

def toggle_column(page, table, title):
    table.locator('.anticon-setting').click()
    tree = page.locator('.ant-popover:visible .ant-tree-treenode').filter(has_text=title)
    tree.locator('.ant-tree-checkbox').click()
    table.locator('.anticon-setting').click()

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    errors = []
    for viewport in (1440, 390):
        page = browser.new_page(viewport={'width': viewport, 'height': 960})
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/business-table.html?width=1')
        table = page.locator('[data-table-key="fixture-main"]')
        expect(table.locator('tr.ant-table-row')).to_have_count(20)
        expect(header(table, '说明')).to_have_count(0)
        expect(header(table, '不展示字段')).to_have_count(0)
        # Neither persisted-hidden nor hideInTable columns may inflate visible widths.
        assert width(header(table, '名称')) < 600
        for minimum, expected in ((500, 1100), (1800, 1800)):
            numeric_root = page.locator(f'[data-table-key="fixture-numeric-{minimum}"]')
            numeric = numeric_root.locator('.ant-table-container table').last
            expected = max(expected, width(numeric_root.locator('.ant-table-container')))
            assert abs(width(numeric) - expected) < 2, (viewport, minimum, width(numeric))
        if viewport == 1440:
            before = width(header(table, '名称'))
            toggle_column(page, table, '说明')
            expect(header(table, '说明')).to_be_visible()
            assert width(header(table, '名称')) < before
            toggle_column(page, table, '说明')
            expect(header(table, '说明')).to_have_count(0)
            page.reload()
            expect(header(table, '说明')).to_have_count(0)
            # Force horizontal overflow so actual widths can track the drag exactly.
            page.evaluate("localStorage.setItem('crm-table:fixture-main:widths', JSON.stringify({name:1100}))")
            page.reload()
            expect(header(table, '名称')).to_be_visible()
            assert abs(width(header(table, '名称')) - 1100) < 2
            rect = header(table, '名称').bounding_box()
            page.mouse.move(rect['x'] + rect['width'] - 2, rect['y'] + rect['height'] / 2)
            page.mouse.down()
            page.mouse.move(rect['x'] + rect['width'] + 88, rect['y'] + rect['height'] / 2, steps=5)
            page.mouse.up()
            assert page.evaluate("JSON.parse(localStorage.getItem('crm-table:fixture-main:widths')).name") == 1190
            assert abs(width(header(table, '名称')) - 1190) < 2
            expect(page.locator('#fixture-state')).to_contain_text('排序 0')
            expect(table.locator('tbody .business-table-action-cell').first.get_by_role('button', name='详细')).to_be_visible()
        else:
            holder = table.locator('.ant-table-body')
            assert holder.evaluate('(el) => el.scrollWidth > el.clientWidth')
            holder.evaluate('(el) => el.scrollLeft = el.scrollWidth')
            expect(table.locator('tbody .business-table-action-cell').first.get_by_role('button', name='详细')).to_be_visible()
        page.screenshot(path=str(output / f'shared-{viewport}.png'), full_page=True)
        page.goto(base + '/test/student-table-completion.html?layout=table')
        student = page.locator('[data-table-key="registration-pages-1"]')
        if viewport > 390:
            expect(student.locator('tr.ant-table-row')).to_have_count(2)
            assert abs(width(header(student, '学员姓名')) - 180) < 2
            assert abs(width(header(student, '手机号')) - 160) < 2
            expect(student.locator('tr.ant-table-row .ant-avatar')).to_have_count(0)
        else:
            expect(page.locator('#student-avatar-list button.lead-inbox-item')).to_have_count(2)
        page.screenshot(path=str(output / f'student-{viewport}.png'), full_page=True)
        page.close()
        print('PASS visible widths, hidden fields, numeric/grouped/native tables, scrolling:', viewport)
    assert not errors, errors
    # Reproduce the reported screenshot with narrow historical widths and realistic content.
    page = browser.new_page(viewport={'width': 2200, 'height': 1100})
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(base + '/test/student-table-completion.html?long=1')
    page.evaluate("localStorage.setItem('crm-student-management-table-widths',JSON.stringify({courses:32,name:80,mobile:120}))")
    page.reload()
    student = page.locator('[data-table-key="registration-pages-1"]')
    expect(student.locator('tr.ant-table-row')).to_have_count(2)
    course = header(student, '课程服务')
    assert abs(width(course) - 280) < 2
    assert abs(width(header(student, '手机号')) - 120) < 2
    assert student.locator('tr.ant-table-row').evaluate_all('(rows)=>rows.every(r=>r.getBoundingClientRect().height < 140)')
    # Both shrinking below the minimum and widening must work from the protected width.
    for delta, expected in ((-150, 280), (80, 360)):
        course.scroll_into_view_if_needed()
        rect = course.bounding_box()
        page.mouse.move(rect['x'] + rect['width'] - 2, rect['y'] + rect['height'] / 2)
        page.mouse.down()
        page.mouse.move(rect['x'] + rect['width'] - 2 + delta, rect['y'] + rect['height'] / 2, steps=5)
        page.mouse.up()
        assert abs(width(course) - expected) < 2
    page.reload()
    expect(student.locator('tr.ant-table-row')).to_have_count(2)
    assert abs(width(header(student, '课程服务')) - 360) < 2
    page.screenshot(path=str(output / 'student-long-saved-width.png'), full_page=True)
    assert not errors, errors
    page.close()
    print('PASS long course/spec content, narrow saved widths, row height, minimum drag and wider persisted width')
    browser.close()
    print('PASS preferences, toggle, resize, fixed actions; no page errors. Screenshots:', output)
