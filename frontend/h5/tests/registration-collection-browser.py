# -*- coding: utf-8 -*-
"""Real H5 components, isolated network fixtures, no live business API."""
import base64,json,re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
OUT=Path('frontend/workbench/tmp/registration-student-info');OUT.mkdir(parents=True,exist_ok=True)
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
keys=[('name','姓名','text','basic'),('id_card','身份证号码','text','basic'),('gender','性别','dict','basic'),('age','年龄','text','basic'),('ethnicity','民族','dict','basic'),('household_area','户籍所在地','area','basic'),('mobile','手机号码','text','basic'),('retraining','是否复训','dict','basic'),('education_level','现学历','dict','basic'),('school','毕业院校','text','education'),('graduation_major','毕业专业','text','education'),('graduation_time','毕业时间','text','education'),('registration_level','报名层次','dict','education'),('target_school','拟报名院校','text','education'),('target_major','拟报名专业','text','education'),('employer','工作单位','text','career'),('job','从事职业','text','career'),('certificates','目前持有证书','text','career'),('study_purpose','拟报名学习的目的','textarea','career'),('portrait','证件照片','images','materials'),('identity_images','身份证正反面','images','materials'),('diploma_images','毕业证','images','materials'),('education_report','学历认证报备','images','materials'),('application_images','报名申请表','images','materials'),('work_proof','工作证明','images','materials'),('social_security','社保流水','images','materials'),('labor_contract','劳动合同','images','materials'),('prior_certificate','前置证书','images','materials'),('certificate_lookup','前置证书的网查截图','images','materials'),('commitment','承诺书','images','materials')]
fields=[dict(key=k,label=l,type=t,group=g,enabled=True,required=k in ['name','registration_level','target_school','target_major'],sensitive=False,maxImages=9,maxSizeMb=10)for k,l,t,g in keys]
with sync_playwright() as p:
 browser=p.chromium.launch();page=browser.new_page(viewport={'width':1440,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 state={'images':[],'submitted':False,'fail_upload':False,'ordinary':False,'expired':False};submitted=[]
 def reply(route,data,code=0):route.fulfill(status=200,content_type='application/json',body=json.dumps({'code':code,'data':data,'msg':'测试失败，请重试' if code else ''}))
 def api(route):
  req=route.request;url=req.url
  if '/public-api/' in url:assert req.headers.get('x-student-info-token')=='a'*43 and not req.headers.get('authorization')
  if url.endswith('/detail'):
   status='EXPIRED' if state['expired'] else 'SUBMITTED' if state['submitted'] else 'DRAFT';fs=[f for f in fields if not state['ordinary'] or f['key'] not in ['registration_level','target_school','target_major']]
   return reply(route,{'status':status,'tenantId':1,'configVersion':1,'productName':'合成报名产品','fields':fs if status=='DRAFT' else [],'options':{f['key']:[{'value':'1','label':'测试选项'}]for f in fs if f['type']=='dict'},'images':state['images'] if status=='DRAFT' else []})
  if '/images/' in url:
   if req.method=='DELETE':state['images']=[i for i in state['images'] if str(i['id'])!=url.split('/')[-1]];return reply(route,True)
   return route.fulfill(status=200,content_type='application/octet-stream',body=PNG)
  if url.endswith('/images'):
   if state['fail_upload']:state['fail_upload']=False;return reply(route,None,500)
   text=req.post_data_buffer.decode('latin1')
   def part(name):return text.split('name="'+name+'"')[1].split(chr(13)+chr(10)+chr(13)+chr(10))[1].split(chr(13)+chr(10))[0]
   image={'id':100+len(state['images']),'fieldKey':part('fieldKey'),'side':part('side'),'contentType':'image/png','name':'synthetic.png','size':len(PNG)};state['images'].append(image);return reply(route,image)
  if url.endswith('/submit'):submitted.append(req.post_data_json);state['submitted']=True;return reply(route,True)
  if '/area/tree' in url:return reply(route,[{'id':10,'name':'测试省','leafSelectable':True}])
  return reply(route,{})
 page.route('**/public-api/**',api);page.route('**/app-api/**',api);page.route('**/part-api/**',api)
 def open_page():page.goto('http://127.0.0.1:10086/student-info-form#token='+'a'*43);page.reload();expect(page.locator('#name')).to_be_visible()
 for width in [1440,768,390,320]:
  page.set_viewport_size({'width':width,'height':1000});open_page();page.locator('details').evaluate_all('(els)=>els.forEach(x=>x.open=true)');assert page.locator('.collection-field').count()==30;assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),width;page.screenshot(path=str(OUT/('h5-'+str(width)+'.png')),full_page=True)
 page.set_viewport_size({'width':390,'height':900});open_page();page.locator('#gender').click();expect(page.locator('.van-picker')).to_be_visible();page.locator('.van-picker__cancel').click();expect(page.locator('#gender')).to_contain_text('请选择');page.locator('#gender').click();page.locator('.van-picker__confirm').click();expect(page.locator('#gender')).to_contain_text('测试选项')
 page.locator('#gender').click();page.screenshot(path=str(OUT/'picker-mobile.png'),full_page=True);page.locator('.van-picker__cancel').click()
 page.set_viewport_size({'width':390,'height':900});open_page();page.locator('[data-group=materials]').evaluate('(x)=>{if(!x.open)x.querySelector("summary").click()}');expect(page.locator('[data-group=materials]')).to_have_attribute('open','');file={'name':'one.png','mimeType':'image/png','buffer':PNG}
 page.get_by_label('证件照片添加图片',exact=True).set_input_files([file,{**file,'name':'two.png'}]);expect(page.get_by_role('button',name='预览证件照片',exact=True)).to_have_count(2)
 page.get_by_label('身份证正反面身份证正面',exact=True).set_input_files(file);expect(page.get_by_label('身份证正反面身份证正面',exact=True)).to_have_count(0)
 page.get_by_label('身份证正反面身份证反面',exact=True).set_input_files(file);expect(page.get_by_role('button',name='预览身份证正反面身份证反面',exact=True)).to_have_count(1)
 state['fail_upload']=True;page.get_by_label('毕业证添加图片',exact=True).set_input_files(file);expect(page.get_by_role('button',name='提交信息',exact=True)).to_be_disabled();page.get_by_role('button',name='重试',exact=True).click();expect(page.get_by_role('button',name='提交信息',exact=True)).to_be_enabled()
 page.get_by_role('button',name='删除毕业证添加图片',exact=True).click();expect(page.get_by_role('button',name='预览毕业证',exact=True)).to_have_count(0)
 page.get_by_role('button',name='提交信息',exact=True).click();expect(page.locator('.van-notice-bar')).to_contain_text('请填写姓名');page.locator('#name').fill('合成学员');page.locator('[data-group=education]').evaluate('(x)=>{if(!x.open)x.querySelector("summary").click()}');page.locator('#registration_level').click();page.locator('.van-picker__confirm').click();page.locator('#target_school').fill('合成院校');page.locator('#target_major').fill('合成专业');page.get_by_role('button',name='提交信息',exact=True).click();page.get_by_role('button',name='确认',exact=True).click();expect(page.locator('.van-empty')).to_contain_text('信息已提交');assert len(submitted)==1 and len(submitted[0]['values']['portrait'])==2 and len(submitted[0]['values']['identity_images'])==2
 state.update(submitted=False,images=[],ordinary=True);open_page();assert page.locator('#registration_level').count()==0
 state['expired']=True;page.reload();expect(page.locator('.van-empty')).to_contain_text('链接已失效');assert not errors,errors
 browser.close();print('PASS H5 real components: widths, 30 fields, conditional required, multi-image, identity sides, retry/delete, submit/expired')
