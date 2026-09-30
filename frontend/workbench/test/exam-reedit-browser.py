# -*- coding: utf-8 -*-
"""Actual React/Ant Design browser checks using isolated test transport only."""
import os
import re
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.mkdtemp(prefix='exam-reedit-browser-'))
url = os.environ.get('WORKBENCH_TEST_URL', 'http://localhost:5174') + '/test/exam-reedit.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(url)
        expect(page.locator('.exam-calendar-event')).to_have_count(1)
        expect(page.locator('.exam-calendar-event')).to_have_text('待修正考试')
        expect(page.locator('.exam-month .exam-countdown')).to_have_count(0)
        page.screenshot(path=str(out / f'calendar-{width}.png'), full_page=True, animations='disabled')
        page.locator('.exam-calendar-event').click()
        expect(page.get_by_role('button', name=re.compile('重新编辑'))).to_be_visible()
        expect(page.get_by_role('dialog').locator('.exam-countdown')).to_be_visible()
        page.get_by_role('button', name=re.compile('重新编辑')).click()
        editor = page.get_by_role('dialog').filter(has=page.get_by_text('重新编辑考期', exact=True))
        expect(editor.get_by_label('考期名称')).to_have_value('待修正考试')
        expect(editor.get_by_label('备注')).to_have_value('原始完整备注')
        expect(page.locator('.exam-calendar-event')).to_have_count(0)
        page.wait_for_timeout(350)
        page.screenshot(path=str(out / f'editor-{width}.png'), full_page=True, animations='disabled')
        assert editor.evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
        editor.get_by_role('button', name=re.compile('取.*消')).click()
        expect(page.locator('.ant-modal-confirm-title')).to_be_visible()
        page.get_by_role('button', name='继续编辑').click()
        expect(editor).to_be_visible()
        # Opening the editor consumes the claim; time afterwards must not invalidate saving.
        page.clock.install()
        page.clock.fast_forward(301000)
        editor.get_by_label('考期名称').fill('修改后的考试')
        page.evaluate('window.examReeditFixture.failPublish = true')
        editor.get_by_role('button', name='保存并发布').click()
        expect(page.get_by_text('测试发布失败，草稿已保存', exact=False)).to_be_visible()
        expect(page.get_by_role('dialog').get_by_label('考期名称')).to_have_value('修改后的考试')
        page.get_by_role('dialog').get_by_role('button', name='保存并发布').click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert page.evaluate('window.examReeditFixture.creates') == 1
        assert page.evaluate('window.examReeditFixture.updates') == [101]
        expect(page.locator('.exam-calendar-event')).to_contain_text('修改后的考试')
        assert not errors, errors
        page.close()

        # An already-open day detail also loses expired rows without a refresh.
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto(url)
        page.clock.install()
        page.locator('.exam-calendar-event').click()
        expect(page.get_by_role('dialog')).to_contain_text('待修正考试')
        page.clock.fast_forward(300500)
        expect(page.locator('.exam-calendar-event')).to_have_count(0)
        expect(page.get_by_role('dialog')).to_contain_text('当天暂无单日考试')
        expect(page.get_by_role('button', name=re.compile('重新编辑'))).to_have_count(0)
        page.close()

        # Multiday ranges survive prefilling after navigating into the next month.
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto(url)
        page.evaluate("window.resetExamReedit('MULTI_DAY')")
        dates = page.evaluate('[window.examReeditFixture.rows[0].startDate, window.examReeditFixture.rows[0].endDate]')
        page.get_by_role('button', name='下一月', exact=True).click()
        page.locator('.exam-multiDay-days button').first.click()
        page.get_by_role('button', name=re.compile('重新编辑')).click()
        editor = page.get_by_role('dialog').filter(has=page.get_by_text('重新编辑考期', exact=True))
        expect(editor.locator('.ant-picker-range input').nth(0)).to_have_value(dates[0])
        expect(editor.locator('.ant-picker-range input').nth(1)).to_have_value(dates[1])
        expect(page.locator('.exam-multiDay-bar')).to_have_count(0)
        editor.get_by_role('button', name='保存草稿').click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert page.evaluate('window.examReeditFixture.creates') == 1
        assert page.evaluate('window.examReeditFixture.rows[0].recordStatus') == 'DRAFT'
        page.close()

        # Deadline expiration clears month/drawer projections without refreshing.
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto(url)
        page.clock.install()
        page.evaluate("window.resetExamReedit('MULTI_DAY')")
        page.get_by_role('button', name='刷新', exact=True).click()
        expect(page.locator('.exam-multiDay-bar').first).to_contain_text('待修正考试')
        expect(page.locator('.exam-month .exam-countdown')).to_have_count(0)
        page.get_by_role('button', name=re.compile('多日考试安排')).click()
        expect(page.get_by_role('button', name=re.compile('重新编辑'))).to_be_visible()
        page.clock.fast_forward(299000)
        expect(page.get_by_role('button', name=re.compile('重新编辑'))).to_be_visible()
        page.clock.fast_forward(1500)
        expect(page.get_by_role('button', name=re.compile('重新编辑'))).to_have_count(0)
        expect(page.locator('.exam-multiDay-bar')).to_have_count(0)
        expect(page.get_by_text('暂无多日考试安排', exact=True)).to_be_visible()
        page.close()

        # Successful claim with a lost response: recover same key even after the row expires.
        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto(url)
        page.clock.install()
        page.evaluate('window.examReeditFixture.lostResponse = true')
        page.locator('.exam-calendar-event').click()
        page.get_by_role('button', name=re.compile('重新编辑')).click()
        expect(page.get_by_role('button', name='重试取回')).to_be_visible()
        page.get_by_role('dialog').get_by_role('button', name='Close').click()
        page.clock.fast_forward(301000)
        page.get_by_role('button', name='重试取回').click()
        expect(page.get_by_role('dialog').get_by_label('考期名称')).to_have_value('待修正考试')
        keys = page.evaluate('window.examReeditFixture.claims')
        assert len(keys) == 2 and keys[0] == keys[1]
        page.get_by_role('dialog').get_by_role('button', name=re.compile('取.*消')).click()
        page.get_by_role('button', name='放弃编辑', exact=True).click()
        expect(page.get_by_role('dialog')).to_have_count(0)
        expect(page.locator('.exam-calendar-event')).to_have_count(0)
        assert page.evaluate('window.examReeditFixture.creates') == 0
        page.close()

        page = browser.new_page(viewport={'width': width, 'height': 960})
        page.goto(url)
        page.evaluate("window.resetExamReedit('EXACT', false)")
        page.get_by_role('button', name='刷新', exact=True).click()
        page.locator('.exam-calendar-event').click()
        expect(page.get_by_role('button', name=re.compile('重新编辑'))).to_have_count(0)
        expect(page.get_by_role('dialog')).to_contain_text('已撤销')
        page.close()
        print('PASS reedit/prefill/cancel/expiry/replay/publish retry/owner:', width, flush=True)
    browser.close()
print('Browser evidence:', out)
