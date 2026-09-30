# -*- coding: utf-8 -*-
"""Real Workbench forms with synthetic transport; no live business writes."""
from pathlib import Path
import os
import re
import tempfile
from playwright.sync_api import sync_playwright, expect

out = Path(tempfile.mkdtemp(prefix='free-exam-browser-'))
out.mkdir(exist_ok=True)
base_url = os.environ.get('WORKBENCH_TEST_URL', 'http://localhost:5175').rstrip('/')
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        page = browser.new_page(viewport={'width': width, 'height': 960})
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(base_url + '/test/free-exam.html')
        expect(page.get_by_role('switch', name='显示多日考期')).to_be_checked()
        expect(page.get_by_text('秋季多日考试', exact=False).first).to_be_visible()
        page.get_by_role('switch', name='显示多日考期').click()
        expect(page.get_by_text('秋季多日考试', exact=False)).to_have_count(0)
        page.get_by_role('switch', name='显示多日考期').click()
        # All calendar entry points show the entire selected day, including overflow.
        target = page.evaluate("""() => {
            const state = window.freeExamFixture;
            const date = state.multiDay[0].startDate.slice(0, 8) + '10';
            state.exams = Array.from({length: 5}, (_, i) => ({id: 100 + i,
                scheduleType: 'EXACT', exactDate: date, scheduleName: '同日考试' + (i + 1),
                remark: '完整考试备注' + (i + 1), recordStatus: 'DRAFT', displayStatus: 'DRAFT', categoryPathSnapshot: []}));
            state.multiDay.push(...[42, 43].map(id => ({...state.multiDay[0], id,
                scheduleName: '重叠多日考试' + id})));
            return date;
        }""")
        page.get_by_role('button', name='刷新', exact=True).click()
        day = page.locator('.exam-month-day[data-date="' + target + '"]')
        expect(day.locator('.exam-calendar-event')).to_have_count(5)
        expect(day.locator('.exam-calendar-overflow')).to_have_count(0)
        expect(day.locator('.exam-multiDay-overflow')).to_have_count(0)
        def check_day():
            dialog = page.get_by_role('dialog')
            expect(dialog).to_have_count(1)
            expect(dialog.locator('.ant-modal-title')).to_contain_text(str(int(target[8:])) + '日')
            expect(dialog.locator('.ant-list-item')).to_have_count(8)
            expect(dialog.get_by_text('同日考试5', exact=True)).to_have_count(1)
            expect(dialog.get_by_text('重叠多日考试43', exact=True)).to_have_count(1)
            expect(dialog.get_by_role('button', name='详情', exact=True)).to_have_count(0)
            expect(dialog.get_by_role('button', name=re.compile(r'编\s*辑'))).to_have_count(5)
            expect(dialog.get_by_role('button', name='发送通知', exact=True)).to_have_count(0)
            expect(dialog.get_by_text('备注：完整考试备注5', exact=True)).to_have_count(1)
            return dialog
        def close_day():
            page.get_by_role('dialog').get_by_role('button', name='Close').click()
            expect(page.get_by_role('dialog')).to_have_count(0)
        for trigger in [day.locator('.exam-month-date'), day.locator('.exam-calendar-event').first,
                        page.locator('.exam-multiDay-days button[data-date="' + target + '"]').first]:
            trigger.click()
            check_day()
            close_day()
        day.click(position={'x': 2, 'y': day.bounding_box()['height'] - 3})
        check_day()
        close_day()
        segment = page.locator('.exam-multiDay-days button[data-date="' + target + '"]').first
        segment.focus()
        segment.press('Enter')
        check_day()
        page.screenshot(path=str(out / f'exam-day-dense-{width}.png'), full_page=True, animations='disabled')
        assert page.evaluate("document.querySelector('.ant-modal-body').scrollWidth <= document.querySelector('.ant-modal-body').clientWidth")
        close_day()
        # Every date segment, including continuation weeks, resolves to its own day.
        segments = page.locator('.exam-multiDay-days button')
        for index in [0, segments.count() // 2, segments.count() - 1]:
            segment = segments.nth(index)
            chosen = segment.get_attribute('data-date')
            segment.click()
            expect(page.get_by_role('dialog').locator('.ant-modal-title')).to_have_text(
                f'{int(chosen[:4])}年{int(chosen[5:7])}月{int(chosen[8:])}日 考期安排')
            close_day()
        day.locator('.exam-calendar-event').first.focus()
        day.locator('.exam-calendar-event').first.press('Space')
        check_day()
        page.get_by_role('dialog').get_by_role('button', name=re.compile(r'编\s*辑')).first.click()
        editor = page.get_by_role('dialog').filter(has=page.get_by_label('考期名称'))
        expect(editor.get_by_label('考期名称')).to_have_value('同日考试1')
        editor.get_by_role('button', name=re.compile(r'取\s*消')).click()
        expect(editor).to_have_count(0)
        close_day()
        page.evaluate('window.freeExamFixture.exams = []; window.freeExamFixture.multiDay.splice(1)')
        page.get_by_role('button', name='刷新', exact=True).click()
        expect(page.locator('.exam-calendar-event')).to_have_count(0)
        page.screenshot(path=str(out / f'multi-day-calendar-{width}.png'), full_page=True)
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
        page.get_by_role('button', name='新增考期').click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_label('考期名称')).to_have_value('')
        expect(dialog.get_by_text('单日', exact=True)).to_be_visible()
        expect(dialog.get_by_text('多日', exact=True)).to_be_visible()
        expect(dialog.get_by_label('日期', exact=True)).to_be_visible()
        expect(dialog.get_by_text('产品', exact=True)).to_have_count(0)
        page.get_by_role('button', name='保存草稿').click()
        expect(dialog.get_by_text('请填写考期名称', exact=True)).to_be_visible()
        dialog.get_by_label('考期名称').fill('自由命名秋季场')
        page.evaluate('window.freeExamFixture.fail = true')
        page.get_by_role('button', name='保存草稿').click()
        expect(page.get_by_text('测试保存失败')).to_be_visible()
        expect(dialog.get_by_label('考期名称')).to_have_value('自由命名秋季场')
        page.evaluate('window.freeExamFixture.fail = false')
        page.screenshot(path=str(out / f'exam-{width}.png'), full_page=True)
        page.get_by_role('button', name='保存并发布').click()
        expect(dialog).to_have_count(0)
        expect(page.get_by_text('自由命名秋季场', exact=True)).to_be_visible()
        request = page.evaluate('window.freeExamFixture.requests.at(-1)')
        assert request['scheduleName'] == '自由命名秋季场'
        assert not {'productId', 'categoryId', 'selectedAttrs'} & request.keys()
        page.get_by_role('button', name='新增考期').click()
        dialog = page.get_by_role('dialog')
        dialog.get_by_label('考期名称').fill('确定多日考试')
        dialog.get_by_text('多日', exact=True).click()
        expect(dialog.get_by_placeholder('开始日期')).to_have_value('')
        dialog.get_by_placeholder('开始日期').fill('2099-10-10')
        dialog.get_by_placeholder('结束日期').fill('2099-10-12')
        dialog.get_by_placeholder('结束日期').press('Enter')
        dialog.get_by_label('考期名称').click()
        dialog.get_by_role('button', name='保存草稿').click()
        expect(dialog).to_have_count(0)
        saved = page.evaluate('window.freeExamFixture.requests.at(-1)')
        assert saved['scheduleType'] == 'MULTI_DAY'
        assert saved['startDate'] == '2099-10-10' and saved['endDate'] == '2099-10-12'
        assert 'exactDate' not in saved and 'roughStartDate' not in saved
        page.goto(base_url + '/test/free-exam.html?class')
        page.get_by_role('button', name='创建班级').click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_label('班级名称')).to_have_value('')
        expect(dialog.get_by_text('SKU', exact=True)).to_have_count(0)
        dialog.get_by_label('班级名称').fill('自由命名一班')
        dialog.get_by_label('考期', exact=True).click()
        page.locator('.ant-select-item-option').filter(has_text='自由考试').click()
        dialog.get_by_label('班主任', exact=True).click()
        page.locator('.ant-select-item-option').filter(has_text='测试班主任').click()
        dialog.get_by_label('班级名称').click()
        page.screenshot(path=str(out / f'class-{width}.png'), full_page=True)
        dialog.get_by_role('button', name='确 定').click()
        expect(dialog).to_have_count(0)
        assert page.evaluate('window.freeExamFixture.requests[0]') == {'className': '自由命名一班', 'examScheduleId': 7, 'homeroomUserId': 8}
        assert not errors, errors
        page.close()
    browser.close()
print(f'PASS Workbench free exam/class forms at 1440 and 390, required names, failure retention and no catalog fields; screenshots: {out}')
