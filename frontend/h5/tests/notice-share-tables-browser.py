# -*- coding: utf-8 -*-
"""Verify both public readers using Java-exported sanitized content and production H5 assets."""
import argparse
import json
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--fixtures', type=Path, required=True)
parser.add_argument('--dist', type=Path, required=True)
args = parser.parse_args()
fixtures, dist = args.fixtures.resolve(), args.dist.resolve()
samples = ['fixed', 'percent', 'cells', 'plain']
for sample in samples:
    assert (fixtures / f'{sample}.content.html').is_file()
    assert (fixtures / f'{sample}.page.html').is_file()
assert (dist / 'index.html').is_file()


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(dist), **kw)

    def log_message(self, *_):
        pass

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path == '/page':
            sample = parse_qs(url.query)['case'][0]
            assert sample in samples
            self.send_response(200)
            self.send_header('Content-Type', 'text/html;charset=UTF-8')
            self.end_headers()
            self.wfile.write((fixtures / f'{sample}.page.html').read_bytes())
        else:
            if url.path == '/notice/share': self.path = '/index.html'
            super().do_GET()


server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'


def check_document_layout(page, width):
    metrics = page.evaluate('''() => {
        const main = document.querySelector('main');
        const body = document.querySelector('.notice-public-body, .content');
        const rect = main.getBoundingClientRect();
        const style = getComputedStyle(main);
        return {width: rect.width, left: rect.left, bodyFont: getComputedStyle(body).fontSize,
            padding: parseFloat(style.paddingLeft),
            overflow: document.documentElement.scrollWidth > innerWidth + 1};
    }''')
    assert abs(metrics['width'] - min(width, 1120)) < 1, metrics
    assert abs(metrics['left'] - max(0, (width - 1120) / 2)) < 1, metrics
    assert metrics['bodyFont'] == '16px', metrics
    assert 16 <= metrics['padding'] <= 48, metrics
    assert not metrics['overflow'], metrics


try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome', headless=True)
        for client in ['server', 'h5']:
            for width in [390, 768, 1440, 1920]:
                context = browser.new_context(viewport={'width': width, 'height': 960}, java_script_enabled=client == 'h5')
                page = context.new_page()
                errors = []
                page.on('pageerror', lambda e: errors.append(str(e)))
                for sample in samples:
                    page.unroute_all()
                    if client == 'h5':
                        # A repeated hash URL keeps the previous Vue instance; open each fixture fresh.
                        page.goto('about:blank')
                        data = {'code': 0, 'data': {'title': '公告表格格式验证', 'content': (fixtures / f'{sample}.content.html').read_text(encoding='utf-8'), 'publishTime': 1791532800000, 'attachments': []}}
                        response_body = json.dumps(data, ensure_ascii=False)
                        page.route('**/public-api/system/notice-share/get', lambda route: route.fulfill(content_type='application/json', body=response_body))
                        page.goto(base + '/notice/share#token=' + 'a' * 43)
                    else:
                        page.goto(base + '/page?case=' + sample)
                    expect(page.get_by_role('heading', name='公告表格格式验证', exact=True)).to_be_visible()
                    check_document_layout(page, width)
                    if sample == 'plain':
                        expect(page.locator('.notice-table-scroll')).to_have_count(0)
                        expect(page.get_by_text('加粗', exact=True)).to_be_visible()
                        continue
                    wrapper = page.locator('.notice-table-scroll')
                    expect(wrapper).to_have_count(1)
                    expect(wrapper).to_have_attribute('tabindex', '0')
                    metrics = page.evaluate('''() => {
                        const wrap = document.querySelector('.notice-table-scroll');
                        const table = wrap.querySelector('table');
                        const cells = [...table.rows[0].cells];
                        return {overflow: wrap.scrollWidth > wrap.clientWidth + 1,
                            tableWidth: table.getBoundingClientRect().width, layout: getComputedStyle(table).tableLayout,
                            widths: cells.map(cell => cell.getBoundingClientRect().width),
                            border: parseFloat(getComputedStyle(cells[0]).borderTopWidth),
                            padding: parseFloat(getComputedStyle(cells[0]).paddingLeft),
                            rowHeight: table.rows[0].getBoundingClientRect().height,
                            pageOverflow: document.documentElement.scrollWidth > innerWidth + 1};
                    }''')
                    assert not metrics['pageOverflow'], (client, width, sample, metrics)
                    assert metrics['border'] > 0 and metrics['padding'] > 0, metrics
                    expect(wrapper.locator('tr').first.locator('td, th').first).to_have_css('border-top-color', 'rgb(221, 221, 221)')
                    if sample == 'fixed':
                        assert metrics['overflow'] == (width < 1000), metrics
                        assert abs(metrics['tableWidth'] - 900) < 3, metrics
                        assert metrics['layout'] == 'fixed', metrics
                        assert abs(metrics['widths'][0] - 200) < 3, metrics
                        assert metrics['rowHeight'] >= 55, metrics
                        expect(page.locator('td[colspan="2"]')).to_have_count(1)
                        expect(page.locator('td[rowspan="2"]')).to_have_count(1)
                        cell = page.locator('tbody tr').nth(1).locator('td').first
                        expect(cell).to_have_css('text-align', 'center')
                        expect(cell).to_have_css('background-color', 'rgb(240, 248, 255)')
                        expect(cell).to_have_css('border-top-color', 'rgb(18, 52, 86)')
                        expect(cell.locator('span')).to_have_css('color', 'rgb(200, 0, 0)')
                    elif sample == 'cells':
                        assert metrics['tableWidth'] >= 739, metrics
                        assert metrics['widths'][0] >= 199 and metrics['widths'][1] >= 299, metrics
                    elif sample == 'percent':
                        assert not metrics['overflow'], metrics
                        assert abs(metrics['widths'][0] / sum(metrics['widths']) - .25) < .04, metrics
                    if sample in ['fixed', 'cells'] and width == 390:
                        assert metrics['overflow'], metrics
                        wrapper.focus()
                        wrapper.press('ArrowRight')
                        # Poll from the test process: page-side RAF polling is disabled in the no-JS reader.
                        for _ in range(30):
                            if wrapper.evaluate('(node) => node.scrollLeft > 0'): break
                            time.sleep(.05)
                        assert wrapper.evaluate('(node) => node.scrollLeft > 0'), (client, sample, 'keyboard scroll')
                        wrapper.evaluate('(node) => node.scrollLeft = node.scrollWidth')
                        assert wrapper.evaluate('(node) => node.scrollLeft + node.clientWidth >= node.scrollWidth - 2')
                        wrapper.evaluate('(node) => node.scrollLeft = 0')
                    page.screenshot(path=str(fixtures / f'{client}-{sample}-{width}.png'), full_page=True)
                assert not errors, errors
                # Resize an already-open reader, including amfe-flexible's root-size updates.
                for resized_width in [390, 1440, width]:
                    page.set_viewport_size({'width': resized_width, 'height': 960})
                    page.wait_for_timeout(150)
                    check_document_layout(page, resized_width)
                if client == 'h5':
                    # The loaded public CSS must stop widening the app on a SPA route change.
                    page.evaluate("document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/login')")
                    expect(page.locator('.notice-public')).to_have_count(0)
                    assert page.locator('#app').evaluate('(el) => el.clientWidth <= 540'), 'public width leaked to login'
                context.close()
                print('PASS', client, width, 'responsive document/resize, fixed/percentage/cell widths, merged cells, safe styles, scroll and plain content', flush=True)
        browser.close()
finally:
    server.shutdown()
    server.server_close()
