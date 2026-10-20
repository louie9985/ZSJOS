# -*- coding: utf-8 -*-
"""Real Chrome interactions with production editors and synthetic HTTP; never deletes real drafts."""
from pathlib import Path
from tempfile import gettempdir
import os
import re
from playwright.sync_api import sync_playwright, expect

URL = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5174') + '/test/viral-tab-retention.html?draftManagement=1'
out = Path(gettempdir()) / 'zsjos-viral-draft-management'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    # Other workstreams may trigger Vite reloads; keep this fixture on its loaded source snapshot.
    page.route_web_socket(re.compile(r'ws://.*'), lambda socket: None)
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))

    def editor(): return page.locator('.standalone-viral-decompose:visible')
    def manager(): return page.get_by_role('dialog', name=re.compile(r'.*草稿管理$'))
    def title(): return editor().get_by_role('textbox', name='验收标题', exact=True)
    def row(text): return manager().get_by_role('row').filter(has_text=text)
    def confirm(): return page.get_by_role('dialog').filter(has=page.get_by_text('确认删除草稿？', exact=True))
    def choose(index):
        editor().get_by_role('combobox', name='选择已保存草稿').click()
        page.locator('.ant-select-item-option-content').filter(has_text=f'已保存{index} · TEST-{index}').click()
    def open_manager():
        editor().get_by_role('button', name='草稿管理', exact=True).click()
        expect(row('另一份草稿')).to_be_visible()
    def set_flag(key, value): page.evaluate('([key,value]) => { if(value) document.documentElement.dataset[key] = value; else delete document.documentElement.dataset[key] }', [key,value])

    for width in [1440, 390]:
        for index, tab in enumerate(['账号拆解', '内容拆解']):
            print('Checking', width, index, flush=True)
            page.set_viewport_size({'width': width, 'height': 900})
            page.goto(URL)
            page.get_by_role('button', name='打开' + tab, exact=True).click()
            choose(index); title().fill('尚未保存的正文标题')
            open_manager()
            expect(row('无删除权限草稿').get_by_role('button', name='删除草稿', exact=True)).to_have_count(0)
            row('另一份草稿').get_by_role('button', name='删除草稿', exact=True).click()
            expect(confirm()).to_contain_text('另一份草稿')
            expect(page.locator('html')).not_to_have_attribute('data-deletes', '1')
            confirm().get_by_role('button', name=re.compile(r'取\s*消')).click()
            expect(row('另一份草稿')).to_be_visible()
            row('另一份草稿').get_by_role('button', name='删除草稿', exact=True).click()
            set_flag('deleteError', '1')
            confirm().get_by_role('button', name='确认删除草稿', exact=True).click()
            expect(page.get_by_text('草稿已被其他人修改，请刷新后重试', exact=True)).to_be_visible()
            expect(row('另一份草稿')).to_be_visible()
            set_flag('deleteError', '')
            confirm().get_by_role('button', name=re.compile(r'取\s*消')).click()
            expect(confirm()).to_have_count(0)
            row('另一份草稿').get_by_role('button', name='删除草稿', exact=True).click()
            confirm().get_by_role('button', name='确认删除草稿', exact=True).click()
            expect(row('另一份草稿')).to_have_count(0)
            manager().get_by_role('button', name='Close', exact=True).click()
            expect(title()).to_have_value('尚未保存的正文标题')
            # Saved draft identity is tracked even after a subsequent save increments its version.
            editor().get_by_role('button', name='保存草稿', exact=True).click()
            expect(page.locator('html')).to_have_attribute('data-pending', 'save')
            page.get_by_role('button', name='完成请求', exact=True).click()
            expect(page.locator('html')).not_to_have_attribute('data-pending', 'save')
            title().fill('再次编辑未保存')
            editor().get_by_role('button', name='草稿管理', exact=True).click()
            expect(row('尚未保存的正文标题')).to_be_visible()
            row('尚未保存的正文标题').get_by_role('button', name='删除草稿', exact=True).click()
            expect(confirm()).to_contain_text('尚未保存的修改将一并丢弃')
            page.wait_for_timeout(250)
            page.screenshot(path=str(out / f'confirm-{index}-{width}.png'))
            confirm().get_by_role('button', name=re.compile(r'取\s*消')).click()
            manager().get_by_role('button', name='Close', exact=True).click()
            expect(title()).to_have_value('再次编辑未保存')
            editor().get_by_role('button', name='草稿管理', exact=True).click()
            row('尚未保存的正文标题').get_by_role('button', name='删除草稿', exact=True).click()
            confirm().get_by_role('button', name='确认删除草稿', exact=True).click()
            expect(row('尚未保存的正文标题')).to_have_count(0)
            manager().get_by_role('button', name='Close', exact=True).click()
            expect(title()).to_have_count(0)
            editor().get_by_role('combobox', name='选择已保存草稿').click()
            expect(page.locator('.ant-select-item-option-content').filter(has_text='TEST-' + str(index))).to_have_count(1)
            page.keyboard.press('Escape')

    # A newly saved create-mode editor must be recognized as the current draft as well.
    page.goto(URL)
    page.get_by_role('button', name='打开账号拆解', exact=True).click()
    editor().get_by_role('button', name='新建拆解', exact=True).click()
    title().fill('新建后保存')
    editor().get_by_role('button', name='保存草稿', exact=True).click()
    expect(page.locator('html')).to_have_attribute('data-pending', 'save')
    page.get_by_role('button', name='完成请求', exact=True).click()
    expect(editor().get_by_role('button', name='草稿管理', exact=True)).to_be_enabled()
    title().fill('新建保存后又编辑')
    open_manager()
    row('新建后保存').get_by_role('button', name='删除草稿', exact=True).click()
    expect(confirm()).to_contain_text('尚未保存的修改将一并丢弃')
    confirm().get_by_role('button', name='确认删除草稿', exact=True).click()
    expect(row('新建后保存')).to_have_count(0)
    manager().get_by_role('button', name='Close', exact=True).click()
    expect(title()).to_have_count(0)

    page.goto(URL)
    page.get_by_role('button', name='打开账号拆解', exact=True).click()
    open_manager()
    set_flag('listError', '500')
    manager().get_by_role('button', name=re.compile(r'刷\s*新')).click()
    expect(manager().get_by_text('验收列表加载失败', exact=True)).to_be_visible()
    set_flag('listError', '')
    manager().get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(row('另一份草稿')).to_be_visible()
    set_flag('listError', '403')
    manager().get_by_role('button', name=re.compile(r'刷\s*新')).click()
    expect(manager().get_by_text('无权查看此表格', exact=True)).to_be_visible()
    expect(manager().get_by_role('button', name='删除草稿', exact=True)).to_have_count(0)
    assert not errors, errors
    browser.close()
print('PASS: both editors at 1440/390px; cancel, delete failure/retry, owner actions, current/other draft, refreshed version, unsaved state, load retry/403; screenshots:', out)
