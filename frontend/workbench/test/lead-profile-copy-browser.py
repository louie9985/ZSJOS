# UTF-8. Real Chromium copy/paste with synthetic data; existing Vite server only.
import socket
from playwright.sync_api import sync_playwright, expect

ip = socket.gethostbyname(socket.gethostname())
assert not ip.startswith('127.'), 'Requires a non-loopback HTTP address'
values = ['测试姓名', '+8613800000000', 'wechat_abcdefghijklmnopqrstuvwxyz0123456789' * 5]

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def open_fixture(origin, query=''):
        page.goto(origin + '/test/lead-contact-rows.html' + query)
        page.locator('.lead-profile-fields').wait_for()
        page.evaluate('''() => {
            const sink = document.createElement('textarea');
            sink.id = 'paste-sink'; document.body.append(sink);
        }''')

    def check_paste(button, value, success=True):
        button.focus()
        button.press('Enter')
        if success:
            expect(page.get_by_role('button', name='已复制', exact=True)).to_be_visible()
        assert page.evaluate("document.activeElement.tagName === 'BUTTON'")
        assert page.locator('textarea').count() == 1, 'temporary copy node leaked'
        sink = page.locator('#paste-sink')
        sink.fill('')
        sink.focus()
        sink.press('Control+v')
        expect(sink).to_have_value(value)

    # No Clipboard API stubbing: non-loopback HTTP must actually be insecure.
    open_fixture(f'http://{ip}:5174')
    assert page.evaluate('!isSecureContext && !navigator.clipboard')
    for label, value in zip(['姓名', '手机号', '微信号'], values):
        check_paste(page.get_by_role('button', name='复制' + label, exact=True), value)
        page.get_by_role('button', name='切换客资', exact=True).click()

    open_fixture(f'http://{ip}:5174', '?default')
    for index, value in enumerate(values[1:]):
        check_paste(page.locator('.lead-field-copy-btn').nth(index), value, False)

    # Secure-context API path, then real fallback after async API denial.
    open_fixture('http://localhost:5174')
    assert page.evaluate('isSecureContext && !!navigator.clipboard')
    check_paste(page.get_by_role('button', name='复制姓名', exact=True), values[0])
    page.get_by_role('button', name='切换客资', exact=True).click()
    page.evaluate("() => { navigator.clipboard.writeText = async () => { throw new DOMException('Denied', 'NotAllowedError') } }")
    check_paste(page.get_by_role('button', name='复制手机号', exact=True), values[1])
    page.get_by_role('button', name='切换客资', exact=True).click()
    page.evaluate('() => { document.execCommand = () => false }')
    page.get_by_role('button', name='复制微信号', exact=True).click()
    expect(page.get_by_text('复制失败，请手动选择文本复制', exact=True)).to_be_visible()
    assert page.get_by_role('button', name='已复制', exact=True).count() == 0
    assert page.locator('textarea').count() == 1
    assert not errors, errors
    browser.close()
    print('PASS: actual insecure HTTP copy/paste (all fields, both layouts), secure API, denied API fallback, failure feedback, focus and cleanup')
