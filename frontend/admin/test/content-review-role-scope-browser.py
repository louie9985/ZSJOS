# -*- coding: utf-8 -*-
import os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
out=Path(os.environ['TEMP'])/'content-review-self-select-browser';out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page(viewport={'width':1440,'height':1050})
    page.goto(os.environ.get('CONTENT_ROLE_TEST_URL','http://127.0.0.1:5268/test/content-review-role-scope.html'))
    item=page.locator('.el-form-item').filter(has=page.get_by_text('自选角色范围',exact=True))
    expect(item).to_be_visible()
    expect(item.get_by_text('编导',exact=True)).to_be_visible()
    item.locator('.el-select').click()
    page.get_by_role('option',name='测试审核组',exact=True).click()
    page.locator('.el-drawer__header').click()
    page.screenshot(path=str(out/'admin-role-scope.png'),full_page=True)
    page.get_by_role('button',name='确 定',exact=True).click()
    page.wait_for_function('window.roleScopeFixture.read() === "7,9"')
    page.get_by_role('button',name='编辑审核节点',exact=True).click()
    expect(item.get_by_text('测试审核组',exact=True)).to_be_visible()
    browser.close()
print('PASS actual SIMPLE designer save/reopen role range')
