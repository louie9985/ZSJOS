# -*- coding: utf-8 -*-
"""Verify the real student page using the existing isolated transport fixture."""
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('STUDENT_TABLE_TEST_BASE', 'http://127.0.0.1:5174')
output = Path(tempfile.gettempdir()) / 'zsjos-student-filter-toolbar'
output.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1930, 1440, 1280, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/student-table-completion.html?layout=table')
        table = width > 768
        scope = page.locator('.student-filter-toolbar' if table else '.inbox-avatar-toolbar')
        for label in ('全部学习规划师', '全部接收状态', '全部交付阶段', '全部任务状态', '全部班级', '全部服务状态'):
            expect(page.get_by_role('combobox', name=label, exact=True)).to_have_count(1)
            expect(scope.get_by_role('combobox', name=label, exact=True)).to_be_visible()
        expect(page.get_by_placeholder('搜索姓名、学员编号、手机号、微信号或客资编号')).to_have_count(1)
        expect(scope.get_by_role('button', name=re.compile('筛选'))).to_have_count(1)
        owner = page.get_by_role('combobox', name='全部学习规划师', exact=True)
        owner.click()
        page.locator('.ant-select-dropdown').get_by_title('规划师2', exact=True).click()
        page.wait_for_function('subjectFixture.requests.at(-1).ownerUserId === 21')
        page.get_by_role('combobox', name='全部服务状态', exact=True).click()
        page.locator('.ant-select-dropdown:visible').get_by_title('已暂停', exact=True).click()
        page.wait_for_function("subjectFixture.requests.at(-1).serviceStatus === 'paused'")
        search = page.get_by_placeholder('搜索姓名、学员编号、手机号、微信号或客资编号')
        search.fill('测试学员')
        search.press('Enter')
        page.wait_for_function("subjectFixture.requests.at(-1).keyword === '测试学员' && subjectFixture.requests.at(-1).ownerUserId === 21 && subjectFixture.requests.at(-1).serviceStatus === 'paused' && subjectFixture.requests.at(-1).pageNo === 1")
        owner_select = page.locator('.ant-select').filter(has=owner)
        owner_select.hover()
        owner_select.locator('.ant-select-clear').click()
        page.wait_for_function('subjectFixture.requests.at(-1).ownerUserId === undefined')
        boxes = scope.locator('.ant-select').evaluate_all('(nodes) => nodes.map(n => { const r=n.getBoundingClientRect(); return {x:r.x,y:r.y,w:r.width,h:r.height} })')
        for box in boxes:
            assert box['x'] >= 0 and box['x'] + box['w'] <= width + 1, (width, box)
        for i, a in enumerate(boxes):
            for b in boxes[i+1:]:
                assert not (a['x'] < b['x']+b['w']-1 and b['x'] < a['x']+a['w']-1 and a['y'] < b['y']+b['h']-1 and b['y'] < a['y']+a['h']-1), (a,b)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), width
        page.screenshot(path=str(output / f'filters-{width}.png'), full_page=True)
        assert not errors, errors
        print('PASS unique controls, layout and filter parameters', width)
        page.close()
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        class_name = '202701-06 人社健康管理师高级技能等级认定培训班（周末学习与考前辅导）'
        exam = '2027年1月6日 全国统一考试安排及补充说明'
        label = class_name + ' · 王老师 · ' + exam
        def fixture_classes(route):
            response = route.fetch()
            source = response.text()
            old = 'api.deliveryClasses.page = async () => ({ list: [], total: 0 });'
            assert old in source, 'Existing isolated class fixture changed'
            import json
            payload = json.dumps({'list': [{'id': 901, 'className': class_name, 'homeroomUserNameSnapshot': '王老师', 'examScheduleSnapshot': exam}], 'total': 1}, ensure_ascii=False)
            # Only SERVING contributes the class, matching the two status requests.
            route.fulfill(response=response, body=source.replace(old, 'api.deliveryClasses.page = async (p) => p.status === "SERVING" ? (' + payload + ') : ({list: [], total: 0});'))
        page.route('**/test/student-table-completion.tsx*', fixture_classes)
        page.goto(base + '/test/student-table-completion.html?layout=table')
        page.get_by_role('combobox', name='全部班级', exact=True).click()
        popup = page.locator('.student-class-filter-popup:visible')
        option = popup.locator('.ant-select-item-option-content')
        expect(option).to_have_text(label)
        option.hover()
        geometry = popup.evaluate('(el) => {const r=el.getBoundingClientRect(); return {x:r.x,right:r.right,width:r.width}}')
        assert geometry['x'] >= 0 and geometry['right'] <= width + 1, geometry
        assert geometry['width'] > 300, geometry
        assert option.evaluate('(el) => getComputedStyle(el).whiteSpace === "normal" && el.scrollWidth <= el.clientWidth && el.scrollHeight <= el.clientHeight'), 'Class text is clipped'
        page.screenshot(path=str(output / f'class-options-{width}.png'), full_page=True)
        option.click()
        page.wait_for_function('subjectFixture.requests.at(-1).classId === 901')
        expect(page.locator('.ant-select').filter(has=page.get_by_role('combobox', name='全部班级', exact=True))).to_have_attribute('title', label)
        print('PASS complete class labels, viewport bounds and selection', width)
        page.close()
    browser.close()
