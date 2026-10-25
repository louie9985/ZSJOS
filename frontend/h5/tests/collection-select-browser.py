# -*- coding: utf-8 -*-
"""Real production H5 bundle with isolated API fixtures; no live business writes.

After npm run build, start vite preview on 127.0.0.1:5194.
"""
import json
import os
from urllib.parse import urlparse
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('COLLECTION_SELECT_URL', 'http://127.0.0.1:5194')
OUT = Path(os.environ.get('TEMP', '/tmp')) / 'zsjos-collection-desktop-select'
OUT.mkdir(parents=True, exist_ok=True)
education = [dict(value=str(i), label=label) for i, label in enumerate(
    ['其他', '初中', '高中', '中专', '大专', '本科', '硕士', '博士'])]
ethnicity = [dict(value=str(i), label=f'测试民族{i:02d}') for i in range(30)]
fields = [dict(key=key, label=label, type=kind, group='basic', required=False,
               enabled=True, sensitive=False) for key, label, kind in [
    ('name', '姓名', 'text'), ('mobile', '手机号', 'text'),
    ('education_level', '现学历', 'dict'), ('ethnicity', '民族', 'dict'),
    ('retraining', '是否复训', 'dict'), ('empty', '空选项', 'dict')]]
fields += [dict(key=key, label=label, type='text', group=group, required=False,
                enabled=True, sensitive=False) for key, label, group in [
    ('school', '毕业院校', 'education'), ('job', '从事职业', 'career'),
    ('proof', '测试材料说明', 'materials')]]

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors, submitted, pending = [], [], []
    draft = dict(revision=0, values={})
    page.on('pageerror', lambda error: errors.append(str(error)))

    def api(route):
        if route.request.url.endswith('/detail'):
            data = dict(status='DRAFT', tenantId=1, configVersion=1, productName='测试报名',
                        fields=fields, images=[], options=dict(education_level=education,
                        ethnicity=ethnicity, retraining=[dict(value='yes', label='是'), dict(value='no', label='否')], empty=[]), draft=draft.copy())
        elif route.request.url.endswith('/draft'):
            request = route.request.post_data_json
            assert request['revision'] == draft['revision'], (request, draft)
            draft.update(revision=draft['revision']+1, values=request['values'])
            data = {**draft, 'savedAt': '2026-10-22 12:00:00'}
        elif route.request.url.endswith('/submit'):
            assert route.request.post_data_json['revision'] == draft['revision']
            submitted.append(route.request.post_data_json)
            pending.append(route)
            return
        else:
            data = {}
        route.fulfill(content_type='application/json', body=json.dumps(dict(code=0, data=data)))

    if os.environ.get('COLLECTION_SELECT_DIST') == '1':
        distribution = Path(__file__).resolve().parents[1] / 'dist'
        def asset(route):
            path = distribution / urlparse(route.request.url).path.lstrip('/')
            if not path.is_file(): path = distribution / 'index.html'
            route.fulfill(path=str(path), content_type={'.js':'text/javascript', '.css':'text/css', '.html':'text/html', '.json':'application/json'}.get(path.suffix, 'application/octet-stream'))
        page.route('**/*', asset)
    page.route('**/public-api/**', api)
    page.route('**/app-api/**', api)
    page.route('**/part-api/**', api)

    def load(width):
        page.goto('about:blank')
        draft.update(revision=0, values={})
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(BASE + '/student-info-form#token=' + 'a' * 43)
        expect(page.locator('#education_level')).to_be_visible()

    panel = page.locator('.desktop-select-panel:visible')
    for width in [1440, 390, 320]:
        load(width)
        groups = page.locator('.collection-group')
        expect(groups).to_have_count(4)
        expect(page.locator('.collection-group[open]')).to_have_count(4)
        for group in ['basic', 'education', 'career', 'materials']:
            svg = page.locator(f'[data-group={group}] svg')
            expect(svg).to_be_visible()
            assert svg.evaluate('(svg) => svg.querySelector("path").getTotalLength() > 0')
        page.locator('#school').fill('测试院校')
        section = page.locator('[data-group=education]')
        section.locator('summary').click()
        expect(section).not_to_have_attribute('open', '')
        section.locator('summary').click()
        expect(section).to_have_attribute('open', '')
        expect(page.locator('#school')).to_have_value('测试院校')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        page.screenshot(path=str(OUT / f'groups-expanded-{width}.png'), full_page=True)
    for width in [1440, 1024, 601]:
        load(width)
        page.locator('#education_level').click()
        expect(panel).to_be_visible()
        expect(page.locator('.van-picker:visible')).to_have_count(0)
        expect(page.locator('.van-overlay:visible')).to_have_count(0)
        trigger_box = page.locator('#education_level').bounding_box()
        box = panel.bounding_box()
        assert abs(box['x'] - trigger_box['x']) < 12, (width, box, trigger_box)
        assert box['x'] >= 0 and box['x'] + box['width'] <= width, (width, box)
        assert box['y'] >= 0 and box['y'] + box['height'] <= 1000, (width, box)
        page.wait_for_timeout(250)
        page.screenshot(path=str(OUT / f'desktop-short-{width}.png'))
        panel.get_by_role('option', name='本科', exact=True).click()
        expect(panel).to_have_count(0)
        expect(page.locator('#education_level')).to_contain_text('本科')
        page.locator('#education_level').press('ArrowDown')
        page.keyboard.press('ArrowUp')
        page.keyboard.press('Enter')
        expect(page.locator('#education_level')).to_contain_text('大专')
        page.locator('#education_level').click()
        page.keyboard.press('Escape')
        expect(panel).to_have_count(0)
        expect(page.locator('#education_level')).to_be_focused()
        page.locator('#education_level').click()
        page.locator('#name').click()
        expect(panel).to_have_count(0)
        page.locator('#education_level').press('ArrowDown')
        page.keyboard.press('Tab')
        expect(panel).to_have_count(0)
        expect(page.locator('#ethnicity')).to_be_focused()

    load(1440)
    page.set_viewport_size({'width': 1440, 'height': 650})
    page.locator('#education_level').click()
    page.wait_for_timeout(250)
    box = panel.bounding_box()
    page.screenshot(path=str(OUT / 'desktop-short-window.png'))
    assert box['y'] >= 0 and box['y'] + box['height'] <= 650, (box, page.locator('#education_level').bounding_box(), page.evaluate('scrollY'))
    page.keyboard.press('Escape')
    expect(panel).to_have_count(0)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.locator('#ethnicity').click()
    expect(panel.get_by_role('searchbox')).to_be_focused()
    panel.get_by_role('listbox').hover()
    page.mouse.wheel(0, 500)
    page.wait_for_function('document.getElementById("ethnicity-desktop-options").scrollTop > 0')
    page.screenshot(path=str(OUT / 'desktop-long-wheel.png'))
    panel.get_by_role('searchbox').fill('不存在')
    expect(panel).to_contain_text('没有匹配选项')
    panel.get_by_role('searchbox').fill('测试民族25')
    expect(panel.get_by_role('option')).to_have_count(1)
    page.keyboard.press('ArrowDown')
    page.keyboard.press('Enter')
    expect(page.locator('#ethnicity')).to_contain_text('测试民族25')
    page.locator('#ethnicity').click()
    expect(panel.get_by_role('searchbox')).to_have_value('')
    expect(panel.get_by_role('option', name='测试民族25', exact=True)).to_have_attribute('aria-selected', 'true')
    page.keyboard.press('Escape')
    page.locator('#empty').click()
    expect(panel).to_contain_text('暂无可选项')
    page.keyboard.press('ArrowDown')
    page.keyboard.press('Enter')
    expect(panel).to_be_visible()
    page.keyboard.press('Escape')

    for width in [600, 390, 320]:
        load(width)
        assert page.locator('.desktop-select').count() == 0
        page.locator('#education_level').click()
        picker = page.locator('.van-picker:visible')
        expect(picker).to_be_visible()
        page.wait_for_timeout(250)
        page.screenshot(path=str(OUT / f'mobile-picker-{width}.png'))
        picker.get_by_text('本科', exact=True).click()
        picker.locator('.van-picker__cancel').click()
        expect(page.locator('#education_level')).to_contain_text('请选择')
        page.locator('#education_level').click()
        picker.get_by_text('本科', exact=True).click()
        page.wait_for_timeout(350)
        picker.locator('.van-picker__confirm').click()
        expect(page.locator('#education_level')).to_contain_text('本科')
        page.locator('#ethnicity').click()
        popup = page.locator('.collection-select-popup:visible')
        popup.get_by_role('searchbox').fill('测试民族25')
        popup.get_by_text('测试民族25', exact=True).click()
        popup.get_by_role('button', name='取消', exact=True).click()
        expect(page.locator('#ethnicity')).to_contain_text('请选择')
        page.locator('#ethnicity').click()
        expect(popup.get_by_role('searchbox')).to_have_value('')
        popup.get_by_role('searchbox').fill('测试民族25')
        popup.get_by_text('测试民族25', exact=True).click()
        page.screenshot(path=str(OUT / f'mobile-search-{width}.png'))
        popup.get_by_role('button', name='确定', exact=True).click()
        expect(page.locator('#ethnicity')).to_contain_text('测试民族25')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width

    # Exercise the original mobile touch handler, not just clicking picker rows.
    page.locator('#education_level').click()
    page.wait_for_timeout(250)
    column = page.locator('.van-picker:visible .van-picker-column').bounding_box()
    cdp = page.context.new_cdp_session(page)
    x, y = column['x'] + column['width'] / 2, column['y'] + column['height'] / 2
    cdp.send('Input.dispatchTouchEvent', dict(type='touchStart', touchPoints=[dict(x=x, y=y)]))
    for distance in [10, 20, 30, 44]:
        cdp.send('Input.dispatchTouchEvent', dict(type='touchMove', touchPoints=[dict(x=x, y=y - distance)]))
        page.wait_for_timeout(80)
    cdp.send('Input.dispatchTouchEvent', dict(type='touchEnd', touchPoints=[]))
    page.wait_for_timeout(500)
    page.locator('.van-picker:visible .van-picker__confirm').click()
    expect(page.locator('#education_level')).not_to_contain_text('本科')
    # Restore the committed fixture value for subsequent payload assertions.
    page.locator('#education_level').click()
    page.locator('.van-picker:visible').get_by_text('本科', exact=True).click()
    page.wait_for_timeout(350)
    page.locator('.van-picker:visible .van-picker__confirm').click()
    expect(page.locator('#education_level')).to_contain_text('本科')

    page.locator('#education_level').click()
    page.set_viewport_size({'width': 1440, 'height': 1000})
    expect(page.locator('.van-picker:visible')).to_have_count(0)
    expect(page.locator('#education_level')).to_contain_text('本科')
    page.locator('#education_level').click()
    page.set_viewport_size({'width': 390, 'height': 1000})
    expect(panel).to_have_count(0)
    expect(page.locator('#education_level')).to_contain_text('本科')
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.get_by_role('button', name='提交信息', exact=True).click()
    page.get_by_role('button', name='确认', exact=True).click()
    expect(page.locator('#education_level')).to_be_disabled()
    assert submitted[0]['values']['education_level'] == '5'
    assert submitted[0]['values']['ethnicity'] == '25'
    pending.pop().fulfill(content_type='application/json', body=json.dumps(dict(code=0, data=True)))
    expect(page.locator('.van-empty')).to_contain_text('信息已提交')
    assert not errors, errors
    browser.close()
    print('PASS desktop 1440/1024/601: anchor, wheel, search, click, keyboard, empty, disabled, submit; mobile 600/390/320: cancel/confirm, search, resize; ' + str(OUT))
