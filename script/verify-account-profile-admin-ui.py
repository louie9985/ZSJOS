#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Vue configuration consumer acceptance with synthetic service responses."""
import json,re
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
fields=json.loads(re.search(r"SET defaults_json=CAST\('(\[.*\])' AS JSON",(root/'script/sql/mysql/migrations/V209__media_account_profile.sql').read_text(encoding='utf-8')).group(1))
config={'published':{'id':3,'versionNo':3,'status':'published','version':0,'fields':fields},'draft':{'id':2,'versionNo':2,'status':'draft','version':0,'fields':fields[:3]}}
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page(viewport={'width':1440,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    def route(r):
        url=r.request.url;data=[]
        if '/media-account-field-config/draft/reconcile' in url:
            config['draft']['fields']=fields;config['draft']['versionNo']=4;config['draft']['version']+=1;data=True
        elif '/media-account-field-config/draft' in url and r.request.method=='PUT':
            req=r.request.post_data_json
            assert all(f['requiredForCreate']==False for f in req['fields'])
            config['draft']['fields']=req['fields'];config['draft']['version']+=1;data=True
        elif '/media-account-field-config' in url: data=config
        elif '/dict-type/simple-list' in url: data=[{'type':t,'name':t,'status':0} for t in ['zsjos_account_platform','zsjos_account_publish_rhythm','zsjos_account_content_format']]
        r.fulfill(json={'code':0,'data':data})
    page.route('**/admin-api/**',route)
    page.goto('http://127.0.0.1/test/account-profile.html')
    page.get_by_text('第三方账号字段配置',exact=True).wait_for()
    page.get_by_role('button',name='保留草稿并合并最新字段',exact=True).click()
    page.get_by_text('当前草稿版本 V4',exact=False).wait_for()
    assert page.get_by_text('填写责任',exact=True).count()>0
    assert page.get_by_text('计入待补',exact=True).count()>0
    for width in [1440,736,360]:
        page.set_viewport_size({'width':width,'height':1000});page.wait_for_timeout(250)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'),f'Admin overflow {width}'
        page.screenshot(path=str(root/f'output/account-profile-acceptance/admin-{width}.png'))
    assert not errors,errors
    browser.close()
print('PASS: Admin field policy configuration, explicit old-draft merge, desktop and mobile widths')
