# UTF-8. Isolated HTTP fixtures in real Chromium; no business API mutations.
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

out=Path('D:/ZSJ-OS-backups/source-review-browser');out.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    page=browser.new_page();errors=[]
    page.on('pageerror',lambda error:errors.append(str(error)))
    for width in [1600,390]:
        page.set_viewport_size({'width':width,'height':1000})
        page.goto('http://127.0.0.1:5174/test/conversion-details.html?mode=review')
        panel=page.locator('.performance-panel').filter(has=page.get_by_text('多周期成交率',exact=True))
        expect(panel.get_by_text('来源待核实，暂无法计算（3条客资）',exact=True)).to_be_visible()
        expect(page.locator('.performance-panel').filter(has=page.get_by_text('多周期业绩',exact=True)).get_by_text('1 笔订单',exact=True)).to_be_visible()
        panel.locator('button').click()
        dialog=page.get_by_role('dialog')
        expect(dialog.get_by_text('该范围的来源记录需核实，暂不展示成交率明细。订单金额统计不受影响。',exact=True)).to_be_visible()
        expect(dialog.locator('tbody tr')).to_have_count(0)
        expect(dialog.get_by_text('50.00%',exact=False)).to_have_count(0)
        page.wait_for_timeout(500)
        page.screenshot(path=str(out/f'source-review-{width}.png'),full_page=True)
        page.goto('http://127.0.0.1:5174/test/conversion-details.html?mode=detail-review')
        page.locator('.performance-panel').filter(has=page.get_by_text('多周期成交率',exact=True)).locator('button').click()
        expect(page.get_by_role('dialog').get_by_text('该范围的来源记录需核实，暂不展示成交率明细。订单金额统计不受影响。',exact=True)).to_be_visible()
        expect(page.get_by_role('dialog').get_by_text('50.00%',exact=False)).to_have_count(0)
    assert not errors,errors
    browser.close()
print('PASS source review and stale-card protection at desktop/mobile widths')
