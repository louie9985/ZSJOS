# -*- coding: utf-8 -*-
"""Collection runtime from a production build, with synthetic network data only."""
import ast, base64, json
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'frontend/workbench/tmp/student-archive-extension/browser';OUT.mkdir(parents=True,exist_ok=True)
tree=ast.parse((ROOT/'frontend/h5/tests/registration-collection-browser.py').read_text(encoding='utf-8'))
keys=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='keys' for t in n.targets))
keys[9:9]=[('gift_shipping_address','礼品邮寄地址','textarea','basic'),('certificate_shipping_address','证书邮寄地址','textarea','basic')]
formats={'education_report':['pdf'],'application_images':['jpg','jpeg','png','pdf','doc','docx']}
for key in ['work_proof','social_security','labor_contract','commitment']:formats[key]=['jpg','jpeg','png','pdf']
fields=[dict(key=k,label=l,type=t,group=g,enabled=True,required=k=='name',sensitive=k.endswith('shipping_address'),maxImages=9,maxSizeMb=10,allowedExtensions=formats.get(k,['jpg','jpeg','png']) if t=='images' else None) for k,l,t,g in keys]
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
PDF=b'%PDF-1.7\nsynthetic\n%%EOF'
with sync_playwright() as p:
 b=p.chromium.launch();page=b.new_page(viewport={'width':1440,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 state={'images':[],'submitted':False,'fail':False};saved=[]
 def reply(route,data,code=0):route.fulfill(status=200,content_type='application/json',body=json.dumps({'code':code,'data':data,'msg':'合成上传失败，请重试' if code else ''}))
 def api(route):
  req=route.request;url=req.url
  if url.endswith('/detail'):return reply(route,{'status':'SUBMITTED' if state['submitted'] else 'DRAFT','tenantId':1,'productName':'合成产品','configVersion':2,'fields':fields,'options':{},'images':state['images']})
  if '/area/tree' in url:return reply(route,[{'id':10,'name':'合成地区','leafSelectable':True}])
  if '/images/' in url:
   item=next(i for i in state['images'] if str(i['id'])==url.rsplit('/',1)[-1])
   if req.method=='DELETE':state['images'].remove(item);return reply(route,True)
   return route.fulfill(status=200,content_type='application/octet-stream',body=PDF if item['contentType']=='application/pdf' else PNG)
  if url.endswith('/images'):
   if state['fail']:state['fail']=False;return reply(route,None,500)
   body=req.post_data_buffer.decode('latin1')
   def part(key):return body.split('name="'+key+'"')[1].split('\r\n\r\n')[1].split('\r\n')[0]
   key=part('fieldKey');pdf='filename="synthetic.pdf"' in body
   item={'id':100+len(state['images']),'fieldKey':key,'side':part('side'),'contentType':'application/pdf' if pdf else 'image/png','name':'synthetic.pdf' if pdf else 'synthetic.png','size':len(PDF)}
   state['images'].append(item);return reply(route,item)
  if url.endswith('/submit'):saved.append(req.post_data_json);state['submitted']=True;return reply(route,True)
  return reply(route,{})
 page.route('**/public-api/**',api);page.route('**/app-api/**',api);page.route('**/part-api/**',api)
 url='http://127.0.0.1:5354/student-info-form#token='+'a'*43
 for width in [1440,390]:
  page.set_viewport_size({'width':width,'height':1000});page.goto(url);expect(page.locator('#name')).to_be_visible()
  page.locator('details').evaluate_all('(els)=>els.forEach(e=>e.open=true)')
  assert page.locator('.collection-field').count()==32
  address_order=page.locator('.collection-field').evaluate_all('(els)=>els.map(e=>e.querySelector("textarea")?.id).filter(Boolean)')
  assert address_order.index('gift_shipping_address')<address_order.index('certificate_shipping_address')
  assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  page.screenshot(path=str(OUT/f'h5-{width}.png'),full_page=True)
 page.locator('#name').fill('合成学员');page.locator('#gift_shipping_address').fill('合成礼品地址');page.locator('#certificate_shipping_address').fill('合成证书地址')
 report=page.locator('.collection-field').filter(has=page.locator('label').filter(has_text='学历认证报备'))
 expect(report.locator('input[type=file]')).to_have_attribute('accept','.pdf')
 report.locator('input[type=file]').set_input_files({'name':'bad.png','mimeType':'image/png','buffer':PNG})
 assert not state['images']
 state['fail']=True
 report.locator('input[type=file]').set_input_files({'name':'synthetic.pdf','mimeType':'application/pdf','buffer':PDF})
 expect(page.get_by_role('button',name='提交信息',exact=True)).to_be_disabled()
 report.get_by_role('button',name='重试',exact=True).click()
 expect(report.get_by_text('synthetic.pdf',exact=True)).to_be_visible()
 with page.expect_download() as dl:report.get_by_role('button',name='预览学历认证报备',exact=True).click()
 assert dl.value.suggested_filename=='synthetic.pdf'
 application=page.locator('.collection-field').filter(has=page.locator('label').filter(has_text='报名申请表'))
 expect(application.locator('input[type=file]')).to_have_attribute('accept','.jpg,.jpeg,.png,.pdf,.doc,.docx')
 page.get_by_role('button',name='提交信息',exact=True).click();page.get_by_role('button',name='确认',exact=True).click()
 expect(page.locator('.van-empty')).to_contain_text('信息已提交')
 assert saved[-1]['values']['gift_shipping_address']=='合成礼品地址'
 assert saved[-1]['values']['certificate_shipping_address']=='合成证书地址'
 assert saved[-1]['values']['education_report']==[100]
 assert not errors,errors
 b.close();print('PASS production H5 1440/390, 32 fields, address order/submit, PDF format policy/retry/download, Word accept metadata',flush=True)
