# -*- coding: utf-8 -*-
"""Dense month layouts using real components and synthetic, isolated API data."""
import json
import os
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5174')
OUT = Path(tempfile.mkdtemp(prefix='calendar-auto-size-'))
results = []

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [320, 390, 768, 1440]:
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        page.route('**/*', lambda route: route.continue_() if urlparse(route.request.url).netloc == urlparse(BASE).netloc and '/admin-api/' not in route.request.url else route.abort())
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        for scene in ['personal', 'course']:
            item = '.personal-calendar-event' if scene == 'personal' else '.course-calendar-summary'
            title = item + '-title'
            for actions in [False, True]:
                heights = []
                for state, count in [('single', 1), ('dense', 8)]:
                    page.goto(f'{BASE}/test/calendar-navigation.html?inner=1&scene={scene}&state={state}' + ('&actions=1' if actions else ''))
                    expect(page.locator(item)).to_have_count(count)
                    expect(page.get_by_label('校验结果')).to_contain_text('布局通过')
                    assert page.locator(item + ' .ant-badge').count() == 0
                    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                    metrics = page.locator(item).evaluate_all('''els => els.map(el => {
                      const title = el.querySelector('[class$="-title"]');
                      const content = el.closest('.ant-picker-calendar-date-content');
                      const a = el.getBoundingClientRect(), b = content.getBoundingClientRect();
                      const range = document.createRange(); range.selectNodeContents(title);
                      return {width: a.width, available: b.width, bottom: a.bottom, cellBottom: b.bottom,
                        height: a.height, contentHeight: b.height, wrap: getComputedStyle(title).whiteSpace,
                        clipped: [...range.getClientRects()].some(r => r.left < a.left - 1 || r.right > a.right + 1 || r.bottom > a.bottom + 1),
                        overflow: el.scrollWidth > el.clientWidth + 1};
                    })''')
                    assert all(not m['clipped'] and not m['overflow'] and m['wrap'] == 'normal' for m in metrics), metrics
                    assert all(abs(m['width'] - m['available']) < 2 and m['bottom'] <= m['cellBottom'] + 1 for m in metrics), metrics
                    row_tops = page.locator(item).first.evaluate('el => [...el.closest("tr").querySelectorAll("td .ant-picker-calendar-date-value")].map(e => e.getBoundingClientRect().top)')
                    assert max(row_tops) - min(row_tops) < 2, row_tops
                    heights.append(metrics[0]['contentHeight'])
                    if state == 'dense':
                        page.locator(item).first.scroll_into_view_if_needed()
                        page.screenshot(path=str(OUT / f'{scene}-{width}-actions-{actions}.png'), full_page=True)
                        page.locator(title).first.click()
                        expect(page.get_by_role('dialog')).to_be_visible()
                        expect(page.locator(f'.{scene}-calendar-timeline-card')).to_have_count(count)
                assert heights[1] > heights[0] * 3, heights
                results.append({'scene': scene, 'width': width, 'actions': actions, 'heights': heights})
        assert not errors, errors
        page.close()
    browser.close()

(OUT / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'PASS: {len(results)} dense layout scenarios. Evidence: {OUT}')
