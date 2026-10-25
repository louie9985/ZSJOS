# -*- coding: utf-8 -*-
"""Production pages with isolated transport; never changes live records."""
from pathlib import Path
from tempfile import gettempdir
import re
import os
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir())/'zsjos-unscheduled-browser'
OUT.mkdir(exist_ok=True)
WB = os.environ.get('WB_TEST_ORIGIN', 'http://127.0.0.1:5174') + '/test/planner-unscheduled-class.html'
ADMIN = 'http://127.0.0.1:5215'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome',headless=True)
    page = browser.new_page(viewport={'width':1440,'height':960})
    errors=[]
    page.on('pageerror',lambda error:errors.append(str(error)))
    for width in (1440,390):
        page.set_viewport_size({'width':width,'height':960})
        page.goto(WB+'?classes')
        row=page.locator('tr.ant-table-row' if width==1440 else '.delivery-class-card').filter(has_text='未确定考期')
        expect(row).to_contain_text('未确定考期');expect(row).to_contain_text('规划师甲')
        expect(row.get_by_role('button',name='编辑班级')).to_have_count(0)
        expect(row.get_by_role('button',name='结课')).to_have_count(0)
        page.screenshot(path=str(OUT/f'workbench-class-{width}.png'),full_page=True)
        page.goto(ADMIN+'?classes')
        row=page.locator('.el-table__row').filter(has_text='US20')
        expect(row).to_contain_text('未确定考期');expect(row).to_contain_text('规划师甲')
        expect(row.get_by_role('button')).to_have_count(0)
        page.screenshot(path=str(OUT/f'admin-class-{width}.png'),full_page=True)
        page.goto(ADMIN)
        page.get_by_role('button',name='查看',exact=True).click()
        drawer=page.locator('.el-drawer')
        drawer.locator('.el-select').click()
        option=page.locator('.el-select-dropdown__item').filter(has_text='未确定考期 · 规划师甲')
        expect(option).to_be_visible();option.click()
        drawer.get_by_role('button',name='保存分班',exact=True).click()
        page.wait_for_function('unscheduledFixture.requests.length===1')
        assert page.evaluate('unscheduledFixture.requests[0].assignments[0].classId')==2
        page.screenshot(path=str(OUT/f'admin-registration-{width}.png'),full_page=True)
        print('PASS both class pages/owner/immutable and Admin enrollment',width,flush=True)
    page.set_viewport_size({'width':1440,'height':960})
    page.goto(WB)
    page.get_by_role('combobox').last.click()
    page.locator('.ant-select-item-option').filter(has_text='未确定考期 · 规划师甲').click()
    page.get_by_role('button',name='保存分班',exact=True).click()
    page.wait_for_function('unscheduledFixture.requests.some(r=>Array.isArray(r))')
    assert page.evaluate('unscheduledFixture.requests.find(r=>Array.isArray(r))[0].classId')==2
    page.screenshot(path=str(OUT/'workbench-registration-1440.png'),full_page=True)
    page.goto(WB+'?transfer')
    page.get_by_role('button',name='调班',exact=True).click()
    dialog=page.get_by_role('dialog')
    dialog.get_by_role('combobox').click()
    expect(page.locator('.ant-select-item-option').filter(has_text='未确定考期')).to_be_visible()
    expect(page.locator('.ant-select-item-option').filter(has_text='待分班')).to_have_count(0)
    assert page.evaluate('unscheduledFixture.requests[0].assignableOnly') is True
    page.goto(WB+'?restore')
    page.get_by_role('button',name='恢复服务',exact=True).click()
    expect(page.get_by_role('dialog').locator('.ant-select')).to_contain_text('未确定考期')
    page.goto(WB+'?denied')
    expect(page.get_by_role('button',name='保存分班',exact=True)).to_have_count(0)
    page.goto(ADMIN+'?denied')
    page.get_by_role('button',name='查看',exact=True).click()
    expect(page.get_by_role('button',name='保存分班',exact=True)).to_have_count(0)
    assert not errors,errors
    browser.close()
print('PASS Workbench enrollment, target query/restore, dual-client denied; screenshots '+str(OUT),flush=True)
