# -*- coding: utf-8 -*-
"""Actual React panel, isolated synthetic HTTP, Chrome desktop/mobile checks."""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright,expect

OUTPUT=Path(gettempdir())/'zsjos-weekly-diagnosis'
OUTPUT.mkdir(exist_ok=True)
ORIGIN=os.environ.get('ZSJOS_WEEKLY_TEST_ORIGIN','http://127.0.0.1:5174')
URL=ORIGIN+'/test/weekly-account-diagnosis.html?panel&diagnosis&early-diagnosis'
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width,height in [(1440,1000),(390,844)]:
        page=browser.new_page(viewport={'width':width,'height':height})
        page.set_default_timeout(60000)
        errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
        page.goto(URL+('&unbound' if width==390 else ''),wait_until='domcontentloaded',timeout=60000)
        review=page.locator('[data-profile-section="REVIEW"]').first
        expect(page.get_by_role('button',name='填写诊断表',exact=True)).to_have_count(1)
        expect(review.locator('.account-profile-section-heading').get_by_role('button',name='填写诊断表',exact=True)).to_be_visible()
        expect(page.get_by_role('button',name='填写诊断',exact=True)).to_have_count(0)
        expect(page.get_by_role('button',name=re.compile('基于最新记录修订$'))).to_have_count(0)
        expect(review.get_by_role('button',name=re.compile('查看本项历史$'))).to_have_count(3)
        # Unequal requirement lengths must not move the three desktop history actions apart.
        review.locator('[data-profile-key="diagnosis_14d"] > div:nth-child(2) > .ant-typography-secondary').evaluate("element => { element.textContent += ' 需要记录本周期内容方向和流量验证证据。'.repeat(8); }")
        history_buttons=review.get_by_role('button',name=re.compile('查看本项历史$'))
        if width==1440:
            tops=[history_buttons.nth(i).bounding_box()['y'] for i in range(3)]
            assert max(tops)-min(tops)<=1,tops
        assert review.evaluate('(element)=>element.scrollWidth<=element.clientWidth')
        review.locator('.account-profile-section-heading').scroll_into_view_if_needed();page.wait_for_timeout(350)
        page.screenshot(path=str(OUTPUT/f'weekly-entry-{width}.png'))
        page.get_by_role('button',name='填写诊断表',exact=True).click()
        dialog=page.get_by_role('dialog')
        expect(dialog.get_by_text('通用诊断（本次各周期共用，填写一次）',exact=True)).to_be_visible()
        page.wait_for_timeout(350) # Finish Ant Design's modal entrance before visual inspection.
        assert dialog.get_by_role('heading',name='7天诊断',exact=False).count()==1
        assert dialog.get_by_role('heading',name='14天诊断',exact=False).count()==1
        assert dialog.get_by_role('heading',name='28天诊断',exact=False).count()==1
        assert dialog.locator('.ant-modal-body').evaluate('(el)=>el.scrollWidth<=el.clientWidth')
        assert dialog.bounding_box()['width']<=width-32
        page.screenshot(path=str(OUTPUT/f'weekly-{width}-top.png'))
        page.get_by_role('button',name='提交诊断',exact=True).click()
        expect(dialog.get_by_text('请填写方向概况',exact=True)).to_be_visible()
        def choose(name,label):
            control=dialog.get_by_label(name,exact=True);control.click()
            dropdown=page.locator('[id="'+control.get_attribute('aria-controls')+'"]').locator('..')
            dropdown.locator('.ant-select-item-option-content').filter(has_text=label).click()
        for name in ['当前阶段','账号状态','主要瓶颈','次要瓶颈','学员配合等级']:choose(name,'测试历史标签')
        choose('是否需要换内容方向测试','是');choose('流量是否正常','否')
        for name in ['配合等级证据','主要瓶颈证据','次要瓶颈证据','一句话诊断结论','改进措施','重点观测数据']:dialog.get_by_label(name,exact=True).fill('周期内事实与证据')
        dialog.get_by_label('方向一句话概况',exact=True).fill('围绕专业知识进行内容测试')
        dialog.get_by_label('7天内最高点赞',exact=False).fill('0')
        dialog.locator('.ant-modal-body').evaluate('(el)=>el.scrollTop=el.scrollHeight')
        page.screenshot(path=str(OUTPUT/f'weekly-{width}-bottom.png'))
        submit=page.get_by_role('button',name='提交诊断',exact=True)
        assert submit.bounding_box()['y']+submit.bounding_box()['height']<=height
        submit.click()
        try: expect(dialog).to_have_count(0)
        except AssertionError:
            page.screenshot(path=str(OUTPUT/f'failure-{width}.png'))
            print('Form errors:',page.locator('.ant-form-item-explain-error').all_text_contents(),flush=True)
            print('Browser errors:',errors,flush=True)
            print('Messages:',page.locator('.ant-message').all_text_contents(),flush=True)
            raise
        payload=json.loads(page.locator('html').get_attribute('data-weekly-payload'))
        assert payload['highestLikes']==0 and payload['changeDirection']=='true' and payload['trafficNormal']=='false'
        expect(page.get_by_role('button',name=re.compile('基于最新记录修订$'))).to_have_count(0)
        assert not errors,errors
        page.close()
    page=browser.new_page();page.goto(URL+'&weekly-error')
    page.get_by_role('button',name='填写诊断表',exact=True).click()
    dialog=page.get_by_role('dialog')
    expect(dialog.get_by_text('诊断表单加载失败：测试统计预览加载失败，请重试',exact=True)).to_be_visible()
    dialog.get_by_role('button',name='重试加载',exact=True).click()
    expect(dialog.get_by_text('通用诊断（本次各周期共用，填写一次）',exact=True)).to_be_visible()
    page=browser.new_page();page.goto(ORIGIN+'/test/account-positioning-summary.html?panel&diagnosis')
    page.get_by_role('button',name='填写启动诊断',exact=True).click()
    dialog=page.get_by_role('dialog')
    assert dialog.get_by_label('是否需要换内容方向测试',exact=True).count()==0
    assert dialog.get_by_label('7天内最高点赞',exact=False).count()==0
    for name in ['当前阶段','账号状态','主要瓶颈']:
        control=dialog.get_by_label(name,exact=True);control.click()
        page.locator('[id="'+control.get_attribute('aria-controls')+'"]').locator('..').locator('.ant-select-item-option-content').filter(has_text='测试历史标签').click()
    dialog.get_by_label('主要瓶颈证据',exact=True).fill('启动证据')
    dialog.get_by_label('一句话诊断结论',exact=True).fill('启动判断')
    page.get_by_role('button',name='提交诊断',exact=True).click();expect(dialog).to_have_count(0)
    startup=json.loads(page.locator('html').get_attribute('data-diagnosis-payload'))
    assert startup['templateType']=='diagnosis_initial' and startup['cycle']==0
    browser.close()
print('PASS weekly 7/14/28 merged form, required/zero fields, unbound submission, retry, history and 1440/390 layouts',OUTPUT)
