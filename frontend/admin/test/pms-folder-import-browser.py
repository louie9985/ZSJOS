# -*- coding: utf-8 -*-
"""Real Chrome against production folder-import component; synthetic transport only."""
import tempfile, json, hashlib
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
out=Path(tempfile.mkdtemp(prefix='pms-folder-browser-'))
folder=out/'培训';folder.mkdir();(folder/'话术').mkdir()
(folder/'a.txt').write_text('产品介绍',encoding='utf-8');(folder/'b.txt').write_text('恢复测试',encoding='utf-8')
(folder/'话术'/'c.txt').write_text('沟通话术',encoding='utf-8');(folder/'skip.exe').write_bytes(b'fixture')
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [1440,390]:
  context=browser.new_context(viewport={'width':width,'height':1000})
  page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto('http://127.0.0.1:5308/test/pms-folder-import.html')
  actual=page.evaluate("async () => { const m=await import('/src/views/pms/kb/importing/files.ts'); return m.fingerprint(new File([new Uint8Array(2*1024*1024+7).fill(97)],'large.txt')) }")
  assert actual==hashlib.sha256(b'a'*(2*1024*1024+7)).hexdigest()
  page.get_by_role('button',name='从文件夹创建',exact=True).click()
  dialog=page.get_by_role('dialog').filter(has_text='文件夹导入')
  expect(dialog.get_by_role('button',name='选择文件夹',exact=True)).to_be_enabled()
  dialog.locator('input[type=file]').set_input_files(str(folder))
  expect(dialog.get_by_text('培训：4 个文件',exact=False)).to_be_visible(timeout=20000)
  dialog.get_by_role('button',name='检查导入清单').click()
  expect(dialog.get_by_text('不支持的文件类型',exact=True)).to_be_visible()
  page.evaluate('importFixture.failUpload = true')
  dialog.get_by_role('button',name='创建并导入').click()
  expect(dialog.get_by_text('成功 2 / 跳过 1 / 失败 1',exact=False)).to_be_visible(timeout=20000)
  assert page.evaluate('importFixture.maximum')<=3
  box=dialog.bounding_box();assert box['x']>=0 and box['x']+box['width']<=width+1
  page.screenshot(path=str(out/f'partial-{width}.png'),full_page=True)
  page.reload();page.get_by_role('button',name='我的导入记录',exact=True).click()
  page.get_by_role('button',name='查看 / 继续',exact=True).click()
  dialog=page.get_by_role('dialog').filter(has_text='文件夹导入')
  dialog.locator('input[type=file]').set_input_files(str(folder))
  expect(dialog.get_by_role('button',name='继续 / 重试失败文件')).to_be_enabled(timeout=20000)
  dialog.get_by_role('button',name='继续 / 重试失败文件').click()
  expect(dialog.get_by_text('成功 3 / 跳过 1 / 失败 0',exact=False)).to_be_visible(timeout=20000)
  assert page.evaluate('importFixture.uploads')==['培训/b.txt']
  page.screenshot(path=str(out/f'complete-{width}.png'),full_page=True)
  dialog.get_by_role('button',name='收起',exact=True).click()
  page.get_by_role('button',name='上传到已有库',exact=True).click()
  expect(dialog.get_by_text('目标：销售资料 / 培训 / 所选文件夹',exact=True)).to_be_visible()
  dialog.locator('input[type=file]').set_input_files(str(folder))
  expect(dialog.get_by_text('培训：4 个文件',exact=False)).to_be_visible(timeout=20000)
  dialog.get_by_role('button',name='检查导入清单').click()
  dialog.get_by_role('button',name='开始导入',exact=True).click()
  expect(dialog.get_by_text('成功 3 / 跳过 1 / 失败 0',exact=False)).to_be_visible(timeout=20000)
  assert not errors,errors
  context.close()
 # A real iframe checks presentation integration, not authenticated Workbench acceptance.
 context=browser.new_context(viewport={'width':1440,'height':1000})
 page=context.new_page();page.goto('http://127.0.0.1:5308/test/pms-folder-import.html')
 page.evaluate("document.body.innerHTML='<iframe title=admin-embed style=width:100%;height:950px src=/test/pms-folder-import.html?embed=1></iframe>'")
 embedded=page.frame_locator('iframe')
 embedded.get_by_role('button',name='上传到已有库',exact=True).click()
 expect(embedded.get_by_text('目标：销售资料 / 培训 / 所选文件夹',exact=True)).to_be_visible()
 page.screenshot(path=str(out/'iframe.png'),full_page=True)
 context.close()
 browser.close()
print(json.dumps({'passed':['new_library','directory_input','hashing','unsupported_skip','three_workers','partial_failure','reload_history','reselect_resume_only_failed','existing_target','desktop_mobile_bounds','multi_chunk_SHA256','iframe_host'],'screenshots':str(out)},ensure_ascii=False))
