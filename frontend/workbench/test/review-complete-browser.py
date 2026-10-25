# -*- coding: utf-8 -*-
"""Current production components with isolated adapters; no live writes or service startup."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

ORIGIN = os.environ.get('WB_TEST_ORIGIN', 'http://127.0.0.1:5174')
OUT = Path(gettempdir()) / 'zsjos-complete-review-browser'
OUT.mkdir(exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        page.set_default_timeout(10000)
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(ORIGIN + '/test/review-complete.html')
        expect(page.locator('.lead-inbox-item')).to_have_count(2)
        page.locator('.lead-inbox-item').first.click()
        page.get_by_role('tab', name='学员档案', exact=True).filter(visible=True).click()
        page.get_by_role('button', name='修改从事职业', exact=True).filter(visible=True).click()
        editor = page.locator('.archive-inline-editor:visible')
        editor.get_by_label('从事职业').fill('未保存的职业')
        before = page.evaluate('completeReviewFixture.studentReads.length')
        rejected = []
        def dismiss(dialog):
            rejected.append(dialog.message)
            dialog.dismiss()
        page.on('dialog', dismiss)
        if width > 768:
            page.locator('.lead-inbox-item').last.click()
            page.locator('.lead-inbox-item').first.click()
        else:
            page.locator('.ant-drawer-close:visible').click()
        expect(editor.get_by_label('从事职业')).to_have_value('未保存的职业')
        assert rejected and page.evaluate('completeReviewFixture.studentReads.length') == before
        page.evaluate('completeReviewFixture.holdSave = true')
        editor.get_by_role('button', name=re.compile('保\s*存')).click()
        expect(editor.get_by_label('从事职业')).to_be_disabled()
        if width > 768: page.locator('.lead-inbox-item').last.click()
        else: page.locator('.ant-drawer-close:visible').click()
        assert page.evaluate('completeReviewFixture.studentReads.length') == before
        page.evaluate('completeReviewFixture.releaseSave()')
        expect(editor).to_have_count(0)
        assert page.evaluate('completeReviewFixture.saves') == 1
        assert page.evaluate('completeReviewFixture.leadReads.includes(101)'), 'student-only permission must read its authorized linked lead'
        page.get_by_role('button', name='修改对接渠道', exact=True).filter(visible=True).click()
        editor = page.locator('.archive-inline-editor:visible')
        editor.get_by_label('对接渠道', exact=True).fill('未保存的渠道')
        page.get_by_role('button', name='离开学员页').evaluate('(button) => button.click()')
        expect(editor.get_by_label('对接渠道', exact=True)).to_have_value('未保存的渠道')
        page.remove_listener('dialog', dismiss)
        page.on('dialog', lambda dialog: dialog.accept())
        page.screenshot(path=str(OUT / f'archive-{width}.png'), full_page=True)
        page.get_by_role('button', name='离开学员页').evaluate('(button) => button.click()')
        expect(page.get_by_text('已离开学员页', exact=True)).to_be_visible()
        assert not errors, errors
        page.close()
        print('PASS archive dirty/busy/selection/navigation and linked-lead permission', width, flush=True)

    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.goto(ORIGIN + '/test/review-complete.html?navigation')
    page.locator('.ant-menu-submenu-title').hover()
    expect(page.locator('.ant-menu-submenu-title')).to_have_attribute('aria-expanded', 'true')
    expect(page.get_by_text('Leaf', exact=True)).to_be_visible()
    page.screenshot(path=str(OUT / 'collapsed-navigation.png'))
    page.close()
    print('PASS collapsed navigation opens the nested submenu', flush=True)

    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.goto(ORIGIN + '/test/review-complete.html?groups')
    page.locator('tr.ant-table-row[data-row-key="1"] input[type=checkbox]').check()
    expect(page.get_by_text('已选 1 个班级', exact=True)).to_be_visible()
    page.evaluate('completeReviewFixture.candidateFail = true')
    page.locator('.ant-pagination-item-2').click()
    expect(page.get_by_text('候选翻页失败', exact=True)).to_be_visible()
    expect(page.get_by_text('已选 1 个班级', exact=True)).to_be_visible()
    page.get_by_role('button', name=re.compile('重\s*试')).click()
    expect(page.get_by_text('候选翻页失败', exact=True)).to_have_count(0)
    page.get_by_role('button', name='核对调整').click()
    page.get_by_role('button', name='确认调整').click()
    page.wait_for_function('completeReviewFixture.moved.length === 1')
    assert page.evaluate('completeReviewFixture.moved[0]') == 1
    page.close()
    print('PASS group selection survives a failed next-page request', flush=True)

    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.goto(ORIGIN + '/test/lead-task-indicators.html?layout=table&hold')
    page.wait_for_function('window.taskFixture?.reads === 1')
    page.evaluate("window.dispatchEvent(new Event('focus')); taskFixture.invalidate()")
    assert page.evaluate('taskFixture.reads') == 1
    page.evaluate('taskFixture.releaseRead()')
    expect(page.locator('tbody tr.ant-table-row')).to_have_count(3)
    expect(page.locator('.ant-spin-spinning')).to_have_count(0)
    page.close()
    print('PASS background task refresh does not strand initial loading', flush=True)

    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        page.goto(ORIGIN + '/test/teaching-service-lifecycle.html?closed')
        page.evaluate('''async () => {
          const source = await (await fetch('/src/components/TeachingServiceLifecyclePanel.tsx')).text();
          const url = source.match(/from "([^\"]+services\/teachingServiceLifecycle\.ts[^\"]*)"/)[1];
          const {teachingServiceLifecycleApi: api} = await import(url);
          window.targets = []; api.targets = () => new Promise(resolve => targets.push(resolve));
          window.classes = [{id:10,className:'Class A',classNo:'A',homeroomUserName:'Owner A'}, {id:11,className:'Class B',classNo:'B',homeroomUserName:'Owner B'}];
        }''')
        page.get_by_role('button', name='恢复服务', exact=True).click()
        page.wait_for_function('targets.length === 1')
        page.locator('.ant-modal:visible .ant-modal-footer button').first.click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        page.get_by_role('button', name='恢复服务', exact=True).click()
        page.wait_for_function('targets.length === 2')
        page.evaluate('targets[1](classes)')
        expect(page.locator('.ant-modal:visible .ant-select')).to_contain_text('Class A')
        page.locator('.ant-modal:visible .ant-select').click()
        page.locator('.ant-select-item-option').nth(1).click()
        page.evaluate('targets[0](classes)')
        expect(page.locator('.ant-modal:visible .ant-select')).to_contain_text('Class B')
        page.locator('.ant-modal:visible textarea').fill('Synthetic restore')
        page.locator('.ant-modal:visible .ant-modal-footer button').last.click()
        page.wait_for_function('teachingFixture.requests.length === 1')
        assert page.evaluate('teachingFixture.requests[0].data.targetClassId') == 11
        page.close()
    print('PASS stale restore-target responses cannot change the selected destination', flush=True)
    browser.close()

print('PASS complete review browser regressions', OUT)
