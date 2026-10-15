# -*- coding: utf-8 -*-
import os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
out=Path(os.environ['TEMP'])/'review-fixes-browser';out.mkdir(exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for width in [1440,390]:
  page=browser.new_page(viewport={'width':width,'height':960})
  page.goto('http://127.0.0.1:5274/test/review-fixes.html')
  expect(page.locator('.attachment-card')).to_have_count(11)
  page.wait_for_function('window.reviewMetrics.metadata === 1')
  assert page.evaluate('window.reviewMetrics.content')==0
  assert page.evaluate('window.reviewMetrics.points')==0
  page.locator('.attachment-card').first.get_by_role('button',name='预览').click()
  expect(page.get_by_text('该文件类型不支持在线预览，请下载查看')).to_be_visible()
  assert page.evaluate('window.reviewMetrics.content')==1
  assert page.evaluate('window.reviewMetrics.marker') is False
  assert page.locator('iframe').count()==0
  page.evaluate('window.reviewMetrics.safe=true')
  page.locator('.attachment-card').first.get_by_role('button',name='重试附件',exact=True).click()
  page.locator('.attachment-card').first.get_by_role('button',name='预览').click()
  expect(page.locator('iframe')).to_be_visible()
  assert 'allow-scripts' not in page.locator('iframe').get_attribute('sandbox')
  with page.expect_download():
   page.get_by_role('button',name='下载原文件').click()
  page.locator('.ant-modal-footer button').last.click()
  assert page.evaluate('window.reviewMetrics.revoked') >= 2
  # The save dialog stays open while an older read is pending.
  page.get_by_role('button',name='保存为快捷色',exact=True).click()
  page.get_by_label('快捷色名称').fill('新色')
  page.evaluate("document.querySelectorAll('button').forEach(b=>{if(b.textContent==='延迟刷新')b.click()})")
  page.wait_for_function('window.reviewMetrics.paletteReads === 2')
  page.get_by_role('button',name='OK',exact=True).click()
  expect(page.get_by_test_id('palette')).to_have_text('新色')
  page.evaluate('window.resolveOldPalette()')
  expect(page.get_by_test_id('palette')).to_have_text('新色')
  # Concurrent refreshes for two point files share one detail request.
  page.evaluate("document.querySelectorAll('.points-attachment-list .attachment-card button').forEach((b,i)=>{if(i<2)b.click()})")
  page.wait_for_function('window.reviewMetrics.points === 1')
  page.screenshot(path=str(out/f'review-{width}.png'),full_page=True)
  page.close()
 browser.close()
print('PASS lazy content, unsafe MIME, palette race, point refresh dedup at 1440/390')
