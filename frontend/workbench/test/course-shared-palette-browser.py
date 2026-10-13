# -*- coding: utf-8 -*-
import os,tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
BASE=os.environ.get('WORKBENCH_TEST_URL','http://127.0.0.1:5198')
OUT=Path(tempfile.mkdtemp(prefix='course-shared-palette-'))
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [390,1440]:
  context=browser.new_context(viewport={'width':width,'height':1000})
  page=context.new_page();page.goto(BASE+'/test/calendar-colors.html?scene=course&denied=1')
  legend=page.locator('.calendar-color-legend')
  expect(legend.get_by_text('公共营养师',exact=True)).to_be_visible()
  page.get_by_role('button',name='新增课程').click()
  editor=page.get_by_role('dialog',name='新增课程安排')
  editor.get_by_role('button',name='管理快捷色',exact=True).click()
  manage=page.get_by_role('dialog',name='管理快捷色',exact=True)
  expect(manage.locator('.calendar-color-manage-row')).to_have_count(4)
  manage.locator('.calendar-color-manage-row').first.locator('button').first.click()
  editing=page.get_by_role('dialog',name='编辑快捷色',exact=True)
  editing.get_by_label('快捷色名称').fill('共享营养课程')
  editing.locator('.ant-modal-footer .ant-btn-primary').click()
  expect(legend.get_by_text('共享营养课程',exact=True)).to_have_count(1)
  expect(editor.get_by_role('button',name='共享营养课程',exact=True)).to_be_visible()
  editor.get_by_role('button',name='管理快捷色',exact=True).click()
  for count in [4,3,2,1]:
   expect(manage.locator('.calendar-color-manage-row')).to_have_count(count)
   manage.locator('.calendar-color-manage-row').first.locator('button').last.click()
   page.locator('.ant-popconfirm .ant-btn-primary').click()
  expect(manage.get_by_text('暂无快捷色',exact=True)).to_be_visible()
  page.reload()
  expect(legend.locator('.calendar-color-swatch')).to_have_count(0)
  page.get_by_role('button',name='新增课程').click()
  expect(editor.locator('.calendar-color-swatches button')).to_have_count(0)
  # The color picker remains available after deleting every shared entry.
  expect(editor.get_by_label('自选颜色')).to_be_visible()
  page.goto(BASE+'/test/calendar-colors.html?scene=course&readonly=1')
  expect(page.get_by_role('button',name='新增课程')).to_have_count(0)
  page.screenshot(path=str(OUT/f'shared-{width}.png'),full_page=True)
  print('PASS shared initial edit/delete/empty/reload/manage permission',width,flush=True)
  context.close()
 browser.close()
print('Screenshots',OUT)
