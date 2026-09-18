# -*- coding: utf-8 -*-
"""Actual account panel with isolated synthetic transport; no live business writes."""
import json
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    url = 'http://127.0.0.1:5174/test/account-positioning-summary.html?panel&diagnosis'
    def choose(name, label='测试历史标签'):
        page.get_by_label(name, exact=True).click()
        controls = page.get_by_label(name, exact=True).get_attribute('aria-controls')
        page.locator(f'[id="{controls}"]').locator('..').locator('.ant-select-item-option-content').filter(has_text=label).click()
        expect(page.locator(f'[id="{controls}"]').locator('..')).to_be_hidden()
    def fill_common():
        for name in ['当前阶段', '账号状态', '主要瓶颈']:
            choose(name)
        page.get_by_label('主要瓶颈证据', exact=True).fill('已分析内容表现')
        page.get_by_label('一句话诊断结论', exact=True).fill('改善表达和内容结构')
    def submit():
        page.get_by_role('button', name='提交诊断', exact=True).click()
    def payload():
        return json.loads(page.locator('html').get_attribute('data-diagnosis-payload'))
    page.goto(url + '&dict-error')
    page.get_by_role('button', name='填写启动诊断', exact=True).click()
    expect(page.get_by_role('button', name='提交诊断', exact=True)).to_be_disabled()
    page.get_by_role('button', name='重试字典').click()
    expect(page.get_by_role('button', name='提交诊断', exact=True)).to_be_enabled()
    fill_common()
    choose('次要瓶颈')
    submit()
    expect(page.get_by_text('选择次要瓶颈后请填写证据', exact=True)).to_be_visible()
    page.get_by_label('次要瓶颈', exact=True).locator('xpath=ancestor::div[contains(@class,"ant-select")][1]').hover()
    page.locator('.ant-select-clear').click()
    expect(page.get_by_label('次要瓶颈证据', exact=True)).to_be_disabled()
    expect(page.get_by_text('选择次要瓶颈后请填写证据', exact=True)).to_have_count(0)
    submit()
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert payload()['templateType'] == 'diagnosis_initial' and payload()['cycle'] == 0
    assert not payload().get('secondaryProblem') and not payload().get('secondaryProblemEvidence')

    # New startup with selected secondary evidence, including responsive screenshots.
    for width, height in [(1440, 900), (390, 640)]:
        page.set_viewport_size({'width': width, 'height': height})
        page.goto(url)
        page.get_by_role('button', name='填写启动诊断', exact=True).click()
        fill_common(); choose('次要瓶颈')
        page.get_by_label('次要瓶颈证据', exact=True).fill('次要证据')
        dialog = page.get_by_role('dialog')
        assert dialog.bounding_box()['width'] <= width - 32
        if width == 1440:
            assert dialog.bounding_box()['width'] == 1080
            assert abs(page.get_by_label('主要瓶颈', exact=True).bounding_box()['y'] - page.get_by_label('次要瓶颈', exact=True).bounding_box()['y']) < 2
        assert page.locator('.ant-modal-body').evaluate('(e)=>e.scrollWidth<=e.clientWidth')
        page.screenshot(path=str(Path(gettempdir()) / f'diagnosis-startup-{width}.png'))
        submit(); expect(dialog).to_have_count(0)
        assert payload()['secondaryProblemEvidence'] == '次要证据'

    page.set_viewport_size({'width': 1440, 'height': 900})
    for i, (template, label) in enumerate([('diagnosis_7d','7天账号数据诊断'), ('diagnosis_14d','14天验证指标诊断'), ('diagnosis_28d','28天调整触发条件')]):
        page.get_by_role('button', name='填写周期诊断', exact=True).click()
        expect(page.get_by_label('第几轮诊断', exact=True)).to_have_value('1')
        choose('诊断模板', label)
        page.get_by_label('第几轮诊断', exact=True).fill(str(i+1))
        fill_common(); choose('学员配合等级'); choose('次要瓶颈')
        for name in ['配合等级证据', '次要瓶颈证据', '改进措施', '重点观测数据']:
            page.get_by_label(name, exact=True).fill('周期诊断测试证据')
        if i == 2:
            for width, height in [(1440,900),(390,640)]:
                page.set_viewport_size({'width':width,'height':height})
                assert page.locator('.ant-modal-body').evaluate('(e)=>e.scrollWidth<=e.clientWidth')
                box = page.get_by_role('button',name='提交诊断',exact=True).bounding_box()
                assert box['y'] + box['height'] <= height
                page.screenshot(path=str(Path(gettempdir()) / f'diagnosis-periodic-{width}.png'))
                if width == 390:
                    page.locator('.ant-modal-body').evaluate('(e)=>e.scrollTop=e.scrollHeight')
                    page.screenshot(path=str(Path(gettempdir()) / 'diagnosis-periodic-390-bottom.png'))
            page.set_viewport_size({'width':1440,'height':900})
        submit(); expect(page.get_by_role('dialog')).to_have_count(0)
        assert payload()['templateType'] == template and payload()['cycle'] == i+1
    page.get_by_role('button',name='基于最新记录修订',exact=True).click()
    expect(page.get_by_label('诊断模板',exact=True)).to_be_disabled()
    expect(page.get_by_label('第几轮诊断',exact=True)).to_be_disabled()
    expect(page.get_by_label('第几轮诊断',exact=True)).to_have_value('3')
    page.get_by_label('一句话诊断结论',exact=True).fill('修订后的结论')
    submit(); expect(page.get_by_role('dialog')).to_have_count(0)
    assert payload()['templateType']=='diagnosis_28d' and payload()['cycle']==3 and payload()['previousEntryId']==42
    expect(page.locator('#writes')).to_have_text('写请求 5')
    assert not errors, errors
    print('PASS: dictionary retry; startup optional/pair validation/clear; three periodic templates and rounds; locked revision; desktop/mobile overflow and footer; isolated submissions.')
    browser.close()
