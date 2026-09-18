# -*- coding: utf-8 -*-
"""Browser regression with the actual panel and isolated synthetic API responses."""
import json
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    url = 'http://127.0.0.1:5174/test/account-positioning-summary.html?panel&diagnosis&early-diagnosis'
    def row(key):
        return page.locator(f'[data-profile-key="{key}"]')
    def choose(name):
        control = page.get_by_label(name, exact=True)
        control.click()
        popup = page.locator(f'[id="{control.get_attribute("aria-controls")}"]').locator('..')
        popup.locator('.ant-select-item-option-content').filter(has_text='测试历史标签').click()
    page.goto(url + '&task-error')
    expect(row('diagnosis_7d')).to_contain_text('测试任务加载失败')
    row('diagnosis_7d').get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(row('diagnosis_7d')).to_contain_text('可提前填写')
    for width, height in [(1440, 900), (390, 844)]:
        page.set_viewport_size({'width': width, 'height': height})
        for key in ['diagnosis_7d', 'diagnosis_14d', 'adjustment_28d']:
            expect(row(key)).to_contain_text('截止时间：')
            expect(row(key)).to_contain_text('北京时间')
        row('diagnosis_7d').scroll_into_view_if_needed()
        assert page.locator('html').evaluate('(e)=>e.scrollWidth<=e.clientWidth')
        page.screenshot(path=str(Path(gettempdir()) / f'diagnosis-deadline-{width}.png'), animations='disabled')
        row('diagnosis_7d').get_by_role('button', name='填写诊断', exact=True).click()
        dialog = page.get_by_role('dialog')
        expect(dialog).to_contain_text('可以提前填写')
        expect(dialog).to_contain_text('截止时间：')
        expect(page.get_by_label('第几轮诊断', exact=True)).to_have_count(0)
        expect(page.get_by_label('诊断模板', exact=True)).to_have_count(0)
        expect(dialog).to_contain_text('本次填写：7天账号数据诊断')
        page.wait_for_timeout(400)  # Ant Design motion must settle before visual inspection.
        page.screenshot(path=str(Path(gettempdir()) / f'diagnosis-deadline-form-{width}.png'), animations='disabled')
        page.get_by_role('button', name='Close', exact=True).click()
        expect(dialog).to_have_count(0)
    page.set_viewport_size({'width': 1440, 'height': 900})
    row('diagnosis_7d').get_by_role('button', name='填写诊断', exact=True).click()
    for name in ['当前阶段', '账号状态', '主要瓶颈', '次要瓶颈', '学员配合等级']:
        choose(name)
    for name in ['主要瓶颈证据', '次要瓶颈证据', '配合等级证据', '一句话诊断结论', '改进措施', '重点观测数据']:
        page.get_by_label(name, exact=True).fill('测试诊断证据')
    page.get_by_role('button', name='提交诊断', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    payload = json.loads(page.locator('html').get_attribute('data-diagnosis-payload'))
    assert payload['taskId'] == 700 and payload['templateType'] == 'diagnosis_7d' and payload['cycle'] == 1
    expect(row('diagnosis_7d')).to_contain_text('暂无待填写任务')
    expect(row('diagnosis_14d')).to_contain_text('可提前填写')
    row('diagnosis_7d').get_by_role('button', name=re.compile('基于最新记录修订')).click()
    expect(page.get_by_role('dialog')).to_contain_text('本次填写：7天账号数据诊断')
    page.get_by_label('一句话诊断结论', exact=True).fill('修订测试结论')
    page.get_by_role('button', name='提交诊断', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    revision = json.loads(page.locator('html').get_attribute('data-diagnosis-payload'))
    assert revision['templateType'] == 'diagnosis_7d' and revision['cycle'] == 1 and revision['previousEntryId'] == 42
    page.get_by_role('button', name='填写周期诊断', exact=True).first.click()
    expect(page.get_by_role('dialog')).to_contain_text('14天验证指标诊断')
    page.get_by_role('button', name='Close', exact=True).click()
    page.goto(url + '&overdue')
    expect(row('diagnosis_7d')).to_contain_text('已逾期，仍可补填')
    row('diagnosis_7d').get_by_role('button', name='填写诊断', exact=True).click()
    expect(page.get_by_role('dialog')).to_contain_text('本次任务已逾期，仍可补填')
    page.goto(url + '&empty-tasks')
    expect(row('diagnosis_7d')).to_contain_text('暂无待填写任务')
    assert not errors, errors
    print('PASS: deadline display desktop/mobile; task retry; early submit bound to task; completed task disappears; generic entry selects remaining template; overdue catch-up; empty state.')
    browser.close()
