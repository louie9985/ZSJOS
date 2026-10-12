# -*- coding: utf-8 -*-
"""Real React components, isolated API transport; no external issuance."""
import os,re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
url='http://127.0.0.1:5237/test/points-redemption.html'
out=Path(os.environ['TEMP'])/'points-redemption-browser';out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width in (1440,390):
        page=browser.new_page(viewport={'width':width,'height':960});page.goto(url)
        expect(page.get_by_text('暂无积分兑换码申请')).to_be_visible()
        page.locator('button').filter(has_text='申请兑换码').click()
        dialog=page.get_by_role('dialog',name='申请积分兑换码')
        dialog.get_by_role('button',name=re.compile('确.*定')).click()
        expect(dialog.get_by_text('请选择套餐')).to_be_visible()
        dialog.locator('.ant-select').click();page.get_by_text('5000积分套餐 · 5000积分 · 30天有效',exact=True).last.click()
        dialog.get_by_label('申请理由').fill('申请用于课程学习支持')
        dialog.get_by_role('button',name=re.compile('确.*定')).click()
        expect(page.get_by_text('请至少上传一份附件')).to_be_visible()
        dialog.locator('input[type=file]').set_input_files({'name':'申请说明.pdf','mimeType':'application/pdf','buffer':b'%PDF-1.4 isolated'})
        expect(dialog.get_by_text('申请说明.pdf')).to_be_visible()
        dialog.get_by_role('button',name=re.compile('确.*定')).click()
        detail=page.get_by_role('dialog',name='积分申请详情')
        expect(detail.get_by_text('审批中',exact=True)).to_be_visible()
        expect(detail.get_by_role('button',name='查看兑换码')).to_have_count(0)
        detail.get_by_role('button',name='Close').click()
        page.evaluate('approvePoints()')
        page.locator('button').filter(has_text='刷新').click()
        page.get_by_role('button',name='查看详情').click()
        detail=page.get_by_role('dialog',name='积分申请详情')
        detail.get_by_role('button',name='查看兑换码').click()
        expect(detail.get_by_text('KM-ISOLATED-TEST-CODE-0123456789')).to_be_visible()
        page.screenshot(path=str(out/f'points-{width}.png'),full_page=True)
        detail.get_by_role('button',name='Close').click()
        expect(page.get_by_text('KM-ISOLATED-TEST-CODE-0123456789')).to_have_count(0)
        assert page.evaluate('testPointsNotification(1)')['routes'] == ['/zsjos/points-redemption?id=1']
        assert page.evaluate('testPointsNotification(999)')['warnings']
        page.close()
    page=browser.new_page();page.goto(url+'?state=denied');expect(page.get_by_text('无积分兑换码查询权限')).to_be_visible()
    page.goto(url+'?state=error');expect(page.get_by_text('申请加载失败（模拟）')).to_be_visible();page.get_by_role('button',name=re.compile('重.*试')).click();expect(page.get_by_text('暂无积分兑换码申请')).to_be_visible()
    page.goto(url+'?state=packages');expect(page.get_by_text('5000积分套餐')).to_be_visible();page.get_by_role('button',name='新增套餐').click();expect(page.get_by_label('兑换积分')).to_be_visible()
    page.goto(url+'?state=packages-readonly');expect(page.get_by_text('5000积分套餐')).to_be_visible();expect(page.get_by_role('button',name='新增套餐')).to_have_count(0);expect(page.get_by_role('button',name='编辑')).to_have_count(0)
    browser.close()
print('PASS desktop/mobile submit, validation, upload, approval result, reveal, clear, denied, retry and packages')
