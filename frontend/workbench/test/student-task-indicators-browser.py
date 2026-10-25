# -*- coding: utf-8 -*-
from pathlib import Path
import re
from playwright.sync_api import sync_playwright, expect
OUT=Path('D:/ZSJ-OS-backups/student-task-indicators');OUT.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    for width,layout in [(1440,'inbox'),(1440,'table'),(390,'inbox')]:
        page=browser.new_page(viewport={'width':width,'height':1000});errors=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto(f'http://127.0.0.1:5174/test/student-service-sidebar.html?tasks=1&layout={layout}')
        marker=page.locator('.lead-task-indicator')
        expect(marker).to_have_count(1)
        if layout=='table':
            rows=page.locator('tr.ant-table-row');expect(rows).to_have_count(2)
            expect(rows.first).to_contain_text('课程2')
            expect(rows.nth(1).locator('.lead-task-indicator')).to_have_count(0)
            marker.focus();tip=page.get_by_role('tooltip').last
            expect(tip).to_contain_text('学员跟进');expect(tip).to_contain_text('首联协助');expect(tip).to_contain_text('截止')
        else:
            rows=page.locator('#student-avatar-list .lead-inbox-item');expect(rows).to_have_count(2)
            expect(rows.first).to_contain_text('课程2')
            expect(rows.first).to_contain_text('已逾期：学员跟进')
            expect(rows.nth(1).locator('.lead-task-border')).to_have_count(0)
            rows.nth(1).click()
            if width<768:
                page.get_by_role('button',name='关闭',exact=True).click()
                page.wait_for_timeout(350)
            expect(page.locator('.has-pending-tasks')).to_have_count(1)
            border=page.locator('.lead-task-border')
            assert border.evaluate("e=>getComputedStyle(e,'::before').animationName")=='lead-task-border-flow'
            page.emulate_media(reduced_motion='reduce');expect(border).to_be_hidden()
            page.emulate_media(reduced_motion='no-preference');expect(border).to_be_visible()
            page.get_by_role('button',name='收起学员列表').focus();page.keyboard.press('Enter')
            expect(border).to_be_visible()
            expect(rows.first).to_have_attribute('aria-label',re.compile('.*首联协助.*'))
            page.get_by_role('button',name='展开学员列表').focus();page.keyboard.press('Enter')
        page.wait_for_timeout(350)
        page.screenshot(path=str(OUT/f'{layout}-{width}.png'),full_page=True)
        page.evaluate('sidebarFixture.completed=true;sidebarFixture.invalidate()')
        expect(marker).to_have_count(0);expect(page.locator('.has-pending-tasks')).to_have_count(0)
        expect(rows.first).to_contain_text('课程1')
        page.evaluate('sidebarFixture.completed=false;sidebarFixture.invalidate()')
        expect(marker).to_have_count(1);expect(rows.first).to_contain_text('课程2')
        assert not errors,errors
        print(f'PASS {layout} {width}: service-specific personal tasks, priority, multi-task reasons, completion, continuous/collapsed/reduced motion')
        page.close()
    browser.close()
