# -*- coding: utf-8 -*-
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in (1440,390):
  page=browser.new_page(viewport={'width':width,'height':1000})
  errors=[]
  page.on('pageerror',lambda e: errors.append(str(e)))
  page.goto('http://127.0.0.1:5189/test/student-close-reason.html')
  rows=page.locator('tr.ant-table-row')
  expect(rows).to_have_count(3)
  expect(rows.nth(0)).to_contain_text('操作人填写的关闭原因')
  expect(rows.nth(1)).not_to_contain_text('操作人填写的关闭原因')
  expect(rows.nth(1)).to_contain_text('—')
  expect(rows.nth(2)).to_contain_text('历史未记录')
  expect(page.get_by_text('暂停原因',exact=True)).to_have_count(0)
  expect(page.get_by_text('终止原因',exact=True)).to_have_count(0)
  assert not errors,errors
  page.screenshot(path=str(Path(gettempdir())/f'student-close-reason-{width}.png'),full_page=True)
  print('PASS browser',width)
 browser.close()
