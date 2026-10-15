# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=b.new_page(viewport={'width':width,'height':1000})
  errors=[]
  page.on('pageerror',lambda error:errors.append(str(error)))
  page.goto('http://127.0.0.1:5298/test/registration-early.html')
  page.get_by_role('button',name='查看',exact=True).first.click()
  expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_disabled()
  page.get_by_role('button',name='保存分班',exact=True).click()
  expect(page.get_by_text('已建立交付服务，报名任务仍需财务审核通过后完成',exact=True)).to_be_visible()
  expect(page.get_by_role('button',name='保存分班',exact=True)).to_have_count(0)
  expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_disabled()
  assert page.evaluate('window.registrationRows[1].deliveryEstablished') is False
  page.screenshot(path=f'frontend/workbench/tmp/registration-early-admin-{width}.png',full_page=True)
  if width==1440:
   page.evaluate('window.approveFinance()')
   page.locator('.el-drawer__close-btn').click()
   page.get_by_role('button',name='查看',exact=True).first.click()
   expect(page.get_by_role('button',name='完成报名履约',exact=True)).to_be_enabled()
   page.get_by_role('button',name='完成报名履约',exact=True).click()
   assert page.evaluate('window.registrationRows[0].deliveries[0].serviceRelationId')==1001
  assert not errors,errors
  page.close()
 b.close()
print('PASS Admin desktop/mobile save-to-delivery, finance gate, completion and sibling isolation')
