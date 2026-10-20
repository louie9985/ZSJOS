# -*- coding: utf-8 -*-
"""Synthetic browser verification; all reads/writes intercepted, no shared data mutation."""
import os,re
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright,expect
out=Path(os.environ['TEMP'])/'zsjos-education-team-browser-20261020';out.mkdir(parents=True,exist_ok=True)
state={'mode':'ok'};errors=[];mutations=[];calls=[]
member=dict(userId=2,name='测试教务甲',username='fixture-education',accountStatus=0,departmentName='测试交付部',postNames=['学习规划师'],educationOwnershipSupported=True,orderCreationSupported=True,todayNewIntentionCount=5,todayOldIntentionFollowUpRecordCount=12,todayStudentContactCount=7,todayPendingCount=2,todayFollowUpTotalCount=4,todayFollowUpRemainingCount=2,todayReceivedCount=3,todayQualifiedCount=1,todayFollowUpRecordCount=4,todayOrderAmount=2000,pendingQualificationCount=2,todayFollowUpStatus='incomplete',firstFollowTimeoutCount=0,suspendedLeadCount=1,validLeadCount=3,convertedLeadCount=1,effectiveOrderCount=1,effectiveOrderAmount=2000,categoryCounts=[dict(value='fixture',label='测试分类',count=3,configured=True)])
lead=dict(id=5,leadNo='KZ-EDU-005',submittedName='测试客资',submittedMobile='13800000000',leadCategoryLabelSnapshot='测试分类',sourceChannelLabelSnapshot='来源快照',statusLabel='已挂起',status='suspended',ownerIdentity='education',assignmentStatus='owned',availableActions=[dict(code='EDUCATION_TEAM_'+action,enabled=True) for action in ['TRANSFER','RESTORE','RECYCLE']],visibleTabs=['overview'])
def handler(route):
 path=urlparse(route.request.url).path;calls.append(path)
 assert '/subordinate-sales' not in path,path
 if route.request.method in ['PUT'] or '/leads/batch-' in path:
  mutations.append((path,route.request.post_data_json));data=True if 'account-status' in path else dict(successCount=1,failureCount=0,items=[dict(leadId=5,leadNo='KZ-EDU-005',success=True,code='SUCCESS',message='操作成功')]);route.fulfill(json={'code':0,'data':data});return
 if path.endswith('/advanced-filter/catalog'):data=dict(fields=[dict(fieldKey='educationTeam.accountStatus',label='账号状态',valueType='select',operators=['in'],supportedScenes=['education_team'],options=[dict(value='0',label='启用'),dict(value='1',label='停用')])],relativeDateOptions=[])
 elif '/advanced-filter-template/' in path:data=[]
 elif path.endswith('/education-team/search-page'):
  if state['mode']=='error':route.fulfill(json={'code':1900003050,'msg':'无权查看教务团队'});return
  data=dict(list=[] if state['mode']=='empty' else [member],total=0 if state['mode']=='empty' else 1)
 elif path.endswith('/overview'):data=member
 elif path.endswith('/leads/search-page'):data=dict(list=[lead],total=1)
 elif path.endswith('/tasks'):data=dict(list=[dict(id=7,taskType='lead_follow_up_reminder',leadId=5,leadNo='KZ-EDU-005',leadName='测试客资',dueAt=1792296000000,overdue=False)],total=1)
 elif path.endswith('/leads/5'):data=lead
 elif path.endswith('/batch-transfer-candidates'):data=[dict(id=3,nickname='测试教务乙')]
 else:data=[]
 route.fulfill(json={'code':0,'data':data})
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True);page=browser.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.route('**/admin-api/**',handler)
 for width in [1440,390]:
  page.set_viewport_size({'width':width,'height':950});page.goto('http://127.0.0.1:5174/test/education-team.html')
  expect(page.get_by_role('heading',name='教务团队',exact=True)).to_have_count(0);expect(page.locator('.education-team-header')).to_have_count(0);expect(page.locator('.education-team-card')).to_be_visible()
  toolbar=page.locator('.education-team-toolbar');expect(toolbar.get_by_role('button',name=re.compile('刷.*新'))).to_be_visible()
  assert toolbar.evaluate('(el) => Math.abs(el.getBoundingClientRect().top - el.parentElement.getBoundingClientRect().top - parseFloat(getComputedStyle(el.parentElement).paddingTop)) < 1')
  if width==1440:assert toolbar.bounding_box()['height']<60
  previous=calls.count('/admin-api/zsjos/education-team/search-page');toolbar.get_by_role('button',name=re.compile('刷.*新')).click();expect(page.locator('.education-team-card')).to_be_visible();assert calls.count('/admin-api/zsjos/education-team/search-page')==previous+1
  expect(page.locator('.education-team-card')).to_contain_text('新意向5老意向跟进次数12接收客资数3')
  expect(page.locator('.education-team-card')).to_contain_text('今日学员联系数7')
  expect(page.locator('.education-team-card')).not_to_contain_text('剩余未判定')
  page.screenshot(path=str(out/f'list-{width}.png'),full_page=True);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  page.locator('.education-team-card').click();expect(page.get_by_role('tab',name='员工概况')).to_be_visible();page.get_by_role('tab',name='名下客资').click();expect(page.get_by_role('button',name='KZ-EDU-005')).to_be_visible()
  page.screenshot(path=str(out/f'leads-{width}.png'),full_page=True);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
 page.set_viewport_size({'width':1440,'height':950});page.get_by_role('checkbox').last.check();page.get_by_role('button',name=re.compile('转.*派')).click()
 page.get_by_role('combobox',name='目标教务人员').click();page.get_by_text('测试教务乙',exact=True).click();page.get_by_role('textbox',name='操作原因').fill('测试团队内转派');page.get_by_role('button',name=re.compile('确.*定')).click();expect(page.get_by_text('操作成功',exact=False)).to_be_visible();assert mutations[-1][0].endswith('/education-team/leads/batch-transfer');assert mutations[-1][1]['targetUserId']==3
 page.get_by_role('button',name=re.compile('关.*闭')).click();page.get_by_role('tab',name='跟进任务').click();expect(page.get_by_text('跟进提醒',exact=True)).to_be_visible();page.get_by_role('button',name='KZ-EDU-005').click();expect(page.get_by_text('来源快照',exact=True)).to_be_visible();page.get_by_role('button',name='返回员工详情').click()
 page.get_by_role('button',name='停用账号',exact=True).click();page.get_by_role('textbox',name='账号调整原因').fill('测试账号状态');page.get_by_role('button',name=re.compile('确.*定')).click();expect(page.get_by_text('账号状态已更新',exact=True)).to_be_visible();assert mutations[-1][0].endswith('/education-team/2/account-status')
 state['mode']='error';page.reload();expect(page.get_by_text('无权查看教务团队',exact=True)).to_be_visible();state['mode']='empty';page.get_by_role('button',name=re.compile('重.*试')).click();expect(page.get_by_text('当前范围暂无教务人员，请检查交付组织配置和管理范围',exact=True)).to_be_visible()
 state['mode']='ok';page.goto('http://127.0.0.1:5174/test/education-team.html?denied=1');page.locator('.education-team-card').click();expect(page.get_by_role('button',name='停用账号',exact=True)).to_have_count(0);page.get_by_role('tab',name='名下客资').click();expect(page.get_by_role('button',name=re.compile('转.*派'))).to_have_count(0)
 assert not errors,errors;browser.close()
print('PASS education team desktop/mobile, independent APIs, leads/tasks/detail, transfer, account, error/retry/empty, sales-permission denial; screenshots',out)
