"""UTF-8. Real Chromium interactions using isolated synthetic API fixtures, no live data writes."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
OUT=Path(__file__).resolve().parents[1]/'output/partner-assignment'
OUT.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in (390,768):
      for scenario in ('configured','empty','unavailable','load-error','submit-error'):
        page=browser.new_page(viewport={'width':width,'height':900})
        page.add_init_script("localStorage.setItem('h5_access_token','synthetic-fixture');")
        state={'fail':scenario=='load-error','submitFail':scenario=='submit-error'}; writes=[]
        def api(route):
            path=route.request.url.split('?')[0]; data={}
            if path.endswith('/auth/permission-info'): data={'user':{'id':1,'nickname':'测试兼职'},'permissions':['zsjos:lead:submit'],'roles':[]}
            elif path.endswith('/lead/product/catalog'): data={'categoryTree':[],'spus':[],'skus':[]}
            elif path.endswith('/system/area/tree'): data=[{'id':110000,'name':'测试省','children':[{'id':110100,'name':'测试市'}]}]
            elif '/dict-data/type' in path: data=[{'value':'fixture','label':'测试选项'}]
            elif path.endswith('/lead/assignment-options'):
                if state['fail']: route.fulfill(status=503,content_type='application/json',body='{}');return
                data={'configured':scenario!='empty','specifiedAvailable':scenario not in ('empty','unavailable'),'reason':'指定接单人暂不可用，请选择自动分配或联系管理员' if scenario=='unavailable' else None}
            elif path.endswith('/lead/create'):
                writes.append(route.request.post_data_json)
                if state['submitFail']:
                    route.fulfill(content_type='application/json',body=json.dumps({'code':1045090003,'msg':'指定接单人暂不可用，请选择自动分配或联系管理员','data':None},ensure_ascii=False));return
                data={'leadId':1,'leadNo':'TEST-LEAD-001','outcome':'created','assignmentStatus':'pending_acceptance'}
            route.fulfill(content_type='application/json; charset=utf-8',body=json.dumps({'code':0,'data':data},ensure_ascii=False))
        page.route('**/*-api/**',api)
        page.goto('http://localhost:10086/lead/submit')
        page.get_by_placeholder('请输入姓名',exact=True).fill('测试客户')
        page.get_by_placeholder('请输入微信号').fill('fixture-contact')
        page.get_by_placeholder('请选择省/市').click()
        page.get_by_text('测试省',exact=True).click(); page.get_by_text('测试市',exact=True).click()
        page.get_by_role('button',name='下一步',exact=True).click()
        page.get_by_role('button',name='未明确课程').click()
        page.get_by_role('button',name='下一步',exact=True).click()
        page.get_by_text('来源渠道',exact=True).first.click(); page.get_by_role('radio',name='测试选项',exact=True).click()
        page.get_by_text('客资分类',exact=True).first.click(); page.get_by_role('radio',name='测试选项',exact=True).click()
        page.get_by_placeholder('可选，描述客户需求').fill('保留表单测试')
        page.get_by_role('button',name='下一步',exact=True).click()
        if scenario=='empty': expect(page.get_by_text('分配方式',exact=True)).to_have_count(0)
        elif scenario=='load-error':
            expect(page.get_by_role('button',name='确认提交',exact=True)).to_be_disabled()
            state['fail']=False; page.get_by_role('button',name='重试',exact=True).click()
            expect(page.get_by_role('radio',name='指定分配',exact=True)).to_be_visible()
        elif scenario=='unavailable':
            expect(page.get_by_role('radio',name='指定分配',exact=True)).to_be_disabled()
        else:
            page.get_by_role('radio',name='指定分配',exact=True).click()
        expect(page.locator('.van-toast')).to_be_hidden(timeout=6000)
        page.screenshot(path=str(OUT/f'{scenario}-{width}.png'),full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        page.get_by_role('button',name='确认提交',exact=True).click()
        if scenario=='submit-error':
            expect(page.get_by_role('button',name='改选自动分配')).to_be_visible()
            expect(page.get_by_text('保留表单测试',exact=True)).to_be_visible()
            assert writes[-1]['dispatchMode']=='specified'
            state['submitFail']=False
            expect(page.locator('.van-toast')).to_be_hidden(timeout=6000)
            page.get_by_role('button',name='改选自动分配').click()
            page.get_by_role('button',name='确认提交',exact=True).click()
        expect(page.get_by_text('提交成功',exact=True)).to_be_visible()
        assert 'specifiedSalesUserId' not in writes[-1] and 'specifiedOwnerIdentity' not in writes[-1]
        assert writes[-1]['dispatchMode']==('specified' if scenario=='configured' else 'auto')
        print('PASS H5',width,scenario)
        page.close()
    browser.close()
