# -*- coding: utf-8 -*-
"""Verify page refresh using real components and isolated transport only."""
import os
import re
from playwright.sync_api import sync_playwright, expect

URL = os.environ.get('EXAM_NOTE_TEST_URL', 'http://127.0.0.1:5219') + '/test/exam-calendar-note.html'

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 960})
    page.goto(URL)
    side = page.get_by_role('complementary', name='日历说明')
    expect(side).to_contain_text('无需考试')
    page.evaluate('''async () => {
      const { examCalendarNote } = await import('/src/services/examCalendarNote.ts');
      const { api } = await import('/src/services/api.ts');
      window.refreshChecks = { note: 0, exact: 0, multi: 0, fail: false };
      const checks = window.refreshChecks;
      const exact = api.examCalendar.exactPage, multi = api.examCalendar.multiDayPage;
      api.examCalendar.exactPage = async (...args) => { checks.exact++; return exact(...args) };
      api.examCalendar.multiDayPage = async (...args) => { checks.multi++; return multi(...args) };
      examCalendarNote.get = async () => {
        checks.note++;
        if (checks.fail) throw new Error('刷新说明失败（模拟）');
        return { content: '<p>服务端新说明 ' + checks.note + '</p>', version: checks.note + 1, images: [] };
      };
    }''')
    page.get_by_role('button', name='刷新', exact=True).click()
    expect(side).to_contain_text('服务端新说明 1')
    assert page.evaluate('refreshChecks.exact > 0 && refreshChecks.multi > 0')
    page.get_by_role('button', name='下一月', exact=True).click()
    expect(side).to_contain_text('服务端新说明 1')
    assert page.evaluate('refreshChecks.note') == 1

    page.evaluate('refreshChecks.fail = true')
    page.get_by_role('button', name='刷新', exact=True).click()
    expect(side).to_contain_text('刷新说明失败（模拟）')
    page.evaluate('refreshChecks.fail = false')
    side.get_by_role('button', name=re.compile(r'重\s*试')).click()
    expect(side).to_contain_text('服务端新说明 3')

    side.get_by_role('button', name=re.compile(r'编\s*辑')).click()
    editor = page.get_by_role('dialog', name='编辑日历说明').locator('[data-slate-editor]')
    expect(editor).to_be_visible()
    editor.locator('p').first.click()
    page.keyboard.press('End')
    page.keyboard.type(' unsaved draft')
    # Dispatch the page action behind the modal to verify refresh does not remount the editor.
    page.get_by_role('button', name='刷新', exact=True).evaluate('(button) => button.click()')
    expect(side).to_contain_text('服务端新说明 4')
    expect(editor).to_contain_text('unsaved draft')
    expect(editor).to_contain_text('服务端新说明 3')
    page.close()

    page = browser.new_page()
    page.goto(URL + '?state=denied')
    expect(page.locator('.exam-note-panel')).to_contain_text('无权查看考期说明')
    page.evaluate('''async () => {
      const { examCalendarNote } = await import('/src/services/examCalendarNote.ts');
      window.noteReads = 0;
      examCalendarNote.get = async () => { window.noteReads++; throw new Error('unexpected read') };
    }''')
    page.get_by_role('button', name='刷新', exact=True).click()
    expect(page.locator('.exam-note-panel')).to_contain_text('无权查看考期说明')
    assert page.evaluate('noteReads') == 0
    browser.close()
    print('PASS page refresh: schedules + latest note, month independence, failure/retry, draft preservation, denied access')
