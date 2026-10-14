# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
from pathlib import Path
import os
import re

out = Path(os.environ['TEMP']) / 'hrm-trial-browser'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto('http://127.0.0.1:5296/test/hrm-manual.html?trial')
        dialog = page.get_by_role('dialog').filter(has=page.locator('form')).first
        expect(dialog).to_be_visible(timeout=60000)
        dialog.get_by_role('tab', name='入职信息').click()

        def field(label):
            return dialog.locator('.el-form-item').filter(has=page.locator('label', has_text=re.compile('^' + re.escape(label) + '$')))

        expect(field('员工状态')).to_contain_text('试岗')
        expect(field('司龄（年）').get_by_role('spinbutton')).to_have_value('0.27')
        days = field('试岗期（天）').get_by_role('spinbutton')
        days.fill('3')
        days.press('Tab')
        dialog.get_by_role('button', name='按试岗期计算', exact=True).click()
        expect(field('试岗结束日期').locator('input')).to_have_value('2026-01-17 00:00:00')
        actual = field('实际结束试岗日期').locator('input')
        actual.fill('2026-01-18 00:00:00')
        actual.press('Enter')
        field('修改原因').locator('textarea').fill('核实试岗记录')
        field('试岗期（天）').scroll_into_view_if_needed()
        page.screenshot(path=str(out / f'trial-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        dialog.get_by_role('button', name=re.compile('确.*定')).click()
        page.wait_for_function('hrmCalls.length === 1')
        saved = page.evaluate('hrmCalls[0]')
        assert saved['type'] == 2 and saved['status'] == 9 and saved['trialDays'] == 3
        assert int(saved['plannedTrialEndTime']) - int(saved['entryTime']) == 3 * 86400000
        assert int(saved['actualTrialEndTime']) - int(saved['entryTime']) == 4 * 86400000
        assert saved['changeMode'] == 'CORRECTION'
        expect(dialog).to_be_hidden()
        page.wait_for_function("document.querySelectorAll('.el-overlay').length <= 1")
        page.evaluate('openHrm()')
        expect(dialog).to_be_visible()
        dialog.get_by_role('tab', name='入职信息').click()
        expect(field('试岗期（天）').get_by_role('spinbutton')).to_have_value('3')
        for label in ['试岗结束日期', '实际结束试岗日期']:
            date = field(label).locator('input')
            date.fill('')
            date.press('Enter')
        field('试岗期（天）').get_by_role('spinbutton').fill('')
        field('试岗期（天）').get_by_role('spinbutton').press('Tab')
        field('修改原因').locator('textarea').fill('清空错误试岗资料')
        dialog.get_by_role('button', name=re.compile('确.*定')).click()
        page.wait_for_function('hrmCalls.length === 2')
        cleared = page.evaluate('hrmCalls[1]')
        assert all(cleared[k] is None for k in ['trialDays', 'plannedTrialEndTime', 'actualTrialEndTime'])
        assert cleared['status'] == 9
        assert not errors, errors
        page.close()
    browser.close()
print('PASS desktop/mobile: dictionary trial, calculated/manual dates, save/reopen/clear, decimal tenure, no overflow')

