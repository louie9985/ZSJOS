# -*- coding: utf-8 -*-
"""Chrome status-color checks against isolated transport; no real business API."""
import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5174')
OUT = Path(tempfile.mkdtemp(prefix='exam-status-color-'))
results = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [390, 1440]:
        for theme in ['default-light', 'default-dark']:
            context = browser.new_context(viewport={'width': width, 'height': 1000})
            context.route('**/*', lambda route: route.continue_() if
                          urlparse(route.request.url).netloc == urlparse(BASE).netloc
                          and '/admin-api/' not in route.request.url else route.abort())
            page = context.new_page()
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.goto(BASE + '/test/exam-color.html?case=status')
            page.evaluate('''theme => {
              localStorage.setItem('crm-theme', JSON.stringify({preset: theme}));
              const date = new Date();
              const fmt = d => [d.getFullYear(), String(d.getMonth()+1).padStart(2,'0'), String(d.getDate()).padStart(2,'0')].join('-');
              const rows = [];
              for (const status of ['DRAFT','PUBLISHED','REVOKED']) {
                for (const type of ['EXACT','MULTI_DAY']) {
                  for (const color of ['#FFCC00','#001133',null]) {
                    rows.push({id:rows.length+1, scheduleName:status+'-'+type+'-'+(color || 'default'),
                      scheduleType:type, recordStatus:status, displayStatus:status,
                      categoryPathSnapshot:[], backgroundColor:color, exactDate:fmt(date),
                      startDate:fmt(new Date(date.getFullYear(),date.getMonth()-1,28)),
                      endDate:fmt(new Date(date.getFullYear(),date.getMonth()+1,3)),
                      canReedit:status === 'REVOKED', reeditDeadline:Date.now()+300000});
                  }
                }
              }
              sessionStorage.setItem('exam-color-fixture-status',JSON.stringify(rows));
            }''', theme)
            page.reload()
            expect(page.locator('.exam-month .exam-calendar-event')).to_have_count(9)
            expect(page.locator('.exam-multiDay-bar').first).to_be_visible()
            checks = page.locator('.exam-month .exam-status-tone').evaluate_all('''els => els.map(el => {
              const style = getComputedStyle(el);
              const status = ['draft','published','revoked'].find(s => el.classList.contains('tone-'+s));
              const legend = getComputedStyle(document.querySelector('.exam-calendar-swatch.tone-'+status));
              const custom = status === 'published' && !el.textContent.includes('default');
              const dark = el.textContent.includes('#001133');
              const expected = custom ? (dark ? 'rgb(0, 17, 51)' : 'rgb(255, 204, 0)') : legend.backgroundColor;
              const foreground = custom ? (dark ? 'rgb(255, 255, 255)' : 'rgb(0, 0, 0)') : legend.color;
              return {text:el.textContent, status, bg:style.backgroundColor, fg:style.color,
                ok:style.backgroundColor === expected && style.color === foreground
                  && style.borderColor === (custom ? expected : legend.borderColor)
                  && (status !== 'revoked' || style.borderTopStyle === 'dashed'),
                inline:el.style.backgroundColor};
            })''')
            assert len(checks) >= 36, len(checks)
            assert all(c['ok'] for c in checks), checks
            assert all(not c['inline'] for c in checks if c['status'] != 'published')
            page.screenshot(path=str(OUT / f'{width}-{theme}.png'), full_page=True)
            event = page.locator('.exam-month .exam-calendar-event').filter(has_text='DRAFT-EXACT-#FFCC00')
            event.click()
            record = page.locator('.exam-schedule-record').filter(has_text='DRAFT-EXACT-#FFCC00')
            record.get_by_role('button', name=re.compile(r'编\s*辑$')).click()
            preview = page.get_by_label('发布后效果预览', exact=True)
            expect(preview).to_be_visible()
            expect(preview.locator('.exam-calendar-event')).to_have_css('background-color', 'rgb(255, 204, 0)')
            page.get_by_role('button', name='选择考期底色').click()
            page.locator('.ant-color-picker-clear').click()
            default_bg = page.locator('.exam-calendar-swatch.tone-published').evaluate('(el) => getComputedStyle(el).backgroundColor')
            expect(preview.locator('.exam-calendar-event')).to_have_css('background-color', default_bg)
            page.get_by_role('button', name='选择考期底色').click()
            page.get_by_role('button', name=re.compile(r'取\s*消$')).last.click()
            record.get_by_role('button', name=re.compile(r'发\s*布$')).click()
            expect(event).to_have_class('exam-calendar-event exam-status-tone tone-published')
            expect(event).to_have_css('background-color', 'rgb(255, 204, 0)')
            # Refresh after publishing closes the day dialog; reopen the updated record.
            event.click()
            record.get_by_role('button', name=re.compile(r'撤\s*销$')).click()
            page.locator('.ant-popconfirm').get_by_role('button', name=re.compile(r'确\s*定$')).click()
            expect(event).to_have_class('exam-calendar-event exam-status-tone tone-revoked')
            revoked_bg = page.locator('.exam-calendar-swatch.tone-revoked').evaluate('(el) => getComputedStyle(el).backgroundColor')
            expect(event).to_have_css('background-color', revoked_bg)
            expect(event).to_have_css('border-top-style', 'dashed')
            row = page.evaluate("JSON.parse(sessionStorage.getItem('exam-color-fixture-status'))[0]")
            assert row['recordStatus'] == 'REVOKED' and row['backgroundColor'] == '#FFCC00', row
            assert not errors, errors
            results.append({'width':width,'theme':theme,'segments':len(checks),'transition':'draft-published-revoked','previewClear':True})
            context.close()
    browser.close()
(OUT / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'passed':results,'artifacts':str(OUT)}, ensure_ascii=False))
