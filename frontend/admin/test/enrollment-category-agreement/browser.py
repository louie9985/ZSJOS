from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[2] / 'node_modules/.cache/agreement-fixture-dist'
server = ThreadingHTTPServer(('127.0.0.1', 0), partial(SimpleHTTPRequestHandler, directory=str(root)))
Thread(target=server.serve_forever, daemon=True).start()
url = f'http://127.0.0.1:{server.server_port}/test/enrollment-category-agreement/index.html'

def choose(page, name):
    page.locator('.el-select').click()
    page.get_by_role('option', name='分类 / '+name).click()

with sync_playwright() as runtime:
    browser = runtime.chromium.launch(channel='msedge')
    for width in [1280, 390]:
        page = browser.new_page(viewport={'width':width, 'height':850})
        page.goto(url)
        choose(page, '产品甲')
        expect(page.get_by_role('button', name='发布版本')).to_be_disabled()
        page.get_by_role('button', name='手机预览').click()
        expect(page.locator('article')).to_contain_text('示例产品')
        page.keyboard.press('Escape')
        page.get_by_role('button', name='保存草稿').click()
        page.get_by_role('button', name='发布版本').click()
        page.locator('.el-message-box__btns button').last.click()
        expect(page.get_by_text('当前发布版本 V2', exact=False)).to_be_visible()
        choose(page, '产品乙')
        expect(page.get_by_text('该产品尚未发布协议', exact=False)).to_be_visible()
        page.goto(url+'?readonly')
        choose(page, '产品甲')
        expect(page.get_by_text('当前发布版本 V2', exact=False)).to_be_visible()
        expect(page.get_by_role('button', name='保存草稿')).to_have_count(0)
        page.goto(url)
        page.evaluate("url=>{let frame=document.createElement('iframe');frame.src=url;document.body.replaceChildren(frame)}",url)
        choose(page.frame_locator('iframe'), '产品甲')
        expect(page.frame_locator('iframe').get_by_text('当前发布版本 V2', exact=False)).to_be_visible()
        page.close()
    browser.close()
print('Category selection, draft, publish, preview, isolation, read-only and embed passed at both widths.')
