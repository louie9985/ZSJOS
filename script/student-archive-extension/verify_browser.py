# -*- coding: utf-8 -*-
"""Actual React/Vue/H5 components with isolated synthetic API responses."""
from pathlib import Path
import base64, json, re
from playwright.sync_api import sync_playwright, expect
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'frontend/workbench/tmp/student-archive-extension/browser';OUT.mkdir(parents=True,exist_ok=True)
DOCS=['jpg','jpeg','png','pdf','doc','docx'];PICS=['jpg','jpeg','png']
FIELDS=[dict(key=k,label=l,type=t,allowedExtensions=e) for k,l,t,e in [
 ('channel','对接渠道','text',[]),('registration_fee','报名费','number',[]),('admission_ticket','准考证','composite',DOCS),
 ('theory','理论','composite',DOCS),('skill','技能','composite',DOCS),('thesis','论文/综合评审','composite',PICS),
 ('assessment_result','评定结果','text',[]),('issuing_authority','发证单位','composite',DOCS),
 ('certificate_images','证书图片','composite',PICS),('certificate_shipping_address','证书邮寄地址','textarea',[]),('certificate_tracking_no','证书邮寄单号','text',[])]]
PDF=b'%PDF-1.7\nsynthetic\n%%EOF'
with sync_playwright() as p:
 browser=p.chromium.launch()
 for client,port in [('workbench',5352),('admin',5353)]:
  page=browser.new_page(viewport={'width':1440,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  state={'revision':0,'values':{},'attachments':[],'readonly':False,'sensitive':True,'fail':False};saved=[]
  def reply(route,data,code=0,msg=''):
   route.fulfill(status=200,content_type='application/json',body=json.dumps({'code':code,'data':data,'msg':msg}))
  def api(route):
   req=route.request;url=req.url
   if '/extra/attachments/' in url:return route.fulfill(status=200,content_type='application/octet-stream',body=PDF)
   if url.endswith('/extra/attachments'):
    body=req.post_data_buffer.decode('latin1');field=re.search(r'name="fieldKey"\r\n\r\n([^\r]+)',body).group(1)
    item={'id':101+len(state['attachments']),'fieldKey':field,'name':'synthetic.pdf','contentType':'application/pdf','size':len(PDF),'side':''}
    state['attachments'].append(item);return reply(route,item)
   if url.endswith('/extra'):
    if req.method=='POST':
     if state['fail']:state['fail']=False;return reply(route,None,1900090009,'版本已变化，请刷新后重试')
     data=req.post_data_json;assert data['revision']==state['revision'];saved.append(data);state['values'].update(data['changes']);state['revision']+=1;return reply(route,state['revision'])
    return reply(route,{'revision':state['revision'],'canMaintain':not state['readonly'],'canReadAttachments':state['sensitive'],'fields':FIELDS,'values':state['values'],'attachments':state['attachments'] if state['sensitive'] else [],'history':[{'revision':state['revision'],'operatorUserId':1,'operatorName':'合成维护人','modifiedAt':1792368000000,'reason':'合成维护原因'}] if state['revision'] else []})
   return reply(route,{'personId':1,'serviceRelationId':2,'revision':0,'canMaintain':False,'history':[],'information':{'status':'NONE','fields':[],'values':{},'images':[],'canReadSensitive':True,'canExport':False}})
  page.route(re.compile(r'^http://127\.0\.0\.1:[0-9]+/(?:admin-api/)?zsjos/.*'),api)
  url=f'http://127.0.0.1:{port}/test/student-archive-extra.html'
  for width in [1440,390]:
   page.set_viewport_size({'width':width,'height':1000});page.goto(url)
   expect(page.get_by_role('button',name='维护考试与证书信息',exact=True)).to_be_visible()
   expect(page.get_by_text('暂无已提交的收集资料',exact=True)).to_be_visible()
   assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),(client,width)
   page.screenshot(path=str(OUT/f'{client}-{width}.png'),full_page=True)
  page.get_by_role('button',name='维护考试与证书信息',exact=True).click()
  dialog=page.get_by_role('dialog');expect(dialog).to_be_visible()
  rowtype='.ant-form-item' if client=='workbench' else '.el-form-item'
  def row(label):return dialog.locator(rowtype).filter(has=page.locator('label' if client=='workbench' else '.el-form-item__label').filter(has_text=re.compile('^'+re.escape(label)+'$')))
  theory=row('理论');theory.locator('textarea').nth(0).fill('理论成绩 85')
  theory.locator('textarea').nth(1).fill('https://example.com/theory')
  theory.locator('input[type=file]').set_input_files({'name':'synthetic.pdf','mimeType':'application/pdf','buffer':PDF})
  expect(theory).to_contain_text('synthetic.pdf')
  row('维护原因').locator('textarea').fill('合成维护原因')
  page.wait_for_timeout(350) # Let dialog transitions finish before visual capture.
  page.screenshot(path=str(OUT/f'{client}-editor-mobile.png'),full_page=True)
  assert dialog.evaluate('(x)=>x.scrollWidth<=x.clientWidth'),client
  state['fail']=True
  dialog.locator('.ant-modal-footer button' if client=='workbench' else '.el-dialog__footer button').last.click()
  expect(dialog.get_by_text('版本已变化，请刷新后重试',exact=True)).to_be_visible()
  dialog.locator('.ant-modal-footer button' if client=='workbench' else '.el-dialog__footer button').last.click()
  expect(dialog).not_to_be_visible()
  assert saved[-1]['changes']['theory']=={'text':'理论成绩 85','links':['https://example.com/theory'],'attachmentIds':[101]},saved
  expect(page.get_by_text('理论成绩 85',exact=False)).to_be_visible()
  with page.expect_download() as dl:page.get_by_role('button',name='synthetic.pdf',exact=True).click()
  assert dl.value.suggested_filename=='synthetic.pdf'
  page.get_by_text('考试与证书维护记录',exact=True).click();expect(page.get_by_text(re.compile('合成维护人'))).to_be_visible()
  state['readonly']=True;page.reload()
  expect(page.get_by_text('理论成绩 85',exact=False)).to_be_visible()
  expect(page.get_by_role('button',name='维护考试与证书信息',exact=True)).to_have_count(0)
  state['readonly']=False;state['sensitive']=False;page.reload()
  page.get_by_role('button',name='维护考试与证书信息',exact=True).click()
  expect(row('证书邮寄地址').locator('textarea')).to_be_disabled()
  expect(row('理论').locator('button').filter(has_text='上传附件')).to_be_disabled()
  dialog.locator('.ant-modal-footer button' if client=='workbench' else '.el-dialog__footer button').first.click()
  assert not errors,(client,errors)
  page.close();print('PASS',client,'independent/no collection, 1440/390, composite text/link/PDF, retry/history/download/read-only/sensitive gates',flush=True)
 browser.close()
