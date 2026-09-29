# -*- coding: utf-8 -*-
import base64
import json
from playwright.sync_api import sync_playwright, expect

PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
def file(name, mime='image/png'):
    return {'name': name, 'mimeType': mime, 'buffer': PNG}

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    base = 'http://localhost:5174/test/deferred-attachment-batch.html'
    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.goto(base)
        picker = page.locator('input[type=file]')
        picker.set_input_files([file('a.png'), file('b.png'), file('c.png')])
        expect(page.locator('.deferred-attachment-item')).to_have_count(3)
        assert json.loads(page.locator('#calls').inner_text()) == []
        picker.set_input_files([file('invalid.txt', 'text/plain'), file('d.png')])
        expect(page.locator('.deferred-attachment-item')).to_have_count(4)
        page.get_by_role('button', name='删除d.png', exact=True).click()
        expect(page.locator('.deferred-attachment-item')).to_have_count(3)
        page.get_by_role('button', name='确认上传', exact=True).click()
        expect(page.locator('.status-error')).to_have_count(1)
        expect(page.locator('.status-done')).to_have_count(2)
        page.get_by_role('button', name='确认上传', exact=True).click()
        expect(page.locator('.status-done')).to_have_count(3)
        assert json.loads(page.locator('#calls').inner_text()) == ['a.png', 'b.png', 'c.png', 'b.png']
        picker.set_input_files([file(f'extra-{i}.png') for i in range(8)])
        expect(page.locator('.deferred-attachment-item')).to_have_count(9)
        expect(picker).to_be_disabled()
        page.get_by_role('button', name='删除extra-0.png', exact=True).click()
        picker.set_input_files([file('replacement.png')])
        expect(page.locator('.deferred-attachment-item')).to_have_count(9)
        names = [item['name'] for item in json.loads(page.locator('#state').inner_text())]
        assert names == ['a.png', 'b.png', 'c.png'] + [f'extra-{i}.png' for i in range(1, 6)] + ['replacement.png']
        page.goto(base + '?mixed')
        page.locator('input[type=file]').set_input_files([file('voucher.pdf', 'application/pdf'), file('image.png')])
        expect(page.locator('.deferred-attachment-item')).to_have_count(2)
    assert not errors, errors
    browser.close()
    print('PASS: desktop/mobile batch, append, invalid type, delete, max count, deferred upload, failed retry, mixed file caller')
