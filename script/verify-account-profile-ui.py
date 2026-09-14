#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Browser acceptance against real Workbench components with synthetic API fixtures."""
import json, re
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
fields=json.loads(re.search(r"SET defaults_json=CAST\('(\[.*\])' AS JSON",(ROOT/'script/sql/mysql/migrations/V209__media_account_profile.sql').read_text(encoding='utf-8')).group(1))
output=ROOT/'output/account-profile-acceptance'
output.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for role in ['DIRECTOR','OPERATOR','OPERATOR-DENIED']:
        page=browser.new_page(viewport={'width':1440,'height':1000})
        errors=[];page.on('pageerror',lambda e: errors.append(str(e)))
        state={'version':0,'values':{},'entries':[],'created':False,'profile_requests':0}
        def account(): return {'id':777,'accountNo':'TEST-777','nickname':state['values'].get('nickname'),'platformLabel':None,'primaryProblems':[],'detailSnapshots':[],'taskLine':[],'version':state['version'],'availableActions':['MAINTAIN_ACCOUNT','VIEW_ACCOUNT_HISTORY']}
        student={'personId':900001,'personNo':'TEST-STUDENT','name':'示例学员','mobile':'','services':[{'serviceRelationId':900001,'status':'active','version':1}]}
        def route(r):
            url=r.request.url;method=r.request.method;data=[]
            if '/profile' in url: state['profile_requests']+=1
            if '/zsjos/media-account/create' in url:
                assert role=='DIRECTOR'
                command=r.request.post_data_json
                assert command['detailValues']=={} and 'platformValue' not in command and 'nickname' not in command
                assert command['serviceRelationId']==900001 and command['idempotencyKey']
                state['created']=True;data=777
            elif '/profile/files' in url:
                data={'id':909,'name':'review.pdf','type':'application/pdf','size':12,'previewUrl':'https://example.invalid/review.pdf'}
            elif '/profile/records' in url:
                command=r.request.post_data_json
                assert role=='DIRECTOR' and command['fieldKey']=='diagnosis_7d' and command['fileIds']==[909]
                assert command['version']==state['version']
                state['version']+=1
                state['entries'].insert(0,{'id':1,'kind':'RECORD','fieldKey':'diagnosis_7d','title':'7 天账号数据诊断','content':command['content'],'snapshots':[],'files':[{'id':909,'name':'review.pdf','previewUrl':'https://example.invalid/review.pdf'}],'operatedBy':'示例编导','operatedAt':'2026-09-11T14:00:00','resultVersion':state['version']})
                data=state['version']
            elif '/profile/history' in url: data={'list':state['entries'],'total':len(state['entries'])}
            elif url.split('?')[0].endswith('/profile'):
                if method=='PUT':
                    command=r.request.post_data_json
                    assert command['version']==state['version']
                    assert all(next(f for f in fields if f['key']==k)['ownerType']==role for k in command['changes'])
                    state['values'].update(command['changes']);state['version']+=1;data=state['version']
                else:
                    missing=[f['key'] for f in fields if f['enabled'] and f['requiredForComplete'] and f['ownerType'] in ['DIRECTOR','OPERATOR'] and f['type']!='record' and not state['values'].get(f['key'])]
                    data={'account':account(),'config':{'id':3,'versionNo':3,'fields':fields},'values':{**state['values'],'account_no':'TEST-777','student_name':'示例学员'},'snapshots':[],'editableFields':[f['key'] for f in fields if f['ownerType']==role],'missingFields':missing,'missingByOwner':{},'sourceNotes':{},'files':{},'studentName':'示例学员','directorName':'示例编导','operatorName':'示例运营','canViewHistory':True}
            elif '/maintenance-history' in url: data={'list':[],'total':0}
            elif '/system/dict-data/simple-list' in url: data=[]
            elif '/zsjos/media-students/page' in url: data={'list':[student],'total':1}
            elif '/zsjos/media-students/900001' in url: data={'student':student,'accounts':[account()] if role.startswith('OPERATOR') or state['created'] else [],'positioningCards':[],'positioningDrafts':[],'contents':[],'productionTickets':[],'operationTimeline':[],'studentTaskLine':[],'pendingStats':{'accountCount':0,'positioningCount':0,'contentCount':0,'productionCount':0}}
            elif '/contact-context' in url: data={'serviceRelationId':900001,'version':1,'availableActions':['CREATE_MEDIA_ACCOUNT'] if role=='DIRECTOR' else [],'directorStage':'positioning_interview_completed','deliveryStages':[]}
            elif '/positioning-interview' in url: data={'studentPersonId':900001,'serviceRelationId':900001,'status':'completed','version':1,'fields':[],'items':[],'attachments':[],'statusOptions':{},'availableActions':[]}
            r.fulfill(json={'code':0,'data':data})
        page.route('**/admin-api/**',route)
        page.goto('http://127.0.0.1:5174/test/account-profile.html?'+role.lower())
        page.get_by_role('tab',name='概览',exact=True).wait_for()
        if role=='OPERATOR-DENIED':
            page.get_by_role('tab',name='未命名账号',exact=False).click()
            page.get_by_text('暂无账号档案查看权限，请联系管理员',exact=True).wait_for()
            for width in [1440,360]:
                page.set_viewport_size({'width':width,'height':1000})
                page.screenshot(path=str(output/f'permission-denied-{width}.png'))
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            assert state['profile_requests']==0, 'Unauthorized client must not request profile or history'
            assert page.get_by_role('button',name=re.compile('维护账号表')).count()==0
            assert not errors,errors
            page.close()
            continue
        if role=='DIRECTOR':
            page.wait_for_timeout(1000)
            page.get_by_role('button',name=re.compile('新增账号')).click()
            page.get_by_role('dialog',name='维护账号表',exact=False).wait_for()
            assert state['created']
        else:
            assert page.get_by_role('button',name=re.compile('新增账号')).count()==0
            page.get_by_role('tab',name='未命名账号',exact=False).click()
            page.get_by_role('button',name=re.compile('维护账号表')).click()
        dialog=page.get_by_role('dialog',name='维护账号表',exact=False)
        if role=='DIRECTOR':
            dialog.get_by_label('学员加入目标',exact=True).fill('希望建立稳定内容产出')
            dialog.get_by_role('button',name='账号状态 · 待补',exact=False).click()
            assert dialog.get_by_label('账号名称',exact=True).count()==0
        else:
            dialog.get_by_label('账号名称',exact=True).fill('示例账号')
            dialog.get_by_role('button',name='账号定位卡 · 待补',exact=False).click()
            assert dialog.get_by_label('学员加入目标',exact=True).count()==0
        dialog.get_by_role('button',name=re.compile(r'^保\s*存$')).click()
        page.wait_for_function('document.querySelectorAll(".ant-message-success").length > 0')
        page.wait_for_timeout(400)
        assert dialog.is_visible(), 'Save must leave the editor open'
        assert state['version']==1
        dialog.get_by_role('button',name='保存并返回',exact=True).click()
        dialog.wait_for(state='hidden')
        assert state['version']==2
        if role=='DIRECTOR':
            row=page.locator('.account-profile-row').filter(has_text=next(f['label'] for f in fields if f['key']=='diagnosis_7d'))
            row.get_by_role('button',name='填写记录',exact=True).click()
            record=page.get_by_role('dialog',name=next(f['label'] for f in fields if f['key']=='diagnosis_7d'),exact=True)
            record.get_by_label('复盘记录内容').fill('本次诊断作为独立历史记录保留')
            record.locator('input[type=file]').set_input_files({'name':'review.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4 test'})
            record.get_by_text('review.pdf · 查看/下载',exact=True).wait_for()
            record.get_by_role('button',name='追加记录',exact=True).click()
            record.wait_for(state='hidden')
            page.locator('.account-profile-history').get_by_text('本次诊断作为独立历史记录保留',exact=True).wait_for()
            assert page.locator('.account-profile-history').get_by_role('link').get_attribute('href')=='https://example.invalid/review.pdf'
        for width in [1440,736,360]:
            page.set_viewport_size({'width':width,'height':1000})
            page.wait_for_timeout(250)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'{role} detail overflow at {width}'
            sections=page.locator('[data-profile-section]')
            assert sections.count()==3
            assert sections.locator('.account-profile-section-heading h5').all_text_contents()==['账号定位卡','账号状态','账号复盘记录']
            assert page.locator('[data-profile-section="POSITIONING"] [data-profile-key="positioning_history"]').count()==1
            assert page.locator('[data-profile-section="STATUS"] [data-profile-key="nickname"]').count()==1
            assert page.locator('[data-profile-section="REVIEW"] .account-profile-history').count()==1
            assert page.get_by_text('改版前的维护历史',exact=True).count()==0
            assert page.get_by_text('内容生产历史',exact=True).count()==0
            if width==1440:
                boxes=[sections.nth(i).bounding_box() for i in range(3)]
                assert max(b['y'] for b in boxes)-min(b['y'] for b in boxes)<2, boxes
                assert boxes[0]['x']<boxes[1]['x']<boxes[2]['x'], boxes
                status=page.locator('[data-profile-status]')
                assert status.nth(0).bounding_box()['x'] < status.nth(1).bounding_box()['x']
            page.screenshot(path=str(output/f'{role.lower()}-detail-{width}.png'),full_page=False)
        page.set_viewport_size({'width':1440,'height':1000})
        page.get_by_role('button',name=re.compile('维护账号表')).click()
        dialog.wait_for()
        for width in [1440,736,360]:
            page.set_viewport_size({'width':width,'height':1000})
            page.wait_for_timeout(250)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'{role} page overflow at {width}'
            assert dialog.evaluate('(e) => e.scrollWidth <= e.clientWidth + 1'), f'{role} dialog overflow at {width}'
            page.screenshot(path=str(output/f'{role.lower()}-{width}.png'),full_page=False)
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: empty creation, ownership, partial save stays open, append record with attachment, desktop/736/360 without overflow')
