# UTF-8. Isolated real Chromium; only synthetic API responses, no shared business mutations.
import json
from pathlib import Path
from urllib.parse import urlparse,parse_qs
from playwright.sync_api import sync_playwright,expect
OUT=Path('D:/ZSJ-OS-backups/sales-performance-browser');OUT.mkdir(parents=True,exist_ok=True)
nodes=[dict(key='ORG:10',title='销售转化中心',scopeType='CENTER',scopeId=10,selectable=True),dict(key='ORG:11',parentKey='ORG:10',title='销售转化一部',scopeType='DEPT',scopeId=11,selectable=True),dict(key='USER:1',parentKey='ORG:11',title='测试销售甲',scopeType='USER',scopeId=1,selectable=True)]
def metric(k,label):return dict(key=k,label=label,start='2026-09-01T00:00:00',end='2026-09-22T12:00:00',amount=128000,orders=42,converted=30,denominator=110,rate=30/110,average=3200)
def target(period='month'):return dict(id=1,scopeType='USER',scopeId=1,name='测试销售甲',periodType=period,periodStart='2026-09-01',automaticFloor=160000,automaticSprint=200000,floorAmount=160000,sprintAmount=200000,manual=True,complete=True,missing=0,version=0)
groups=[dict(key='A',label='分类甲',amount=80000,count=25,share=.625),dict(key='B',label='分类乙',amount=48000,count=17,share=.375)]
errors=[]
mode={"overview":"ok"}
mutations=[]
def handler(route):
 path=urlparse(route.request.url).path;query=parse_qs(urlparse(route.request.url).query)
 if route.request.method!='GET':mutations.append(route.request.post_data_json);route.fulfill(json={'code':0,'data':True});return
 if path.endswith('/overview') and mode['overview']=='error':route.fulfill(json={'code':1900090001,'msg':'无权查看该业绩范围'});return
 if path.endswith('/tree'):data=nodes
 elif path.endswith('/overview'):data=dict(asOf='2026-09-22T12:00:00',targets=[dict(key=k,label=l,actual=metric(k,l),target=target()) for k,l in [('lastWeek','上周'),('week','本周'),('month','本月'),('quarter','本季度'),('year','本年')]],performance=[metric(k,l) for k,l in [('today','今日'),('week','本周'),('month','本月'),('quarter','本季度'),('year','本年'),('last7','近7日'),('last30','近30日'),('last60','近60日'),('last90','近90日')]],conversion=[metric(k,l) for k,l in [('month','本月'),('lastMonth','上月'),('last7','近7日'),('last30','近30日'),('last60','近60日'),('last90','近90日')]],pending=dict(accept=2,qualification=3,todayFollowUp=5,overdueFollowUp=2,missingTarget=0),missingAttributionOrders=0,missingAttributionAmount=0,canDetail=True)
 elif path.endswith('/analysis'):data=dict(asOf='2026-09-22T12:00:00',start='2026-09-01',end='2026-09-22',averages=[metric(k,l) for k,l in [('all','整体'),('inbound','线上引流'),('self','非引流'),('repurchase','复购')]],trend=[metric('2026-09-01','9月1日')],sources=groups,products=groups,contributors=groups,target=target(),averageTrends={k:[dict(metric(str(d),f'9月{d}日'),average=2500+d*100+i*350) for d in range(1,8)] for i,k in enumerate(['all','inbound','self','repurchase'])},contributionMetrics=[dict(userId=1,name='测试销售甲',actual=metric('user','测试销售甲'),floorRate=.8,sprintRate=.64,share=1)])
 elif path.endswith('/leads'):data=dict(asOf='2026-09-22T12:00:00',workload=dict(assigned=12,missed=1,received=11,valid=8,followUps=18),categories=groups,stages=groups,calendar=[dict(date=f'2026-09-{d:02}',received=10,valid=4,invalid=2,pending=1,overdue=2,ended=1,lateCompleted=1,onTime=5,dueCount=8) for d in range(1,23)],funnel=groups,followUp=groups)
 elif path.endswith('/history') and 'target' not in path:data=[dict(month=m,amount=m*10000,previousAmount=m*8500) for m in range(1,13)]
 elif path.endswith('/details'):data=dict(list=[dict(id=1,number='DD-TEST-001',kind='order',label='线上引流',occurredAt='2026-09-21',amount=1000,state='审批通过')],total=1)
 elif path.endswith('/list'):data=[target()]
 elif path.endswith('/organizations'):data=[dict(deptId=10,centerId=10,kind='CENTER'),dict(deptId=11,centerId=10,kind='DEPT')]
 elif path.endswith('/organization-candidates'):data=nodes[:2]
 else:data=[]
 route.fulfill(json={'code':0,'data':data})
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 page=browser.new_page(viewport={'width':1600,'height':1100});page.on('pageerror',lambda e:errors.append(str(e)))
 page.route('**/zsjos/sales-performance**',handler)
 page.goto('http://127.0.0.1:5174/test/sales-performance.html')
 expect(page.locator('.performance-target')).to_have_count(5)
 expect(page.locator('.performance-panel').filter(has=page.get_by_text('多周期业绩',exact=True)).locator('button')).to_have_count(9)
 expect(page.locator('.performance-panel').filter(has=page.get_by_text('多周期成交率',exact=True)).locator('button')).to_have_count(6)
 page.screenshot(path=str(OUT/'overview-desktop.png'),full_page=True)
 mode['overview']='error';page.locator('.performance-heading button').first.click();expect(page.get_by_text('无权查看该业绩范围',exact=True)).to_be_visible()
 mode['overview']='ok';page.locator('.ant-alert button').filter(has_text='重').first.click();expect(page.locator('.performance-target')).to_have_count(5)
 page.get_by_text('销售转化一部',exact=True).first.click();expect(page.get_by_role('heading',name='销售转化中心 / 销售转化一部',exact=True)).to_be_visible()
 page.get_by_role('tab',name='经营分析').click();expect(page.get_by_text('平均客单价',exact=True)).to_be_visible();page.screenshot(path=str(OUT/'analysis-desktop.png'),full_page=True)
 expect(page.get_by_role('img',name='各来源客单价趋势').locator('circle')).to_have_count(28)
 page.set_viewport_size({'width':390,'height':844});assert page.evaluate('document.documentElement.scrollWidth<=innerWidth');page.screenshot(path=str(OUT/'analysis-mobile.png'),full_page=True);page.set_viewport_size({'width':1600,'height':1100})
 page.get_by_role('tab',name='客资与跟进').click();expect(page.locator('.performance-day')).to_have_count(22);page.screenshot(path=str(OUT/'leads-desktop.png'),full_page=True)
 page.set_viewport_size({'width':390,'height':844});assert page.evaluate('document.documentElement.scrollWidth<=innerWidth');page.screenshot(path=str(OUT/'leads-mobile.png'),full_page=True);page.set_viewport_size({'width':1600,'height':1100})
 page.locator('.performance-day').first.click();expect(page.get_by_text('DD-TEST-001')).to_be_visible();page.locator('.ant-drawer-close').last.click()
 page.set_viewport_size({'width':390,'height':844});page.get_by_role('tab',name='业绩总览').click();expect(page.locator('.performance-target')).to_have_count(5)
 assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),'mobile overflow'
 page.screenshot(path=str(OUT/'overview-mobile.png'),full_page=True)
 page.goto('http://127.0.0.1:5174/test/sales-performance.html?target=1');expect(page.get_by_placeholder('填写调整原因（必填，将保留历史修订记录）')).to_be_visible();page.screenshot(path=str(OUT/'targets-mobile.png'),full_page=True)
 page.set_viewport_size({'width':1600,'height':1100});page.screenshot(path=str(OUT/'targets-desktop.png'),full_page=True)
 page.get_by_role('spinbutton',name='测试销售甲保底目标').fill('170000');page.get_by_role('spinbutton',name='测试销售甲冲刺目标').fill('220000');page.get_by_placeholder('填写调整原因（必填，将保留历史修订记录）').fill('测试目标修订');page.get_by_role('button',name='保存 1 项修改').click()
 expect(page.get_by_text('目标已保存',exact=True)).to_be_visible();assert mutations[-1]['items'][0]['floorAmount']==170000 and mutations[-1]['items'][0]['sprintAmount']==220000
 assert not errors,errors
 print('PASS real Chrome desktop/mobile: five targets, nine performance, six conversion, tabs, calendar, drilldown, target page, no runtime errors')
 browser.close()
