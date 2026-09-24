# -*- coding: utf-8 -*-
"""Exercise production tab retention and editors against synthetic transport."""
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

URL = 'http://127.0.0.1:5174/test/viral-tab-retention.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    def open_tab(name):
        page.get_by_role('button', name='打开' + name, exact=True).click()
        expect(page.get_by_role('tab', name=name, exact=True)).to_have_attribute('aria-selected', 'true')
        page.evaluate('() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))')
    def close_tab(name):
        page.locator('.ant-tabs-tab').filter(has=page.get_by_role('tab', name=name, exact=True)).get_by_role('button', name='remove').click()
    def editor():
        return page.locator('.standalone-viral-decompose:visible')
    def title():
        return editor().get_by_role('textbox', name='验收标题', exact=True)
    def choose_draft(index):
        editor().get_by_role('combobox', name='选择已保存草稿').click()
        page.locator('.ant-select-item-option-content').filter(has_text=f'TEST-{index}').click()
    def unload_blocked():
        return page.evaluate("""() => { const event = new Event('beforeunload', { cancelable: true }); window.dispatchEvent(event); return event.defaultPrevented }""")

    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        page.goto(URL)
        open_tab('账号拆解'); choose_draft(0)
        title().fill('账号未保存')
        editor().get_by_role('textbox', name='记录正文').fill('重复组未保存')
        scroll = editor().locator('.viral-account-section-body').last
        scroll.evaluate('(el) => { el.scrollTop = 150 }')
        scroll_top = scroll.evaluate('(el) => el.scrollTop')
        open_tab('内容拆解')
        editor().get_by_role('button', name='新建拆解').click()
        title().fill('内容新建未保存')
        open_tab('首页'); open_tab('账号拆解')
        expect(title()).to_have_value('账号未保存')
        expect(editor().get_by_role('textbox', name='记录正文')).to_have_value('重复组未保存')
        assert scroll.evaluate('(el) => el.scrollTop') == scroll_top
        collapse = editor().get_by_role('button', name='折叠分组')
        collapse.click()
        open_tab('首页'); open_tab('账号拆解')
        expect(collapse).to_have_attribute('aria-expanded', 'false')
        expect(editor().get_by_role('img', name='账号主页截图')).to_be_visible()
        expect(page.locator('html')).to_have_attribute('data-loads', '2')
        assert unload_blocked()
        close_tab('账号拆解')
        page.get_by_role('button', name='继续填写', exact=True).click()
        expect(title()).to_have_value('账号未保存')
        open_tab('内容拆解'); expect(title()).to_have_value('内容新建未保存')
        # Popups are owned by the retained page and disappear when it is hidden.
        editor().get_by_role('combobox', name='选择已保存草稿').click()
        open_tab('首页')
        expect(page.locator('.ant-select-dropdown:visible')).to_have_count(0)
        open_tab('内容拆解')
        page.keyboard.press('Escape')
        title().click()
        page.screenshot(path=str(Path(gettempdir()) / f'viral-retention-{width}.png'), full_page=True)
        close_tab('账号拆解')  # Closing a hidden dirty editor also asks.
        page.get_by_role('button', name='放弃修改', exact=True).click()
        open_tab('账号拆解')
        expect(editor().get_by_role('textbox', name='验收标题')).to_have_count(0)
        choose_draft(0); expect(title()).to_have_value('已保存0')
        open_tab('内容拆解')
        editor().get_by_role('button', name='刷新草稿列表').click()
        page.get_by_role('button', name='放弃修改', exact=True).click()
        choose_draft(1)
        title().fill('已有内容草稿未保存')
        open_tab('账号拆解'); open_tab('内容拆解')
        expect(title()).to_have_value('已有内容草稿未保存')
        # Existing retained routes still keep their instances.
        for name, path in [('内容审核', '/zsjos/material-library/content-review'), ('媒体学员', '/zsjos/media-students')]:
            open_tab(name)
            page.get_by_role('textbox', name=path, exact=True).fill('保留状态')
            open_tab('首页'); open_tab(name)
            expect(page.get_by_role('textbox', name=path, exact=True)).to_have_value('保留状态')

    page.goto(URL)
    open_tab('账号拆解'); choose_draft(0)
    title().fill('本次保存')
    editor().get_by_role('button', name='保存草稿').click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    title().fill('保存期间新增')
    close_tab('账号拆解')
    expect(page.get_by_text('正在保存或上传，请完成后再关闭或更换草稿')).to_be_visible()
    expect(page.get_by_role('dialog')).to_have_count(0)
    open_tab('首页')
    expect(page.locator('.ant-spin-fullscreen:visible')).to_have_count(0)
    page.get_by_role('button', name='完成请求').click()
    open_tab('账号拆解'); expect(title()).to_have_value('保存期间新增')
    assert unload_blocked()
    close_tab('账号拆解'); page.get_by_role('button', name='继续填写').click()
    editor().get_by_role('button', name='保存草稿').click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    page.get_by_role('button', name='完成请求').click()
    expect(editor().get_by_role('button', name='新建拆解')).to_be_enabled()
    assert not unload_blocked()
    title().fill('失败保留')
    page.get_by_role('button', name='切换保存失败').click()
    editor().get_by_role('button', name='保存草稿').click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    page.get_by_role('button', name='完成请求').click()
    expect(editor().get_by_text('验收保存失败')).to_be_visible()
    assert unload_blocked()
    expect(title()).to_have_value('失败保留')
    # Upload can finish off-screen without destroying the editor or covering another page.
    editor().get_by_role('button', name='删除图片').click()
    editor().locator('input[type=file]').set_input_files({'name': 'test.png', 'mimeType': 'image/png', 'buffer': b'fixture'})
    expect(page.locator('html')).to_have_attribute('data-pending', 'upload')
    close_tab('账号拆解'); expect(page.get_by_role('dialog')).to_have_count(0)
    open_tab('首页'); page.get_by_role('button', name='完成请求').click()
    open_tab('账号拆解'); expect(editor().get_by_role('img', name='账号主页截图')).to_be_visible()
    # Confirm browser-native refresh warning is registered.
    dialogs = []
    def dismiss_reload(dialog):
        dialogs.append(dialog.type); dialog.dismiss()
    page.on('dialog', dismiss_reload)
    try:
        page.reload(timeout=3000)
    except Exception:
        pass
    page.remove_listener('dialog', dismiss_reload)
    assert dialogs == ['beforeunload'], dialogs
    expect(title()).to_have_value('失败保留')
    page.get_by_role('button', name='更换身份').click()
    expect(editor().get_by_role('textbox', name='验收标题')).to_have_count(0)
    assert not unload_blocked()
    choose_draft(0)
    title().fill('授权失效前保存')
    editor().get_by_role('button', name='保存草稿').click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    page.get_by_role('button', name='切换授权').click()
    expect(page.locator('.standalone-viral-decompose')).to_have_count(0)
    page.get_by_role('button', name='完成请求').click()
    assert not unload_blocked()
    # Newly created material identity survives tab switches and subsequent saves update it.
    page.goto(URL)
    open_tab('内容拆解'); editor().get_by_role('button', name='新建拆解').click()
    title().fill('新内容保存')
    editor().get_by_role('button', name='保存草稿').click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    page.get_by_role('button', name='完成请求').click()
    expect(editor().get_by_role('button', name='新建拆解')).to_be_enabled()
    open_tab('首页'); open_tab('内容拆解')
    title().fill('新内容再次保存')
    editor().get_by_role('button', name='保存草稿').click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    expect(page.locator('html')).to_have_attribute('data-last-write', '/zsjos/material/42')
    page.get_by_role('button', name='完成请求').click()
    expect(editor().get_by_role('button', name='新建拆解')).to_be_enabled()
    assert not unload_blocked()
    close_tab('内容拆解')
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert not errors, errors
    print('PASS: desktop/mobile retention, draft/new, repeat fields, image, scroll, popup, close, save race/failure, upload, native reload, identity/permission disposal; existing retained route instances')
    browser.close()
