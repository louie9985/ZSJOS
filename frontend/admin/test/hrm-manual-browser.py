# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright,expect
from pathlib import Path
import os,re
out=Path(os.environ['TEMP'])/'hrm-manual-browser';out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in [1440,390]:
        page=browser.new_page(viewport={'width':width,'height':1000})
        errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto('http://127.0.0.1:5296/test/hrm-manual.html')
        dialog=page.get_by_role('dialog')
        expect(dialog).to_be_visible(timeout=60000)
        dialog.get_by_role('tab',name='入职信息').click()
        for label in ['聘用形式','入职状态','员工状态']:
            field=dialog.locator('.el-form-item').filter(has=page.locator('label',has_text=re.compile('^'+label+'$')))
            expect(field.locator('.el-select')).not_to_have_class(re.compile('is-disabled'))
        probation=dialog.locator('.el-form-item').filter(has=page.locator('label',has_text='试用期（月）')).get_by_role('spinbutton')
        expect(probation).to_be_enabled();probation.fill('0');probation.press('Tab')
        reason=dialog.locator('.el-form-item').filter(has=page.locator('label',has_text='修改原因')).locator('textarea')
        reason.fill('核实原始资料后纠错')
        page.screenshot(path=str(out/f'employee-{width}.png'),full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'horizontal overflow'
        dialog.get_by_role('button',name=re.compile('确.*定')).click()
        page.wait_for_function('hrmCalls.length===1')
        saved=page.evaluate('hrmCalls[0]')
        assert saved['probation']==0 and saved['status']==2 and saved['changeMode']=='CORRECTION'
        assert saved['version']==0 and saved['operationKey']
        assert not errors,errors
        page.close()
    page=browser.new_page(viewport={'width':1440,'height':1000})
    page.goto('http://127.0.0.1:5296/test/hrm-manual.html?view=plans')
    expect(page.get_by_text('合成转正计划',exact=True)).to_be_visible()
    page.get_by_role('button',name='确认发生').click()
    dialog=page.get_by_role('dialog');expect(dialog).to_be_visible()
    dialog.get_by_role('button',name=re.compile('确.*定')).click()
    page.wait_for_function('hrmCalls.length===1')
    saved=page.evaluate('hrmCalls[0]')
    assert saved['planId']==31 and saved['planVersion']==0 and saved['changeMode']=='BUSINESS'
    page.goto('http://127.0.0.1:5296/test/hrm-manual.html?view=reminders')
    expect(page.get_by_text('计划转正',exact=True)).to_be_visible()
    page.get_by_role('button',name='预览接收人和事项数量').click()
    expect(page.get_by_text('当前接收 1 人，待处理 1 项')).to_be_visible()
    page.get_by_role('button',name='保存人事提醒配置').click()
    page.wait_for_function('hrmCalls.length===1')
    assert page.evaluate('hrmCalls[0].regular.advanceDays')==1
    page.screenshot(path=str(out/'reminder-desktop.png'),full_page=True)
    page.set_viewport_size({'width':390,'height':1000})
    page.screenshot(path=str(out/'reminder-mobile.png'),full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    page.goto('http://127.0.0.1:5296/test/hrm-manual.html?failed')
    expect(page.get_by_role('dialog').get_by_text('合成加载失败',exact=True)).to_be_visible()
    expect(page.get_by_role('dialog').get_by_role('button',name=re.compile('确.*定'))).to_be_disabled()
    browser.close()
print('PASS desktop/mobile actual Vue form: editable fields, explicit correction, manual status, version/key payload')
