# UTF-8. Synthetic read-only UI fixture, not authenticated runtime acceptance.
import re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
out=Path('D:/ZSJ-OS-backups/delivery-review-20261015');errors=[]
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 page=browser.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
 mode={'value':'ok'}
 def handle(route):
  if mode['value']=='error':
   route.fulfill(json={'code':1900090001,'msg':'无权查看该业绩范围'});return
  rows=[] if mode['value']=='empty' else [{'fact':{'id':1,'number':'TEST-001','kind':'order','label':'成交订单','occurredAt':'2026-10-01T12:00:00','amount':100,'state':'生效'},'attributionReason':'未记录业务域和历史部门'}]
  route.fulfill(json={'code':0,'data':{'list':rows,'total':len(rows)}})
 page.route('**/admin-api/zsjos/*performance/unclassified-history*',handle)
 for domain in ['sales','delivery']:
  for width in [1440,390]:
   page.set_viewport_size({'width':width,'height':900})
   page.goto('http://127.0.0.1:5187/test/unclassified-history.html?domain='+domain)
   page.get_by_role('button',name='我的未归类历史',exact=True).click()
   expect(page.get_by_text('TEST-001',exact=True)).to_be_visible()
   expect(page.get_by_text('未记录业务域和历史部门',exact=True)).to_be_visible()
   page.wait_for_timeout(350)
   page.screenshot(path=str(out/f'unclassified-{domain}-{width}.png'),full_page=True)
   assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
 mode['value']='error';page.reload();page.get_by_role('button',name='我的未归类历史',exact=True).click()
 expect(page.get_by_text('无权查看该业绩范围',exact=True)).to_be_visible()
 mode['value']='empty';page.get_by_role('button',name=re.compile('重.*试')).click()
 expect(page.get_by_text('暂无未归类历史',exact=True)).to_be_visible()
 page.goto('http://127.0.0.1:5187/test/unclassified-history.html?denied=1')
 expect(page.get_by_role('button',name='我的未归类历史',exact=True)).to_have_count(0)
 assert not errors,errors
 browser.close()
 print('PASS sales/delivery desktop/mobile, success, empty, error/retry, denied entry')
