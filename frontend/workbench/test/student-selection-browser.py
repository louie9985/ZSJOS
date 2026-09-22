# -*- coding: utf-8 -*-
"""Production-page regression with synthetic transport, including delayed responses."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('STUDENT_SELECTION_BASE', 'http://127.0.0.1:5174') + '/test/student-selection.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1280, 'height': 900})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def reset():
        page.goto(BASE)
        expect(page.get_by_text('学员档案', exact=True)).to_be_visible()
        expect(assign()).to_be_enabled()

    def assign():
        return page.get_by_role('button', name=re.compile('指派运营$')).filter(visible=True)

    def search(value):
        box = page.get_by_placeholder('搜索姓名或手机号')
        box.fill(value)
        box.press('Enter')

    def selected(name, number):
        expect(page.locator('.media-students-item[aria-current=true]')).to_have_attribute('aria-label', f'{name} · TEST-{number}')
        expect(page.get_by_text('学员档案', exact=True)).to_be_visible()
        expect(page.locator('.lead-profile-row').filter(has_text='学员编号')).to_contain_text(f'TEST-{number}')
        expect(assign()).to_be_enabled()

    def submit(name, number):
        assign().click()
        dialog = page.get_by_role('dialog')
        expect(dialog.locator('.ant-form-item').filter(has_text='当前学员')).to_contain_text(name)
        expect(dialog.locator('.ant-form-item').filter(has_text='学员编号')).to_contain_text(f'TEST-{number}')
        expect(dialog.locator('.ant-form-item').filter(has_text='当前运营')).to_contain_text(f'原运营{number}')
        dialog.get_by_role('combobox').click()
        page.get_by_title('新运营', exact=True).click()
        dialog.get_by_role('textbox').fill('测试交接')
        dialog.get_by_role('button', name='确认指派').click()
        expect(dialog).not_to_be_visible()
        assert page.evaluate('selectionFixture.writes.at(-1).url') == f'/zsjos/student/service/{number * 10}/collaborators'
        selected(name, number)

    page.goto(BASE + '?route=/zsjos/media-students?personId=3')
    selected('测试乙', 3)
    expect(page.locator('#fixture-route')).to_have_text('?personId=3')
    print('PASS external student link under React StrictMode', flush=True)

    reset()
    search('王静'); selected('王静', 2)
    expect(page.locator('#fixture-route')).to_have_text('?personId=2')
    submit('王静', 2)
    search(''); selected('王静', 2)  # Still in the new result: preserve selection.
    page.get_by_role('button', name='测试乙 · TEST-3', exact=True).click()
    selected('测试乙', 3); submit('测试乙', 3)
    print('PASS search/direct assignment, preserved selection, click/assignment', flush=True)

    reset()
    page.evaluate("selectionFixture.delays['search:王静']=900")
    search('王静')
    expect(assign()).to_be_disabled()
    page.get_by_role('button', name='测试乙 · TEST-3', exact=True).click()
    selected('测试乙', 3)
    page.wait_for_function("selectionFixture.finished.includes('search:王静')")
    selected('测试乙', 3)
    expect(page.locator('.media-students-item')).to_have_count(3)
    submit('测试乙', 3)
    print('PASS older search cannot override a click', flush=True)

    reset()
    page.evaluate("selectionFixture.delays['search:王静']=900")
    search('王静'); search('测试乙'); selected('测试乙', 3)
    page.wait_for_function("selectionFixture.finished.includes('search:王静')")
    selected('测试乙', 3)
    submit('测试乙', 3)
    print('PASS searches resolve out of order', flush=True)

    reset()
    page.evaluate("selectionFixture.delays['/zsjos/media-students/2']=900")
    page.get_by_role('button', name='王静 · TEST-2', exact=True).click()
    page.wait_for_function("selectionFixture.started.includes('/zsjos/media-students/2')")
    expect(assign()).to_have_count(0)
    search('不存在')
    expect(page.get_by_text('从左侧选择一名学员', exact=True)).to_be_visible()
    page.wait_for_function("selectionFixture.finished.includes('/zsjos/media-students/2')")
    expect(page.get_by_text('从左侧选择一名学员', exact=True)).to_be_visible()
    expect(page.locator('.media-students-item')).to_have_count(0)
    expect(assign()).to_have_count(0)
    expect(page.locator('#fixture-route')).to_have_text('')
    assert page.evaluate('selectionFixture.writes.length') == 0
    print('PASS empty result invalidates pending detail and removes actions', flush=True)

    reset()
    page.evaluate("selectionFixture.delays['/zsjos/media-students/2']=900")
    page.get_by_role('button', name='王静 · TEST-2', exact=True).click()
    page.wait_for_function("selectionFixture.started.includes('/zsjos/media-students/2')")
    page.get_by_role('button', name='测试乙 · TEST-3', exact=True).click()
    selected('测试乙', 3)
    page.wait_for_function("selectionFixture.finished.includes('/zsjos/media-students/2')")
    selected('测试乙', 3)
    print('PASS details resolve out of order', flush=True)

    reset()
    page.evaluate("selectionFixture.failures.push('/zsjos/media-students/2')")
    search('王静')
    expect(page.get_by_text('测试加载失败', exact=True)).to_be_visible()
    expect(assign()).to_have_count(0)
    page.evaluate('selectionFixture.failures=[]')
    page.get_by_role('button', name=re.compile(r'^重\s*试$')).click()
    selected('王静', 2)
    print('PASS detail failure and retry', flush=True)

    reset()
    assign().click()
    expect(page.get_by_role('dialog').get_by_text('TEST-1', exact=True)).to_be_visible()
    # Exercise the existing navigation guard without bypassing its business decision.
    page.get_by_role('button', name='王静 · TEST-2', exact=True).dispatch_event('click')
    confirmation = page.get_by_role('dialog').filter(has_text='学员页面有正在编辑的内容')
    confirmation.get_by_role('button', name='继续编辑', exact=True).click()
    expect(confirmation).to_have_count(0)
    expect(page.get_by_role('dialog').get_by_text('TEST-1', exact=True)).to_be_visible()
    page.get_by_role('button', name='王静 · TEST-2', exact=True).dispatch_event('click')
    confirmation.get_by_role('button', name='放弃并切换', exact=True).click()
    selected('王静', 2)
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert page.evaluate('selectionFixture.writes.length') == 0
    print('PASS assignment target retained on cancelled navigation and closed on switch', flush=True)

    page.goto(BASE + '?many=1')
    selected('测试甲', 1)
    expect(page.locator('.media-students-item')).to_have_count(20)
    page.evaluate("selectionFixture.delays['search:王静']=900")
    search('王静')
    page.get_by_role('button', name='测试乙 · TEST-3', exact=True).click()
    selected('测试乙', 3)
    page.wait_for_function("selectionFixture.finished.includes('search:王静')")
    page.locator('.media-students-scroll').evaluate('(element) => element.scrollTop = element.scrollHeight')
    expect(page.locator('.media-students-item')).to_have_count(25)
    selected('测试乙', 3)
    print('PASS pagination retains the displayed query after cancelling a stale search', flush=True)

    # An old student's account URL must not be used for the new search target.
    page.goto(BASE + '?route=/zsjos/media-students?personId=1%26accountId=999')
    expect(page.get_by_text('该账号已失效、不属于此学员或你无权查看；请返回审批核对账号。')).to_be_visible()
    search('王静'); selected('王静', 2)
    expect(page.locator('#fixture-route')).to_have_text('?personId=2')
    assign().click()
    expect(page.get_by_role('dialog').get_by_text('TEST-2', exact=True)).to_be_visible()
    page.screenshot(path=str(Path(gettempdir()) / 'student-selection-desktop.png'))
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(page.get_by_role('dialog').get_by_text('TEST-2', exact=True)).to_be_visible()
    page.screenshot(path=str(Path(gettempdir()) / 'student-selection-mobile.png'))
    assert not errors, errors
    print('PASS stale account parameter removal and desktop/mobile dialog; no page errors', flush=True)
    browser.close()
