#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exercise both real frontend components with synthetic versioned template responses."""
import copy
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'output/positioning-interview-retirement'
OUTPUT.mkdir(parents=True, exist_ok=True)
FIELDS = [
    dict(key='studentIdentity', title='学员身份', type='text', enabled=True, required=False, systemField=True, sort=10),
    dict(key='topic', title='定位方向', type='text', enabled=True, required=True, systemField=False, sort=20, interviewNote='了解学员目标', allowRemark=True),
    dict(key='collectedAt', title='采集时间', type='date', enabled=True, required=False, systemField=True, sort=30),
]


def exercise(browser, kind, origin):
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    published = dict(id=11, templateId=10, versionNo=1, status='published', version=0, fields=copy.deepcopy(FIELDS))
    template = dict(id=10, scene='director_positioning_interview', templateCode='test', name='定位访谈大纲', defaultTemplate=True, status='enabled', version=0, published=published, versions=[published])
    state = dict(mode='success', requests=[], saved=False, published=False)

    def respond(route):
        request = route.request
        url = request.url
        if '/zsjos/director-interview-template' in url:
            raise AssertionError('retired questionnaire API called')
        data = []
        if '/zsjos/positioning-interview-template' in url:
            state['requests'].append((request.method, url))
            if state['mode'] == 'error':
                route.fulfill(json=dict(code=500, msg='配置加载失败，请重试')); return
            if state['mode'] == 'empty':
                route.fulfill(json=dict(code=0, data=[])); return
            if request.method == 'GET':
                data = [template]
            elif '/draft/copy' in url:
                template['draft'] = dict(id=12, templateId=10, versionNo=2, status='draft', version=0, fields=copy.deepcopy(published['fields']))
                template['versions'].append(template['draft']); template['version'] += 1
                data = 12
            elif request.method == 'PUT':
                body = request.post_data_json
                assert body['versionId'] == 12
                assert any(field.get('interviewNote') == '新的访谈注意' for field in body['fields'])
                template['draft']['fields'] = body['fields']; template['draft']['version'] += 1
                state['saved'] = True; data = True
            elif '/publish' in url:
                assert state['saved']
                assert request.post_data_json['version'] == template['draft']['version']
                template['published'] = template.pop('draft'); template['published']['status'] = 'published'
                state['published'] = True; data = True
        route.fulfill(json=dict(code=0, data=data))

    page.route('**/admin-api/**', respond)
    url = origin + '/test/director-template.html'
    page.goto(url)
    page.get_by_role('heading', name='定位访谈大纲配置', exact=True).wait_for()
    page.get_by_role('button', name='复制为新草稿' if kind == 'admin' else '复制为草稿', exact=True).click()
    page.get_by_role('button', name='定位方向', exact=False).first.click()
    note = page.get_by_role('textbox', name='访谈注意', exact=True) if kind == 'workbench' else page.locator('.properties .el-form-item').filter(has_text='访谈注意').locator('textarea')
    note.fill('新的访谈注意')
    page.get_by_role('button', name='保存草稿', exact=True).click()
    page.get_by_role('button', name=re.compile(r'^发\s*布$')).click()
    page.wait_for_function('document.body.innerText.includes("已发布")')
    assert state['published']
    page.locator('.ant-message-notice, .el-message').first.wait_for(state='hidden')
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.wait_for_timeout(250)
        page.screenshot(path=str(OUTPUT / f'{kind}-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'{kind}: overflow {width}: ' + str(page.evaluate('[...document.querySelectorAll("body *")].filter(x=>x.getBoundingClientRect().right>innerWidth+1).slice(0,5).map(x=>[x.className,x.getBoundingClientRect().right])'))
    page.goto(url + '?permission=read')
    page.get_by_role('heading', name='定位访谈大纲配置', exact=True).wait_for()
    assert page.get_by_role('button', name='保存草稿', exact=True).count() == 0
    assert page.get_by_role('button', name=re.compile(r'^发\s*布$')).count() == 0
    if kind == 'workbench':
        before = len(state['requests'])
        page.goto(url + '?permission=none')
        page.get_by_text('无权查看定位访谈配置', exact=True).wait_for()
        assert len(state['requests']) == before
    state['mode'] = 'empty'; page.goto(url)
    page.get_by_text('暂无可用模板', exact=True).wait_for()
    state['mode'] = 'error'; page.goto(url)
    page.get_by_text('配置加载失败，请重试', exact=True).first.wait_for()
    state['mode'] = 'success'
    page.get_by_role('button', name=re.compile(r'^重\s*试$') if kind == 'admin' else '重新加载', exact=True).click()
    page.get_by_role('button', name='定位方向', exact=False).first.wait_for()
    assert not errors, errors
    page.close()
    print(f'PASS: {kind} new API, copy/edit/save/publish, read-only permissions, empty/error/retry, desktop/mobile')


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    try:
        exercise(browser, 'workbench', 'http://127.0.0.1:5174')
        exercise(browser, 'admin', 'http://127.0.0.1')
    finally:
        browser.close()
