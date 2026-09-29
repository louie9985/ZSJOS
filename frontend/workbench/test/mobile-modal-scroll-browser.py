# -*- coding: utf-8 -*-
"""Verify real FollowUpModal touch scrolling against an existing Vite server."""
import argparse
import json
from pathlib import Path
from tempfile import gettempdir

from playwright.sync_api import expect, sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://127.0.0.1:5174')
args = parser.parse_args()
output = Path(gettempdir()) / 'zsjos-mobile-modal-scroll-20260929'
output.mkdir(exist_ok=True)
url = args.base_url.rstrip('/') + '/test/won-follow-up-browser.html?status=valid'


def swipe(page, client, start, end, x):
    client.send('Input.dispatchTouchEvent', {'type': 'touchStart', 'touchPoints': [{'x': x, 'y': start}]})
    for step in range(1, 17):
        y = start + (end - start) * step / 16
        client.send('Input.dispatchTouchEvent', {'type': 'touchMove', 'touchPoints': [{'x': x, 'y': y}]})
        page.wait_for_timeout(20)
    client.send('Input.dispatchTouchEvent', {'type': 'touchEnd', 'touchPoints': []})
    page.wait_for_timeout(350)


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    errors = []
    for width, height in [(320, 568), (390, 844), (390, 430), (768, 430), (1280, 1000)]:
        mobile = width <= 768
        context = browser.new_context(viewport={'width': width, 'height': height}, is_mobile=mobile, has_touch=mobile)
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(url)
        expect(page.get_by_role('radiogroup', name='跟进方式', exact=True)).to_be_visible()
        expect(page.get_by_role('button', name='提交跟进', exact=True)).to_be_enabled()
        page.wait_for_timeout(400)
        body = page.locator('.ant-modal-body')
        header = page.locator('.ant-modal-header')
        footer = page.locator('.ant-modal-footer')
        if mobile:
            assert body.evaluate('(e) => e.scrollHeight > e.clientHeight'), (width, height, 'missing internal scroll range')
            initial_header = header.bounding_box()
            initial_footer = footer.bounding_box()
            assert initial_header['y'] >= 0
            assert initial_footer['y'] + initial_footer['height'] <= height + 1
            box = body.bounding_box()
            start = min(box['y'] + box['height'] - 15, height - 80)
            end = box['y'] + 25
            client = context.new_cdp_session(page)
            swipe(page, client, start, end, width / 2)
            page.wait_for_function("document.querySelector('.ant-modal-body').scrollTop > 0")
            down = body.evaluate('(e) => e.scrollTop')
            assert abs(header.bounding_box()['y'] - initial_header['y']) < 1
            assert abs(footer.bounding_box()['y'] - initial_footer['y']) < 1
            page.screenshot(path=str(output / f'{width}x{height}.png'))
            swipe(page, client, end, start, width / 2)
            assert body.evaluate('(e) => e.scrollTop') < down, 'reverse swipe did not scroll'
            print(json.dumps({'viewport': [width, height], 'swipeScrollTop': down, 'footerBottom': initial_footer['y'] + initial_footer['height']}))
        else:
            assert page.locator('.ant-modal-container').evaluate('(e) => getComputedStyle(e).maxHeight') == 'none'
            assert footer.bounding_box()['y'] + footer.bounding_box()['height'] <= height
            page.screenshot(path=str(output / 'desktop.png'))
        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'horizontal overflow'
        context.close()
    assert not errors, errors
    browser.close()
print('PASS: mobile touch scroll in both directions, fixed header/footer, desktop; screenshots:', output)
