# -*- coding: utf-8 -*-
"""Grouping review stays visible and clickable in one dialog; isolated transport only."""
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('ZSJOS_CLASS_GROUP_BROWSER_BASE', 'http://127.0.0.1:5226/test/delivery-class-groups.html')
OUT = Path(os.environ.get('TEMP', '.')) / 'zsjos-class-group-confirm'
OUT.mkdir(exist_ok=True)

def check_front(page):
    dialog = page.get_by_role('dialog')
    expect(dialog).to_have_count(1)
    expect(page.locator('.ant-modal-mask:visible')).to_have_count(1)
    expect(dialog.get_by_text('确认调整班级归属', exact=True)).to_be_visible()
    button = dialog.get_by_role('button', name=re.compile('确认调整$'))
    expect(button).to_be_enabled()
    button.click(trial=True)
    button.scroll_into_view_if_needed()
    assert button.evaluate('''el => {
      const box = el.getBoundingClientRect()
      const hit = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
      return hit === el || el.contains(hit)
    }'''), 'review action is obscured by another modal or mask'
    return dialog

def moves(page):
    return page.evaluate("classGroupsFixture.calls.filter(c => c.url.endsWith('/group/move')).length")

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        for layout in ('卡片', '表格') if width == 1440 else ('卡片',):
            page = browser.new_page(viewport={'width': width, 'height': 960})
            errors = []
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.goto(BASE)
            page.get_by_role('button', name=f'测试{layout}布局', exact=True).click()
            rows = page.locator('.delivery-class-card' if layout == '卡片' else '.ant-table-row')
            rows.filter(has_text='BJ200').get_by_role('button', name='调整归组', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_role('combobox', name='选择已有班级组').click()
            page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter(has_text='批量测试').click()
            dialog.get_by_role('button', name=re.compile('核对调整$')).click()
            dialog = check_front(page)
            expect(dialog.get_by_text(re.compile('独立班级 → 批量测试'))).to_be_visible()
            assert moves(page) == 0
            page.screenshot(path=str(OUT / f'review-{layout}-{width}.png'), full_page=False)
            dialog.get_by_role('button', name='返回修改', exact=True).click()
            expect(dialog.get_by_text('已选 1 个班级', exact=True)).to_be_visible()
            expect(dialog.locator('.ant-select').last).to_contain_text('批量测试')
            dialog.get_by_role('button', name=re.compile('核对调整$')).click()
            check_front(page)
            page.evaluate('classGroupsFixture.failMove = true')
            dialog.get_by_role('button', name=re.compile('确认调整$')).click()
            expect(page.get_by_text('班级已变化，请刷新后重试', exact=True)).to_be_visible()
            expect(dialog.get_by_role('button', name=re.compile('核对调整$'))).to_be_visible()
            expect(dialog.get_by_text('已选 1 个班级', exact=True)).to_be_visible()
            assert moves(page) == 1
            page.evaluate('classGroupsFixture.failMove = false')
            dialog.get_by_role('button', name=re.compile('核对调整$')).click()
            dialog = check_front(page)
            dialog.get_by_role('button', name=re.compile('确认调整$')).click()
            expect(page.get_by_role('dialog')).to_have_count(0)
            assert moves(page) == 2
            assert page.evaluate('classGroupsFixture.rows.find(r => r.id === 200).groupId') == 1
            assert page.evaluate('classGroupsFixture.rows.find(r => r.id === 200).homeroomUserId') == 21
            assert not errors, errors
            page.close()
            print('PASS single-class visible/clickable review, back, failure/retry, single submission', width, layout, flush=True)
    browser.close()
