# UTF-8. Real Chrome, isolated adapter; no account or service mutations.
import re
import os
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.gettempdir()) / 'zsjos-subordinate-lead-sort'
out.mkdir(exist_ok=True)
base = os.environ.get('SUBORDINATE_SORT_BASE', 'http://127.0.0.1:5174')
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(base + '/test/subordinate-lead-sort.html')
    page.get_by_text('排序测试员工', exact=True).click()
    page.get_by_role('tab', name='名下客资').click()
    log = page.locator('#request')
    expect(log).to_contain_text('pageNo')
    table = page.locator('.ant-table-tbody')
    expect(table.get_by_role('button', name='测试42', exact=True)).to_be_visible()
    page.locator('.ant-pagination-item-2').click()
    expect(log).to_contain_text('"pageNo":2')
    # Sort via actual native BusinessTable headers; pagination must reset.
    page.locator('th').filter(has_text='姓名').click()
    expect(log).to_contain_text('"sortField":"submittedName"')
    expect(log).to_contain_text('"sortOrder":"ascend"')
    expect(log).to_contain_text('"pageNo":1')
    expect(table.get_by_role('button', name='测试01', exact=True)).to_be_visible()
    page.locator('.ant-pagination-item-2').click()
    expect(log).to_contain_text('"pageNo":2')
    expect(log).to_contain_text('"sortOrder":"ascend"')
    expect(table.get_by_role('button', name='测试21', exact=True)).to_be_visible()
    page.locator('th').filter(has_text='姓名').click()
    expect(log).to_contain_text('"sortOrder":"descend"')
    expect(log).to_contain_text('"pageNo":1')
    page.get_by_role('button', name='回收待处理', exact=True).click()
    expect(log).to_contain_text('"recycledOnly":true')
    expect(log).to_contain_text('"sortField":"submittedName"')
    page.get_by_role('button', name='选择名下客资排序').click()
    page.get_by_role('menuitem', name='默认排序', exact=True).click()
    expect(log).not_to_contain_text('sortField')
    # Check additional date fields and failure/retry through the real menu.
    page.get_by_role('button', name='下次查询失败').click()
    page.get_by_role('button', name='选择名下客资排序').click()
    page.wait_for_timeout(500)

    page.locator('.ant-dropdown:visible .ant-dropdown-menu-submenu-title').filter(has_text='下次跟进时间').hover()
    page.get_by_role('menuitem', name='升序', exact=True).click()
    expect(page.get_by_text('模拟排序加载失败', exact=True)).to_be_visible()
    page.locator('.ant-alert').filter(has_text='模拟排序加载失败').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(page.get_by_text('模拟排序加载失败', exact=True)).to_have_count(0)
    expect(log).to_contain_text('"sortField":"nextFollowUpAt"')
    expect(log).to_contain_text('"sortOrder":"ascend"')
    for width in (1440, 390):
        page.set_viewport_size({'width': width, 'height': 1000})
        page.get_by_role('button', name='选择名下客资排序').click()
        page.locator('.ant-dropdown:visible .ant-dropdown-menu-submenu-title').filter(has_text='首跟截止时间').hover()
        page.get_by_role('menuitem', name='降序', exact=True).click()
        expect(log).to_contain_text('"sortField":"currentAssignmentFirstFollowUpDeadlineAt"')
        expect(log).to_contain_text('"sortOrder":"descend"')
        expect(page.get_by_role('button', name='选择名下客资排序')).to_be_visible()
        page.screenshot(path=str(out / f'{width}.png'), full_page=True)
    assert not errors, errors
    print('PASS: headers/menu, asc/desc/default, global page slices, reset/persist, recycled view, error/retry, 1440/390; no page errors')
    browser.close()



