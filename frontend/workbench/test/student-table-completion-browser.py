# -*- coding: utf-8 -*-
"""Run against an existing Workbench Vite server; no live business writes."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('STUDENT_TABLE_TEST_BASE', 'http://127.0.0.1:5174')
output = Path(__file__).resolve().parents[1] / 'tmp'
output.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 1280, 390):
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base + '/test/student-table-completion.html?layout=' + ('inbox' if width == 390 else 'table'))
        if width > 390:
            expect(page.locator('tr.ant-table-row')).to_have_count(2)
            expect(page.locator('tr.ant-table-row .ant-avatar')).to_have_count(0)
            expect(page.locator('tr.ant-table-row').first.locator('td').first).to_contain_text('同一学员')
            expect(page.locator('thead').get_by_text('学习规划师（班主任）', exact=True)).to_be_visible()
            expect(page.locator('tr.ant-table-row').first).to_contain_text('规划师1')
            page.locator('tr.ant-table-row').nth(1).click()
        else:
            page.locator('#student-avatar-list button.lead-inbox-item').nth(1).click()
        expect(page.locator('.student-service-card').filter(visible=True)).to_contain_text('课程2')
        expect(page.locator('.student-service-card').filter(visible=True)).to_contain_text('班级2')
        page.get_by_role('tab', name='课程服务与交接').filter(visible=True).click()
        expect(page.get_by_text('当前课程服务与交接信息', exact=True).filter(visible=True)).to_be_visible()
        page.screenshot(path=str(output / f'student-table-completion-{width}.png'), full_page=True)
        assert not errors, errors
        print('PASS current service and detail', width)
        page.close()
    page = browser.new_page(viewport={'width': 1440, 'height': 960})
    page.goto(base + '/test/student-table-completion.html?saved=1')
    expect(page.locator('tr.ant-table-row')).to_have_count(2)
    expect(page.locator('thead').get_by_text('手机号', exact=True)).to_have_count(0)
    expect(page.locator('thead').get_by_text('激活时间', exact=True)).to_have_count(1)
    expect(page.locator('thead').get_by_text('学习规划师（班主任）', exact=True)).to_have_count(1)
    page.get_by_role('combobox', name='全部学习规划师').click()
    page.locator('.ant-select-dropdown').get_by_title('规划师2', exact=True).click()
    expect(page.locator('tr.ant-table-row')).to_have_count(1)
    assert page.evaluate('subjectFixture.requests.at(-1).ownerUserId') == 21
    page.locator('thead').get_by_text('最近联系时间', exact=True).click()
    page.wait_for_function("subjectFixture.requests.at(-1).sortField === 'latestContactAt'")
    print('PASS column preferences, owner filter and remote sort')
    browser.close()
