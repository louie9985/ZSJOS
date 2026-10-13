# -*- coding: utf-8 -*-
"""Actual notice page, synthetic transport, existing Vite only. No account writes."""
import json
import os
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('NOTICE_WIDTH_TEST_BASE', 'http://127.0.0.1:5197')
OUT = Path(tempfile.gettempdir()) / 'notice-action-single-line-20261013'
OUT.mkdir(exist_ok=True)

MEASURE = """() => {
  const table = document.querySelector('[data-table-key="announcement-management"]');
  const headers = [...table.querySelectorAll('thead th')];
  const highlight = headers.find(x => x.textContent.includes('高亮')).getBoundingClientRect();
  const action = headers.find(x => x.textContent.includes('操作')).getBoundingClientRect();
  const rows = [...table.querySelectorAll('tbody tr[data-row-key]')];
  return { actionWidth: action.width, highlightWidth: highlight.width,
    visibleHighlight: Math.max(0, Math.min(highlight.right, action.left) - highlight.left),
    rows: rows.map(row => ({status:row.children[4].textContent,
      buttons:[...row.querySelectorAll('button')].map(b => b.textContent),
      width:row.lastElementChild.getBoundingClientRect().width})),
    wrapped: rows.some(row => {
      const tops = [...row.querySelectorAll('button')].map(button => button.getBoundingClientRect().top);
      return tops.length > 1 && Math.max(...tops) - Math.min(...tops) > 1;
    }),
    escaped: rows.some(row => [...row.querySelectorAll('button')].some(button => {
      const a=row.lastElementChild.getBoundingClientRect(), b=button.getBoundingClientRect();
      return b.left<a.left-1 || b.right>a.right+1 || b.bottom>a.bottom+1;
    }))};
}"""

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1912, 'height': 948})
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(BASE + '/test/notice-action-width.html')
    expect(page.get_by_role('button', name='查看', exact=True)).to_have_count(3)
    # Match the screenshot's 1558px table viewport without modifying shared styles.
    page.add_style_tag(content='body { margin:0; } .business-table { width:1606px; margin-left:284px; }')
    page.wait_for_timeout(300)
    view = page.evaluate(MEASURE)
    assert 80 <= view['actionWidth'] < 90, view
    assert view['visibleHighlight'] >= view['highlightWidth'] - 1, view
    assert all(row['buttons'] == ['查看'] for row in view['rows']), view
    page.screenshot(path=str(OUT / 'view-desktop.png'), full_page=True)

    page.get_by_role('button', name='查看与分享', exact=True).click()
    expect(page.get_by_role('button', name='对外分享', exact=True)).to_have_count(1)
    page.wait_for_timeout(150)
    share = page.evaluate(MEASURE)
    assert 150 <= share['actionWidth'] <= 175, share
    assert not share['escaped'] and not share['wrapped'], share
    page.screenshot(path=str(OUT / 'share-desktop.png'), full_page=True)

    page.get_by_role('button', name='管理权限', exact=True).click()
    expect(page.get_by_role('button', name='编辑', exact=True)).to_have_count(1)
    page.wait_for_timeout(150)
    manage = page.evaluate(MEASURE)
    assert manage['actionWidth'] > 280, manage
    assert not manage['escaped'] and not manage['wrapped'], manage
    assert len({round(row['width']) for row in manage['rows']}) == 1, manage
    expected = {'已发布':['查看','对外分享','下线','复制草稿'], '草稿':['查看','编辑','发布','删除'], '已下线':['查看','复制草稿']}
    assert all(row['buttons'] == expected[row['status']] for row in manage['rows']), manage
    page.screenshot(path=str(OUT / 'manage-desktop.png'), full_page=True)
    page.get_by_role('button', name='大字号宽松', exact=True).click()
    page.wait_for_timeout(300)
    large = page.evaluate(MEASURE)
    assert large['actionWidth'] > manage['actionWidth'], large
    assert not large['escaped'] and not large['wrapped'], large
    page.screenshot(path=str(OUT / 'manage-large-font.png'), full_page=True)
    page.get_by_role('button', name='默认显示', exact=True).click()
    page.wait_for_timeout(300)
    assert page.evaluate(MEASURE)['actionWidth'] == manage['actionWidth']
    page.locator('.ant-table-body').evaluate('(el) => el.scrollLeft = el.scrollWidth')
    page.wait_for_timeout(100)
    scrolled = page.evaluate(MEASURE)
    assert scrolled['visibleHighlight'] >= scrolled['highlightWidth'] - 1, scrolled
    page.locator('.ant-select').first.click()
    page.locator('.ant-select-item-option').filter(has_text='已下线').click()
    expect(page.get_by_role('button', name='查看', exact=True)).to_have_count(1)
    page.wait_for_timeout(150)
    offline = page.evaluate(MEASURE)
    assert 150 <= offline['actionWidth'] <= 175, offline

    page.get_by_role('button', name='仅查看', exact=True).click()
    page.wait_for_timeout(200)
    assert page.evaluate(MEASURE)['actionWidth'] < 90
    page.get_by_role('button', name='查看', exact=True).click()
    expect(page.get_by_role('dialog')).to_be_visible()
    page.get_by_role('button', name='Close', exact=True).click()
    page.get_by_role('button', name='无查询权限', exact=True).click()
    expect(page.get_by_text('无权查看此表格', exact=True)).to_be_visible()
    expect(page.get_by_role('button', name='查看', exact=True)).to_have_count(0)

    for width in [1440, 390]:
        page.set_viewport_size({'width': width, 'height': 960})
        page.goto(BASE + '/test/notice-action-width.html')
        expect(page.get_by_role('button', name='查看', exact=True)).to_have_count(3)
        for mode in ['仅查看', '查看与分享', '管理权限']:
            page.get_by_role('button', name=mode, exact=True).click()
            page.wait_for_timeout(150)
            measurement = page.evaluate(MEASURE)
            assert not measurement['escaped'] and not measurement['wrapped'], (width, mode, measurement)
            page.locator('.ant-table-body').evaluate('(el) => el.scrollLeft = el.scrollWidth')
            page.wait_for_timeout(100)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), (width, mode)
        page.screenshot(path=str(OUT / f'manage-{width}.png'), full_page=True)

    page.evaluate("localStorage.setItem('crm-table:announcement-management:widths', JSON.stringify({title:320, highlightUntil:190}))")
    page.reload()
    expect(page.get_by_role('button', name='查看', exact=True)).to_have_count(3)
    assert round(page.evaluate(MEASURE)['highlightWidth']) == 190
    assert not errors, errors
    print(json.dumps({'view':view, 'share':share, 'manage':manage, 'large':large, 'offline':offline, 'screenshots':str(OUT)}, ensure_ascii=True))
    browser.close()
