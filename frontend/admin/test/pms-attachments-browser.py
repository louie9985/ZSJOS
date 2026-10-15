# -*- coding: utf-8 -*-
import json, tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
out=Path(tempfile.mkdtemp(prefix='pms-attachments-browser-'))
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [1440,390]:
  page=browser.new_page(viewport={'width':width,'height':1000}); errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto('http://127.0.0.1:5318/test/pms-attachments.html')
  picker=page.locator('input[type=file]')
  picker.set_input_files([{'name':'培训.pdf','mimeType':'application/pdf','buffer':b'fixture'},{'name':'资料.docx','mimeType':'application/octet-stream','buffer':b'fixture'}])
  expect(page.get_by_role('button',name='保存',exact=True)).to_be_disabled()
  expect(page.get_by_role('button',name='保存',exact=True)).to_be_enabled(timeout=20000)
  assert page.evaluate('attachmentFixture.uploads')==['培训.pdf','资料.docx']
  page.evaluate('attachmentFixture.fail=true')
  picker.set_input_files({'name':'失败.zip','mimeType':'application/zip','buffer':b'fixture'})
  expect(page.get_by_text('fixture upload failure，请重试或移除',exact=True)).to_be_visible()
  expect(page.get_by_role('button',name='保存',exact=True)).to_be_disabled()
  page.evaluate('attachmentFixture.fail=false')
  page.get_by_role('button',name='重试',exact=True).click()
  expect(page.get_by_role('button',name='保存',exact=True)).to_be_enabled()
  page.get_by_role('button',name='保存',exact=True).click()
  assert len(page.evaluate('attachmentFixture.saved'))==3
  assert page.evaluate('document.documentElement.scrollWidth')<=width
  page.screenshot(path=str(out/f'attachments-{width}.png'),full_page=True)
  page.get_by_role('button',name='预览／下载',exact=True).first.click()
  expect(page.get_by_role('link',name='下载',exact=True)).to_be_visible()
  assert not errors,errors
  page.reload()
  page.get_by_role('button',name='编辑完整文档',exact=True).click()
  dialog=page.get_by_role('dialog').filter(has_text='编辑文档')
  dialog.locator('input[type=file]').set_input_files({'name':'正文附件.pdf','mimeType':'application/pdf','buffer':b'fixture'})
  expect(dialog.get_by_role('button',name='保存',exact=True)).to_be_enabled(timeout=20000)
  dialog.get_by_role('button',name='保存',exact=True).click()
  expect(dialog).not_to_be_visible()
  page.get_by_role('button',name='编辑完整文档',exact=True).click()
  expect(dialog.get_by_text('正文附件.pdf',exact=True)).to_be_visible()
  page.wait_for_timeout(800)
  page.screenshot(path=str(out/f'editor-{width}.png'),full_page=True)
  dialog.get_by_role('button',name='取消',exact=True).click()
  page.get_by_role('button',name='分享完整文档',exact=True).click()
  share=page.get_by_role('dialog').filter(has_text='分享文档')
  expect(share.get_by_role('checkbox',name='正文附件.pdf')).not_to_be_checked()
  share.get_by_text('正文附件.pdf',exact=True).click()
  share.get_by_role('button',name='开启分享',exact=True).click()
  expect(share.get_by_role('button',name='保存分享设置',exact=True)).to_be_visible()
  assert len(page.evaluate('attachmentFixture.shared'))==1
  page.screenshot(path=str(out/f'share-{width}.png'),full_page=True)
  assert not errors,errors
  page.close()
 browser.close()
print(json.dumps({'passed':['desktop','mobile','upload_order','failure_retry','save_guard','download_entry'],'screenshots':str(out)},ensure_ascii=False))

