# UTF-8. Isolated browser fixtures; no real business mutations.
import os, re
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('DELIVERY_UI_BASE', 'http://127.0.0.1:5174')
out = Path(os.environ.get('TEMP', '.')) / 'zsjos-delivery-isolation-browser-20261018'
out.mkdir(parents=True, exist_ok=True)
nodes = [dict(key='USER:1', title='测试交付人员', scopeType='USER', scopeId=1, selectable=True)]
target = dict(id=7,scopeType='USER',scopeId=1,name='测试交付人员',periodType='month',periodStart='2026-10-01',
 floorAmount=1000,sprintAmount=2000,automaticFloor=1000,automaticSprint=2000,manual=True,complete=True,missing=0,version=0)
metric = dict(key='month',label='本月',start='2026-10-01T00:00:00',end='2026-10-18T12:00:00',amount=500,orders=1,converted=1,denominator=2,rate=.5,average=500,averageAmount=500,averageOrders=1)
mode = {'value':'ok'}
requests, mutations, errors = [], [], []
def handler(route):
 path=urlparse(route.request.url).path
 requests.append(path)
 if '/sales-performance' in path: raise AssertionError('delivery requested sales endpoint: '+path)
 if route.request.method != 'GET':
  mutations.append((path,route.request.post_data_json));route.fulfill(json={'code':0,'data':True});return
 if mode['value']=='error' and path.endswith('/list'):
  route.fulfill(json={'code':1900090001,'msg':'无权查看或修改该业绩范围'});return
 if path.endswith('/tree'): data=nodes
 elif path.endswith('/list'): data=[] if mode['value']=='empty' else [target]
 elif path.endswith('/overview'): data=dict(asOf='2026-10-18T12:00:00',attributionAvailableSince='2026-10-01T00:00:00',targets=[dict(key='month',label='本月',actual=metric,target=target)],performance=[metric],conversion=[metric],pending={},missingAttributionOrders=0,missingAttributionAmount=0,canDetail=True)
 elif path.endswith('/unclassified-history'): data={'list':[],'total':0}
 elif path.endswith('/history') and 'target' not in path: data=[]
 elif path.endswith('/history'): data=[dict(id=1,reason='交付目标调整',before='',after='{}',operatorName='测试操作者',at='2026-10-18T12:00:00')]
 elif path.endswith('/organization-candidates'): data=[dict(key='ORG:10',title='测试交付中心',scopeType='DEPT',scopeId=10,selectable=True)]
 else: data=[]
 route.fulfill(json={'code':0,'data':data})

with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 page=browser.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
 page.route('**/admin-api/**',handler)
 for width in [1440,390]:
  page.set_viewport_size({'width':width,'height':950})
  page.goto(base+'/test/delivery-performance.html')
  expect(page.get_by_role('tab',name='业绩总览')).to_be_visible()
  expect(page.get_by_role('heading',name='目标进度',exact=True)).to_be_visible()
  page.screenshot(path=str(out/f'statistics-{width}.png'),full_page=True)
  assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'statistics overflow'
  page.goto(base+'/test/delivery-performance.html?target=1&periodStart=2026-10-01&scopeType=USER&scopeId=1')
  expect(page.get_by_role('heading',name='交付业绩设置',exact=True)).to_be_visible()
  expect(page.get_by_role('button',name='修订历史')).to_be_visible()
  page.screenshot(path=str(out/f'targets-{width}.png'),full_page=True)
  assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'targets overflow'
 page.set_viewport_size({'width':1440,'height':950})
 page.get_by_role('button',name='修订历史').click()
 expect(page.get_by_text('测试操作者 · 交付目标调整')).to_be_visible()
 page.locator('.ant-drawer-close').click()
 page.get_by_role('spinbutton').first.fill('1500')
 page.get_by_placeholder('填写调整原因（必填，将保留历史修订记录）').fill('测试交付独立保存')
 page.get_by_role('button',name=re.compile('保存 1 项修改')).click()
 expect(page.get_by_text('目标已保存',exact=True)).to_be_visible()
 assert mutations[-1][0].endswith('/delivery-performance-target/batch')
 assert mutations[-1][1]['items'][0]['floorAmount']==1500
 page.get_by_role('button',name='交付组织关联',exact=True).click()
 expect(page.get_by_text('关联交付中心与部门',exact=True)).to_be_visible()
 mode['value']='error';page.goto(base+'/test/delivery-performance.html?target=1')
 expect(page.get_by_text('无权查看或修改该业绩范围',exact=True)).to_be_visible()
 mode['value']='empty';page.get_by_role('button',name=re.compile('重.*试')).click()
 expect(page.get_by_text('无权查看或修改该业绩范围',exact=True)).to_have_count(0)
 mode['value']='ok';page.goto(base+'/test/delivery-performance.html?target=1&denied=1')
 expect(page.get_by_role('heading',name='交付业绩设置',exact=True)).to_be_visible()
 expect(page.get_by_role('button',name='交付组织关联',exact=True)).to_have_count(0)
 expect(page.get_by_role('button',name=re.compile('保存.*项修改'))).to_have_count(0)
 assert not errors, errors
 browser.close()
print('PASS: delivery statistics/settings desktop+mobile, save, history, configure, error/retry/empty and sales-permission denial; screenshots:',out)
