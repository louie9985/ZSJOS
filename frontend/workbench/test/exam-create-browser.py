# -*- coding: utf-8 -*-
"""Actual Workbench page, isolated transport simulating a committed create with lost response."""
import re
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width, edit in [(1440, False), (390, True)]:
        page = browser.new_page(viewport={'width': width, 'height': 1000})
        page.clock.set_fixed_time('2026-10-08T01:00:00Z')
        page.goto('http://127.0.0.1:5247/test/exam-create.html')
        page.get_by_role('button', name=re.compile('新增考期$')).click()
        editor = page.get_by_role('dialog', name='新增考期', exact=True)
        editor.get_by_label('考期名称', exact=True).fill('请求重试考试')
        editor.get_by_role('button', name='保存草稿', exact=True).click()
        expect(page.get_by_text('模拟已落库但响应丢失')).to_be_visible()
        if edit: editor.get_by_label('考期名称', exact=True).fill('重试期间编辑')
        editor.get_by_role('button', name='保存并发布', exact=True).click()
        expect(editor).not_to_be_visible()
        state = page.evaluate('({counts:window.examDatesFixture.counters(),requests:window.examDatesFixture.requests(),rows:window.examDatesFixture.rows()})')
        assert state['counts'] == {'creates': 1, 'updates': int(edit), 'publishes': 1}, state
        assert len(state['requests']) == 2 and state['requests'][0] == state['requests'][1], state
        assert len(state['requests'][0]['idempotencyKey']) >= 16
        saved = [row for row in state['rows'] if row['id'] == 101]
        assert len(saved) == 1 and saved[0]['recordStatus'] == 'PUBLISHED'
        assert saved[0]['scheduleName'] == ('重试期间编辑' if edit else '请求重试考试')
        page.get_by_role('button', name=re.compile('新增考期$')).click()
        editor.get_by_label('考期名称', exact=True).fill('请求重试考试')
        editor.get_by_role('button', name='保存草稿', exact=True).click()
        expect(editor).not_to_be_visible()
        requests = page.evaluate('window.examDatesFixture.requests()')
        assert requests[2]['idempotencyKey'] != requests[0]['idempotencyKey']
        assert page.evaluate('window.examDatesFixture.counters().creates') == 2
        print('PASS lost response, stable original request, edit recovery, publish and fresh create', width, flush=True)
        page.close()
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.clock.set_fixed_time('2026-10-08T01:00:00Z')
    page.goto('http://127.0.0.1:5247/test/exam-create.html?reject=1')
    page.get_by_role('button', name=re.compile('新增考期$')).click()
    editor = page.get_by_role('dialog', name='新增考期', exact=True)
    editor.get_by_label('考期名称', exact=True).fill('被拒绝的内容')
    editor.get_by_role('button', name='保存草稿', exact=True).click()
    expect(page.get_by_text('模拟附件校验拒绝')).to_be_visible()
    editor.get_by_label('考期名称', exact=True).fill('修正后的内容')
    editor.get_by_role('button', name='保存草稿', exact=True).click()
    expect(editor).not_to_be_visible()
    requests = page.evaluate('window.examDatesFixture.requests()')
    assert requests[0]['idempotencyKey'] == requests[1]['idempotencyKey']
    assert requests[1]['scheduleName'] == '修正后的内容'
    assert page.evaluate('window.examDatesFixture.counters()') == {'creates': 1, 'updates': 0, 'publishes': 0}
    print('PASS definite validation rejection accepts corrected request with original key', flush=True)
    browser.close()
