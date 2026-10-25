# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
import re
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in [1920,1440,390]:
        page=browser.new_page(viewport={'width':width,'height':1100})
        page.set_default_timeout(12000)
        page.goto('http://127.0.0.1:5186/test/zsjkqt.html?home-rest',timeout=60000)
        page.get_by_role('button',name=re.compile('新建考勤$')).click()
        page.locator('input[type=file]').first.set_input_files({'name':'synthetic.xlsx','mimeType':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','buffer':b'fixture'})
        panel=page.locator('.monthly-parameters-panel')
        expect(panel).to_be_visible()
        page.get_by_role('button',name='从首页导入休息日',exact=True).click()
        expect(panel.locator('p[role=status]')).to_contain_text('已读取 3')
        select=page.locator('.home-rest-import-select')
        select.scroll_into_view_if_needed()
        geometry=select.evaluate("""el => {const r=document.createRange();r.selectNodeContents(el.querySelector('.ant-select-content').firstChild);const t=r.getBoundingClientRect(),b=el.getBoundingClientRect();return {textTop:t.top, textBottom:t.bottom,top:b.top,bottom:b.bottom}}""")
        assert geometry['textTop']>=geometry['top'] and geometry['textBottom']<=geometry['bottom'],geometry
        controls=page.locator('.monthly-parameters-controls').bounding_box()
        calendar=page.locator('.rest-calendar').bounding_box()
        if width>=1440: assert calendar['x']>controls['x']+controls['width'] and abs(calendar['y']-controls['y'])<2
        else: assert calendar['y']>=controls['y']+controls['height']
        select.get_by_role('combobox').click()
        option=page.locator('.ant-select-item-option').filter(has_text='带薪休息日')
        expect(option).to_be_visible()
        page.wait_for_timeout(350)
        page.screenshot(path=f'C:/Users/EDY/AppData/Local/Temp/zsjkqt-parameters-open-{width}.png',full_page=True)
        option.click()
        expect(select).to_contain_text('带薪休息日')
        page.locator('.day-toggle').filter(has=page.locator('span',has_text=re.compile('^5$'))).click()
        page.get_by_role('heading',name='2026年6月参数确认',exact=True).click()
        page.wait_for_timeout(350)
        for field in page.locator('.settings-grid input').all():
            box=field.bounding_box()
            assert box['x']+box['width']<=width,box
        page.screenshot(path=f'C:/Users/EDY/AppData/Local/Temp/zsjkqt-parameters-{width}.png',full_page=True)
        print({'width':width,'text':geometry,'calendar':calendar})
        page.close()
    browser.close()
