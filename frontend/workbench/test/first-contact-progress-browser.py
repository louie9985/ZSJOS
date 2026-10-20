# -*- coding: utf-8 -*-
import re, json
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect
out=Path('C:/Users/EDY/AppData/Local/Temp/zsjos-first-contact-browser-20261018');out.mkdir(exist_ok=True)
mutations=[];errors=[];state={'fail':False}
def handler(route):
 path=urlparse(route.request.url).path
 if route.request.method in ('POST','PUT') and ('first-contact' in path or path.endswith('/contacts')):
  mutations.append((path,route.request.post_data_json))
  if state['fail']:route.fulfill(json={'code':1900003050,'msg':'清单更新失败，请重试'});return
  route.fulfill(json={'code':0,'data':True if route.request.method=='PUT' else 40});return
 if path.endswith('contact-context'):
  data={'serviceRelationId':10,'version':3,'firstContactChecklist':[],'quickNotes':[],'visibleTabs':['overview'],'availableActions':['UPDATE_FIRST_CONTACT_CHECKLIST'],'firstContactProgress':{'recordId':40,'source':'snapshot','historyIncomplete':False,'submittedAt':1792300000000,'operatorUserId':7,'operatorUserName':'测试规划师','studyPlanStatus':'first_contact','items':[{'key':'add','title':'添加学员','sort':1,'completed':'add' in mutations[-1][1]['completedChecklistKeys'],'attachmentRequired':False,'attachmentFileIds':[]},{'key':'group','title':'邀请进入学习群并推崇 IP 老师','sort':2,'completed':'group' in mutations[-1][1]['completedChecklistKeys'],'attachmentRequired':False,'attachmentFileIds':[]}]}}
 else:data=[]
 route.fulfill(json={'code':0,'data':data})
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True);page=browser.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.add_init_script("document.addEventListener('DOMContentLoaded',()=>{const s=document.createElement('style');s.textContent='*,*::before,*::after{animation:none!important;transition:none!important;scroll-behavior:auto!important}';document.head.appendChild(s)})");page.route('**/admin-api/**',handler)
 for width in (1440,390):
  page.set_viewport_size({'width':width,'height':950});page.goto('http://127.0.0.1:5174/test/first-contact-progress.html')
  expect(page.get_by_text('已完成 1 / 2',exact=True)).to_be_visible();expect(page.get_by_text('学习计划：首联时已完成',exact=False)).to_be_visible()
  page.screenshot(path=str(out/f'overview-{width}.png'),full_page=True);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  page.get_by_role('button',name='更新清单').click();page.wait_for_timeout(300);page.get_by_role('checkbox',name='邀请进入学习群并推崇 IP 老师').locator('xpath=ancestor::label').click();expect(page.get_by_role('checkbox',name='邀请进入学习群并推崇 IP 老师')).to_be_checked();page.get_by_role('button',name=re.compile('确.*定')).click()
  expect(page.get_by_text('已完成 2 / 2',exact=True)).to_be_visible();assert mutations[-1][1]['version']==2
  page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?form=1');page.get_by_role('radio',name='是',exact=True).check()
  expect(page.get_by_role('checkbox',name='首联时已完成学习计划制定')).not_to_be_checked()
  page.get_by_role('checkbox',name='首联时已完成学习计划制定').locator('xpath=ancestor::label').click();expect(page.get_by_role('checkbox',name='首联时已完成学习计划制定')).to_be_checked();page.locator('textarea#remark').fill('首联已完成学习计划')
  expect(page.get_by_role('button',name='+7 天',exact=True)).to_have_count(0);expect(page.get_by_text('将发起延期审批',exact=False)).to_have_count(0)
  page.screenshot(path=str(out/f'form-{width}.png'),full_page=True);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  page.get_by_role('button',name='提交首联',exact=True).click();expect(page.get_by_text('首联已提交',exact=True)).to_be_visible()
  assert mutations[-1][1]['nextContactAt'] is None;assert mutations[-1][1]['studyPlanCompletedAtFirstContact'] is True;assert not mutations[-1][1].get('completedChecklistKeys')
 # Old branch and hidden fields. Use desktop after mobile screenshots to avoid modal auto-scroll during expansion.
 page.set_viewport_size({"width":1440,"height":1200})
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?form=1');page.get_by_role('radio',name='是',exact=True).check();page.wait_for_timeout(200);page.get_by_role('button',name='+3 天',exact=True).click()
 expect(page.get_by_text('将发起延期审批',exact=False)).to_have_count(0);page.get_by_role('checkbox',name='添加学员').locator('xpath=ancestor::label').click();expect(page.get_by_role('checkbox',name='添加学员')).to_be_checked();page.get_by_role('checkbox',name='首联时已完成学习计划制定').locator('xpath=ancestor::label').click();expect(page.get_by_role('checkbox',name='首联时已完成学习计划制定')).to_be_checked()
 page.get_by_role('radio',name='否',exact=True).check();page.get_by_role('radio',name='是',exact=True).check();page.wait_for_timeout(200);expect(page.get_by_role('checkbox',name='添加学员')).not_to_be_checked();expect(page.get_by_role('checkbox',name='首联时已完成学习计划制定')).not_to_be_checked()
 page.get_by_role('button',name='+30 分钟',exact=True).click();page.locator('textarea#remark').fill('先首联，后制定计划');page.get_by_role('button',name='提交首联',exact=True).click();expect(page.get_by_text('首联已提交',exact=True)).to_be_visible();assert mutations[-1][1]['studyPlanCompletedAtFirstContact'] is False
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?form=1');page.get_by_role('radio',name='否',exact=True).check()
 expect(page.get_by_role('button',name='+1 小时',exact=True)).to_be_visible();expect(page.get_by_role('button',name='+1 天',exact=True)).to_have_count(0)
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?form=1&ordinary=1');page.get_by_role('radio',name='是',exact=True).check();page.locator('textarea#remark').fill('无待办主动跟进');page.get_by_role('button',name='提交普通跟进',exact=True).click();expect(page.get_by_text('首联已提交',exact=True)).to_be_visible();assert mutations[-1][1]['nextContactAt'] is None;assert 'taskId' not in mutations[-1][1]
 # A manually chosen date beyond 72h must still display the approval form.
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?form=1');page.get_by_role('radio',name='是',exact=True).check()
 import datetime
 future=(datetime.datetime.now()+datetime.timedelta(days=5)).strftime('%Y-%m-%d %H:%M')
 page.locator('input#nextContactAt').fill(future);page.locator('input#nextContactAt').press('Enter');page.locator('textarea#remark').click()
 expect(page.get_by_text('将发起延期审批',exact=False)).to_be_visible()
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?denied=1');expect(page.get_by_role('button',name='更新清单')).to_have_count(0)
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html?empty=1');expect(page.get_by_text('尚未成功首联',exact=True)).to_be_visible()
 page.goto('http://127.0.0.1:5174/test/first-contact-progress.html');page.get_by_role('button',name='更新清单').click();page.wait_for_timeout(300);page.get_by_role('checkbox',name='添加学员').locator('xpath=ancestor::label').click();expect(page.get_by_role('checkbox',name='添加学员')).not_to_be_checked();state['fail']=True;page.get_by_role('button',name=re.compile('确.*定')).click();expect(page.get_by_text('清单更新失败，请重试',exact=True)).to_be_visible();key=mutations[-1][1]['idempotencyKey'];state['fail']=False;page.get_by_role('button',name=re.compile('确.*定')).click();expect(page.get_by_text('已完成 0 / 2',exact=True)).to_be_visible();assert mutations[-1][1]['idempotencyKey']==key
 assert not errors,errors;browser.close()
print('PASS desktop/mobile overview and form, empty checklist, plan branches, hidden-field reset, update/correction, retry idempotency, denied/empty; screenshots',out)
