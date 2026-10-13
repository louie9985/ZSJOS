# UTF-8. Isolated browser contexts; fixture adapters block real business transport.
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path(os.environ.get('TEMP', '.')) / 'order-partner-owner-browser'
OUT.mkdir(exist_ok=True)
LABEL = '兼职归属新媒体员工'
SCENARIOS = {'assigned': '提交时新媒体员工甲', 'unassigned': '提交时未分配', 'historical': '历史未记录', 'other': '—'}

with sync_playwright() as p:
    browser = p.chromium.launch()
    for frontend, port in [('workbench', 5174), ('admin', 5189)]:
        for width in (1440, 390):
            context = browser.new_context(viewport={'width': width, 'height': 1000})
            page = context.new_page()
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            for scenario, expected in SCENARIOS.items():
                page.goto(f'http://127.0.0.1:{port}/test/order-partner-owner.html?scenario={scenario}')
                if frontend == 'workbench':
                    card = page.locator('.sales-order-list-item')
                    expect(card).to_contain_text(f'{LABEL}：{expected}')
                    if width < 768:
                        card.click()
                        detail = page.get_by_role('dialog')
                        page.wait_for_function("Math.abs(document.querySelector('[role=dialog]').getBoundingClientRect().x)<2")
                    else:
                        detail = page.locator('.sales-order-detail-pane')
                    row = detail.locator('.sales-order-partner-owner')
                else:
                    expect(page.locator('.el-table__body')).to_contain_text(expected)
                    page.get_by_role('button', name='查看', exact=True).click()
                    detail = page.get_by_role('dialog')
                    row = detail.locator('tr').filter(has=page.get_by_text(LABEL, exact=True))
                if scenario == 'other':
                    expect(row).to_have_count(0)
                else:
                    expect(row).to_contain_text(expected)
                    row.scroll_into_view_if_needed()
                    page.wait_for_timeout(350)
                    box = row.bounding_box()
                    assert box['x'] >= -1 and box['x'] + box['width'] <= width + 1, (frontend, width, box)
                    if frontend == 'workbench':
                        assert row.locator('.lead-field-value').evaluate('e=>e.scrollWidth<=e.clientWidth+1')
                    if scenario == 'assigned':
                        page.screenshot(path=str(OUT / f'{frontend}-{width}.png'), full_page=True)
                assert not errors, errors
            print(f'PASS {frontend} {width}: assigned/unassigned/historical/non-partner', flush=True)
            context.close()

    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.add_init_script("localStorage.setItem('crm-theme', JSON.stringify({inboxLayoutMode:'table'}))")
    page = context.new_page()
    page.goto('http://127.0.0.1:5174/test/order-partner-owner.html')
    expect(page.get_by_role('columnheader', name=LABEL, exact=True)).to_be_attached()
    expect(page.get_by_role('cell', name=SCENARIOS['assigned'], exact=True)).to_be_attached()
    page.get_by_role('button', name='详细', exact=True).click()
    expect(page.get_by_role('dialog').locator('.sales-order-partner-owner')).to_contain_text(SCENARIOS['assigned'])
    print('PASS workbench table list and detail; screenshots:', OUT, flush=True)
    browser.close()
