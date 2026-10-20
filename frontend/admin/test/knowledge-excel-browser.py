# -*- coding: utf-8 -*-
import json, tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
out=Path(tempfile.mkdtemp(prefix='knowledge-excel-browser-'))
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [1440,390]:
  page=browser.new_page(viewport={'width':width,'height':1000}); errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto('http://127.0.0.1:5329/test/knowledge-excel.html')
  preview=page.locator('section.knowledge-excel').first
  expect(preview.get_by_text('中文内容',exact=True)).to_be_visible(timeout=30000)
  expect(preview.get_by_text('<script>window.bad=1</script>',exact=True)).to_be_visible()
  assert page.evaluate('window.bad') is None
  expect(preview.get_by_role('cell',name='合并标题',exact=True)).to_have_attribute('rowspan','2')
  expect(preview.get_by_role('cell',name='合并标题',exact=True)).to_have_attribute('colspan','2')
  expect(preview.get_by_role('cell',name='纵向合并',exact=True)).to_have_attribute('rowspan','2')
  assert preview.locator('tbody tr').nth(2).locator('td').count()==1
  preview.get_by_role('tab',name='空表',exact=True).click()
  expect(preview.get_by_text('工作表为空',exact=True)).to_be_visible()
  preview.get_by_role('tab',name='超限表',exact=True).click()
  expect(preview.get_by_text('内容已截断',exact=False)).to_be_visible()
  page.get_by_role('button',name='recycle',exact=True).click()
  expect(preview.get_by_text('中文内容',exact=True)).to_be_visible()
  assert page.evaluate('excelFixture.calls.at(-1).params.recordId')==8
  page.get_by_role('button',name='share',exact=True).click()
  expect(preview.get_by_text('中文内容',exact=True)).to_be_visible()
  assert page.evaluate('excelFixture.calls.at(-1).url').endswith('/excel-preview')
  page.get_by_role('button',name='internal',exact=True).click()
  expect(preview.get_by_text('中文内容',exact=True)).to_be_visible()
  page.evaluate('excelFixture.fail=true; setExcelDocument(2)')
  expect(preview.get_by_text('无权访问该知识内容',exact=True)).to_be_visible()
  page.evaluate('excelFixture.fail=false')
  preview.get_by_role('button',name='重试',exact=True).click()
  expect(preview.get_by_text('中文内容',exact=True)).to_be_visible()
  page.evaluate('setExcelDocument(10)'); page.wait_for_timeout(50)
  page.evaluate('setExcelDocument(11)')
  expect(preview.get_by_text('最新文件',exact=True)).to_be_visible()
  page.wait_for_timeout(700)
  expect(preview.get_by_text('最新文件',exact=True)).to_be_visible()
  page.get_by_role('button',name='预览／下载',exact=True).nth(0).click()
  dialog=page.get_by_role('dialog')
  expect(dialog.get_by_text('中文内容',exact=True)).to_be_visible()
  expect(dialog.get_by_role('link',name='下载',exact=True)).to_be_visible()
  page.screenshot(path=str(out/f'excel-{width}.png'),full_page=True)
  page.keyboard.press('Escape'); expect(dialog).not_to_be_visible()
  page.get_by_role('button',name='预览／下载',exact=True).nth(1).click()
  expect(page.get_by_role('dialog').get_by_role('button',name='预览',exact=True)).to_be_visible()
  expect(page.get_by_role('dialog').get_by_role('link',name='下载',exact=True)).to_be_visible()
  page.keyboard.press('Escape')
  page.get_by_role('button',name='预览／下载',exact=True).nth(2).click()
  expect(page.get_by_role('dialog').locator('.el-image')).to_be_visible()
  assert page.evaluate('document.documentElement.scrollWidth')<=width
  assert not errors,errors
  page.close()
 browser.close()
print(json.dumps({'result':'PASS','widths':[1440,390],'artifacts':str(out)},ensure_ascii=False))
