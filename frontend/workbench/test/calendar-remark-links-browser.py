# -*- coding: utf-8 -*-
"""Real calendar components with synthetic transport; external links are intercepted."""
import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('WORKBENCH_TEST_URL', 'http://127.0.0.1:5198')
OUT = Path(tempfile.mkdtemp(prefix='calendar-remark-links-'))
HINT = '支持 http://、https:// 链接；链接前后请用空格或换行分隔。'
results = []

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [1440, 390]:
        context = browser.new_context(viewport={'width': width, 'height': 1000})
        errors, unexpected = [], []

        def route_request(route):
            url = route.request.url
            if urlparse(url).hostname == 'example.test':
                route.fulfill(content_type='text/html', body='<title>受控链接验证</title>ok')
            elif urlparse(url).netloc == urlparse(BASE).netloc and '/admin-api/' not in url:
                route.continue_()
            elif '/admin-api/' in url:
                unexpected.append(urlparse(url).path)
                route.fulfill(status=500, json={'code': 500, 'msg': 'Unexpected fixture API'})
            else:
                route.abort()

        context.route('**/*', route_request)
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))

        def go(scene, extra=''):
            page.goto(BASE + '/test/calendar-remark-links.html?scene=' + scene + extra)
            page.wait_for_function('window.remarkFixture !== undefined')
            return page.evaluate('window.remarkFixture')

        def visible_dialog():
            return page.get_by_role('dialog').filter(visible=True).last

        def verify_hint(editor):
            expect(editor.get_by_text(HINT, exact=True)).to_be_visible()
            field = editor.locator('.remark-link-field')
            if field.locator('.ant-input-data-count').count():
                # The opening modal scales its descendants; measure after a readable gap appears.
                page.wait_for_function('''el => {
                    const range = document.createRange();
                    range.selectNodeContents(el.querySelector('.ant-form-item-extra'));
                    return range.getBoundingClientRect().top >= el.querySelector('.ant-input-data-count').getBoundingClientRect().bottom + 2;
                }''', arg=field.element_handle())

        def open_day(scene, data):
            if scene == 'exam':
                page.locator('.exam-calendar-event').filter(has_text='验证单日考期').click()
            else:
                page.locator('.ant-picker-cell[title="' + data['date'] + '"]').click()
            expect(visible_dialog()).to_be_visible()

        def open_link(link, keyboard=False):
            expect(link).to_be_visible()
            expect(link).to_have_attribute('target', '_blank')
            expect(link).to_have_attribute('rel', 'noopener noreferrer')
            href = link.get_attribute('href')
            point = None
            if not keyboard:
                link.scroll_into_view_if_needed()
                # Scroll position stabilizes before the modal's scale animation necessarily finishes.
                page.wait_for_function('''el => {
                    const modal = el.closest('.ant-modal');
                    return !modal || getComputedStyle(modal).transform === 'none';
                }''', arg=link.element_handle())
                # Inline links have multiple fragments; their union's centre may be clipped or whitespace.
                point = link.evaluate('''el => {
                    for (const r of el.getClientRects()) {
                        for (const y of [r.top + r.height / 2, r.top + 2, r.bottom - 2]) {
                            const x = r.left + r.width / 2;
                            if (x < 0 || x >= innerWidth || y < 0 || y >= innerHeight) continue;
                            const hit = document.elementFromPoint(x, y);
                            if (hit && (hit === el || el.contains(hit))) return {x, y};
                        }
                    }
                    return null;
                }''')
                assert point, 'No visible, clickable fragment of the link'
            with context.expect_page() as popup_info:
                if keyboard:
                    link.focus()
                    expect(link).to_be_focused()
                    link.press('Enter')
                else:
                    page.mouse.click(point['x'], point['y'])
            popup = popup_info.value
            popup.wait_for_load_state()
            assert popup.url == href, (popup.url, href)
            popup.close()

        def verify_text(container, data):
            text = container.locator('.resource-linked-text-remark').first
            expect(text).to_be_visible()
            assert text.text_content() == data['remark']
            links = text.locator('a')
            expect(links).to_have_count(2)
            expect(links.first).to_have_attribute('href', data['link'])
            assert links.first.locator('.resource-link-label').inner_text() == data['link']
            return links.first

        for scene in ['personal', 'course', 'exam', 'lead']:
            data = go(scene)
            open_day(scene, data)
            dialog = visible_dialog()
            link = verify_text(dialog, data)
            before = page.locator('.ant-modal:visible').count()
            open_link(link)
            assert page.locator('.ant-modal:visible').count() == before
            open_link(link, keyboard=True)
            assert page.locator('.ant-modal:visible').count() == before
            assert dialog.evaluate('(el) => el.scrollWidth <= el.clientWidth + 2')
            assert link.evaluate('(el) => getComputedStyle(el).textDecorationLine.includes("underline")')
            if scene in ['personal', 'course']:
                card = dialog.locator('.' + ('personal' if scene == 'personal' else 'course') + '-calendar-timeline-card')
                assert card.evaluate('(el) => el.scrollWidth <= el.clientWidth + 2')
                assert card.evaluate('(el) => getComputedStyle(el).overflowY') == 'auto'
                assert link.locator('.resource-link-label').evaluate('(el) => getComputedStyle(el).whiteSpace') == 'pre-wrap'
                page.screenshot(path=str(OUT / (scene + '-' + str(width) + '.png')))
                dialog.get_by_role('button', name='编辑日程' if scene == 'personal' else '编辑课程', exact=True).click()
                editor = visible_dialog()
                verify_hint(editor)
                expect(editor.locator('textarea')).to_have_value(data['remark'])
                editor.get_by_role('button', name=re.compile('^取\s*消$')).click()
            elif scene == 'exam':
                expect(dialog.locator('.resource-linked-text-remark')).to_have_count(3)
                for index in range(3):
                    assert dialog.locator('.resource-linked-text-remark').nth(index).text_content() == data['remark']
                page.screenshot(path=str(OUT / ('exam-' + str(width) + '.png')))
                dialog.get_by_role('button', name=re.compile('编\s*辑')).first.click()
                verify_hint(visible_dialog())
                expect(visible_dialog().locator('textarea')).to_have_value(data['remark'])
                visible_dialog().get_by_role('button', name=re.compile('^取\s*消$')).click()
                dialog.get_by_role('button', name='重新编辑').click()
                verify_hint(visible_dialog())
                expect(visible_dialog().locator('textarea')).to_have_value(data['remark'])
            else:
                dialog.get_by_role('button', name='填写跟进记录', exact=True).click()
                verify_hint(visible_dialog())
                expect(visible_dialog().locator('textarea')).to_be_enabled()
            assert page.evaluate('window.remarkFixture.writes + window.remarkFixture.notifications') == 0
            results.append([width, scene, 'links/text/hints/popup/keyboard passed'])
            print(str(width) + ' ' + scene + ' passed', flush=True)

        for scene, button in [('personal', '新建日程'), ('course', '新增课程'), ('exam', '新增考期')]:
            go(scene)
            page.get_by_role('button', name=button).click()
            verify_hint(visible_dialog())
            visible_dialog().locator('textarea').fill('普通备注，不包含网址。')
            verify_hint(visible_dialog())

        data = go('exam')
        page.get_by_role('button', name='多日考试安排').click()
        drawer = page.locator('.ant-drawer:visible')
        open_link(verify_text(drawer, data))
        expect(drawer).to_be_visible()

        for extra in ['', '&exam=1']:
            data = go('notify', extra)
            page.get_by_role('radio', name='全员通知').check()
            page.get_by_role('button', name='预览通知', exact=True).click()
            open_link(verify_text(visible_dialog(), data))
            expect(page.get_by_role('button', name='提交发送', exact=True)).to_be_disabled()
            assert page.evaluate('window.remarkFixture.notifications') == 0
        results.append([width, 'new forms/drawer/course and exam notification preview', 'passed'])

        data = go('form')
        field = visible_dialog().locator('textarea')
        expect(field).to_be_enabled()
        field.fill(data['remark'])
        verify_hint(visible_dialog())
        expect(field).to_have_value(data['remark'])
        page.screenshot(path=str(OUT / ('form-' + str(width) + '.png')))

        data = go('timeline')
        expand = page.locator('.ant-typography-expand')
        expect(expand).to_be_visible()
        expand.click()
        expect(page.get_by_role('link', name='https://example.test/end（新标签页打开）', exact=True)).to_be_visible()
        open_link(page.locator('.fu-node-remark a').first)
        page.screenshot(path=str(OUT / ('timeline-' + str(width) + '.png')))
        page.locator('.ant-typography-collapse').click()
        expect(expand).to_be_visible()

        data = go('events')
        link = page.locator('.resource-linked-text-remark a').first
        open_link(link)
        open_link(link, keyboard=True)
        link.dispatch_event('auxclick', {'button': 1, 'bubbles': True})
        assert page.evaluate('window.remarkFixture.parentEvents') == 0
        link.focus()
        page.keyboard.press('Tab')
        expect(page.locator('.resource-linked-text-remark a').nth(1)).to_be_focused()
        results.append([width, 'follow-up expand/collapse and parent event isolation', 'passed'])

        data = go('lead', '&noFollowUp=1')
        open_day('lead', data)
        expect(visible_dialog().get_by_text('暂无跟进记录查看权限')).to_be_visible()
        expect(visible_dialog().locator('.resource-linked-text-remark')).to_have_count(0)
        go('lead', '&denied=1')
        expect(page.get_by_text('无权查看销售客资跟进日历，请联系管理员配置权限')).to_be_visible()
        expect(page.locator('.resource-linked-text-remark')).to_have_count(0)
        assert not errors, errors
        assert not unexpected, unexpected
        context.close()
    browser.close()

(OUT / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'passed': results, 'evidence': str(OUT)}, ensure_ascii=False))
