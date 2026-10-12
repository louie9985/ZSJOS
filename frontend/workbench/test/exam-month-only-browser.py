# -*- coding: utf-8 -*-
"""Real Chrome month-boundary checks; isolated fixtures, no live API access."""
import calendar
import json
import os
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5174')
OUT = Path(tempfile.mkdtemp(prefix='exam-month-only-'))
results = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [390, 1440]:
        context = browser.new_context(viewport={'width': width, 'height': 1000})
        context.route('**/*', lambda route: route.continue_() if
                      urlparse(route.request.url).netloc == urlparse(BASE).netloc
                      and '/admin-api/' not in route.request.url else route.abort())
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(f'{BASE}/test/calendar-navigation.html?inner=1&scene=exam')
        expect(page.get_by_label('校验结果')).to_contain_text('布局通过')
        initial = page.locator('.exam-month').get_attribute('aria-label')
        page.get_by_role('button', name='下一月', exact=True).click()
        expect(page.locator('.exam-month')).not_to_have_attribute('aria-label', initial)
        page.get_by_role('button', name='上一月', exact=True).click()
        expect(page.locator('.exam-month')).to_have_attribute('aria-label', initial)
        # Mount the actual month component under the existing theme and styles.
        await_setup = """async () => {
          const {default: React} = await import('/node_modules/.vite/deps/react.js');
          const {default: ReactDOM} = await import('/node_modules/.vite/deps/react-dom_client.js');
          const {default: dayjs} = await import('/node_modules/.vite/deps/dayjs.js');
          const {default: Month} = await import('/src/pages/ExamCalendarMonth.tsx');
          const source = document.querySelector('.exam-calendar-page');
          source.style.display = 'none';
          const stage = document.createElement('section');
          stage.className = 'exam-calendar-page';
          source.parentElement.append(stage);
          stage.innerHTML = '<div class="calendar-side-navigation-content" style="overflow-x:auto"></div>';
          const root = ReactDOM.createRoot(stage.firstElementChild);
          window.showMonth = month => {
            const anchor = dayjs(month), end = anchor.endOf('month');
            const exact = (id, date, name) => ({id, scheduleType:'EXACT', exactDate:date,
              scheduleName:name, recordStatus:'PUBLISHED'});
            const multi = (id, start, finish, name) => ({id, scheduleType:'MULTI_DAY',
              startDate:start, endDate:finish, scheduleName:name, recordStatus:'PUBLISHED'});
            window.selectedDay = null;
            root.render(React.createElement(Month, {anchor,
              exactRows:[exact(1, anchor.subtract(1,'day').format('YYYY-MM-DD'), '上月隐藏考期'),
                exact(2, end.add(1,'day').format('YYYY-MM-DD'), '下月隐藏考期'),
                exact(3, anchor.format('YYYY-MM-DD'), '本月首日考期')],
              multiDayRows:[multi(4, anchor.subtract(2,'day').format('YYYY-MM-DD'),
                anchor.add(1,'day').format('YYYY-MM-DD'), '跨月开始考期'),
                multi(5, end.subtract(1,'day').format('YYYY-MM-DD'),
                end.add(2,'day').format('YYYY-MM-DD'), '跨月结束考期')],
              statusLabel: () => '已发布', onDay: date => window.selectedDay = date.format('YYYY-MM-DD')}));
            return anchor.startOf('week').day();
          };
        }"""
        page.evaluate(await_setup)
        for month in ['2026-02-01', '2028-02-01', '2026-08-01', '2026-12-01', '2027-01-01']:
            year, number, _ = map(int, month.split('-'))
            days = calendar.monthrange(year, number)[1]
            week_start = page.evaluate('month => window.showMonth(month)', month)
            month_view = page.locator('.exam-calendar-page:visible .exam-month')
            expect(month_view).to_have_attribute('aria-label', f'{year}年{number}月考期月历')
            expect(month_view.locator('.exam-month-day')).to_have_count(days)
            expect(month_view.get_by_text('上月隐藏考期')).to_have_count(0)
            expect(month_view.get_by_text('下月隐藏考期')).to_have_count(0)
            dates = month_view.locator('[data-date]').evaluate_all('(nodes) => nodes.map(n => n.dataset.date)')
            assert all(date.startswith(month[:7]) for date in dates), dates
            grid = month_view.evaluate('''el => {
              const week = el.querySelector('.exam-month-week').getBoundingClientRect();
              const first = el.querySelector('.exam-month-day').getBoundingClientRect();
              return {column: Math.round((first.left-week.left)/(week.width/7)),
                weeks: el.querySelectorAll('.exam-month-week').length};
            }''')
            first_column = (calendar.monthrange(year, number)[0] + 1 - week_start) % 7
            assert grid == {'column': first_column, 'weeks': (first_column + days + 6) // 7}, grid
            first = month_view.locator(f'.exam-month-day[data-date="{month}"]')
            first.locator('.exam-month-date').click()
            assert page.evaluate('window.selectedDay') == month
            last_date = f'{year}-{number:02d}-{days:02d}'
            month_view.locator(f'.exam-multiDay-days button[data-date="{last_date}"]').click()
            assert page.evaluate('window.selectedDay') == last_date
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            month_view.evaluate('(el) => el.parentElement.scrollLeft = 0')
            page.screenshot(path=str(OUT / f'{width}-{month[:7]}.png'), full_page=True)
            results.append({'width': width, 'month': month[:7], 'days': days, **grid})
        assert not errors, errors
        context.close()
    browser.close()
(OUT / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
print(f'PASS {len(results)} viewport/month cases; navigation, clipping, dates and click targets. Evidence: {OUT}')
