# UTF-8. Actual unchanged Vue notification and BPM action components, isolated transport.
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('ADMIN_REVIEW_NODE_URL', 'http://127.0.0.1:5198')
OUT = Path(__file__).resolve().parents[1] / 'node_modules/.cache/content-review-node'
OUT.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    sockets = []
    page.route_web_socket(re.compile(r'.*/infra/ws.*'), lambda ws: sockets.append(ws))
    page.goto(BASE + '/test/content-review-node.html')
    expect(page.locator('.message')).to_be_visible()
    page.wait_for_function('document.querySelector(".el-badge__content") !== null')
    assert sockets
    event = '{"type":"notify-message-new","content":{"messageId":42}}'
    sockets[0].send(event)
    notice = page.locator('.el-notification')
    expect(notice).to_contain_text('内容审批 · 终审通过')
    expect(notice).to_contain_text('共 6 条作品')
    sockets[0].send(event)
    expect(notice).to_have_count(1)
    notice.click()
    expect(page.locator('#current-route')).to_have_text('/user/notify-message?messageId=42')
    page.screenshot(path=str(OUT / 'admin-notify-fallback.png'))
    page.goto(BASE + '/test/content-review-buttons.html')
    review = page.locator('section[aria-label="内容审核动作"]')
    expect(review.get_by_role('button', name=re.compile(r'通\s*过'))).to_be_visible()
    expect(review.get_by_role('button', name=re.compile(r'拒\s*绝'))).to_be_visible()
    for label in ('转办', '委派', '加签', '减签', '抄送'):
        expect(review.get_by_role('button', name=label, exact=True)).to_have_count(0)
    browser.close()
print('PASS Admin actual notification fields/message-ID dedup/message-detail fallback/content-approval actions')
