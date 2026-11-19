# -*- coding: utf-8 -*-
"""Synthetic browser regression for organization configuration loading and retry."""
import re
from playwright.sync_api import sync_playwright, expect
base='http://127.0.0.1:5189/test/sales-performance.html?target=1&review=1&periodStart=2026-09-01'
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':1440,'height':900})
    page.goto(base+'&orgDelay=1',wait_until='commit',timeout=60000)
    page.get_by_role('button',name='销售组织关联').click()
    modal=page.get_by_role('dialog')
    expect(modal.get_by_role('button',name=re.compile('确\\s*定'))).to_be_disabled()
    expect(modal.get_by_text('设为销售业务部门边界根')).to_be_visible(timeout=6000)
    expect(modal.get_by_role('button',name=re.compile('确\\s*定'))).to_be_enabled()
    modal.get_by_label('系统组织').click()
    page.get_by_title('演示销售中心').last.click()
    expect(modal.get_by_role('checkbox',name='设为销售业务部门边界根')).to_be_checked()
    page.goto(base+'&orgError=1',wait_until='commit',timeout=60000)
    page.get_by_role('button',name='销售组织关联').click()
    modal=page.get_by_role('dialog')
    expect(modal.get_by_text('组织映射加载失败')).to_be_visible()
    expect(modal.get_by_role('button',name=re.compile('确\\s*定'))).to_be_disabled()
    modal.get_by_role('button',name=re.compile('重\\s*试')).click()
    expect(modal.get_by_text('设为销售业务部门边界根')).to_be_visible()
    expect(modal.get_by_role('button',name=re.compile('确\\s*定'))).to_be_enabled()
    browser.close()
print('PASS fixture: organization settings wait for mappings, preserve root, and allow error retry')
