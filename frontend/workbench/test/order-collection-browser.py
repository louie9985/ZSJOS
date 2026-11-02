# -*- coding: utf-8 -*-
"""Isolated fixture, no business API writes; server stopped after checks."""
import subprocess,time,urllib.request,tempfile,re,sys
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
output=ROOT/'tmp/order-collection/browser'
output.mkdir(parents=True,exist_ok=True)
subprocess.run(['node','node_modules/esbuild/bin/esbuild','test/order-collection.tsx','--bundle','--format=esm','--outfile='+str(output/'fixture.js'),'--define:import.meta.env={}','--loader:.svg=dataurl','--loader:.png=dataurl','--loader:.woff2=dataurl','--loader:.woff=dataurl','--loader:.ttf=dataurl'],cwd=ROOT,check=True)
(output/'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="fixture.css"><div id="root"></div><script type="module" src="fixture.js"></script></html>',encoding='utf-8')
log=open(Path(tempfile.gettempdir())/'order-collection-browser.log','w')
p=subprocess.Popen([sys.executable,'-m','http.server','5197','--bind','127.0.0.1','--directory',str(output)],stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
try:
 for i in range(30):
  try: urllib.request.urlopen('http://127.0.0.1:5197/');break
  except Exception: time.sleep(1)
 with sync_playwright() as pw:
  browser=pw.chromium.launch()
  for width in [1440,390]:
   page=browser.new_page(viewport={'width':width,'height':900})
   page.on('pageerror',lambda error: print('PAGEERROR',error,flush=True))
   page.goto('http://127.0.0.1:5197/',wait_until='domcontentloaded',timeout=90000)
   if width>=768: page.get_by_text('表格视图',exact=True).click()
   page.get_by_role('tab',name='待处理',exact=True).click()
   if width>=768: page.get_by_role('button',name='处理资料',exact=True).click(timeout=90000)
   else: page.get_by_role('button',name='查看收集任务 TEST-001',exact=True).click()
   page.get_by_role('button',name='查看已有档案',exact=True).click()
   expect(page.get_by_role('button',name='复用已有档案',exact=True)).to_be_disabled()
   expect(page.get_by_text('需补充：报名层次、拟报名院校')).to_be_visible()
   expect(page.get_by_role('button',name='带入已有内容生成新表')).to_be_enabled()
   page.wait_for_timeout(400)
   page.screenshot(path=str(Path(tempfile.gettempdir())/f'order-collection-{width}.png'),full_page=True)
   page.get_by_role('dialog',name='查看已有档案',exact=True).get_by_role('button',name=re.compile(r'关\s*闭')).last.click()
   page.get_by_role('dialog',name='报名资料收集',exact=True).get_by_role('button',name=re.compile(r'关\s*闭')).last.click()
   page.get_by_role('tab',name='已提交',exact=True).click()
   if width>=768: page.get_by_role('button',name='查看状态',exact=True).click()
   else: page.get_by_role('button',name='查看收集任务 TEST-002',exact=True).click()
   expect(page.get_by_text('此订单已停止内部办理，已发出的链接不因订单终止而失效。')).to_be_visible()
   expect(page.get_by_role('button',name='查看收集表链接')).to_have_count(0)
   expect(page.get_by_text('已保存的合成报名资料')).to_be_visible()
   page.get_by_role('button',name='查看已有档案',exact=True).click()
   expect(page.get_by_text('该客户暂无已填写档案')).to_be_visible()
   print('PASS browser missing fields and terminated task',width,flush=True)
   page.close()
  browser.close()
finally:
 p.terminate();p.wait(timeout=10);log.close()
