# -*- coding: utf-8 -*-
import os,base64,tempfile,json
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
BASE=os.environ.get('WORKBENCH_TEST_URL','http://127.0.0.1:5174')
OUT=Path(tempfile.mkdtemp(prefix='course-attachments-'))
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII=')
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [390,1440]:
  context=browser.new_context(viewport={'width':width,'height':1000})
  page=context.new_page()
  context.route('**/test/course-file.png',lambda r:r.fulfill(content_type='image/png',body=PNG))
  page.goto(BASE+'/test/course-attachments.html')
  page.get_by_role('button',name='新增课程').click()
  editor=page.get_by_role('dialog',name='新增课程安排')
  editor.get_by_label('课程名称',exact=True).fill('附件测试课程')
  editor.get_by_label('课程形式',exact=True).click();page.get_by_title('直播',exact=True).click()
  editor.locator('input[type=file]').set_input_files([{'name':'lecture.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.7 lecture'}, {'name':'retry.png','mimeType':'image/png','buffer':PNG}])
  expect(editor.locator('.deferred-attachment-item')).to_have_count(2)
  assert page.evaluate("sessionStorage.getItem('uploads')")==None
  editor.locator('.ant-modal-footer .ant-btn-primary').click()
  expect(editor.get_by_text('模拟上传失败',exact=True)).to_be_visible()
  assert page.evaluate("sessionStorage.getItem('creates')")==None
  editor.locator('.ant-modal-footer .ant-btn-primary').click()
  expect(editor).not_to_be_visible()
  assert json.loads(page.evaluate("sessionStorage.getItem('uploads')"))=={'lecture.pdf':1,'retry.png':2}
  assert page.evaluate("sessionStorage.getItem('creates')")=='1'
  page.reload();page.locator('.course-calendar-summary').first.click();page.locator('.calendar-day-card').first.click()
  section=page.get_by_label('当天课程附件')
  expect(section.locator('.attachment-card')).to_have_count(2)
  expect(section.get_by_text('lecture.pdf',exact=True)).to_be_visible()
  section.get_by_label('预览图片：retry.png').click()
  preview=page.get_by_role('dialog',name='retry.png',exact=True)
  expect(preview).to_be_visible()
  preview.locator('.ant-modal-close').click()
  card=section.locator('.attachment-card').first
  with page.expect_download() as download:
   card.get_by_role('button',name='下载',exact=False).click()
  assert download.value.suggested_filename=='lecture.pdf'
  page.screenshot(path=str(OUT/f'details-{width}.png'),full_page=True)
  page.get_by_label('日程详细信息',exact=True).get_by_role('button',name='编辑课程',exact=True).click()
  editor=page.get_by_role('dialog',name='编辑课程安排')
  expect(editor.locator('.deferred-attachment-item')).to_have_count(2)
  editor.get_by_label('删除lecture.pdf',exact=True).click()
  editor.locator('.ant-modal-footer .ant-btn-primary').click()
  expect(editor).not_to_be_visible()
  assert json.loads(page.evaluate("sessionStorage.getItem('events')"))[0]['attachmentIds']==[2]
  assert json.loads(page.evaluate("sessionStorage.getItem('uploads')"))=={'lecture.pdf':1,'retry.png':2}
  page.goto(BASE+'/test/course-attachments.html?denied=1')
  expect(page.get_by_role('button',name='新增课程')).to_have_count(0)
  page.locator('.course-calendar-summary').first.click();page.locator('.calendar-day-card').first.click()
  expect(page.get_by_label('当天课程附件').locator('.attachment-card')).to_have_count(1)
  print('PASS',width,'upload/retry/preserve/remove/read/preview/download/query-only',flush=True)
  context.close()
 browser.close()
print('Screenshots',OUT)
