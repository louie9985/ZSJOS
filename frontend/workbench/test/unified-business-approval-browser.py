# -*- coding: utf-8 -*-
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-unified-business-approval'
OUT.mkdir(exist_ok=True)
BASE = 'http://127.0.0.1:5198/test/unified-business-approval.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.set_default_timeout(15000)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    def open_case(query='', width=1440):
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(BASE + '?' + query)
        if 'table' in query:
            page.get_by_role('button', name='详细', exact=False).first.click()
        else:
            expect(page.locator('.business-inbox-item')).to_have_count(3)
        if width <= 768 and 'table' not in query:
            page.locator('.business-inbox-item').first.click()
        return page.locator('.bpm-business-approval')
    def choose(index):
        page.locator('.business-inbox-item').nth(index).click()
    def requests(suffix):
        return page.evaluate('(s) => businessApprovalFixture.requests.filter(r => r.url.endsWith(s))', suffix)

    detail = open_case()
    expect(detail.get_by_text('本轮冻结正文', exact=True)).to_be_visible()
    expect(detail.locator('.content-review-list-pane')).to_have_count(0)
    expect(detail.get_by_role('button', name=re.compile('通过本次审批'))).to_be_disabled()
    editor = detail.locator('.content-review-decision')
    editor.get_by_text('通过', exact=True).click()
    editor.locator('textarea').fill('保存编导结论')
    choose(1)
    page.get_by_role('button', name='继续编辑', exact=True).click()
    expect(editor.locator('textarea')).to_have_value('保存编导结论')
    editor.get_by_role('button', name='保存本条结论').click()
    expect(detail.get_by_role('button', name=re.compile('通过本次审批'))).to_be_enabled()
    detail.get_by_role('button', name=re.compile('通过本次审批')).click()
    page.locator('.ant-modal textarea').fill('本轮审核完成')
    page.locator('.ant-modal').get_by_role('button', name=re.compile(r'确\s*定')).click()
    expect(page.get_by_text('本次操作已完成', exact=True)).to_be_visible()
    assert requests('/complete-director')[0]['body']['taskId'] == 'content-1'
    assert requests('/director-decision')[0]['body']['decision'] == 'APPROVED'
    assert len(requests('/todo-page')) >= 3
    assert not requests('/bpm/task/approve')
    assert all(r['params']['excludeProcessDefinitionKeys'] == 'zsjos_sales_order_dual_approval,zsjos_lead_appeal_review' for r in requests('/todo-page'))
    print('PASS content item decision, leave guard, complete, refresh and original business command', flush=True)

    for index in [1, 2]:
        detail = open_case('failWrite')
        choose(index)
        expect(detail.get_by_text('冻结的拆解正文', exact=True)).to_be_visible()
        expect(detail.locator('.material-approval-toolbar')).to_have_count(0)
        detail.get_by_role('button', name=re.compile(r'^通\s*过$')).click()
        page.locator('.ant-modal textarea').fill('拆解已核验')
        page.locator('.ant-modal').get_by_role('button', name=re.compile(r'确\s*定')).click()
        expect(page.get_by_text('提交失败，请重试', exact=True)).to_be_visible()
        expect(page.locator('.ant-modal textarea')).to_have_value('拆解已核验')
        page.evaluate('businessApprovalFixture.failWrite=false')
        page.locator('.ant-modal').get_by_role('button', name=re.compile(r'确\s*定')).click()
        expect(page.get_by_text('本次操作已完成', exact=True)).to_be_visible()
        assert requests('/zsjos/material-approval/approve')[-1]['body']['taskId'] == ('viral-content' if index == 1 else 'viral-account')
        assert not requests('/bpm/task/approve')
    print('PASS both viral types reuse detail/decision, failed submission preserves input and retries', flush=True)

    detail = open_case('failLocate')
    expect(page.get_by_text('业务读取被拒绝', exact=True)).to_be_visible()
    assert page.locator('.bpm-unified-detail').count() == 0
    page.evaluate('businessApprovalFixture.failLocate=false')
    page.get_by_role('button', name=re.compile(r'^重\s*试$')).click()
    expect(detail.get_by_text('本轮冻结正文', exact=True)).to_be_visible()
    detail = open_case('stale')
    expect(detail.get_by_text('该任务已处理或已流转，当前内容仅供查看，请刷新待办', exact=True)).to_be_visible()
    expect(detail.locator('.content-review-decision')).to_have_count(0)
    print('PASS permission/read error retry and stale-task decisions blocked', flush=True)

    detail = open_case('readonly')
    choose(1)
    expect(detail.get_by_text('冻结的拆解正文', exact=True)).to_be_visible()
    expect(detail.locator('.material-approval-actions button')).to_have_count(0)
    detail = open_case()
    page.get_by_role('tab', name='已办任务', exact=False).click()
    expect(detail.get_by_text('本轮冻结正文', exact=True)).to_be_visible()
    expect(detail.locator('.content-review-decision')).to_have_count(0)
    choose(1)
    expect(detail.get_by_text('本次处理记录', exact=True)).to_be_visible()
    expect(detail.locator('.material-approval-actions button')).to_have_count(0)
    print('PASS business button permissions and done views read-only', flush=True)

    detail = open_case('final')
    editor = detail.locator('.content-review-decision')
    editor.get_by_text('通过', exact=True).click()
    editor.get_by_role('button', name='保存本条结论').click()
    finish = detail.get_by_role('button', name=re.compile('通过终审'))
    expect(finish).to_be_enabled()
    finish.click()
    page.locator('.ant-modal textarea').fill('终审通过')
    page.locator('.ant-modal').get_by_role('button', name=re.compile(r'确\s*定')).click()
    expect(page.get_by_text('本次操作已完成', exact=True)).to_be_visible()
    assert requests('/complete-final')[0]['body']['taskId'] == 'content-1'
    assert requests('/final-decision')[0]['body']['decision'] == 'APPROVED'
    detail = open_case('returned')
    editor = detail.locator('.content-review-decision')
    editor.get_by_text('退回', exact=True).click()
    editor.locator('textarea').fill('需修改作品')
    editor.get_by_role('button', name='保存本条结论').click()
    back = detail.get_by_role('button', name='退回运营修改', exact=True)
    expect(back).to_be_enabled()
    back.click()
    page.locator('.ant-modal textarea').fill('整批退回')
    page.locator('.ant-modal').get_by_role('button', name=re.compile(r'确\s*定')).click()
    expect(page.get_by_text('本次操作已完成', exact=True)).to_be_visible()
    assert requests('/complete-director')[0]['body']['decision'] == 'RETURNED'
    detail = open_case()
    choose(2)
    detail.get_by_role('button', name=re.compile(r'^驳\s*回$')).click()
    page.locator('.ant-modal textarea').fill('拆解需补充')
    page.locator('.ant-modal').get_by_role('button', name=re.compile(r'确\s*定')).click()
    expect(page.get_by_text('本次操作已完成', exact=True)).to_be_visible()
    assert requests('/zsjos/material-approval/reject')[0]['body']['taskId'] == 'viral-account'
    print('PASS final review, batch return and viral rejection use business commands', flush=True)

    detail = open_case('table')
    expect(detail.get_by_text('本轮冻结正文', exact=True)).to_be_visible()
    expect(page.locator('.ant-drawer:visible')).to_have_count(1)
    print('PASS table detail drawer reuses business view', flush=True)

    for width in [1440, 390]:
        detail = open_case('', width)
        expect(detail.get_by_text('本轮冻结正文', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'content-{width}.png'), full_page=True, animations='disabled')
        if width <= 768:
            page.locator('.ant-drawer-close').click()
        choose(2)
        expect(detail.get_by_text('冻结的拆解正文', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'viral-{width}.png'), full_page=True, animations='disabled')
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
    assert not errors, errors
    print('PASS desktop/mobile views and no page errors; screenshots: ' + str(OUT), flush=True)
    browser.close()
