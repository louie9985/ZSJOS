from playwright.sync_api import sync_playwright
with sync_playwright() as p:
 b=p.chromium.launch(headless=True);page=b.new_page(timezone_id='America/Los_Angeles')
 page.goto('http://127.0.0.1:5189/test/home-metric.html?scenario=editor')
 page.get_by_role('button',name='编辑',exact=True).click()
 page.locator('.metric-operand').first.get_by_text('历史分类',exact=True).wait_for()
 assert page.locator('.metric-operand').first.get_by_text('已改名分类',exact=True).count()==0
 page.locator('.metric-operand').first.locator('.filter-condition__value .el-select').click()
 page.get_by_role('option',name='当前新分类',exact=True).click()
 page.keyboard.press('Escape')
 page.locator('.metric-operand').first.get_by_text('当前新分类',exact=True).wait_for()
 page.locator('.metric-operand').first.get_by_role('button',name='移除',exact=True).click()
 assert page.locator('.metric-operand').count()==1
 page.get_by_text('北京时间开始：2026-11-01 00:00:00').wait_for()
 row=page.locator('.metric-operand').first
 row.get_by_role('button',name='条件',exact=True).click()
 row.locator('.filter-condition__field').click()
 page.get_by_role('option',name='基本信息 · 订单姓名',exact=True).click()
 row.locator('.filter-condition__field').get_by_text('订单姓名',exact=False).wait_for()
 row.locator('.el-range-editor').hover()
 row.locator('.el-range__close-icon').click()
 assert page.get_by_text('北京时间开始：2026-11-01 00:00:00').count()==0
 page.get_by_role('button',name='保存草稿',exact=True).click()
 saved=page.evaluate('window.lastSaved')
 assert len(saved['spec']['operands'])==1
 assert saved['spec']['operands'][0]['calculation']['name']=='B'
 assert 'customRange' not in saved['spec']['operands'][0]['calculation']
 assert 'editorId' not in saved['spec']['operands'][0]
 page.reload()
 page.get_by_role('button',name='编辑',exact=True).click()
 page.locator('.metric-operand').first.get_by_role('button',name='移除',exact=True).click()
 row=page.locator('.metric-operand').first
 row.get_by_role('button',name='条件',exact=True).wait_for()
 page.wait_for_timeout(800)
 row.get_by_role('button',name='条件',exact=True).click()
 row.locator('.filter-condition__field').click()
 page.get_by_role('option',name='基本信息 · 订单姓名',exact=True).click()
 period=row.get_by_text('统计周期',exact=True).locator('..').locator('.el-select')
 period.click();page.get_by_role('option',name='不限时间',exact=True).click()
 period.click();page.get_by_role('option',name='自定义区间',exact=True).click()
 assert row.locator('.metric-range-summary').count()==0
 source=row.get_by_text('数据来源',exact=True).locator('..').locator('.el-select')
 source.click();page.get_by_role('option',name='学员',exact=True).click()
 assert row.locator('.metric-range-summary').count()==0
 period.click();page.get_by_role('option',name='自定义区间',exact=True).click()
 assert row.locator('.metric-range-summary').count()==0
 result=page.evaluate("""async()=>{const m=await import('/src/api/zsjos/metricTime.ts');return [m.metricPickerDate('2026-10-01T00:00:00.123'),m.metricPickerDate('2026-10-01T00:00:00.123-07:00')]}""")
 assert result==['2026-10-01T00:00:00.123','2026-10-01T15:00:00.123'],result
 page.goto('http://127.0.0.1:5189/test/home-metric.html?scenario=init')
 page.get_by_text('隔离测试指标',exact=True).wait_for()
 assert page.get_by_role('button',name='编辑',exact=True).is_disabled()
 assert page.get_by_role('button',name='新增指标',exact=True).is_disabled()
 page.evaluate('window.releaseInit()')
 page.get_by_role('button',name='编辑',exact=True).click()
 page.get_by_role('dialog',name='指标配置').wait_for()
 b.close();print('stable identity including delayed responses, snapshot label, date clear/switch/milliseconds passed')
