# -*- coding: utf-8 -*-
"""Real React feedback page and Vue approval component, synthetic APIs only."""
import re
import sys
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-feedback-approval'
OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.set_default_timeout(20000)
    for width in ([] if '--admin-only' in sys.argv else [1440, 390]):
        page.set_viewport_size({'width': width, 'height': 1100})
        page.goto('http://127.0.0.1:5294/test/feedback-approval.html')
        expect(page.get_by_text('当前审批：终审 · 测试审批人乙', exact=True)).to_be_visible()
        page.get_by_role('button', name='需求审批展示验证', exact=False).click()
        expect(page.get_by_text('第二轮调整后的内容', exact=True)).to_be_visible()
        page.wait_for_function("() => { const r = document.querySelector('.ant-drawer-content-wrapper')?.getBoundingClientRect(); return r && r.right <= innerWidth + 1 && r.x >= -1; }")
        page.screenshot(path=str(OUT / f'workbench-{width}.png'), full_page=True)
        page.get_by_role('combobox', name='审批轮次').click()
        page.get_by_title('第 1 轮', exact=True).click()
        expect(page.get_by_text('第一轮原始内容', exact=True)).to_be_visible()
        expect(page.get_by_role('button', name=re.compile(r'催\s*办$'))).to_have_count(0)
        page.get_by_role('combobox', name='审批轮次').click()
        page.get_by_title('第 2 轮（最新）', exact=True).click()
        page.evaluate('feedbackFixture.urgeFail = true')
        page.get_by_role('button', name=re.compile(r'催\s*办$')).click()
        expect(page.get_by_text('网络中断，请重试', exact=True)).to_be_visible()
        first_key = page.evaluate('feedbackFixture.posts[0].idempotencyKey')
        page.evaluate('feedbackFixture.urgeFail = false')
        page.get_by_role('button', name=re.compile(r'催\s*办$')).click()
        expect(page.get_by_role('button', name='催办冷却中', exact=True)).to_be_disabled()
        assert page.evaluate('feedbackFixture.posts[1].idempotencyKey') == first_key
        page.evaluate('feedbackFixture.fail = true')
        page.get_by_role('button', name='刷新流程', exact=True).click()
        expect(page.get_by_text('流程读取失败，请重试', exact=True)).to_be_visible()
        page.evaluate('feedbackFixture.fail = false')
        page.get_by_role('button', name=re.compile(r'重\s*试$')).click()
        expect(page.get_by_text('第二轮调整后的内容', exact=True)).to_be_visible()
        print('PASS Workbench', width, 'summary/round/snapshot/retry/urge/replay/cooldown', flush=True)
    for mode in ([] if '--admin-only' in sys.argv else ['readonly', 'disabled', 'missing']):
        page.goto('http://127.0.0.1:5294/test/feedback-approval.html?' + mode + '=1')
        page.get_by_role('button', name='需求审批展示验证', exact=False).click()
        expect(page.get_by_text('第二轮调整后的内容', exact=True)).to_be_visible()
        expect(page.get_by_role('button', name=re.compile(r'催\s*办$'))).to_have_count(0)
        if mode == 'disabled': expect(page.get_by_text('本轮无需审批', exact=True)).to_be_visible()
        if mode == 'missing': expect(page.get_by_text('本轮审批流程记录缺失，请联系管理员', exact=True)).to_be_visible()
        print('PASS Workbench state', mode, flush=True)
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto('http://127.0.0.1:5295/test/feedback-approval.html')
        result = page.evaluate('''async () => {
          const detail = { actionType: 'business_detail', sceneCode: 'zsjos.feedback.approval_urged', templateParams: { taskId: 'active-task' } };
          const target = await feedbackUrgeTaskTarget(detail);
          const serialized = await feedbackUrgeTaskTarget({ ...detail, templateParams: JSON.stringify(detail.templateParams) });
          const rejected = [];
          for (const templateParams of [{ taskId: 'stale-task' }, {}, 'invalid JSON']) {
            try { await feedbackUrgeTaskTarget({ ...detail, templateParams }); rejected.push(false); }
            catch { rejected.push(true); }
          }
          return { target, serialized, rejected, candidate: isFeedbackUrgeNotification(detail), readOnly: isFeedbackUrgeNotification({ ...detail, actionType: 'message_detail' }) };
        }''')
        assert result['target'] == {'name': 'BpmProcessInstanceDetail', 'query': {'id': 'active-process', 'taskId': 'active-task'}}
        assert result['serialized'] == result['target'] and all(result['rejected'])
        assert result['candidate'] and not result['readOnly']
        print('PASS Admin notification exact-task/stale/malformed/configured-action', width, flush=True)
        expect(page.get_by_text('第二轮调整后的内容', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'admin-{width}.png'), full_page=True)
        page.locator('.el-select__wrapper').click()
        page.get_by_role('option', name='第 1 轮', exact=True).click()
        expect(page.get_by_text('第一轮原始内容', exact=True)).to_be_visible()
        expect(page.get_by_role('button', name=re.compile(r'催\s*办$'))).to_have_count(0)
        page.evaluate('feedbackFixture.fail = true')
        page.get_by_role('button', name='刷新流程', exact=True).click()
        expect(page.get_by_text('流程读取失败，请重试', exact=True)).to_be_visible()
        page.evaluate('feedbackFixture.fail = false')
        page.get_by_role('button', name=re.compile(r'重\s*试$')).click()
        expect(page.get_by_text('第一轮原始内容', exact=True)).to_be_visible()
        print('PASS Admin', width, 'round/snapshot/readonly/retry', flush=True)
        page.goto('http://127.0.0.1:5295/test/feedback-approval.html?message=1')
        page.wait_for_function('typeof openUrgeMessage === "function"')
        page.evaluate('openUrgeMessage("stale-task")')
        page.get_by_role('button', name='查看审批任务', exact=True).click()
        expect(page.locator('#warning')).to_contain_text('审批任务已结束')
        expect(page.get_by_role('button', name='查看审批任务', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'admin-notification-{width}.png'), full_page=True)
        page.evaluate('openUrgeMessage("active-task")')
        page.get_by_role('button', name='查看审批任务', exact=True).click()
        expect(page.locator('#navigation')).to_contain_text('"taskId":"active-task"')
        expect(page.get_by_role('button', name='查看审批任务', exact=True)).not_to_be_visible()
        print('PASS Admin actual notification detail stale/retry/exact-task', width, flush=True)
    assert not errors, errors
    browser.close()
print('Screenshots:', OUT, flush=True)
