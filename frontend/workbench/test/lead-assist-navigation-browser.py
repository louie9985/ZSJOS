# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 b=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=b.new_page(viewport={'width':width,'height':1000})
  page.goto('http://127.0.0.1:5174/test/lead-assist-navigation.html')
  page.get_by_role('button',name='表格模式',exact=True).click()
  def open_task(id):
   page.locator('.task-panel-item').filter(has_text=f'协助测试{id}').get_by_role('button').click()
   expect(page.get_by_role('dialog')).to_be_visible()
   expect(page.get_by_role('tab',name='协助历史',exact=True)).to_have_attribute('aria-selected','true')
   expect(page.get_by_role('dialog',name=f'测试客资{id}',exact=True)).to_be_visible()
  def close_home():
   page.locator('.ant-drawer-close').click()
   page.get_by_role('button',name='返回首页',exact=True).click()
  open_task(101)
  page.get_by_role('tab',name='概览',exact=True).click()
  close_home()
  open_task(101)
  close_home()
  open_task(102)
  close_home()
  open_task(101)
  if width == 1440:
   page.get_by_role('tab',name='跟进记录 (0)',exact=True).click()
   page.get_by_label('跟进备注',exact=True).fill('未提交草稿')
   close_home()
   dialogs=[]
   def cancel(dialog):
    dialogs.append(dialog.message)
    dialog.dismiss()
   page.on('dialog',cancel)
   page.locator('.task-panel-item').filter(has_text='协助测试101').get_by_role('button').click()
   expect(page.get_by_role('region',name='其他待办',exact=True)).to_be_visible()
   assert len(dialogs)==1
  page.close()
 b.close()
print('PASS repeated same assist, changed detail tab, different lead, retained desktop/mobile')
