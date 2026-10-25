# -*- coding: utf-8 -*-
"""Actual account panel: slow requests, retry, deduplication and stale-response guards.

The existing Vite server serves a synthetic Axios fixture. Every real admin API request
is blocked. Account switches rerender the same React root through browser-only host
instrumentation; they do not reload the page or alter production source.
"""
import json
import os
import re
import time
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

ORIGIN = os.environ.get('ZSJOS_WEEKLY_TEST_ORIGIN', 'http://127.0.0.1:5174')
URL = ORIGIN + '/test/weekly-account-diagnosis.html?panel&diagnosis&early-diagnosis'
OUTPUT = Path(gettempdir()) / 'zsjos-diagnosis-loading'
OUTPUT.mkdir(exist_ok=True)


def instrument_host(route):
    response = route.fetch()
    source = response.text()
    root = 'createRoot(document.getElementById("root")).render('
    account = 'account: { id: 900001, accountNo: "TEST-ACCOUNT", version: 1, primaryProblems:'
    assert source.count(root) == 1 and source.count(account) == 1, 'Fixture host changed; review instrumentation'
    source = source.replace(root, 'const diagnosisFixtureRoot = createRoot(document.getElementById("root"));\n'
                            'const renderDiagnosisFixture = () => diagnosisFixtureRoot.render(', 1)
    source = source.replace(account, account.replace('id: 900001', 'id: window.diagnosisAccountId || 900001'), 1)
    source = source.replace('canMaintain: true,', 'canMaintain: true, diagnosisTaskId: location.search.includes("deep-link") ? 700 : undefined,', 1)
    source += '\nwindow.switchDiagnosisAccount = id => { window.diagnosisAccountId = id; renderDiagnosisFixture(); };\nrenderDiagnosisFixture();\n'
    route.fulfill(response=response, body=source)


def open_page(browser, query='', width=1440, height=1000, switchable=False):
    page = browser.new_page(viewport={'width': width, 'height': height})
    page.set_default_timeout(15000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/admin-api/**', lambda route: route.abort())
    if switchable:
        page.route('**/test/account-positioning-summary.tsx*', instrument_host)
    page.goto(URL + query, wait_until='domcontentloaded', timeout=30000)
    expect(page.get_by_role('button', name='填写诊断表', exact=True)).to_be_visible()
    return page, errors


def events(page, endpoint):
    return page.evaluate('(endpoint) => window.diagnosisFixture.events.filter(event => event.endpoint === endpoint)', endpoint)


def wait_finished(page, endpoint, count=1):
    page.wait_for_function('([endpoint, count]) => window.diagnosisFixture.events.filter(event => event.endpoint === endpoint && event.finished).length >= count',
                          arg=[endpoint, count])


def form_loaded(page):
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('通用诊断（本次所选账号共用，填写一次）', exact=True)).to_be_visible()
    expect(dialog.get_by_role('button', name=re.compile(r'^提交诊断'))).to_be_enabled()
    return dialog


