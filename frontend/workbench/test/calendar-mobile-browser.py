# -*- coding: utf-8 -*-
"""Mobile calendar regression with real components and isolated transport fixtures.

Run against a local Vite: WORKBENCH_TEST_URL=http://127.0.0.1:5196.
No live account or business data is read or written.
"""
import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5196')
OUT = Path(tempfile.mkdtemp(prefix='calendar-mobile-'))
results = []

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [320, 390, 768, 1440]:
        context = browser.new_context(viewport={'width': width, 'height': 1000}, has_touch=width <= 768)
        errors = []
        context.route('**/*', lambda route: route.continue_() if urlparse(route.request.url).netloc == urlparse(BASE).netloc and '/admin-api/' not in route.request.url else route.abort())
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))

        def go(scene, state='success'):
            page.goto(f'{BASE}/test/calendar-navigation.html?inner=1&scene={scene}&state={state}')
            expect(page.get_by_role('button', name='下一月', exact=True)).to_be_visible()
            expect(page.get_by_label('校验结果')).to_contain_text('布局通过')

        for scene in ['personal', 'course', 'lead', 'exam', 'media']:
            go(scene)
            expect(page.get_by_label('校验结果')).not_to_contain_text('最近请求 无')
            previous = page.get_by_label('校验结果').inner_text()
            page.get_by_role('button', name='下一月', exact=True).click()
            expect(page.get_by_label('校验结果')).not_to_have_text(previous)
            page.get_by_role('button', name='上一月', exact=True).click()
            expect(page.get_by_label('校验结果')).to_have_text(previous)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), scene
            if width <= 768:
                arrow = page.get_by_role('button', name='下一月', exact=True).bounding_box()
                assert arrow['width'] >= 44 and arrow['height'] >= 44
                selector = '.media-calendar-scroll' if scene == 'media' else '.calendar-side-navigation-content' if scene == 'exam' else '.ant-picker-panel'
                scroll = page.locator(selector)
                if width < 700 or scene == 'media':
                    assert scroll.evaluate('(el) => el.scrollWidth > el.clientWidth'), scene
                    scroll.evaluate('(el) => { el.scrollLeft = el.scrollWidth }')
                    assert scroll.evaluate('(el) => el.scrollLeft > 0'), scene
                    page.screenshot(path=str(OUT / f'{scene}-{width}-scrolled.png'), full_page=True)
                    scroll.evaluate('(el) => { el.scrollLeft = 0 }')
            page.screenshot(path=str(OUT / f'{scene}-{width}.png'), full_page=True)
            if scene in ['personal', 'course', 'lead']:
                marker = {'personal': '.personal-calendar-event', 'course': '.course-calendar-summary', 'lead': '.lead-calendar-count'}[scene]
                page.locator(marker).first.click()
                dialog = page.get_by_role('dialog')
                expect(dialog).to_be_visible()
                if scene in ['personal', 'course']:
                    card = page.locator(f'.{scene if scene == "personal" else "course"}-calendar-timeline-card').first
                    expect(card).to_be_visible()
                    if width <= 768:
                        assert card.evaluate('(el) => el.scrollHeight <= el.clientHeight + 1')
                        assert card.locator('strong').evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
                else:
                    expect(dialog).to_contain_text('当天暂无待跟进客资')
                page.screenshot(path=str(OUT / f'{scene}-{width}-detail.png'), full_page=True)
                dialog.get_by_role('button', name='Close', exact=True).click()
            elif scene == 'exam':
                page.locator('.exam-calendar-event').first.click()
                expect(page.get_by_role('dialog')).to_be_visible()
                page.screenshot(path=str(OUT / f'{scene}-{width}-detail.png'), full_page=True)
            results.append({'width': width, 'scene': scene, 'status': 'passed'})

        if width == 390:
            for scene in ['personal', 'course', 'lead', 'exam', 'media']:
                go(scene, 'error')
                expect(page.get_by_role('button', name=re.compile(r'重\s*试'))).to_be_visible()
                page.get_by_role('button', name=re.compile(r'重\s*试')).click()
                expect(page.locator('.calendar-side-navigation-content').get_by_role('alert')).to_be_visible()
                go(scene, 'empty')
                expect(page.get_by_label('校验结果')).to_contain_text('布局通过')
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                go(scene, 'slow')
                page.get_by_role('button', name='下一月', exact=True).click()
                expect(page.get_by_role('button', name='上一月', exact=True)).to_be_enabled()
            page.goto(f'{BASE}/test/calendar-navigation.html?inner=1&scene=lead&state=denied')
            expect(page.get_by_role('alert')).to_be_visible()
        assert not errors, errors
        context.close()
    browser.close()

(OUT / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Passed {len(results)} calendar/viewport cases, mobile remote states and denied lead view. Evidence: {OUT}')
