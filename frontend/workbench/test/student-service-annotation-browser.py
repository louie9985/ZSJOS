# -*- coding: utf-8 -*-
"""Actual components and isolated transport; never writes to business APIs."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT=Path(__file__).resolve().parents[1]/'tmp/student-service-annotation'
OUT.mkdir(parents=True,exist_ok=True)
BASE='http://127.0.0.1:5186/test/student-service-annotation.html'
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',args=['--renderer-process-limit=1'])
    def open_page(mode='success',width=1440):
        page=browser.new_page(viewport={'width':width,'height':1000})
        page.goto(BASE+'?mode='+mode)
        return page
    def card(page): return page.locator('.student-annotation-card').filter(visible=True)
    def select(page,label,text):
        box=card(page).get_by_role('combobox',name=label)
        box.click()
        page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter(has_text=text).first.click()
    for width in [1440,390]:
        page=open_page(width=width);errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        expect(card(page).get_by_role('combobox',name='是否为代理学员')).to_be_enabled()
        assert card(page).locator('textarea').count()==0
        assert card(page).evaluate('(e)=>e.previousElementSibling.dataset.testid==="first-contact-progress"')
        select(page,'是否为代理学员','是')
        expect(card(page).get_by_text('请填写代理人后保存')).to_be_visible()
        page.wait_for_timeout(1000);assert page.evaluate('annotationFixture.saves.length')==0
        card(page).get_by_role('textbox',name='代理人').fill('示例代理人')
        expect(card(page).get_by_role('status')).to_have_text('已保存',timeout=10000)
        select(page,'是否需要服务','否')
        expect(card(page).get_by_role('status')).to_have_text('已保存',timeout=10000)
        assert page.evaluate('annotationFixture.saves.at(-1).data.serviceNeeded')=='false'
        card(page).scroll_into_view_if_needed()
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
        assert card(page).evaluate('(e)=>e.scrollWidth<=e.clientWidth+1')
        page.screenshot(path=str(OUT/f'{width}.png'),full_page=True)
        assert not errors,errors
        page.close();print('PASS layout/edit/save',width,flush=True)
    for mode in ['readonly','no-update','no-permission','sales','denied','load-error','dict-error','dict-empty']:
        page=open_page(mode)
        if mode in ['no-permission','sales']:
            page.wait_for_timeout(700);expect(card(page)).to_have_count(0)
            assert page.evaluate('annotationFixture.calls.length')==0
        elif mode in ['readonly','no-update']:
            expect(card(page).get_by_role('status')).to_have_text('只读');expect(card(page).get_by_role('combobox')).to_have_count(0)
        else:
            expect(card(page).locator('.ant-alert')).to_be_visible()
            if mode=='load-error':
                page.evaluate('annotationFixture.mode="success"');card(page).get_by_role('button',name='重试',exact=True).click()
                expect(card(page).get_by_role('combobox',name='是否为代理学员')).to_be_enabled()
        page.close();print('PASS state',mode,flush=True)
    page=open_page('save-error')
    select(page,'是否需要服务','是')
    expect(card(page).get_by_text('未保存，内容已保留')).to_be_visible()
    page.evaluate('annotationFixture.mode="success"');card(page).get_by_role('button',name='重试保存').click()
    expect(card(page).get_by_role('status')).to_have_text('已保存')
    page.evaluate('annotationFixture.mode="conflict"');select(page,'是否需要服务','否')
    expect(card(page).get_by_role('button',name='读取最新')).to_be_visible()
    page.on('dialog',lambda dialog:dialog.dismiss())
    card(page).get_by_role('button',name='读取最新').click()
    expect(card(page).get_by_text('未保存，内容已保留')).to_be_visible()
    page.close();print('PASS save retry/conflict preservation',flush=True)
    page=open_page()
    select(page,'是否需要服务','是');expect(card(page).get_by_role('status')).to_have_text('已保存')
    page.locator('#student-avatar-list button.lead-inbox-item').nth(1).click()
    expect(card(page).get_by_role('status')).to_have_text('修改后自动保存')
    select(page,'是否需要服务','否');expect(card(page).get_by_role('status')).to_have_text('已保存')
    assert page.evaluate('annotationFixture.saves.map(s=>s.id)')==[1,2]
    page.locator('#student-avatar-list button.lead-inbox-item').first.click()
    expect(card(page).get_by_role('combobox',name='是否需要服务').locator('..')).to_contain_text('是')
    page.close();print('PASS per-course isolation',flush=True)
    browser.close()
