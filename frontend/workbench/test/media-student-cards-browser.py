# -*- coding: utf-8 -*-
"""Real page and Chrome, synthetic transport; never writes to a live API."""
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

base = os.environ.get('ZSJOS_UI_TEST_URL', 'http://127.0.0.1:5197')
out = Path(gettempdir()) / 'zsjos-media-student-cards'
out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(base + '/test/media-student-cards.html')
    cards = page.locator('.media-students-account-card')
    expect(cards).to_have_count(20)
    expect(cards.first.locator('.media-students-operator')).to_have_count(2)
    expect(cards.first.locator('.media-students-account-row')).to_have_count(3)
    expect(cards.first.locator('a')).to_have_count(4)
    expect(cards.first.locator('a.media-students-external-link')).to_have_count(1)
    expect(cards.first.locator('.media-students-platform-icon svg')).to_have_count(3)
    def check_alignment():
        page.wait_for_function("getComputedStyle(document.querySelector('.media-students-account-card')).backgroundColor!==getComputedStyle(document.querySelector('.media-students-list-pane')).backgroundColor")
        geometry = cards.first.evaluate('''card => {
          const tiles = card.querySelectorAll('.media-students-account-row'), link = tiles[0].querySelector('.media-students-account-link');
          const operators = card.querySelector('.media-students-card-operators'), name = card.querySelector('.media-students-item-heading strong');
          const grid = card.querySelector('.media-students-card-accounts');
          return { width: grid.clientWidth, columns: getComputedStyle(grid).gridTemplateColumns.split(' ').length,
            tileWidth: tiles[0].getBoundingClientRect().width, tileTop: tiles[0].getBoundingClientRect().top,
            secondTop: tiles[1].getBoundingClientRect().top, operatorsTop: operators.getBoundingClientRect().top,
            nameTop: name.getBoundingClientRect().top, operatorsLeft: operators.getBoundingClientRect().left,
            nameRight: name.getBoundingClientRect().right, color: getComputedStyle(link).color,
            bodyColor: getComputedStyle(card).color, operatorColor: getComputedStyle(operators.querySelector('button')).color,
            background: getComputedStyle(card).backgroundColor,
            expectedBackground: getComputedStyle(card).getPropertyValue('--crm-bg-container').trim() };
        }''')
        assert cards.first.locator('.media-students-account-link').first.evaluate("e=>getComputedStyle(e).textAlign==='right' && getComputedStyle(e).borderTopStyle==='solid'")
        expect(cards.first.locator('.anticon-right,.media-students-external-action')).to_have_count(0)
        assert geometry['columns'] == 1, geometry
        assert geometry['secondTop'] > geometry['tileTop'], geometry
        assert geometry['operatorsLeft'] >= geometry['nameRight'], geometry
        assert abs(geometry['operatorsTop'] - geometry['nameTop']) < 5, geometry
        assert cards.first.locator('.media-students-platform-label').count() == 0
        assert cards.first.locator('.media-students-account-row').first.evaluate("e=>getComputedStyle(e).display==='grid'")
        assert geometry['color'] == geometry['operatorColor'] != geometry['bodyColor'], geometry
        assert cards.first.evaluate("e=>getComputedStyle(e).backgroundColor!==getComputedStyle(document.querySelector('.media-students-list-pane')).backgroundColor")
    check_alignment()
    original_radius = cards.first.locator('.media-students-account-link').first.evaluate("e=>getComputedStyle(e).borderRadius")
    cards.first.evaluate("e=>e.style.setProperty('--crm-radius-sm','11px')")
    assert cards.first.locator('.media-students-account-link').first.evaluate("e=>getComputedStyle(e).borderRadius") == '11px'
    assert cards.first.locator('.media-students-operator').first.evaluate("e=>getComputedStyle(e).borderRadius") == '11px'
    cards.first.evaluate("e=>e.style.removeProperty('--crm-radius-sm')")
    assert cards.first.locator('.media-students-account-link').first.evaluate("e=>getComputedStyle(e).borderRadius") == original_radius
    cards.nth(1).locator('.media-students-card-select').click()
    expect(cards.nth(1)).to_have_class(re.compile(r'\bactive\b'))
    expect(cards.first).not_to_have_class(re.compile(r'\bactive\b'))
    cards.first.locator('.media-students-card-select').click()
    expect(cards.first).to_have_class(re.compile(r'\bactive\b'))
    cards.first.locator('.media-students-account-link').first.hover()
    check_alignment()
    page.mouse.move(700, 20)
    expect(page.get_by_role('tab', name='概览', exact=True)).to_have_attribute('aria-selected', 'true')
    before = page.evaluate('mediaCardFixture.detailRequests.length')
    cards.first.locator('.media-students-account-row').nth(1).locator('.media-students-platform-icon').click()
    cards.first.locator('.media-students-account-row').nth(2).locator('.media-students-platform-icon').click()
    assert page.evaluate('mediaCardFixture.detailRequests.length') == before
    page.context.route('https://www.douyin.com/**', lambda route: route.fulfill(body='fixture homepage'))
    with page.expect_popup() as popup:
        cards.first.locator('a.media-students-external-link').click()
    expect(popup.value).to_have_url('https://www.douyin.com/user/example')
    popup.value.close()
    assert page.evaluate('mediaCardFixture.detailRequests.length') == before
    # Restored account-name links open the local tab, even when no external URL is saved.
    page.evaluate('mediaCardFixture.allowNavigation=false')
    cards.first.get_by_role('link', name='查看本站账号：小红书未填写主页', exact=True).click()
    expect(page.get_by_role('tab', name='概览', exact=True)).to_have_attribute('aria-selected', 'true')
    page.evaluate('mediaCardFixture.allowNavigation=true')
    cards.first.get_by_role('link', name='查看本站账号：小红书未填写主页', exact=True).click()
    expect(page.get_by_role('tab', name='小红书未填写主页', exact=True)).to_have_attribute('aria-selected', 'true')
    expect(cards.first.get_by_role('link', name='查看本站账号：无效链接账号', exact=True)).to_have_attribute('href', '/zsjos/media-students?personId=1&accountId=13')
    page.get_by_role('tab', name='概览', exact=True).click()
    page.screenshot(path=str(out / 'desktop.png'), full_page=True, animations='disabled')

    # Navigation guards apply before committing the operator condition.
    page.evaluate('mediaCardFixture.allowNavigation=false')
    before = page.evaluate('mediaCardFixture.queries.length')
    cards.first.get_by_role('button', name='筛选运营：运营乙', exact=True).click()
    expect(page.locator('.media-students-operator-filter')).to_have_count(0)
    assert page.evaluate('mediaCardFixture.queries.length') == before
    page.evaluate('mediaCardFixture.allowNavigation=true')
    cards.first.get_by_role('button', name='筛选运营：运营乙', exact=True).click()
    expect(cards).to_have_count(1)
    expect(page.locator('.media-students-operator-filter')).to_contain_text('当前运营：运营乙')
    assert page.evaluate('mediaCardFixture.queries.at(-1).operatorUserId') == 21
    assert page.evaluate('mediaCardFixture.queries.at(-1).inServicePeriod') is True
    expect(page.get_by_role('tab', name='概览', exact=True)).to_have_attribute('aria-selected', 'true')
    cards.first.get_by_role('button', name='筛选运营：运营甲', exact=True).click()
    expect(cards).to_have_count(20)
    expect(page.locator('.media-students-operator-filter')).to_contain_text('当前运营：运营甲')
    page.locator('.media-students-scroll').evaluate('(e)=>e.scrollTop=e.scrollHeight')
    expect(cards).to_have_count(40)
    assert page.evaluate('mediaCardFixture.queries.at(-1).operatorUserId') == 20
    assert page.evaluate('mediaCardFixture.queries.at(-1).pageNo') == 2
    page.locator('.media-students-operator-filter .ant-tag-close-icon').click()
    expect(cards).to_have_count(20)
    expect(page.locator('.media-students-operator-filter')).to_have_count(0)
    assert page.evaluate('mediaCardFixture.queries.at(-1).operatorUserId===undefined')

    # Keep the keyword when filtering and clearing, including an empty intersection.
    search = page.get_by_placeholder('搜索姓名或手机号')
    search.fill('筛选学员3'); search.press('Enter')
    expect(cards).to_have_count(11)
    cards.first.get_by_role('button', name='筛选运营：运营甲', exact=True).click()
    expect(page.locator('.media-students-operator-filter')).to_be_visible()
    expect(cards).to_have_count(11)
    assert page.evaluate('mediaCardFixture.queries.at(-1).keyword') == '筛选学员3'
    page.get_by_role('tab', name='非服务期', exact=True).click()
    expect(cards).to_have_count(0)
    expect(page.get_by_text('暂无可见学员', exact=True)).to_be_visible()
    page.locator('.media-students-operator-filter .ant-tag-close-icon').click()
    expect(search).to_have_value('筛选学员3')
    expect(cards).to_have_count(0)

    # The operator condition is an intersection with advanced filters, not a replacement.
    page.reload(); expect(cards).to_have_count(20)
    page.get_by_role('button', name='高级筛选', exact=True).click()
    page.locator('.advanced-filter-template-select').click()
    page.locator('.ant-select-item-option').filter(has_text='个人 · 甲的抖音').click()
    page.get_by_role('button', name='应用筛选', exact=True).click()
    expect(cards).to_have_count(20)
    original = page.evaluate('mediaCardFixture.queries.at(-1).advancedFilter')
    assert original['conditions']
    cards.first.get_by_role('button', name='筛选运营：运营乙', exact=True).click()
    expect(cards).to_have_count(1)
    assert page.evaluate('mediaCardFixture.queries.at(-1).advancedFilter') == original
    page.locator('.media-students-operator-filter .ant-tag-close-icon').click()
    expect(cards).to_have_count(20)
    assert page.evaluate('mediaCardFixture.queries.at(-1).advancedFilter') == original

    # Reload gives a fresh page; test pending response invalidation and retry.
    page.reload(); expect(cards).to_have_count(20)
    page.evaluate('mediaCardFixture.nextDelay=1000')
    cards.first.get_by_role('button', name='筛选运营：运营乙', exact=True).click()
    expect(page.locator('.media-students-operator-filter')).to_be_visible()
    page.locator('.media-students-operator-filter .ant-tag-close-icon').click()
    expect(cards).to_have_count(20)
    page.wait_for_timeout(1200)
    expect(cards).to_have_count(20)
    page.evaluate("mediaCardFixture.failure='list'")
    cards.first.get_by_role('button', name='筛选运营：运营乙', exact=True).click()
    expect(page.get_by_role('alert')).to_contain_text('筛选查询失败')
    page.evaluate("mediaCardFixture.failure=''")
    page.get_by_role('button', name='重试加载学员', exact=True).click()
    expect(cards).to_have_count(1)
    assert page.evaluate('mediaCardFixture.queries.at(-1).operatorUserId') == 21

    # Collapsed and mobile preserve the condition, with no horizontal page overflow.
    page.get_by_role('button', name='收起学员列表', exact=True).click()
    expect(page.locator('.media-students-operator-filter .ant-tag')).to_have_attribute('title', '当前运营：运营乙')
    assert page.locator('.media-students-list-pane').bounding_box()['width'] == 72
    expect(page.locator('.media-students-card-accounts')).to_have_count(0)
    page.screenshot(path=str(out / 'collapsed.png'), full_page=True, animations='disabled')
    page.set_viewport_size({'width': 390, 'height': 844})
    assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
    page.screenshot(path=str(out / 'mobile-collapsed.png'), full_page=True, animations='disabled')
    page.get_by_role('button', name='展开学员列表', exact=True).click()
    expect(cards.first.locator('a')).to_have_count(4)
    check_alignment()
    assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
    page.screenshot(path=str(out / 'mobile.png'), full_page=True, animations='disabled')

    denied = browser.new_page(viewport={'width': 1440, 'height': 1000})
    denied.goto(base + '/test/media-student-cards.html?denied')
    expect(denied.locator('.media-students-account-card')).to_have_count(20)
    expect(denied.locator('.media-students-account-card a')).to_have_count(0)
    # Isolated fixture variations: one account spans both columns, no operator, long labels, narrow fallback.
    states = browser.new_page(viewport={'width': 1440, 'height': 1000})
    states.on('pageerror', lambda e: errors.append(str(e)))
    states.goto(base + '/test/media-student-cards.html')
    state_card = states.locator('.media-students-account-card').first
    expect(states.locator('.media-students-account-card')).to_have_count(20)
    states.evaluate("""() => { const s=mediaCardStudents[0];s.accounts=s.accounts.slice(0,1);s.name='测试长姓名用于检查顶部身份信息是否正常换行';s.services=[]; }""")
    states.get_by_role('button', name='刷新学员', exact=True).click()
    expect(state_card.locator('.media-students-account-row')).to_have_count(1)
    expect(state_card.locator('.media-students-operator-unassigned')).to_contain_text('未指派')
    assert state_card.evaluate("c=>Math.abs(c.querySelector('.media-students-card-accounts').clientWidth-c.querySelector('.media-students-account-row').getBoundingClientRect().width)<1")
    states.evaluate("""() => { mediaCardStudents[0].services=[{serviceRelationId:101,status:'active',operatorUserId:20,operatorUserName:'第一位姓名比较长的运营'},{serviceRelationId:102,status:'completed',operatorUserId:21,operatorUserName:'另一位运营'}]; }""")
    states.get_by_role('button', name='刷新学员', exact=True).click()
    expect(state_card.locator('.media-students-operator')).to_have_count(2)
    states.set_viewport_size({'width': 320, 'height': 844})
    states.wait_for_function("getComputedStyle(document.querySelector('.media-students-card-accounts')).gridTemplateColumns.split(' ').length===1")
    assert state_card.evaluate("c=>Math.abs(c.querySelector('.media-students-card-operators').getBoundingClientRect().top-c.querySelector('.media-students-card-select').getBoundingClientRect().top)<2")
    assert state_card.evaluate("c=>[c,...c.querySelectorAll('*')].filter(e=>!e.closest('.anticon')).every(e=>e.scrollWidth<=e.clientWidth+1)")
    assert states.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
    states.screenshot(path=str(out / 'tiles-long-narrow.png'), full_page=True, animations='disabled')
    touch = browser.new_page(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
    touch.goto(base + '/test/media-student-cards.html')
    expect(touch.locator('.media-students-account-card')).to_have_count(20)
    assert touch.locator('.media-students-external-link').first.bounding_box()['width'] >= 44
    expect(touch.locator('.media-students-external-action')).to_have_count(0)
    assert touch.locator('.media-students-operator').first.bounding_box()['height'] >= 44
    assert touch.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
    touch.screenshot(path=str(out / 'tiles-touch.png'), full_page=True, animations='disabled')
    assert not errors, errors
    browser.close()
print('PASS: external links, event isolation, operators, filters, pagination, guard, stale response, retry, desktop/rail/mobile and permission states')
