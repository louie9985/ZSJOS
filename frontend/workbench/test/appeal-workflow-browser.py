# -*- coding: utf-8 -*-
import re
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in [390,1440]:
        page=browser.new_page(viewport={'width':width,'height':1000})
        errors=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto('http://127.0.0.1:5187/test/appeal-workflow.html?error=1')
        expect(page.get_by_text('定位失败测试',exact=True)).to_be_visible()
        expect(page.get_by_role('button',name='通过',exact=True)).to_have_count(0)
        page.get_by_role('button',name=re.compile(r'重\s*试')).click()
        expect(page.get_by_text('申诉测试客资',exact=True)).to_be_visible()
        expect(page.get_by_text('请核查联系记录',exact=True)).to_be_visible()
        expect(page.get_by_role('button',name='改判有效',exact=True)).to_be_visible()
        expect(page.get_by_role('button',name='维持无效',exact=True)).to_be_visible()
        page.get_by_role('textbox',name='裁决理由',exact=True).fill('核实说明')
        expect(page.locator('body')).to_have_attribute('data-dirty','true')
        page.screenshot(path=f'C:/Users/EDY/.codex/appeal-workflow-{width}.png',full_page=True)
        page.goto('http://127.0.0.1:5187/test/appeal-workflow.html?done=1')
        expect(page.get_by_text('申诉测试客资',exact=True)).to_be_visible()
        expect(page.get_by_role('textbox',name='裁决理由',exact=True)).to_have_count(0)
        assert not errors,errors
        page.close()
    browser.close()
print('PASS shared appeal view, task correlation, error/retry, dirty guard, read-only done at desktop/mobile widths')
