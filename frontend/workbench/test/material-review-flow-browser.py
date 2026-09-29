# UTF-8. 素材库详情抽屉的只读流程轨迹（编导视角）：带权限展示，缺权限不展示。
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL = 'http://127.0.0.1:5174/test/material-review.html'
OUT = Path(__file__).resolve().parents[1] / 'node_modules/.cache/material-review-flow'
OUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))

    # 1. 带 bpm:process-instance:query：在途版本的抽屉里出现流程轨迹。
    page.goto(URL + '?view=mine&bpm=1')
    page.get_by_role('button', name='修订审核中', exact=False).click()
    expect(page.locator('.material-layout-summary')).to_be_visible()
    panel = page.locator('.bpm-process-panel')
    expect(panel).to_be_visible()
    expect(panel.get_by_text('审批流程')).to_be_visible()
    panel.get_by_text('审批流程').click()
    nodes = panel.locator('.bpm-approval-timeline-node')
    expect(nodes).to_have_count(3)
    expect(nodes.nth(0)).to_contain_text('发起人')
    expect(nodes.nth(1)).to_contain_text('爆款审核')
    expect(nodes.nth(1)).to_contain_text('程伟')
    expect(nodes.nth(2)).to_contain_text('结束')
    # 只读：不得出现任何流程动作按钮。
    assert panel.locator('button').count() == 0, '只读面板不应出现流程动作按钮'
    page.screenshot(path=str(OUT / 'granted-1440.png'))

    # 2. 缺权限：不渲染面板，抽屉其余部分与撤回入口不受影响。
    page.goto(URL + '?view=mine')
    page.get_by_role('button', name='修订审核中', exact=False).click()
    expect(page.locator('.material-layout-summary')).to_be_visible()
    assert page.locator('.bpm-process-panel').count() == 0, '缺权限时不应渲染流程面板'
    expect(page.get_by_role('button', name='撤回审批', exact=True)).to_be_visible()
    page.screenshot(path=str(OUT / 'denied-1440.png'))

    assert not errors, f'页面报错: {errors}'
    browser.close()
    print('PASS: 素材库在途版本抽屉展示只读流程轨迹；缺 BPM 查询权限时不渲染且不影响撤回入口')
