# -*- coding: utf-8 -*-
"""Real Chrome, isolated HTTP adapter: no authenticated backend writes."""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('CONTENT_REVIEW_INLINE_URL', 'http://127.0.0.1:5174/test/content-review-inline-reviewer.html')
OUT = Path(gettempdir()) / 'zsjos-content-review-inline-reviewer'
OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.set_default_timeout(12000)

    def open_case(mode, width=1440):
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(BASE + '?mode=' + mode)
        expect(page.get_by_role('combobox', name='审批编导', exact=True)).to_be_visible()

    def requests(suffix):
        return page.evaluate('(suffix) => window.inlineFixture.requests.filter(r => r.url.endsWith(suffix))', suffix)

    def choose(name='审核编导乙'):
        select = page.get_by_role('combobox', name='审批编导', exact=True)
        expect(select).to_be_enabled()
        select.click()
        select.fill(name[-1])
        page.get_by_title(name, exact=True).click()
        select.press('Tab')
        page.wait_for_timeout(250)

    def submit_button():
        return page.get_by_role('button', name='提交审批', exact=True)

    for width in (1440, 390):
        open_case('new', width)
        choose()
        assert not requests('/create-from-student')
        assert page.get_by_label('发布标题', exact=True).input_value() == ''
        reviewer_box = page.get_by_role('combobox', name='审批编导', exact=True).bounding_box()
        account_box = page.get_by_label('发布账号', exact=True).bounding_box()
        assert reviewer_box['y'] < account_box['y']
        page.locator('.ant-modal-body').evaluate('el => el.scrollTop = 0')
        page.screenshot(path=str(OUT / f'new-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 2')
        page.get_by_role('button', name='填入测试作品').click()
        page.get_by_role('button', name='保存并提交审批', exact=True).click()
        expect(page.get_by_text('已提交审批', exact=True)).to_be_visible()
        assert len(requests('/submit')) == 1
        assert requests('/submit')[0]['payload']['directorUserId'] == 21
        assert not page.get_by_role('dialog', name='选择审核编导').count()

        open_case('edit', width)
        expect(page.get_by_text('审核编导甲', exact=True)).to_be_visible()
        choose()
        page.get_by_role('button', name='保存修改', exact=True).click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert requests('/save-student-draft')[0]['payload']['draftReviewerSelection']['directorUserId'] == 21
        page.get_by_role('button', name='继续草稿').click()
        expect(page.get_by_text('审核编导乙', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'edit-{width}.png'), full_page=True)
        page.locator('.ant-select-clear').first.click()
        page.get_by_role('button', name='保存修改', exact=True).click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert requests('/save-student-draft')[-1]['payload']['draftReviewerSelection'] is None

        open_case('revision', width)
        expect(page.get_by_text('审核编导甲', exact=True)).to_be_visible()
        choose()
        page.get_by_role('button', name='重新提交审批', exact=True).click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert len(requests('/resubmit-from-student')) == 1
        assert len(requests('/submit')) == 1
        assert requests('/submit')[0]['payload']['directorUserId'] == 21

    for width in (1440, 390):
        open_case('edit', width)
        expect(page.get_by_text('审核编导甲', exact=True)).to_be_visible()
        page.locator('.ant-modal:visible .ant-modal-footer button').first.click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        page.get_by_role('button', name='继续草稿').click()
        expect(page.get_by_text('审核编导甲', exact=True)).to_be_visible()
        submit_button().click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert len(requests('/submit')) == 1
        assert len(requests('/submit-context')) == 2

    for mode, warning in [('stale', '原选编导已不可用'), ('old-version', '审批流程版本已变化')]:
        open_case(mode)
        expect(page.get_by_text(warning, exact=False)).to_be_visible()
        submit_button().click()
        expect(page.get_by_text('请选择可用的审批编导，候选加载失败时请重试', exact=True)).to_be_visible()
        assert not requests('/save-student-draft') and not requests('/submit')
        choose()
        submit_button().click()
        expect(page.get_by_role('dialog')).to_have_count(0)

    open_case('empty')
    expect(page.get_by_text('当前没有可选编导', exact=True)).to_be_visible()
    submit_button().click()
    assert not requests('/submit')
    page.get_by_role('button', name='保存修改', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert requests('/save-student-draft')[0]['payload']['draftReviewerSelection'] is None

    open_case('load-error')
    expect(page.get_by_text('候选加载失败，请重试', exact=True)).to_be_visible()
    page.get_by_role('button', name=re.compile(r'^重\s*试$')).click()
    choose()
    submit_button().click()
    expect(page.get_by_role('dialog')).to_have_count(0)

    for mode in ('version', 'unavailable', 'reject'):
        open_case(mode)
        expect(page.get_by_text('审核编导甲', exact=True)).to_be_visible()
        submit_button().click()
        page.wait_for_function('window.inlineFixture.requests.some(r => r.url.endsWith("/submit"))')
        expect(page.get_by_label('正文文稿', exact=True)).to_have_value('提交失败仍保留正文')
        if mode != 'reject':
            choose()
        else:
            expect(page.get_by_text('草稿已保存，但审批未提交', exact=False)).to_be_visible()
        submit_button().click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert len(requests('/submit')) == 2
        if mode == 'version':
            assert requests('/submit')[-1]['payload']['processDefinitionId'] == 'v3'

    open_case('lost')
    expect(page.get_by_text('审核编导甲', exact=True)).to_be_visible()
    submit_button().dblclick()
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert len(requests('/submit')) == 1

    page.goto(BASE + '?mode=student')
    page.get_by_role('tab', name='概览', exact=True).click()
    page.get_by_role('button', name=re.compile('发起内容审批')).click()
    page.get_by_role('button', name='新建内容审批', exact=True).click()
    choose()
    assert not requests('/create-from-student')
    assert requests('/submit-context')[-1]['params'] == {'studentPersonId': 3}
    page.get_by_role('button', name=re.compile(r'^取\s*消$')).click()
    page.get_by_role('button', name=re.compile('发起内容审批')).click()
    page.get_by_role('button', name='继续填写 · TEST-INLINE-7', exact=True).click()
    choose('审核编导甲')
    page.get_by_role('button', name='保存草稿', exact=True).click()
    page.wait_for_function('window.inlineFixture.requests.some(r => r.url.endsWith("/save-student-draft"))')
    expect(page.get_by_text('草稿已保存，可继续填写', exact=False)).to_be_visible()
    choose()
    page.get_by_role('button', name='保存并提交审批', exact=True).click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    assert len(requests('/save-student-draft')) == 2
    assert requests('/save-student-draft')[-1]['payload']['draftReviewerSelection']['directorUserId'] == 21
    assert len(requests('/submit')) == 1
    assert not errors, json.dumps(errors, ensure_ascii=False)
    browser.close()
print('PASS inline new/edit/revision, search, draft persistence/clear, required selection, empty/retry, stale/changed, failures/input retention, response loss/double click, desktop/mobile')
print(OUT)
