# UTF-8. Real Chromium with isolated synthetic API data; no shared data mutation.
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'media-lead-browser'
OUT.mkdir(parents=True, exist_ok=True)
errors = []
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto('http://127.0.0.1:5191/test/media-lead.html')
    expect(page.get_by_text('新媒体客资分析大盘', exact=True)).to_be_visible()
    expect(page.locator('.performance-tree .ant-tree')).to_be_visible()
    expect(page.locator('.performance-tree .ant-tree-title')).to_have_count(3)
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeType') == 'CENTER'
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeId') == 20
    page.locator('.performance-tree .ant-tree-node-content-wrapper').filter(has_text='新媒体一部').click()
    expect(page.locator('.performance-tree .ant-tree-node-selected')).to_contain_text('新媒体一部')
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeType') == 'DEPT'
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeId') == 10
    page.locator('.performance-tree input[aria-label="搜索组织或人员"]').fill('测试运营')
    expect(page.locator('.performance-tree .ant-tree-title')).to_have_count(3)
    page.locator('.performance-tree .ant-tree-node-content-wrapper').filter(has_text='测试运营').click()
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeType') == 'USER'
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeId') == 1
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.start') == '2026-09-01'
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.end') == '2026-09-30'
    page.get_by_role('button', name='收起组织树').click()
    expect(page.locator('.performance-tree .ant-tree')).not_to_be_visible()
    page.get_by_role('button', name='展开组织树').click()
    expect(page.locator('.performance-tree .ant-tree')).to_be_visible()
    expect(page.locator('div[role=img][aria-label*=有效客资]')).to_have_count(4)
    for period in ['昨日', '今日', '上周', '本周', '上上月', '上月', '本月', '本年', '全部']:
        expect(page.get_by_role('cell', name=period, exact=True)).to_be_visible()
    for heading in ['昨日', '今日', '本周', '上周', '本月', '上月']:
        expect(page.get_by_role('columnheader', name=heading, exact=True)).to_be_visible()
    member = page.get_by_role('row').filter(has=page.get_by_role('cell', name='测试运营', exact=True)).first
    week_tags = member.locator('td').nth(4).locator('.media-lead-metric-tags .ant-tag')
    expect(week_tags).to_have_text(['提交 5', '有效 4', '成交 2'])
    colors = week_tags.evaluate_all('tags => tags.map(tag => getComputedStyle(tag).backgroundColor)')
    assert len(set(colors)) == 3, colors
    main_text = page.locator('.performance-main').inner_text()
    assert '/' not in main_text, [line for line in main_text.splitlines() if '/' in line]
    page.locator('.ant-card').filter(has=page.get_by_text('团队成员进度表', exact=True)).first.screenshot(path=str(OUT / 'dashboard-members-desktop.png'))
    expect(page.get_by_role('img', name='提交客资 20')).to_be_visible()
    expect(page.get_by_role('img', name='判有效客资 13')).to_be_visible()
    expect(page.get_by_role('img', name='成交客资 6')).to_be_visible()
    expect(page.get_by_role('img', name='提交3，有效2，无效0，待判或其他1')).to_be_visible()
    page.locator('button.performance-day').filter(has=page.get_by_role('img', name='提交3，有效2，无效0，待判或其他1')).click()
    expect(page.get_by_role('dialog').get_by_text('KZ202609280001')).to_be_visible()
    assert page.evaluate('window.mediaLeadDetailQueries.at(-1).params.start') == '2026-09-28'
    assert page.evaluate('window.mediaLeadDetailQueries.at(-1).params.end') == '2026-09-28'
    page.locator('.ant-modal-close').click()
    expect(page.locator('.ant-modal-wrap')).not_to_be_visible()
    month_picker = page.locator('input[aria-label="客资统计月份"]')
    month_picker.fill('2026-08')
    expect(page.get_by_role('img', name='提交客资 8')).to_be_visible()
    expect(page.get_by_role('img', name='判有效客资 5')).to_be_visible()
    expect(page.get_by_role('img', name='成交客资 2')).to_be_visible()
    expect(page.get_by_role('img', name='提交5，有效3，无效1，待判或其他1')).to_be_visible()
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.start') == '2026-08-01'
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.end') == '2026-08-31'
    page.locator('button.performance-day').filter(has=page.get_by_role('img', name='提交5，有效3，无效1，待判或其他1')).click()
    expect(page.get_by_role('dialog').get_by_text('KZ202608040001')).to_be_visible()
    assert page.evaluate('window.mediaLeadDetailQueries.at(-1).params.start') == '2026-08-04'
    page.locator('.ant-modal-close').click()
    expect(page.locator('.ant-modal-wrap')).not_to_be_visible()
    page.screenshot(path=str(OUT / 'dashboard-august-cohort.png'), full_page=True)
    month_picker.fill('2026-07')
    expect(page.get_by_text('所选月份暂无提交客资')).to_be_visible()
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.end') == '2026-07-31'
    month_picker.fill('2026-09')
    expect(page.get_by_role('img', name='提交客资 20')).to_be_visible()
    page.locator('[aria-label="查看本月客资明细"]').click()
    expect(page.get_by_role('dialog').get_by_text('KZ202609280001')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_text('2026-09-28 10:20:00')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_text('2026-09-28 12:00:00')).to_be_visible()
    assert '/' not in page.get_by_role('dialog').inner_text()
    missing_order = page.get_by_role('dialog').locator('tbody tr').filter(has_text='KZ202609280002')
    expect(missing_order.locator('td').last).to_have_text('—')
    page.wait_for_timeout(400)  # Capture the fully opened modal, not its entrance transition.
    page.screenshot(path=str(OUT / 'dashboard-details.png'))
    page.locator('.ant-modal-close').click()
    expect(page.locator('.ant-modal-wrap')).not_to_be_visible()
    page.evaluate('window.scrollTo(0, 0)')
    page.screenshot(path=str(OUT / 'dashboard-desktop.png'), full_page=True)
    page.set_viewport_size({'width': 390, 'height': 844})
    expect(page.locator('.performance-tree')).not_to_be_visible()
    page.locator('.performance-mobile-tree button').click()
    drawer = page.locator('.ant-drawer').filter(has=page.get_by_text('统计范围', exact=True))
    expect(drawer).to_be_visible()
    drawer.locator('input[aria-label="搜索组织或人员"]').fill('新媒体一部')
    page.wait_for_timeout(400)  # Capture the fully opened drawer, not its entrance transition.
    page.screenshot(path=str(OUT / 'dashboard-mobile-tree.png'))
    drawer.locator('.ant-tree-node-content-wrapper').filter(has_text='新媒体一部').click()
    expect(page.locator('.ant-drawer-open')).to_have_count(0)
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeType') == 'DEPT'
    assert page.evaluate('window.mediaLeadQueries.at(-1).params.scopeId') == 10
    expect(page.locator('.performance-mobile-tree button')).to_contain_text('新媒体一部')
    page.wait_for_timeout(400)  # Wait for the drawer exit transition before capturing layout.
    expect(page.locator('div[role=img][aria-label*=有效客资]')).to_have_count(4)
    expect(page.locator('.media-lead-metric-tags').first.locator('.ant-tag')).to_have_text(['提交 2', '成交 1'])
    assert '/' not in page.locator('.performance-main').inner_text()
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    mobile_member_card = page.locator('.ant-card').filter(has=page.get_by_text('团队成员进度表', exact=True)).first
    mobile_member_scroller = mobile_member_card.locator('.ant-table-body')
    mobile_member_scroller.evaluate('(element) => { element.scrollLeft = 650 }')
    assert mobile_member_scroller.evaluate('(element) => element.scrollLeft') > 0
    mobile_member_card.screenshot(path=str(OUT / 'dashboard-members-mobile-scrolled.png'))
    page.screenshot(path=str(OUT / 'dashboard-mobile.png'), full_page=True)
    page.goto('http://127.0.0.1:5191/test/media-lead.html?target=1')
    expect(page.get_by_text('客资引流人数指标设置', exact=True)).to_be_visible()
    expect(page.get_by_text('自动汇总', exact=True)).to_be_visible()
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    page.get_by_role('button', name='修订记录').nth(1).click()
    expect(page.get_by_role('dialog').get_by_text('团队目标调整')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_text('15 人')).to_be_visible()
    expect(page.get_by_role('dialog').get_by_text('20 人')).to_be_visible()
    page.screenshot(path=str(OUT / 'target-history-mobile.png'), full_page=True)
    page.locator('.ant-modal-close').click()
    expect(page.get_by_role('dialog')).not_to_be_visible()
    page.screenshot(path=str(OUT / 'targets-mobile.png'), full_page=True)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.screenshot(path=str(OUT / 'targets-desktop.png'), full_page=True)
    page.get_by_role('button', name='恢复自动汇总').click()
    page.get_by_placeholder('调整原因（必填）').fill('恢复团队自动汇总')
    page.get_by_role('button', name='保存 1 项').click()
    expect(page.get_by_text('指标已保存', exact=True)).to_be_visible()
    mutations = page.evaluate('window.mediaLeadMutations')
    assert mutations[-1]['payload'][0]['restoreAutomatic'] is True
    assert mutations[-1]['payload'][0]['scopeType'] == 'DEPT'
    center_picker = page.locator('.ant-select').filter(has_text='选择新媒体中心').locator('input[role=combobox]')
    center_picker.fill('新媒体中心')
    center_picker.press('Enter')
    page.get_by_role('button', name='保存中心').click()
    expect(page.get_by_text('新媒体中心已保存', exact=True)).to_be_visible()
    mutations = page.evaluate('window.mediaLeadMutations')
    assert mutations[-1]['payload'] == {'deptId': 20, 'centerId': 20, 'kind': 'CENTER', 'version': 0}
    expect(page.get_by_role('button', name='取消中心设置')).to_have_count(2)
    page.get_by_role('button', name='取消中心设置').nth(1).click()
    expect(page.get_by_text('取消“新媒体一部”的中心设置？')).to_be_visible()
    page.get_by_role('button', name='取消设置').click()
    expect(page.get_by_text('已取消中心设置')).to_be_visible()
    expect(page.get_by_role('button', name='取消中心设置')).to_have_count(1)
    mutations = page.evaluate('window.mediaLeadMutations')
    assert mutations[-1]['path'].endswith('/org/unset')
    assert mutations[-1]['payload'] == {'deptId': 10, 'version': 0}
    page.screenshot(path=str(OUT / 'center-corrected-desktop.png'), full_page=True)
    page.set_viewport_size({'width': 390, 'height': 844})
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    page.screenshot(path=str(OUT / 'center-corrected-mobile.png'), full_page=True)
    page.goto('http://127.0.0.1:5191/test/media-lead.html?legacy=1')
    expect(page.get_by_text('月度漏斗暂不可用')).to_be_visible()
    expect(page.get_by_role('img', name='提交3，有效2，无效0，待判或其他1')).to_be_visible()
    assert not errors, errors
    print('PASS real Chrome desktop/mobile: monthly cohort calendar, funnel, date drilldown, scope tree, targets and center correction; screenshots', OUT)
    browser.close()
