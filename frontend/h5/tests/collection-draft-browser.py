# -*- coding: utf-8 -*-
"""Production H5 with isolated draft API fixtures; no real personal data or business writes."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT=Path(__file__).resolve().parents[1]
OUT=Path(os.environ.get('TEMP','/tmp'))/'zsjos-collection-draft'
OUT.mkdir(parents=True,exist_ok=True)
FIELDS=[dict(key=k,label=l,type=t,group='basic',enabled=True,required=k=='name',sensitive=True)
        for k,l,t in [('name','姓名','text'),('mobile','手机号','text'),('id_card','身份证号码','text'),('age','年龄','text'),('ethnicity','民族','dict')]]

with sync_playwright() as p:
    browser=p.chromium.launch()
    for width in [1440,390]:
        context=browser.new_context(viewport=dict(width=width,height=1000))
        page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        state=dict(values={},revision=0,fail=False,hold=False,submitted=False,conflict=False)
        requests=[];held=[];submissions=[]

        def reply(route,data=None,code=0,msg=''):
            route.fulfill(content_type='application/json',body=json.dumps(dict(code=code,data=data,msg=msg)))

        def route_request(route):
            url=route.request.url;path=url.split('example.test',1)[-1].split('?',1)[0]
            if path.startswith('/public-api/'):
                assert route.request.headers.get('x-student-info-token')=='a'*43
                assert not route.request.headers.get('authorization')
                if path.endswith('/detail'):
                    return reply(route,dict(status='SUBMITTED' if state['submitted'] else 'DRAFT',tenantId=1,
                        fields=[] if state['submitted'] else FIELDS,images=[],options=dict(ethnicity=[dict(value='01',label='测试民族')]),
                        draft=dict(revision=state['revision'],values=state['values'],savedAt='2026-10-21 16:00:00' if state['revision'] else None)))
                if path.endswith('/draft'):
                    assert route.request.method=='PUT'
                    data=route.request.post_data_json;requests.append(data)
                    assert 'age' not in data['values']
                    if data['revision']!=state['revision']:return reply(route,code=1900090011,msg='草稿已在其他页面更新，请保留当前内容后重新打开链接')
                    if state['fail']:state['fail']=False;return reply(route,code=500,msg='草稿保存失败，请重试')
                    if state['hold']:held.append((route,data));return
                    assert data['revision']==state['revision'],(data,state)
                    state['revision']+=1;state['values']=data['values']
                    return reply(route,dict(revision=state['revision'],values=state['values'],savedAt='2026-10-21 16:00:00'))
                if path.endswith('/submit'):
                    assert not held,'submit must wait for in-flight draft'
                    data=route.request.post_data_json
                    if data.get('revision')!=state['revision']:return reply(route,code=1900090011,msg='草稿已在其他页面更新')
                    submissions.append(data);state['submitted']=True;state['values']={}
                    return reply(route,True)
                raise AssertionError(path)
            if path.startswith(('/app-api/','/part-api/')):return reply(route,{})
            file=ROOT/'dist'/path.lstrip('/')
            if not file.is_file():file=ROOT/'dist/index.html'
            return route.fulfill(path=str(file),content_type={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json'}.get(file.suffix,'application/octet-stream'))

        page.route('**/*',route_request)
        page.goto('https://example.test/student-info-form#token='+'a'*43)
        expect(page.locator('#name')).to_be_visible()
        status=page.locator('.collection-draft')
        # A second tab has committed a newer draft while this tab has no pending edits.
        page.locator('#name').fill('older fixture')
        expect(status).to_contain_text('草稿已保存')
        state['revision']+=1;state['values']={**state['values'],'name':'newer fixture'}
        page.get_by_role('button',name='提交信息',exact=True).click();page.get_by_role('button',name='确认',exact=True).click()
        expect(page.locator('.van-notice-bar')).to_contain_text('其他页面更新')
        assert not submissions and state['values']['name']=='newer fixture'
        state.update(values={},revision=0);page.reload();expect(page.locator('#name')).to_be_visible()
        status=page.locator('.collection-draft')
        expect(status).to_contain_text('填写后自动保存草稿')
        page.locator('#name').fill('合成草稿')
        page.locator('#mobile').fill('1')
        expect(status).to_contain_text('草稿已保存')
        assert state['values']['mobile']=='1'
        page.locator('#ethnicity').click()
        if width > 600:
            page.locator('.desktop-select-panel:visible').get_by_role('option',name='测试民族',exact=True).click()
        else:
            page.locator('.van-picker:visible .van-picker__confirm').click()
        expect(status).to_contain_text('草稿已保存')
        assert state['values']['ethnicity']=='01'
        page.reload();expect(page.locator('#name')).to_have_value('合成草稿');expect(page.locator('#mobile')).to_have_value('1')
        expect(page.locator('#ethnicity')).to_contain_text('测试民族')
        state['fail']=True;page.locator('#name').fill('重试后的草稿')
        expect(status).to_contain_text('草稿保存失败')
        expect(page.locator('#name')).to_have_value('重试后的草稿')
        status.get_by_role('button',name='重试保存').click();expect(status).to_contain_text('草稿已保存')
        # Changes during an in-flight request must cause a later serialized save.
        state['hold']=True;page.locator('#name').fill('第一版')
        expect(status).to_contain_text('草稿保存中')
        page.wait_for_timeout(800);assert len(held)==1
        page.locator('#name').fill('第二版')
        route,data=held.pop();state['revision']+=1;state['values']=data['values'];state['hold']=False
        reply(route,dict(revision=state['revision'],values=state['values'],savedAt='2026-10-21 16:00:00'))
        expect(status).to_contain_text('草稿已保存')
        page.wait_for_function("document.querySelector('.collection-draft').textContent.includes('草稿已保存')")
        assert state['values']['name']=='第二版',state
        page.reload();expect(page.locator('#name')).to_have_value('第二版')
        # A conflict must never silently overwrite a second page's draft or submit.
        state['revision']+=1;state['values']={**state['values'],'name':'其他页面的新内容'}
        page.locator('#name').fill('冲突中的本地内容')
        expect(status).to_contain_text('其他页面更新')
        page.get_by_role('button',name='提交信息',exact=True).click()
        expect(page.locator('.van-notice-bar')).to_contain_text('其他页面更新')
        assert not submissions
        expect(status.get_by_role('button',name='重试保存')).to_have_count(0)
        status.get_by_role('button',name='重新加载最新草稿').click()
        page.get_by_role('button',name='取消',exact=True).click()
        expect(page.locator('#name')).to_have_value('冲突中的本地内容')
        status.get_by_role('button',name='重新加载最新草稿').click()
        page.get_by_role('button',name='确认',exact=True).click()
        expect(page.locator('#name')).to_have_value('其他页面的新内容')
        assert state['values']['name']=='其他页面的新内容'
        page.locator('#name').fill('冲突解决后的内容');expect(status).to_contain_text('草稿已保存')
        expect(page.locator('.van-notice-bar')).to_have_count(0)
        assert not page.evaluate("Object.values(localStorage).some(v=>v.includes('冲突中的本地内容'))")
        page.screenshot(path=str(OUT/f'draft-{width}.png'),full_page=True)
        # Submit before the debounce fires: final request contains current contents.
        page.locator('#name').fill('最终提交')
        page.get_by_role('button',name='提交信息',exact=True).click();page.get_by_role('button',name='确认',exact=True).click()
        expect(page.locator('.van-empty')).to_contain_text('信息已提交')
        assert submissions[0]['values']['name']=='最终提交'
        before=len(requests);page.wait_for_timeout(900);assert len(requests)==before
        page.reload();expect(page.locator('.van-empty')).to_contain_text('信息已提交')
        assert page.locator('.collection-draft').count()==0
        assert not errors,errors
        context.close();print('PASS',width,'restore/unfinished text/error/retry/serialized edits/conflict/final submit/no local cache')
    browser.close()
