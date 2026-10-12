# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright,expect
import re,os
from pathlib import Path
out=Path(os.environ['TEMP'])/'points-redemption-browser'
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page(viewport={'width':1280,'height':900})
    page.goto('http://127.0.0.1:5238/test/points-config.html')
    expect(page.get_by_text('5000积分套餐',exact=True)).to_be_visible(timeout=60000)
    page.evaluate('openPointsSecret()')
    dialog=page.get_by_role('dialog')
    secret=dialog.get_by_placeholder('已配置，留空保留原密钥')
    expect(secret).to_have_value('');expect(secret).to_have_attribute('type','password')
    page.screenshot(path=str(out/'secret-config.png'),full_page=True)
    dialog.get_by_role('button',name=re.compile('确.*定')).click()
    page.wait_for_function('pointsConfigCalls.length===1')
    assert page.evaluate('pointsConfigCalls[0].value') == ''
    expect(dialog).not_to_be_visible()
    page.evaluate('openPointsSecret()');dialog.get_by_placeholder('已配置，留空保留原密钥').fill('synthetic-secret-value-for-isolated-testing-only')
    dialog.get_by_role('button',name=re.compile('确.*定')).click();page.wait_for_function('pointsConfigCalls.length===2')
    expect(dialog).not_to_be_visible()
    page.get_by_role('button',name='新增套餐').click();dialog=page.get_by_role('dialog')
    dialog.get_by_label('套餐名称').fill('8000积分套餐');dialog.get_by_label('兑换积分').fill('8000')
    dialog.get_by_role('button',name=re.compile('保.*存')).click();page.wait_for_function('pointsConfigCalls.length===3')
    assert page.evaluate('pointsConfigCalls[2].creditsAmount')==8000
    page.goto('http://127.0.0.1:5238/test/points-config.html?readonly')
    expect(page.get_by_text('5000积分套餐',exact=True)).to_be_visible()
    expect(page.get_by_role('button',name='新增套餐')).to_have_count(0)
    expect(page.get_by_role('button',name='编辑',exact=True)).to_have_count(0)
    page.evaluate('openPointsSecret()')
    expect(page.get_by_placeholder('已配置，留空保留原密钥')).to_be_disabled()
    expect(page.get_by_role('dialog').get_by_role('button',name=re.compile('确.*定'))).to_be_disabled()
    browser.close()
print('PASS actual Vue sensitive config blank-preservation/password input/update and package save')
