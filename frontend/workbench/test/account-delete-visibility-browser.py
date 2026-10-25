# -*- coding: utf-8 -*-
"""Real MediaStudentsPage and Chrome; all API responses and writes are isolated."""
import os
import re
import sys
from pathlib import Path
from tempfile import gettempdir
from urllib.parse import parse_qs
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('ZSJOS_UI_TEST_URL', 'http://127.0.0.1:5174') + '/test/account-delete-visibility.html'
OUT = Path(gettempdir()) / 'zsjos-account-delete-visibility'
OUT.mkdir(exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    context.route('**/admin-api/**', lambda route: route.abort('blockedbyclient'))

    def visit(query=''):
        page.goto(BASE + query, wait_until='domcontentloaded', timeout=90000)
        expect(page.locator('.media-students-account-card')).to_have_count(1)
        expect(page.locator('.media-students-account-card')).to_contain_text('删除验收学员')
        expect(page.get_by_text('已经删除的测试账号', exact=True)).to_have_count(0)

    def writes():
        return page.evaluate('accountDeleteVisibilityFixture.requests')

    def reason_dialog(reason='误新增的测试账号'):
        page.get_by_role('button', name='删除账号', exact=True).click()
        dialog = page.get_by_role('dialog', name='确认删除账号', exact=True)
        expect(dialog.get_by_role('button', name='确认删除', exact=True)).to_be_disabled()
        dialog.get_by_label('删除原因').fill(reason)
        return dialog

    def final_dialog(dialog):
        before = len(writes())
        dialog.get_by_role('button', name='确认删除', exact=True).click()
        final = page.get_by_role('dialog', name='再次确认删除账号', exact=True)
        expect(final).to_be_visible()
        assert len(writes()) == before, 'Opening final confirmation must not send a deletion request'
        return final

    def check_removed(remaining):
        expect(page.get_by_role('dialog')).to_have_count(0)
        expect(page.get_by_role('tab', name='概览', exact=True)).to_have_attribute('aria-selected', 'true')
        expect(page.get_by_role('tab', name='待删除的测试账号', exact=True)).to_have_count(0)
        expect(page.locator('.media-students-account-card').get_by_text('待删除的测试账号', exact=True)).to_have_count(0)
        expect(page.locator('.media-students-account-card')).to_have_count(1)
        expect(page.locator('.media-students-account-card .media-students-account-row')).to_have_count(remaining)
        route = parse_qs(page.locator('#fixture-route').text_content().lstrip('?'))
        assert route.get('personId') == ['900000'], route
        assert not any(key in route for key in ['accountId', 'contentId', 'positioningCardId', 'diagnosisTaskId']), route
        assert route.get('tab', ['overview']) == ['overview'], route
        expect(page.get_by_text(re.compile('该账号已失效、不属于此学员或你无权查看'))).to_have_count(0)

    def check_retained_refresh():
        page.set_viewport_size({'width': 1440, 'height': 1000})
        visit('?retained')
        final_dialog(reason_dialog()).get_by_role('button', name='仍然删除', exact=True).click()
        page.wait_for_function('accountDeleteVisibilityFixture.profileRefreshStarted === 1')
        page.get_by_role('button', name='切换到其他页签', exact=True).click()
        expect(page.locator('#fixture-route')).to_have_attribute('data-path', '/other')
        expect(page.get_by_role('heading', name='其他工作台页签', exact=True)).to_be_visible()
        page.wait_for_function('accountDeleteVisibilityFixture.detailRefreshCompleted === 1')
        expect(page.locator('.media-students-account-card .media-students-account-row')).to_have_count(1)
        expect(page.locator('#fixture-route')).to_have_attribute('data-path', '/other')
        expect(page.get_by_role('heading', name='其他工作台页签', exact=True)).to_be_visible()
        assert page.evaluate('accountDeleteVisibilityFixture.profileRefreshCompleted') == 1
        page.get_by_role('button', name='返回原学员页签', exact=True).click()
        expect(page.locator('#fixture-route')).to_have_attribute('data-path', '/zsjos/media-students')
        check_removed(1)
        assert len(writes()) == 1
        print('PASS retained page: delayed post-delete refresh never steals another tab; returning clears stale account route', flush=True)

    if '--retained-only' in sys.argv:
        check_retained_refresh()
        assert not errors, errors
        context.close()
        browser.close()
        sys.exit(0)

    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        visit()
        expect(page.get_by_role('tab', name='待删除的测试账号', exact=True)).to_have_attribute('aria-selected', 'true')
        expect(page.locator('.media-students-account-card .media-students-account-row')).to_have_count(2)
        dialog = reason_dialog()
        final = final_dialog(dialog)
        expect(final.get_by_text(re.compile('MA-DELETE-900001'))).to_be_visible()
        expect(page.locator('.ant-message-notice')).to_have_count(0)
        page.wait_for_timeout(350)  # Let Ant Design's second-layer entrance animation finish before the screenshot.
        expect(final.get_by_role('button', name='仍然删除', exact=True)).to_be_in_viewport()
        page.screenshot(path=str(OUT / f'confirm-{width}.png'), animations='disabled')
        box = final.bounding_box()
        assert box['width'] <= width - 16 and box['x'] >= 0 and box['x'] + box['width'] <= width, box
        final.get_by_role('button', name=re.compile(r'^取\s*消$')).click()
        expect(final).to_have_count(0)
        expect(dialog.get_by_label('删除原因')).to_have_value('误新增的测试账号')
        assert writes() == []
        final = final_dialog(dialog)
        final.get_by_role('button', name='仍然删除', exact=True).dblclick()
        check_removed(1)
        assert len(writes()) == 1 and writes()[0]['body'] == {'reason': '误新增的测试账号'}
        expect(page.get_by_role('tab', name='保留的测试账号', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'removed-{width}.png'), full_page=True, animations='disabled')
        print(f'PASS {width}: second confirmation, cancel, one request, account/card/tab removal and route reset', flush=True)

        visit('?single')
        final_dialog(reason_dialog()).get_by_role('button', name='仍然删除', exact=True).click()
        check_removed(0)
        expect(page.locator('.media-students-account-card')).to_contain_text('暂无可见账号')
        print(f'PASS {width}: last account deletion keeps the student and overview', flush=True)

    visit('?fail')
    dialog = reason_dialog('失败后保留删除原因')
    final = final_dialog(dialog)
    final.get_by_role('button', name='仍然删除', exact=True).click()
    expect(page.get_by_text('账号已被其他人修改，请刷新后重试', exact=True)).to_be_visible()
    expect(page.get_by_role('tab', name='待删除的测试账号', exact=True)).to_be_visible()
    # Implementations may retain the final dialog for retry or return to the reason form.
    if final.count() and final.is_visible():
        final.get_by_role('button', name=re.compile(r'^取\s*消$')).click()
    expect(dialog.get_by_label('删除原因')).to_have_value('失败后保留删除原因')
    final_dialog(dialog).get_by_role('button', name='仍然删除', exact=True).click()
    check_removed(1)
    assert len(writes()) == 2
    print('PASS failure: unchanged account and reason retained, successful retry', flush=True)

    visit('?pending')
    expect(page.get_by_role('tab', name='待删除的测试账号', exact=True)).to_be_visible()
    expect(page.get_by_text('删除审批中，账号已冻结', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='删除账号', exact=True)).to_have_count(0)
    assert writes() == []
    visit('?operator')
    page.get_by_role('button', name='申请删除账号', exact=True).click()
    dialog = page.get_by_role('dialog', name='申请删除账号', exact=True)
    dialog.get_by_label('删除原因').fill('运营申请删除')
    dialog.get_by_role('button', name='提交审批', exact=True).click()
    expect(dialog).to_have_count(0)
    expect(page.get_by_text('删除审批中，账号已冻结', exact=True)).to_be_visible()
    expect(page.get_by_role('tab', name='待删除的测试账号', exact=True)).to_have_attribute('aria-selected', 'true')
    assert len(writes()) == 1 and writes()[0]['url'].endswith('/request-delete')
    visit('?denied')
    expect(page.get_by_role('button', name=re.compile('^(删除账号|申请删除账号)$'))).to_have_count(0)
    visit('?invalid')
    expect(page.get_by_text(re.compile('该账号已失效、不属于此学员或你无权查看'))).to_be_visible()
    assert writes() == []
    check_retained_refresh()
    assert not errors, errors
    context.close()
    browser.close()
    print('PASS pending remains, operator submits approval, denied actions absent, invalid deep link rejected, no page errors', flush=True)
