# -*- coding: utf-8 -*-
"""Admin 素材管理详情抽屉的只读审批轨迹：带权限展示，缺权限不展示。"""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = 'http://127.0.0.1:81/test/material-flow.html'
OUT = Path(__file__).resolve().parents[1] / 'node_modules/.cache/material-flow'
OUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))

    def open_detail(suffix=''):
        page.goto(BASE + suffix)
        page.get_by_role('button', name='详情', exact=True).first.click()
        expect(page.get_by_role('dialog', name='查看爆款拆解')).to_be_visible()

    # 1. 带 bpm:process-instance:query：出现「审批流程」折叠区，展开为三节点轨迹。
    open_detail('?bpm=1')
    drawer = page.get_by_role('dialog', name='查看爆款拆解')
    expect(drawer.get_by_text('审批流程', exact=True)).to_be_visible()
    drawer.get_by_text('审批流程', exact=True).click()
    # 展开后按节点容器断言，避免与详情区同名文本冲突。
    panel = drawer.locator('.el-collapse')
    expect(panel.get_by_text('爆款审核', exact=True)).to_be_visible()
    assert '程伟' in panel.inner_text(), '轨迹应显示审核节点处理人'
    page.screenshot(path=str(OUT / 'granted-1440.png'))

    # 2. 缺权限：整个折叠区不渲染。
    open_detail()
    drawer = page.get_by_role('dialog', name='查看爆款拆解')
    assert drawer.locator('.el-collapse').count() == 0, '缺权限时不应渲染审批流程区'
    expect(drawer.get_by_text('MAT-TEST-2', exact=False)).to_be_visible()
    page.screenshot(path=str(OUT / 'denied-1440.png'))

    assert not errors, f'页面报错: {errors}'
    browser.close()
    print('PASS: Admin 素材详情在授权时展示只读审批轨迹；缺 bpm:process-instance:query 时不渲染且不影响详情其余部分')