def finish(page, errors, label):
    assert not errors, errors
    assert not events(page, 'weekly'), 'Loading checks must never submit a diagnosis'
    print('PASS', label, flush=True)
    page.close()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='chrome', headless=True)
    for width, height in [(1440, 1000), (390, 844)]:
        page, errors = open_page(browser, '&slow-overview=2000&slow-preview=900', width, height)
        expect(page.get_by_text('正在加载诊断截止时间…', exact=True)).to_have_count(3)
        started = time.monotonic()
        # Repeated synchronous clicks cover the window before a state-driven disabled update.
        page.get_by_role('button', name='填写诊断表', exact=True).evaluate('(button) => { button.click(); button.click(); button.click(); }')
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_text('正在加载诊断表单…', exact=True)).to_be_visible(timeout=800)
        assert time.monotonic() - started < 1.0
        expect(dialog.locator('#account-diagnosis-form')).to_have_count(0)
        expect(dialog.get_by_role('button', name=re.compile(r'^提交诊断'))).to_be_disabled()
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUTPUT / f'loading-{width}.png'))
        assert dialog.bounding_box()['width'] <= width - 32
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        form_loaded(page)
        assert len(events(page, 'overview')) == 1
        assert len(events(page, 'preview')) == 1
        assert not events(page, 'tasks') and not events(page, 'nodes')
        page.screenshot(path=str(OUTPUT / f'loaded-{width}.png'))
        finish(page, errors, f'{width}px immediate feedback, single overview/preview, repeated-click guard')

    page, errors = open_page(browser, '&preview-error')
    wait_finished(page, 'overview')
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('诊断表单加载失败：测试统计预览加载失败，请重试', exact=True)).to_be_visible()
    expect(dialog.locator('#account-diagnosis-form')).to_have_count(0)
    expect(dialog.get_by_role('button', name=re.compile(r'^提交诊断'))).to_be_disabled()
    page.wait_for_timeout(350)
    page.screenshot(path=str(OUTPUT / 'preview-error.png'))
    dialog.get_by_role('button', name='重试加载', exact=True).evaluate('(button) => { button.click(); button.click(); }')
    form_loaded(page)
    assert len(events(page, 'overview')) == 1 and len(events(page, 'preview')) == 2
    finish(page, errors, 'persistent preview error and retry reuse loaded overview')

    page, errors = open_page(browser, '&overview-error&slow-overview=700')
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('诊断表单加载失败：测试任务加载失败，请重试', exact=True)).to_be_visible()
    assert len(events(page, 'overview')) == 1 and not events(page, 'preview')
    dialog.get_by_role('button', name='重试加载', exact=True).click()
    form_loaded(page)
    assert len(events(page, 'overview')) == 2 and len(events(page, 'preview')) == 1
    finish(page, errors, 'pending overview shared with dialog and failed overview retried')

    page, errors = open_page(browser, '&overview-error')
    expect(page.get_by_text('诊断任务加载失败：测试任务加载失败，请重试', exact=True)).to_have_count(3)
    page.get_by_role('button', name=re.compile(r'^重\s*试$')).first.click()
    wait_finished(page, 'overview', 2)
    expect(page.get_by_text('诊断任务加载失败：测试任务加载失败，请重试', exact=True)).to_have_count(0)
    assert len(events(page, 'overview')) == 2 and not events(page, 'preview')
    finish(page, errors, 'deadline error has working retry')

    for query, text in [('&future-nodes', '该周诊断尚未开放，请在对应周开始后填写'),
                        ('&empty-tasks', '暂无待填写任务')]:
        page, errors = open_page(browser, query)
        wait_finished(page, 'overview')
        expect(page.get_by_text(text, exact=False)).to_have_count(3)
        buttons = page.get_by_role('button', name='填写诊断', exact=True)
        assert buttons.count() == 0
        expect(page.get_by_role('button', name='填写诊断表', exact=True)).to_have_count(1)
        expect(page.get_by_role('button', name='填写诊断表', exact=True)).to_be_disabled()
        assert not events(page, 'preview') and page.get_by_role('dialog').count() == 0
        page.screenshot(path=str(OUTPUT / ('future.png' if 'future' in query else 'empty.png')))
        finish(page, errors, query + ' has consistent unavailable entry state')

    page, errors = open_page(browser, '&denied-preview')
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('诊断表单加载失败：没有该账号诊断填写权限', exact=True)).to_be_visible()
    expect(dialog.get_by_role('button', name=re.compile(r'^提交诊断'))).to_be_disabled()
    expect(dialog.locator('#account-diagnosis-form')).to_have_count(0)
    finish(page, errors, 'authorization failure remains explicit and form unavailable')

    page, errors = open_page(browser, '&slow-preview=1500')
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    page.wait_for_function('window.diagnosisFixture.events.some(event => event.endpoint === "preview")')
    page.get_by_role('dialog').locator('.ant-modal-close').click()
    expect(page.get_by_role('dialog')).to_have_count(0)
    wait_finished(page, 'preview')
    expect(page.get_by_role('dialog')).to_have_count(0)
    page.evaluate('window.diagnosisFixture.delays.preview = 0')
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    form_loaded(page)
    assert len(events(page, 'overview')) == 1 and len(events(page, 'preview')) == 2
    finish(page, errors, 'closing ignores late preview and permits fresh retry')

    page, errors = open_page(browser, '&slow-preview=1500', switchable=True)
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    page.wait_for_function('window.diagnosisFixture.events.some(event => event.endpoint === "preview")')
    page.evaluate('window.switchDiagnosisAccount(900002)')
    expect(page.get_by_role('dialog')).to_have_count(0)
    wait_finished(page, 'overview', 2)
    wait_finished(page, 'preview')
    expect(page.get_by_role('dialog')).to_have_count(0)
    page.evaluate('window.diagnosisFixture.delays.preview = 0')
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    form_loaded(page)
    assert [event['accountId'] for event in events(page, 'preview')] == [900001, 900002]
    assert events(page, 'preview')[-1]['nodeKey'].startswith('900002:')
    finish(page, errors, 'same component account switch ignores old preview')

    page, errors = open_page(browser, '&slow-overview=1500', switchable=True)
    page.wait_for_function('window.diagnosisFixture.events.some(event => event.endpoint === "overview")')
    page.evaluate('window.diagnosisFixture.delays.overview = 0; window.switchDiagnosisAccount(900002)')
    wait_finished(page, 'overview', 2)
    page.get_by_role('button', name='填写诊断表', exact=True).click()
    form_loaded(page)
    assert len(events(page, 'overview')) == 2
    assert events(page, 'preview')[0]['accountId'] == 900002
    assert events(page, 'preview')[0]['nodeKey'].startswith('900002:')
    finish(page, errors, 'late old overview cannot replace new account overview')

    page, errors = open_page(browser, '&deep-link&preview-error', switchable=True)
    dialog = page.get_by_role('dialog')
    expect(dialog.get_by_text('诊断表单加载失败：测试统计预览加载失败，请重试', exact=True)).to_be_visible()
    dialog.get_by_role('button', name='重试加载', exact=True).click()
    form_loaded(page)
    assert len(events(page, 'overview')) == 1 and len(events(page, 'preview')) == 2
    dialog.locator('.ant-modal-close').click()
    page.evaluate('window.switchDiagnosisAccount(900001)')
    expect(page.get_by_role('dialog')).to_have_count(0)
    finish(page, errors, 'deep-link failure retries without duplicate automatic opening')
    browser.close()

print(json.dumps({'result': 'PASS', 'screenshots': str(OUTPUT)}, ensure_ascii=False))
