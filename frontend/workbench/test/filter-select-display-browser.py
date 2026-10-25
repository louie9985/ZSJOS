# -*- coding: utf-8 -*-
"""Display-only regression using production controls and isolated candidates."""
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('STUDENT_TABLE_TEST_BASE', 'http://127.0.0.1:5174')
output = Path(tempfile.gettempdir()) / 'zsjos-filter-select-display'
output.mkdir(exist_ok=True)

def check_popup(page, width):
    popup = page.locator('.workbench-filter-popup:visible:not(.ant-slide-up-leave)')
    expect(popup).to_be_visible()
    page.wait_for_function('(el) => el.getBoundingClientRect().width >= Math.min(480, innerWidth - 24) - 1', arg=popup.element_handle())
    rect = popup.evaluate('(el) => {const r=el.getBoundingClientRect(); return {x:r.x,right:r.right,width:r.width}}')
    assert rect['x'] >= -1 and rect['right'] <= width + 1, rect
    assert 300 < rect['width'] <= 480, rect
    for label in popup.locator('.ant-select-item-option-content, .ant-select-item-group').all():
        assert label.evaluate('(el) => getComputedStyle(el).whiteSpace === "normal" && el.scrollWidth <= el.clientWidth + 1 && el.scrollHeight <= el.clientHeight + 1'), label.inner_text()
    return popup

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/filter-select-display.html')
        owner = page.get_by_role('combobox', name='全部学习规划师', exact=True)
        owner.click()
        popup = check_popup(page, width)
        popup.locator('.ant-select-item-option').first.click()
        page.wait_for_function('filterDisplayFixture.quick.ownerUserId === 21')
        expect(page.locator('.ant-select').filter(has=owner).locator('.workbench-filter-label')).to_have_attribute('title', '测试班主任 · 中世健健康管理师项目交付服务中心第一教学工作组')
        page.locator('.ant-select').filter(has=owner).hover()
        page.locator('.ant-select').filter(has=owner).locator('.ant-select-clear').click()
        page.wait_for_function('filterDisplayFixture.quick.ownerUserId === undefined')
        project = page.get_by_role('combobox', name='项目', exact=True)
        project.click()
        popup = check_popup(page, width)
        project.fill('健康管理师')
        expect(popup.locator('.ant-select-item-option')).to_have_count(1)
        popup.locator('.ant-select-item-option').click()
        page.wait_for_function('filterDisplayFixture.classes.projectCategoryId === 1')
        page.get_by_role('combobox', name='班主任筛选', exact=True).click()
        popup = check_popup(page, width)
        page.screenshot(path=str(output / f'filters-{width}.png'), full_page=True)
        popup.locator('.ant-select-item-option').click()
        page.wait_for_function('filterDisplayFixture.classes.homeroomUserId === 21')
        page.get_by_role('combobox', name='指定人员', exact=True).click()
        check_popup(page, width).locator('.ant-select-item-option').click()
        page.wait_for_function('filterDisplayFixture.scope.targetUserId === 21')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Selected name expands the page'
        multi = page.get_by_role('combobox', name='多选显示验证', exact=True)
        multi.click()
        popup = check_popup(page, width)
        popup.locator('.ant-select-item-option').nth(0).click()
        popup.locator('.ant-select-item-option').nth(1).click()
        page.wait_for_function('filterDisplayFixture.multiple.join() === "a,b"')
        page.keyboard.press('Escape')
        page.locator('.ant-select').filter(has=multi).hover()
        page.locator('.ant-select').filter(has=multi).locator('.ant-select-clear').click()
        page.wait_for_function('filterDisplayFixture.multiple.length === 0')
        page.get_by_role('button', name='高级筛选', exact=True).click()
        template = page.locator('.advanced-filter-template-select')
        template.click()
        popup = check_popup(page, width)
        popup.locator('.ant-select-item-option').click()
        field = page.locator('.advanced-filter-condition > .ant-select').first
        field.click()
        check_popup(page, width)
        page.keyboard.press('Escape')
        page.locator('.advanced-filter-value-control .ant-select').click()
        popup = check_popup(page, width)
        page.screenshot(path=str(output / f'advanced-{width}.png'), full_page=True)
        page.keyboard.press('Escape')
        page.get_by_role('button', name='应用筛选', exact=True).click()
        page.wait_for_function('filterDisplayFixture.applied.conditions[0].value[0] === "21"')
        page.get_by_role('button', name='高级筛选', exact=True).click()
        page.locator('.advanced-filter-value-control .ant-select').click()
        check_popup(page, width).locator('.ant-select-item-option').nth(1).click()
        page.keyboard.press('Escape')
        page.get_by_role('button', name=re.compile(r'^取\s*消$')).click()
        assert page.evaluate('filterDisplayFixture.applied.conditions[0].value.join()') == '21'
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Filter creates horizontal page overflow'
        assert not errors, errors
        print('PASS wrapped labels, geometry, titles, search, clear, multiple, template/apply/cancel', width)
        page.close()
    browser.close()
