# -*- coding: utf-8 -*-
"""Real Workbench components, synthetic transport. Never writes live business data."""
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-feedback-inline-approval'
OUT.mkdir(exist_ok=True)
BASE = 'http://127.0.0.1:5174/test/'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    page.set_default_timeout(15000)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.route('**/fixture-attachment.svg', lambda route: route.fulfill(content_type='image/svg+xml', body='<svg xmlns="http://www.w3.org/2000/svg" width="300" height="160"><rect width="300" height="160" fill="#e6f4ff"/></svg>'))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(BASE + 'feedback-task-detail.html')
        if width > 768:
            page.get_by_role('button', name=re.compile('详\\s*细')).click()
        else:
            page.locator('.business-inbox-item').first.click()
        drawer = page.locator('.ant-drawer-body').filter(has=page.locator('.feedback-task-detail'))
        expect(drawer.locator('.feedback-approval-sidebar .bpm-approval-actions')).to_be_visible()
        expect(drawer.get_by_text('提交时冻结的标签', exact=True)).to_be_visible()
        expect(drawer.get_by_role('button', name='打开业务详情')).to_have_count(0)
        image = drawer.get_by_alt_text('需求示意图')
        expect(image).to_be_visible()
        image.click(); expect(page.locator('.ant-modal')).to_be_visible(); page.keyboard.press('Escape')
        expect(page.locator('.ant-modal')).not_to_be_visible()
        box = drawer.locator('.feedback-approval-layout').evaluate('e=>{const a=e.children[0].getBoundingClientRect(),b=e.children[1].getBoundingClientRect();return {ax:a.x,ay:a.y,ar:a.right,bx:b.x,by:b.y,br:b.right,w:innerWidth}}')
        assert box['br'] <= width + 1, box
        assert box['bx'] > box['ar'] if width > 768 else box['by'] > box['ay'], box
        page.screenshot(path=str(OUT / f'task-{width}.png'), full_page=True)
        drawer.locator('.bpm-approval-actions button').nth(0 if width > 768 else 1).click()
        dialog = page.get_by_role('dialog').last
        dialog.locator('textarea').fill('同意本轮需求')
        dialog.locator('.ant-modal-footer button.ant-btn-primary').click()
        page.wait_for_function('taskFixture.actions.length === 1')
        assert page.evaluate('taskFixture.actions[0].id') == 'feedback-task'
        assert page.evaluate('taskFixture.actions[0].url') == '/bpm/task/' + ('approve' if width > 768 else 'reject')
        print('PASS task detail entry/layout/image/decision', width, flush=True)
    for mode in ['readonly', 'stale', 'fail', 'done']:
        page.set_viewport_size({'width': 1440, 'height': 1000})
        page.goto(BASE + 'feedback-task-detail.html?' + mode)
        page.get_by_role('button', name=re.compile('详\\s*细')).click()
        drawer = page.locator('.ant-drawer-body')
        expect(drawer.locator('.bpm-approval-actions button')).to_have_count(0)
        if mode == 'fail':
            expect(drawer.get_by_text('需求内容加载失败，请重试')).to_be_visible()
            page.evaluate('taskFixture.fail = false')
            drawer.get_by_role('button', name=re.compile('重\\s*试')).click()
            expect(drawer.locator('.bpm-approval-actions')).to_be_visible()
        print('PASS task boundary', mode, flush=True)
    for mode in ['approver', 'approver&other-user', 'approver&no-approve']:
        page.goto(BASE + 'feedback-approval.html?' + mode)
        page.get_by_role('button', name='需求审批展示验证', exact=False).click()
        expect(page.get_by_text('第二轮调整后的内容', exact=True)).to_be_visible()
        actions = page.locator('.feedback-task-actions .bpm-approval-actions')
        if mode == 'approver':
            expect(actions).to_be_visible()
            page.get_by_role('combobox', name='审批轮次').click(); page.get_by_title('第 1 轮', exact=True).click()
            expect(page.get_by_text('第一轮原始内容', exact=True)).to_be_visible(); expect(actions).to_have_count(0)
            page.get_by_role('combobox', name='审批轮次').click(); page.get_by_title('第 2 轮（最新）', exact=True).click()
            expect(actions).to_be_visible()
            page.evaluate('feedbackFixture.taskFail = true'); page.get_by_role('button', name='刷新流程', exact=True).click()
            expect(page.get_by_text('当前待办读取失败', exact=True)).to_be_visible(); expect(actions).to_have_count(0)
            page.evaluate('feedbackFixture.taskFail = false'); page.locator('.feedback-task-actions').get_by_role('button', name=re.compile('重\\s*试')).click()
            expect(actions).to_be_visible()
            actions.locator('button').nth(1).click(); dialog = page.get_by_role('dialog').last
            dialog.locator('textarea').fill('请补充需求场景'); dialog.locator('.ant-modal-footer button.ant-btn-primary').click()
            page.wait_for_function('feedbackFixture.actions.length === 1')
            assert page.evaluate('feedbackFixture.actions[0].id') == 'task-2'
            expect(actions).to_have_count(0)
        else: expect(actions).to_have_count(0)
        print('PASS feedback detail boundary', mode, flush=True)
    for mode in ['', 'readonly', 'disabled', 'missing']:
        page.goto(BASE + 'feedback-approval.html?' + mode)
        page.get_by_role('button', name='需求审批展示验证', exact=False).click()
        expect(page.get_by_text('第二轮调整后的内容', exact=True)).to_be_visible()
        urge = page.get_by_role('button', name=re.compile('催\\s*办$'))
        if mode:
            expect(urge).to_have_count(0)
        else:
            page.evaluate('feedbackFixture.urgeFail = true'); urge.click()
            expect(page.get_by_text('网络中断，请重试', exact=True)).to_be_visible()
            key = page.evaluate('feedbackFixture.posts[0].idempotencyKey')
            page.evaluate('feedbackFixture.urgeFail = false'); urge.click()
            expect(page.get_by_role('button', name='催办冷却中', exact=True)).to_be_disabled()
            assert page.evaluate('feedbackFixture.posts[1].idempotencyKey') == key
        print('PASS retained submitter flow', mode or 'urge-retry', flush=True)
    assert not errors, errors
    browser.close()
print('Screenshots:', OUT)
