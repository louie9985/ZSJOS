# -*- coding: utf-8 -*-
"""Actual media/class page entry buttons, scoped refresh and class preselection; synthetic transport."""
import re
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []; page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto('http://127.0.0.1:5174/test/media-student-filter.html')
    expect(page.get_by_role('button', name=re.compile('添加学员'), exact=False)).to_have_count(0)
    page.goto('http://127.0.0.1:5174/test/media-student-filter.html?entry=1')
    page.set_viewport_size({'width':390,'height':1000})
    expect(page.get_by_role('button', name=re.compile('添加学员'))).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    page.get_by_role('button', name=re.compile('添加学员'), exact=False).click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('添加编导运营学员')).to_be_visible()
    dialog.get_by_label('学员姓名', exact=True).fill('编导入口测试')
    dialog.get_by_label('微信号', exact=True).fill('synthetic-wechat')
    dialog.get_by_label('责任编导', exact=True).click(); page.get_by_title('责任编导甲', exact=True).click()
    before = page.evaluate('mediaFilterFixture.queries.length')
    dialog.get_by_role('button', name=re.compile('添加学员'), exact=False).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    page.wait_for_function('(before) => mediaFilterFixture.queries.length > before', arg=before)
    page.goto('http://127.0.0.1:5174/test/delivery-class-groups.html?entry=1')
    page.get_by_role('button', name='测试卡片布局', exact=True).click()
    card = page.locator('.delivery-class-card').filter(has_text='BJ200')
    card.get_by_role('button', name=re.compile('添加学员'), exact=False).click()
    expect(page.get_by_role('dialog')).to_contain_text('添加教学学员')
    expect(page.get_by_role('dialog')).to_contain_text('批量测试小班')
    page.get_by_role('dialog').get_by_label('学员姓名', exact=True).fill('班级入口测试')
    page.get_by_role('dialog').get_by_label('微信号', exact=True).fill('synthetic-wechat')
    page.get_by_role('dialog').get_by_role('button', name=re.compile('添加学员'), exact=False).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert page.evaluate("classGroupsFixture.calls.filter(c => c.url.endsWith('/student-entry/create')).at(-1).data.classId") == 200
    assert not errors, errors
    browser.close()
print('PASS actual media/class buttons, permission gating, class preselection, submit and refresh')
