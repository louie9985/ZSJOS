# -*- coding: utf-8 -*-
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
OUT=Path(gettempdir())/'zsjos-won-follow-up-browser'
OUT.mkdir(exist_ok=True)
URL='http://127.0.0.1:5174/test/won-follow-up-browser.html'
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True)
 page=browser.new_page(viewport={'width':1280,'height':1000})
 errors=[]
 page.on('pageerror',lambda e:errors.append(str(e)))
 for panel in [False,True]:
  for won in [True,False]:
   page.goto(URL+('?panel=1&' if panel else '?')+'status='+('won' if won else 'valid'))
   expect(page.get_by_role('radiogroup', name='跟进方式',exact=True)).to_be_visible()
   for label in ['跟进方式','跟进结果']:
    page.get_by_role('radiogroup',name=label,exact=True).locator('label').filter(has_text='测试选项').click()
   page.get_by_label('跟进备注',exact=True).fill('成交后测试跟进')
   page.get_by_role('radiogroup',name='销售阶段',exact=True).locator('label').filter(has_text='意向客户').click()
   if won:
    expect(page.get_by_text('选填，不填写则不安排下次跟进')).to_be_visible()
   page.screenshot(path=str(OUT/f'{"panel" if panel else "modal"}-{won}-desktop.png'),full_page=True)
   page.get_by_role('button',name='提交跟进',exact=True).click()
   if won:
    page.wait_for_function('followUpFixture.requests.length === 1')
    assert page.evaluate('followUpFixture.requests[0].nextFollowUpAt === undefined')
    assert page.evaluate('followUpFixture.requests[0].salesStage')=='intent_customer'
    assert page.evaluate('followUpFixture.changed')==1
   else:
    expect(page.get_by_text('请选择下次跟进时间',exact=True)).to_be_visible()
    assert page.evaluate('followUpFixture.requests.length')==0
    page.get_by_role('button',name='+1 天',exact=True).click()
    page.get_by_role('button',name='提交跟进',exact=True).click()
    page.wait_for_function('followUpFixture.requests.length === 1')
    assert page.evaluate('followUpFixture.requests[0].nextFollowUpAt > Date.now()')
   page.set_viewport_size({'width':390,'height':844})
   page.screenshot(path=str(OUT/f'{"panel" if panel else "modal"}-{won}-mobile.png'),full_page=True)
   page.set_viewport_size({'width':1280,'height':1000})
 assert not errors,errors
 browser.close()
print('PASS: both real forms, won optional time/editable stage, pre-deal required time, future shortcut, success refresh, desktop/mobile; screenshots:',OUT)
