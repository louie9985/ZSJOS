# -*- coding: utf-8 -*-
"""Synthetic browser transport; never modifies business data or real grants."""
import os, tempfile, json, re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
BASE=os.environ.get('WORKBENCH_TEST_URL','http://127.0.0.1:5174')
OUT=Path(tempfile.mkdtemp(prefix='home-rest-days-browser-'))
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in [390,1440]:
        context=browser.new_context(viewport={'width':width,'height':1000})
        context.route('**/admin-api/**',lambda route:route.abort())
        page=context.new_page()
        errors=[]
        page.on('pageerror',lambda error:errors.append(str(error)))
        page.goto(BASE+'/test/home-rest-days.html')
        expect(page.locator('.home-rest-date.is-rest')).to_have_count(1)
        mark=page.locator('.home-rest-date.is-rest').first
        style=mark.evaluate('(el)=>{const s=getComputedStyle(el);return {bg:s.backgroundColor,fg:s.color,r:s.borderRadius,w:el.offsetWidth,h:el.offsetHeight}}')
        assert style['bg']=='rgb(207, 19, 34)' and style['fg']=='rgb(255, 255, 255)' and style['w']==style['h'],style
        panel=page.locator('.home-calendar-panel')
        geometry=panel.evaluate('(el)=>({h:el.clientHeight,scroll:el.scrollHeight})')
        assert geometry['scroll']<=geometry['h']+2,geometry
        expect(page.get_by_role('button',name='查看完整日历')).to_have_count(0)
        before_url=page.url
        panel.locator('.ant-picker-cell-in-view .home-rest-date').first.click()
        assert page.url==before_url
        bottom_gap=panel.evaluate('(el)=>el.getBoundingClientRect().bottom-el.querySelector(".ant-picker-content").getBoundingClientRect().bottom')
        assert bottom_gap < 35, bottom_gap
        page.get_by_role('button',name='管理休息日').click()
        dialog=page.get_by_role('dialog')
        expect(dialog).to_be_visible()
        candidates=dialog.locator('.ant-picker-cell-in-view .home-rest-date:not(.is-rest)')
        chosen=candidates.first.get_attribute('aria-label').split()[0]
        candidates.first.click()
        dialog.get_by_role('button',name='标记休息',exact=True).click()
        dialog.get_by_role('button',name='下个月').click()
        page.wait_for_timeout(200)
        nextdate=dialog.locator('.ant-picker-cell-in-view .home-rest-date:not(.is-rest)').first
        nextkey=nextdate.get_attribute('aria-label').split()[0]
        nextdate.click()
        dialog.get_by_role('button',name='标记休息',exact=True).click()
        expect(dialog.get_by_label('待保存日期')).to_contain_text(chosen)
        expect(dialog.get_by_label('待保存日期')).to_contain_text(nextkey)
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUT/f'{width}-manager.png'),full_page=True)
        dialog.get_by_role('button',name='保存修改').click()
        expect(dialog).not_to_be_visible()
        page.reload()
        expect(page.locator('.home-calendar-panel .ant-picker-cell-in-view .home-rest-date.is-rest')).to_have_count(2)
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUT/f'{width}-home.png'),full_page=True)
        # Another viewer reads the same synthetic backend after opening the homepage.
        viewer=context.new_page(); viewer.goto(BASE+'/test/home-rest-days.html?readonly')
        expect(viewer.get_by_role('button',name='管理休息日')).to_have_count(0)
        expect(viewer.locator('.ant-picker-cell-in-view .home-rest-date.is-rest')).to_have_count(2)
        assert not errors,errors
        context.close()
        print('PASS',width,'multi-month, save, shared read, geometry',flush=True)
    context=browser.new_context(viewport={'width':390,'height':950})
    page=context.new_page();page.goto(BASE+'/test/home-rest-days.html?conflict')
    page.get_by_role('button',name='管理休息日').click();dialog=page.get_by_role('dialog')
    dialog.locator('.ant-picker-cell-in-view .home-rest-date:not(.is-rest)').first.click()
    dialog.get_by_role('button',name='标记休息',exact=True).click();dialog.get_by_role('button',name='保存修改').click()
    expect(dialog.get_by_role('button',name='刷新并核对')).to_be_visible()
    expect(dialog.get_by_role('button',name='保存修改')).to_be_disabled()
    dialog.get_by_role('button',name='刷新并核对').click()
    expect(dialog.get_by_role('button',name='保存修改')).to_be_enabled()
    dialog.get_by_role('button',name='保存修改').click();expect(dialog).not_to_be_visible()
    page.goto(BASE+'/test/home-rest-days.html?loadfail')
    expect(page.get_by_text(re.compile('休息日加载失败'))).to_be_visible()
    page.goto(BASE+'/test/home-rest-days.html?denied')
    expect(page.get_by_text('无权访问休息日，请联系管理员')).to_be_visible()
    expect(page.get_by_role('button',name='管理休息日')).to_have_count(0)
    context.close();browser.close()
print('PASS conflict recovery and error/denied states; screenshots',OUT)
