# -*- coding: utf-8 -*-
"""Real UI with synthetic transport; no real task or attachment mutations."""
from pathlib import Path
from tempfile import gettempdir
import base64
import re
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-appeal-review'
OUT.mkdir(exist_ok=True)
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a23sAAAAASUVORK5CYII=')
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    page.set_default_timeout(15000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/test/appeal-evidence.png', lambda route: route.fulfill(content_type='image/png', body=PNG))
    def enter(width=1440, query=''):
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto('http://127.0.0.1:5174/test/lead-appeal-review.html?appealId=101' + query)
        expect(page.get_by_role('tab', name='申诉记录 · 处理中', exact=True)).to_have_attribute('aria-selected', 'true')
        expect(page.get_by_role('textbox', name='裁决理由', exact=True)).to_be_visible()
    for width in (1440, 390):
        enter(width)
        expect(page.locator('.appeal-review-main .attachment-card')).to_have_count(2)
        page.wait_for_timeout(400)
        page.screenshot(path=str(OUT / f'review-{width}.png'), full_page=True)
        page.locator('.appeal-review-main').get_by_role('button', name='eye 预览', exact=True).first.click()
        expect(page.get_by_role('button', name='下载原文件')).to_be_visible()
        page.locator('.ant-modal-close').last.click()
        reason = page.get_by_role('textbox', name='裁决理由', exact=True)
        reason.fill('已核实沟通材料，客户有明确学习意向。')
        # Common detail tabs retain the draft.
        page.get_by_role('tab', name='概览', exact=True).click()
        page.get_by_role('tab', name='申诉记录 · 处理中', exact=True).click()
        expect(reason).to_have_value('已核实沟通材料，客户有明确学习意向。')
        expect(page.locator('.appeal-review-main').get_by_text('历史裁决意见', exact=True)).to_be_visible()
        expect(page.locator('.appeal-review-history details')).to_have_count(0)
        expect(page.locator('.appeal-round .appeal-decision-form')).to_have_count(1)
        expect(reason).to_have_value('已核实沟通材料，客户有明确学习意向。')
        reason.fill('重新核实，确认客户意向有效。')
        page.evaluate("appealReviewFixture.fail = '任务状态已变化，请刷新后重试'")
        page.get_by_role('button', name='改判有效', exact=True).click()
        page.get_by_role('button', name='确认执行', exact=True).click()
        expect(page.get_by_text('任务状态已变化，请刷新后重试', exact=True)).to_be_visible()
        expect(reason).to_have_value('重新核实，确认客户意向有效。')
        expect(page.get_by_role('button', name='改判有效', exact=True)).not_to_have_class(re.compile('.*ant-btn-loading.*'))
        page.evaluate("appealReviewFixture.fail = ''")
        page.get_by_role('button', name='改判有效', exact=True).click()
        page.get_by_role('button', name='确认执行', exact=True).click()
        expect(page.get_by_role('textbox', name='裁决理由', exact=True)).to_have_count(0)
        expect(page.locator('.appeal-review-main').get_by_text('重新核实，确认客户意向有效。', exact=True)).to_be_visible()
        assert page.evaluate('appealReviewFixture.posts[0].payload.idempotencyKey === appealReviewFixture.posts[1].payload.idempotencyKey')
        assert page.evaluate('appealReviewFixture.posts[1].id') == 101
        assert page.evaluate('appealReviewFixture.posts[1].payload.taskId') == 'task-101'
        expect(page.locator('.appeal-lead-context').get_by_text('KZ-APPEAL-TEST', exact=True)).to_be_visible()
        assert page.evaluate('appealReviewFixture.lists') < 6
        print('PASS review/attachments/history/draft/retry/retain-selection', width, flush=True)
    enter(1440, '&restricted=1')
    expect(page.get_by_role('tab', name='概览', exact=True)).to_have_count(0)
    page.get_by_role('textbox', name='裁决理由', exact=True).fill('证据不足，维持本轮无效结论。')
    page.get_by_role('button', name='维持无效', exact=True).click()
    page.get_by_role('button', name='确认执行', exact=True).click()
    expect(page.get_by_text('本轮已结束，符合条件的申请人可手动发起下一轮申诉。')).to_be_visible()
    print('PASS restricted task-only view and uphold', flush=True)
    enter(1440)
    reason = page.get_by_role('textbox', name='裁决理由', exact=True)
    reason.fill('补充附件后重新核实。')
    page.once('dialog', lambda dialog: dialog.dismiss())
    page.locator('.ant-drawer-close').click()
    expect(reason).to_have_value('补充附件后重新核实。')
    page.locator('.appeal-decision-form input[type=file]').set_input_files({'name': 'proof.png', 'mimeType': 'image/png', 'buffer': PNG})
    page.evaluate('appealReviewFixture.uploadFail = true')
    page.get_by_role('button', name='改判有效', exact=True).click()
    page.get_by_role('button', name='确认执行', exact=True).click()
    expect(page.get_by_text('有裁决附件上传失败，请重试失败项', exact=True)).to_be_visible()
    assert page.evaluate('appealReviewFixture.posts.length') == 0
    expect(reason).to_have_value('补充附件后重新核实。')
    expect(page.get_by_role('button', name='改判有效', exact=True)).not_to_have_class(re.compile('.*ant-btn-loading.*'))
    page.evaluate('appealReviewFixture.uploadFail = false')
    page.get_by_role('button', name='改判有效', exact=True).click()
    page.get_by_role('button', name='确认执行', exact=True).click()
    expect(reason).to_have_count(0)
    assert page.evaluate('appealReviewFixture.posts[0].payload.attachments[0].infraFileId') == 10
    print('PASS common upload failure/retry and close draft protection', flush=True)
    enter(1440, '&layout=split')
    expect(page.locator('.appeal-shared-detail')).to_have_count(1)
    expect(page.locator('.business-inbox-detail-pane .appeal-shared-detail')).to_be_visible()
    page.get_by_role('textbox', name='裁决理由', exact=True).fill('待保存意见')
    page.once('dialog', lambda dialog: dialog.dismiss())
    page.locator('.business-inbox-item').last.click()
    expect(page.get_by_role('textbox', name='裁决理由', exact=True)).to_have_value('待保存意见')
    print('PASS split layout single detail and switch protection', flush=True)
    page.goto('http://127.0.0.1:5174/test/lead-appeal-review.html?appealId=101&denied=1')
    expect(page.get_by_text('本轮正在处理中，当前仅可查看。')).to_be_visible()
    expect(page.get_by_role('button', name='改判有效', exact=True)).to_have_count(0)
    page.goto('http://127.0.0.1:5174/test/lead-appeal-review.html?appealId=101&loadError=1')
    expect(page.get_by_text('当前账号无权读取申诉任务')).to_be_visible()
    expect(page.locator('.appeal-shared-detail').get_by_role('button', name=re.compile('重\\s*试'))).to_be_visible()
    # Existing submitter entry continues to use server-owned cycle eligibility.
    for width in (1440, 390):
        page.set_viewport_size({'width': width, 'height': 1000})
        for mode in ('new', 'next', 'pending', 'final'):
            page.goto('http://127.0.0.1:5174/test/lead-appeal-cycles.html?mode=' + mode)
            if mode in ('new', 'next'):
                round_no = 1 if mode == 'new' else 2
                page.get_by_role('button', name=f'发起第 {round_no} 次申诉').click()
                page.get_by_placeholder('填写申诉理由').fill('针对当前无效判定申请复核')
                page.get_by_role('button', name='提交申诉', exact=True).click()
                page.get_by_role('button', name='确认执行', exact=True).click()
                page.wait_for_function('window.submitted !== undefined')
                assert page.evaluate('window.submitted.invalidEventId') == 20
            else:
                expect(page.get_by_role('button', name='发起第', exact=False)).to_have_count(0)
    print('PASS existing submitter cycle entry desktop/mobile', flush=True)
    for width in (1440, 390):
        enter(width, '&full=1')
        expect(page.locator('.appeal-round')).to_have_count(4)
        expect(page.locator('.appeal-decision-form')).to_have_count(1)
        expect(page.locator('.appeal-round.is-target')).to_contain_text('第 3 轮')
        for content in ('第 1 轮补充说明', '第 2 轮补充说明', '历史裁决意见'):
            expect(page.locator('.appeal-review-main').get_by_text(content, exact=True)).to_be_visible()
    assert not errors, errors
    print('PASS denied/error states and no browser exceptions', flush=True)
    browser.close()
