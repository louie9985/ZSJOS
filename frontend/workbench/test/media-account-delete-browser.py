# -*- coding: utf-8 -*-
"""Production panel + isolated synthetic transport, with no live account writes."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjos-media-account-delete'
OUT.mkdir(exist_ok=True)
BASE = os.environ.get('ZSJOS_UI_TEST_URL', 'http://127.0.0.1:5174') + '/test/media-account-delete.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 900})
        for operator in [False, True]:
            page.goto(BASE + ('?operator' if operator else ''))
            action = '申请删除账号' if operator else '删除账号'
            page.get_by_role('button', name=action, exact=True).click()
            dialog = page.get_by_role('dialog')
            confirm = dialog.get_by_role('button', name='提交审批' if operator else '确认删除', exact=True)
            expect(confirm).to_be_disabled()
            expect(dialog.get_by_text(re.compile('所属编导审批' if operator else '确认后直接删除账号'))).to_be_visible()
            dialog.get_by_label('删除原因').fill('误新增账号')
            expect(confirm).to_be_enabled()
            assert dialog.bounding_box()['width'] <= width - 24
            page.wait_for_timeout(300)  # Wait for the modal animation before visual inspection.
            page.screenshot(path=str(OUT / f'{"operator" if operator else "director"}-{width}.png'))
            confirm.click()
            if not operator:
                final = page.get_by_role('dialog', name='再次确认删除账号', exact=True)
                expect(final).to_be_visible()
                assert page.evaluate('deletionFixture.requests.length') == 0
                final.get_by_role('button', name='仍然删除', exact=True).click()
            expect(dialog).to_have_count(0)
            expect(page.get_by_role('button', name=action, exact=True)).to_have_count(0)
            page.wait_for_function('deletionFixture.saved === 1')
            requests = page.evaluate('deletionFixture.requests')
            assert len(requests) == 1 and requests[0]['body'] == {'reason': '误新增账号'}
            assert requests[0]['url'].endswith('/request-delete' if operator else '/delete')
            if operator:
                expect(page.get_by_text('删除审批中，账号已冻结', exact=True)).to_be_visible()
            else:
                expect(page.get_by_text('账号已删除，历史记录保留', exact=True)).to_be_visible()
            print(f'PASS {action} {width}: correct command, confirmation, refresh, no duplicate entry', flush=True)
    page.goto(BASE + '?fail')
    page.get_by_role('button', name='删除账号', exact=True).click()
    dialog = page.get_by_role('dialog', name='确认删除账号', exact=True)
    dialog.get_by_label('删除原因').fill('保留失败后重试的原因')
    dialog.get_by_role('button', name='确认删除', exact=True).click()
    page.get_by_role('dialog', name='再次确认删除账号', exact=True).get_by_role('button', name='仍然删除', exact=True).click()
    expect(page.get_by_text('账号已被其他人修改，请刷新后重试', exact=True)).to_be_visible()
    expect(dialog.get_by_label('删除原因')).to_have_value('保留失败后重试的原因')
    dialog.get_by_role('button', name='确认删除', exact=True).click()
    page.get_by_role('dialog', name='再次确认删除账号', exact=True).get_by_role('button', name='仍然删除', exact=True).click()
    expect(dialog).to_have_count(0)
    page.wait_for_function('deletionFixture.saved === 1')
    assert len(page.evaluate('deletionFixture.requests')) == 2
    for case in ['denied', 'noQuery']:
        page.goto(BASE + '?' + case)
        expect(page.get_by_role('button', name=re.compile('^(申请删除账号|删除账号)$'))).to_have_count(0)
    assert not errors, errors
    browser.close()
    print('PASS failure preserves reason/retry, permission denied, and no page errors', flush=True)
