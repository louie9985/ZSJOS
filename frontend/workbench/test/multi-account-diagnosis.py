# -*- coding: utf-8 -*-
"""Actual React diagnosis forms; isolated transport, no shared business writes."""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

ORIGIN = os.environ.get('ZSJOS_MULTI_DIAGNOSIS_ORIGIN', 'http://127.0.0.1:5174')
OUTPUT = Path(gettempdir()) / 'zsjos-multi-account-diagnosis'
OUTPUT.mkdir(exist_ok=True)
URL = ORIGIN + '/test/multi-account-diagnosis.html?panel&diagnosis&early-diagnosis'

def choose(page, control, label):
    control.click()
    options = page.locator('[id="' + control.get_attribute('aria-controls') + '"]').locator('..')
    options.locator('.ant-select-item-option-content').get_by_text(label, exact=True).click()

def add(page, dialog, account='视频号'):
    dialog.get_by_role('button', name='添加账号', exact=True).click()
    page.locator('.ant-popover').get_by_role('button').filter(has_text=account).click()

def fill(page, dialog, initial=False):
    if not initial:
        expect(dialog.locator('[id$="_highestLikes"]')).to_have_count(dialog.locator('[id$="_currentStage"]').count())
    for key in ['primaryProblem'] + ([] if initial else ['secondaryProblem', 'cooperationLevel']):
        choose(page, dialog.locator('#' + key), '测试历史标签')
    for key in ['primaryProblemEvidence', 'conclusion'] + ([] if initial else ['secondaryProblemEvidence', 'cooperationEvidence', 'improvementMeasures', 'observedData']):
        dialog.locator('#' + key).fill('本周期事实与证据，保留输入')
    if not initial:
        choose(page, dialog.locator('#changeDirection'), '是')
        choose(page, dialog.locator('#trafficNormal'), '否')
        dialog.locator('#directionSummary').fill('保持专业科普方向，优化开头表达')
        for field in dialog.locator('[id$="_highestLikes"]').all():
            field.fill('0')
        expect(dialog.get_by_role('button', name='提交诊断（' + str(dialog.locator('[id$="_currentStage"]').count()) + ' 个账号）')).to_be_enabled()

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    all_errors = []
    live_requests = []
    def open_form(width=1440, query=''):
        page = browser.new_page(viewport={'width':width, 'height':1000 if width > 600 else 844})
        page.set_default_timeout(20000)
        page.on('pageerror', lambda error: all_errors.append(str(error)))
        page.route('**/admin-api/**', lambda route: (live_requests.append(route.request.url), route.abort()))
        page.goto(URL + query, wait_until='domcontentloaded')
        page.get_by_role('button', name='填写启动诊断' if 'initial' in query else '填写诊断表', exact=True).click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_role('button', name='添加账号', exact=True)).to_be_visible()
        return page, dialog

    for width in [1440,390]:
        page, dialog = open_form(width, '&batch-error&candidate-error')
        assert dialog.locator('[id$="_currentStage"]').count() == 1
        assert dialog.locator('input[type=checkbox]').count() == 0
        dialog.get_by_role('button', name='添加账号', exact=True).click()
        popover = page.locator('.ant-popover')
        expect(popover.get_by_text('测试账号加载失败，请重试', exact=True)).to_be_visible()
        popover.get_by_role('button', name=re.compile(r'重\s*试')).click()
        expect(popover.get_by_role('button').filter(has_text='已填写账号')).to_be_disabled()
        assert popover.get_by_role('button').filter(has_text='抖音主账号').count() == 0
        popover.get_by_role('button').filter(has_text='视频号').click()
        expect(dialog.locator('#accounts_900002_currentStage').locator('..')).to_contain_text('其他历史标签')
        choose(page, dialog.locator('#accounts_900002_currentStage'), '测试历史标签')
        dialog.locator('#directionSummary').fill('删除账号后仍保留的共用输入')
        dialog.get_by_role('button', name='移除视频号', exact=True).click()
        expect(dialog.locator('#directionSummary')).to_have_value('删除账号后仍保留的共用输入')
        add(page, dialog)
        expect(dialog.locator('#accounts_900002_currentStage').locator('..')).to_contain_text('测试历史标签')
        choose(page, dialog.locator('#accounts_900002_currentStage'), '其他历史标签')
        fill(page, dialog)
        page.wait_for_timeout(350)
        assert dialog.locator('.ant-modal-body').evaluate('(el)=>el.scrollWidth<=el.clientWidth')
        page.screenshot(path=str(OUTPUT / f'form-{width}-bottom.png'))
        dialog.locator('.ant-modal-body').evaluate('(el)=>el.scrollTop=0')
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUTPUT / f'form-{width}-top.png'))
        submit = dialog.get_by_role('button', name='提交诊断（2 个账号）', exact=True)
        submit.click()
        expect(dialog.get_by_text('测试提交失败，未保存任何账号', exact=True)).to_be_visible()
        expect(dialog.locator('#conclusion')).to_have_value('本周期事实与证据，保留输入')
        submit.click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        payloads = page.evaluate('window.batchFixture.payloads')
        assert len(payloads) == 2 and payloads[0] == payloads[1]
        payload = payloads[-1]
        assert len(payload['accounts']) == 2
        a,b = [row['weekly'] for row in payload['accounts']]
        assert a['common']['currentStage'] != b['common']['currentStage']
        assert a['common']['accountStatus'] != b['common']['accountStatus']
        assert a['common']['conclusion'] == b['common']['conclusion']
        assert a['highestLikes'] == b['highestLikes'] == 0
        page.close()

    page, dialog = open_form(query='&slow-preview')
    add(page, dialog)
    dialog.get_by_role('button', name='移除视频号', exact=True).click()
    page.wait_for_timeout(1700)
    assert dialog.locator('#accounts_900002_currentStage').count() == 0
    dialog.get_by_role('button', name='移除抖音主账号', exact=True).click()
    expect(dialog.get_by_role('button', name='提交诊断（0 个账号）', exact=True)).to_be_disabled()
    add(page, dialog, '视频号')
    fill(page, dialog)
    dialog.get_by_role('button', name='提交诊断（1 个账号）', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert [row['accountId'] for row in page.evaluate('window.batchFixture.payloads[0].accounts')] == [900002]
    page.close()

    page, dialog = open_form(query='&account-preview-error')
    add(page, dialog)
    expect(dialog.get_by_text('测试视频号指标加载失败', exact=True)).to_be_visible()
    expect(dialog.get_by_role('button', name='提交诊断（2 个账号）', exact=True)).to_be_disabled()
    dialog.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(dialog.get_by_text('测试视频号指标加载失败', exact=True)).to_have_count(0)
    dialog.locator('#directionSummary').fill('关闭前的草稿')
    dialog.locator('.ant-modal-close').click()
    confirm = page.get_by_role('dialog').filter(has_text='放弃本次诊断填写？')
    confirm.get_by_role('button', name='继续填写', exact=True).click()
    expect(dialog.locator('#directionSummary')).to_have_value('关闭前的草稿')
    page.close()

    page, dialog = open_form(query='&metric-error')
    add(page, dialog); fill(page, dialog)
    dialog.get_by_role('button', name='提交诊断（2 个账号）', exact=True).click()
    expect(dialog.get_by_text('已刷新账号与指标，填写内容已保留，请核对后再次提交。', exact=True)).to_be_visible()
    expect(dialog.locator('#conclusion')).to_have_value('本周期事实与证据，保留输入')
    dialog.get_by_role('button', name='提交诊断（2 个账号）', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0);page.close()

    page, dialog = open_form(query='&initial')
    add(page, dialog);fill(page, dialog, initial=True)
    assert dialog.locator('#changeDirection').count() == 0
    assert dialog.locator('[id$="_highestLikes"]').count() == 0
    dialog.get_by_role('button', name='提交诊断（2 个账号）', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    payload = page.evaluate('window.batchFixture.payloads[0]')
    assert payload['templateType'] == 'diagnosis_initial' and all(row['initial']['cycle'] == 0 and 'weekly' not in row for row in payload['accounts'])
    page.close()
    assert not all_errors,all_errors
    assert not live_requests,live_requests
    browser.close()
    print('PASS: desktop/mobile form, add/remove, defaults, candidate retry/disabled/duplicates, per-account states/zero likes, preserved drafts, identical network retry, partial and empty selection, late preview removal, preview retry, dirty-close guard, metric refresh, initial/weekly separation; no page errors or live API requests.')
