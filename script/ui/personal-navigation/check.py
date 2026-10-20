# -*- coding: utf-8 -*-
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from threading import Thread
from functools import partial
import json,re
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[3]/'backend/target/personal-navigation-verification/browser'
class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self,*args): pass
server=ThreadingHTTPServer(('127.0.0.1',0),partial(QuietHandler,directory=str(root)))
Thread(target=server.serve_forever,daemon=True).start()
base='http://127.0.0.1:'+str(server.server_port)
with sync_playwright() as pw:
 browser=pw.chromium.launch()
 for client,url in [('admin','/admin/admin.html'),('workbench','/workbench/index.html')]:
  page=browser.new_page(viewport={'width':1280,'height':900})
  errors=[]; page.on('pageerror',lambda e:errors.append(e.stack))
  state={'value':{'groups':[]},'fail':False,'load_fail':False,'puts':0}
  def api(route):
   if route.request.method=='PUT':
    if state['fail']: route.fulfill(status=500,body='error'); return
    state['value']=json.loads(route.request.post_data);state['puts']+=1
   elif state['load_fail']: route.fulfill(status=500,body='error');return
   route.fulfill(json=state['value'])
  page.route('**/fixture/preference',api)
  def open_dialog():
   if client=='workbench': page.get_by_role('button',name='系统设置',exact=True).click()
   page.get_by_role('button',name='导航栏调整',exact=True).click()
  page.goto(base+url)
  page.wait_for_timeout(1000)
  print(client, page.locator("body").inner_text(), errors, flush=True)
  page.screenshot(path=str(root/(client+"-initial.png")))
  open_dialog()
  page.get_by_role('button',name='上移客资管理',exact=True).click()
  page.get_by_role('button',name=re.compile('取'+chr(92)+'s*消')).click()
  assert state['puts']==0
  open_dialog()
  page.get_by_role('button',name='上移客资管理',exact=True).click()
  state['fail']=True
  page.get_by_role('button',name='保存并关闭',exact=True).click()
  page.get_by_text('保存失败，调整内容已保留，请重试。',exact=True).first.wait_for()
  assert page.get_by_role('dialog',name='导航栏调整',exact=True).is_visible()
  state['fail']=False
  with page.expect_navigation(): page.get_by_role('button',name='保存并关闭',exact=True).click()
  assert state['value']['groups'][0]['keys']==['menu:2','menu:1']
  open_dialog()
  page.get_by_role('button',name='子菜单',exact=True).click()
  page.get_by_role('button',name='上移角色管理',exact=True).click()
  page.get_by_role('button',name='返回上一级',exact=True).click()
  for width in [1280,375,320]:
   page.set_viewport_size({'width':width,'height':1000})
   page.wait_for_timeout(150)
   dialog=page.get_by_role('dialog',name='导航栏调整',exact=True)
   assert dialog.evaluate('(e)=>e.scrollWidth<=e.clientWidth+2'),(client,width)
   assert page.get_by_role('button',name='保存并关闭').is_visible()
   if width==375: page.screenshot(path=str(root/(client+'-mobile.png')),full_page=True)
  page.set_viewport_size({'width':1280,'height':900})
  page.screenshot(path=str(root/(client+'-desktop.png')),full_page=True)
  page.get_by_role('button',name='恢复默认',exact=True).click()
  with page.expect_navigation(): page.get_by_role('button',name='保存并关闭',exact=True).click()
  assert state['value']=={'groups':[]}
  state['load_fail']=True;open_dialog()
  page.get_by_text('导航设置读取失败，请重试。',exact=True).wait_for()
  state['load_fail']=False
  page.get_by_role('button',name=re.compile('重新加载|重'+chr(92)+'s*试')).click()
  page.get_by_role('button',name='上移客资管理',exact=True).wait_for()
  page.goto(base+url+'?denied')
  if client=='workbench':
   page.get_by_role('button',name='系统设置',exact=True).click()
   assert page.get_by_role('button',name='导航栏调整',exact=True).count()==0
  else:
   open_dialog();page.get_by_text('暂无导航设置权限',exact=True).wait_for()
  assert not errors,errors
  print(client,'PASS actual component: sorting, cancellation, save failure/retry, reload, child order, reset, load retry, denied, desktop/mobile')
  page.close()
 browser.close()
server.shutdown()
