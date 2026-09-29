# UTF-8. 真实页面组件 + 隔离适配器，覆盖"有/无 bpm:process-instance:query"两个方向。
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL = 'http://127.0.0.1:5174/test/material-approval-flow.html'
OUT = Path(__file__).resolve().parents[1] / 'node_modules/.cache/material-approval-flow'
OUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))

    def open_todo(suffix='', width=1440, height=900):
        page.set_viewport_size({'width': width, 'height': height})
        page.goto(URL + suffix)
        expect(page.get_by_text('爆款账号审批与爆款内容审批')).to_be_visible()
        page.get_by_role('row').filter(has_text='爆款内容拆解验收 1').get_by_role('button', name='审批').click()
        expect(page.get_by_role('dialog')).to_be_visible()
        expect(page.get_by_role('dialog').locator('.material-approval-detail')).to_be_visible()

    # 1. 有 BPM 查询权限：面板出现、默认折叠，展开后是后端下发的三节点轨迹。
    open_todo('?bpm=1')
    dialog = page.get_by_role('dialog')
    panel = dialog.locator('.bpm-process-panel')
    expect(panel).to_be_visible()
    expect(panel.get_by_text('审批流程')).to_be_visible()
    collapse = panel.locator('.ant-collapse-item')
    assert not collapse.evaluate("(el) => el.className.includes('ant-collapse-item-active')"), '面板应默认折叠'
    panel.get_by_text('审批流程').click()
    nodes = panel.locator('.bpm-approval-timeline-node')
    expect(nodes).to_have_count(3)
    expect(nodes.nth(0)).to_contain_text('发起人')
    expect(nodes.nth(1)).to_contain_text('爆款审核')
    expect(nodes.nth(1)).to_contain_text('程伟')
    expect(nodes.nth(2)).to_contain_text('结束')
    # 面板只读：不得出现任何流程类动作按钮，也不得出现内容批审的误导文案。
    assert panel.locator('button').count() == 0, '只读面板不应出现任何流程动作按钮'
    assert '逐条保存审核结论' not in panel.inner_text(), '不应出现内容批审专用文案'
    # 页面的通过/驳回仍在，走业务接口。
    actions = dialog.locator('.material-approval-actions button')
    expect(actions).to_have_count(2)
    page.screenshot(path=str(OUT / 'granted-1440.png'))
    # 窄屏：面板不得把右栏撑出横向滚动。
    page.set_viewport_size({'width': 390, 'height': 844})
    page.wait_for_timeout(250)
    assert page.locator('body').evaluate('(b) => b.scrollWidth <= window.innerWidth + 1')
    page.screenshot(path=str(OUT / 'granted-390.png'))

    # 2. 无 BPM 查询权限：面板不渲染，页面不报错，通过/驳回仍可用（关键反向用例）。
    open_todo()
    dialog = page.get_by_role('dialog')
    assert dialog.locator('.bpm-process-panel').count() == 0, '缺权限时不应渲染流程面板'
    expect(dialog.locator('.material-approval-actions button')).to_have_count(2)
    page.screenshot(path=str(OUT / 'denied-1440.png'))

    assert not errors, f'页面报错: {errors}'
    browser.close()
    print('PASS: 审核流面板在有权限时展示后端三节点轨迹且只读；缺权限时不渲染、不报错，审批动作不受影响')
