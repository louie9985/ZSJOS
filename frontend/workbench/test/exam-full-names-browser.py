# -*- coding: utf-8 -*-
"""Verify real month geometry with synthetic transport, without live writes."""
import os
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.mkdtemp(prefix='exam-full-names-'))
url = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5174') + '/test/exam-full-names.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(url)
        expect(page.locator('.exam-calendar-event')).to_have_count(10)
        date = page.evaluate('window.examFullNamesFixture.date')
        day = page.locator('.exam-month-day[data-date="' + date + '"]')
        expect(page.locator('.exam-month .ant-tag, .exam-month .exam-countdown')).to_have_count(0)
        expect(page.locator('.exam-calendar-overflow, .exam-multiDay-overflow')).to_have_count(0)
        expect(page.locator('.exam-multiDay-days button[data-date="' + date + '"]')).to_have_count(6)
        for label in ['已撤销', '已发布', '草稿', '多日考试']:
            expect(page.locator('.exam-calendar-legend').get_by_text(label, exact=True)).to_be_visible()
        geometry = page.evaluate('''() => {
            const labels = [...document.querySelectorAll('.exam-calendar-event > span, .exam-multiDay-bar-label')];
            const clipped = labels.filter(el => el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.clientHeight + 1 || getComputedStyle(el).whiteSpace === 'nowrap');
            const overlaps = [];
            document.querySelectorAll('.exam-month-week').forEach(week => {
                const bars = [...week.querySelectorAll('.exam-multiDay-bar, .exam-calendar-event')];
                for (let i=0; i<bars.length; i++) for (let j=i+1; j<bars.length; j++) {
                    const a=bars[i].getBoundingClientRect(), b=bars[j].getBoundingClientRect();
                    if (Math.min(a.right,b.right)-Math.max(a.left,b.left)>1 && Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>1) overlaps.push([i,j]);
                }
            });
            const mismatch = [...document.querySelectorAll('.exam-calendar-swatch.exam-status-tone')].filter(swatch => {
                const tone = [...swatch.classList].find(x => x.startsWith('tone-'));
                const item = document.querySelector('.exam-month .' + tone);
                if (!item) return true;
                const a=getComputedStyle(swatch), b=getComputedStyle(item);
                return a.backgroundColor !== b.backgroundColor || a.borderTopColor !== b.borderTopColor || a.borderTopStyle !== b.borderTopStyle;
            });
            return {clipped:clipped.length, overlaps, mismatch:mismatch.length, horizontal:document.documentElement.scrollWidth>innerWidth, scrollable:document.documentElement.scrollHeight>innerHeight};
        }''')
        assert geometry == {'clipped': 0, 'overlaps': [], 'mismatch': 0, 'horizontal': False, 'scrollable': True}, geometry
        assert page.evaluate('''() => new Set([...document.querySelectorAll('.exam-calendar-swatch.exam-status-tone')].map(el => {
            const s = getComputedStyle(el); return [s.backgroundColor,s.borderTopColor,s.borderTopStyle].join('|');
        })).size''') == 3
        expect(page.locator('.exam-month .tone-upcoming, .exam-month .tone-in_progress, .exam-month .tone-ended')).to_have_count(0)
        expect(page.locator('.exam-calendar-event.tone-published')).to_have_count(7)
        for label in ['即将开始', '正在进行', '已结束']:
            expect(page.locator('.exam-calendar-legend').get_by_text(label, exact=True)).to_have_count(0)
        page.screenshot(path=str(out / f'legend-{width}.png'), animations='disabled')
        day.scroll_into_view_if_needed()
        page.screenshot(path=str(out / f'dense-{width}.png'), animations='disabled')
        day.locator('.exam-calendar-event').last.click()
        dialog = page.get_by_role('dialog')
        expect(dialog.locator('.ant-list-item')).to_have_count(16)
        expect(dialog).to_contain_text('此备注仅应出现在当天详情')
        expect(dialog.locator('.exam-countdown')).to_have_count(2)
        dialog.get_by_role('button', name='Close').click()
        expect(dialog).to_have_count(0)
        segment = page.locator('.exam-multiDay-days button[data-date="' + date + '"]').last
        segment.focus()
        segment.press('Enter')
        expect(dialog.locator('.ant-list-item')).to_have_count(16)
        dialog.get_by_role('button', name='Close').click()
        expect(dialog).to_have_count(0)
        # Each publication filter includes all matching time phases, for both exam types.
        for label, exact_count, multi_count in [('已发布', 7, 4), ('草稿', 2, 1), ('已撤销', 1, 1)]:
            page.get_by_role('combobox', name='发布状态').click()
            expect(page.locator('.ant-select-item-option')).to_have_count(3)
            page.locator('.ant-select-item-option').filter(has_text=label).click()
            expect(page.locator('.exam-calendar-event')).to_have_count(exact_count)
            expect(page.locator('.exam-multiDay-days button[data-date="' + date + '"]')).to_have_count(multi_count)
            page.locator('.exam-calendar-event').first.click()
            expect(dialog.locator('.ant-list-item')).to_have_count(exact_count + multi_count)
            for time_label in ['即将开始', '正在进行', '已结束']:
                expect(dialog.get_by_text(time_label, exact=True)).to_have_count(0)
            expect(dialog.locator('.exam-status-label')).to_have_count(exact_count + multi_count)
            assert all(label in text for text in dialog.locator('.exam-status-label').all_text_contents())
            dialog.get_by_role('button', name='Close').click()
            expect(dialog).to_have_count(0)
        page.locator('.exam-calendar-filter .ant-select-clear').click()
        expect(page.locator('.exam-calendar-event')).to_have_count(10)
        page.get_by_role('switch', name='显示多日考期').click()
        expect(page.locator('.exam-multiDay-bar')).to_have_count(0)
        page.get_by_role('switch', name='显示多日考期').click()
        expect(page.locator('.exam-multiDay-days button[data-date="' + date + '"]')).to_have_count(6)
        for count in [1, 3, 4, 5, 110]:
            page.goto(url + '?count=' + str(count) + '&noMulti&viewer')
            expect(page.locator('.exam-calendar-event')).to_have_count(count)
            if count == 110:
                assert 'exact:2' in page.evaluate('window.examFullNamesFixture.calls')
                page.get_by_role('combobox', name='发布状态').click()
                page.locator('.ant-select-item-option').filter(has_text='已发布').click()
                expect(page.locator('.exam-calendar-event')).to_have_count(73)
                page.locator('.exam-calendar-event').last.click()
                expect(page.get_by_role('dialog').locator('.ant-list-item')).to_have_count(73)
                page.get_by_role('dialog').get_by_role('button', name='Close').click()
                expect(page.get_by_role('dialog')).to_have_count(0)
                page.locator('.exam-calendar-filter .ant-select-clear').click()
                expect(page.locator('.exam-calendar-event')).to_have_count(count)
            page.locator('.exam-calendar-event').last.click()
            expect(page.get_by_role('dialog').locator('.ant-list-item')).to_have_count(count)
            expect(page.get_by_role('dialog').locator('.ant-list-item button')).to_have_count(0)
        for state in ['empty', 'error', 'denied', 'multi-error', 'loading']:
            page.goto(url + '?state=' + state)
            if state == 'empty':
                expect(page.get_by_text('当前日历范围暂无符合条件的考期')).to_be_visible()
            elif state == 'loading':
                expect(page.locator('.ant-spin-spinning')).to_be_visible()
                expect(page.locator('.exam-calendar-event')).to_have_count(10)
            else:
                expect(page.locator('.ant-alert').first).to_be_visible()
                if state == 'denied':
                    expect(page.get_by_text('无权查看考期日历', exact=True)).to_be_visible()
                page.evaluate("window.examFullNamesFixture.mode = 'success'")
                page.get_by_role('button', name='刷新', exact=True).click()
                expect(page.locator('.exam-calendar-event')).to_have_count(10)
                expect(page.locator('.ant-alert')).to_have_count(0)
        assert not errors, errors
        print('PASS names/geometry/legend/pagination/states:', width, flush=True)
        page.close()
    browser.close()
print('Browser evidence:', out)
