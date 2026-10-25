# -*- coding: utf-8 -*-
"""Real Chrome checks of the migrated UI with synthetic HTTP; no live business requests."""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

OUT = Path(gettempdir()) / 'zsjkqt-browser'
OUT.mkdir(exist_ok=True)
BASE = os.environ.get('ZSJKQT_FIXTURE_URL', 'http://127.0.0.1:5174/test/zsjkqt.html')
WORKBOOK = {'name': 'synthetic.xlsx', 'mimeType': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'buffer': b'synthetic workbook bytes; parsed by isolated adapter'}

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 1000}, accept_downloads=True)
    page.set_default_timeout(10000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def fresh(suffix=''):
        page.goto(BASE + suffix)
        page.evaluate("sessionStorage.removeItem('zsjkqt_browser_fixture')")
        page.reload()
        page.wait_for_function('window.zsjkqtFixture !== undefined')

    def wait_saved():
        expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('已保存')

    def new():
        page.get_by_role('button', name=re.compile('新建考勤$')).click()
        expect(page.get_by_role('heading', name='选择企业微信月报', exact=True)).to_be_visible()

    def import_source():
        page.locator('input[type=file]').first.set_input_files(WORKBOOK)

    def close():
        page.get_by_role('button', name='返回草稿箱', exact=True).click()
        expect(page.get_by_role('heading', name='中世健考勤通 · 草稿箱', exact=True)).to_be_visible()

    def open_name(name):
        page.get_by_role('row').filter(has=page.get_by_text(name, exact=True)).get_by_role('button', name='打开', exact=True).click()

    def modal_ok(name='确定'):
        page.get_by_role('dialog').get_by_role('button', name=re.compile('^' + r'\s*'.join(re.escape(c) for c in name) + '$')).click()

    try:
        fresh('?denied=1')
        expect(page.get_by_text('没有中世健考勤通访问权限', exact=True)).to_be_visible()
        assert page.evaluate('zsjkqtFixture.requests.length') == 0
        fresh('?api-denied=1')
        expect(page.get_by_text('没有中世健考勤通访问权限', exact=True).first).to_be_visible()
        fresh()
        expect(page.get_by_text('暂无考勤草稿，点击“新建考勤”开始', exact=True)).to_be_visible()
        sentinel = page.locator('#zsjkqt-isolation-sentinel button').evaluate('(e)=>({background:getComputedStyle(e).backgroundColor,font:getComputedStyle(e).fontFamily})')
        page.evaluate("zsjkqtFixture.failOperation='config'")
        page.get_by_role('button', name=re.compile('新建考勤$')).click()
        expect(page.get_by_text('合成网络失败，请重试', exact=True).first).to_be_visible()
        new()
        page.evaluate("zsjkqtFixture.failOperation='parse'")
        import_source()
        expect(page.get_by_text('读取月报失败：合成网络失败，请重试', exact=True)).to_be_visible()
        import_source()
        expect(page.get_by_role('heading', name='2026年6月参数确认', exact=True)).to_be_visible()
        page.get_by_label('应出勤日', exact=True).fill('21')
        page.get_by_label('计薪日', exact=True).fill('23')
        page.get_by_role('button', name='普通休息日', exact=True).click()
        page.locator('.day-toggle').nth(6).click()
        page.get_by_role('button', name='进入员工确认', exact=True).click()
        expect(page.get_by_placeholder('搜索姓名或部门')).to_be_visible()
        page.locator('.employee-card select').nth(1).select_option('事假')
        page.get_by_label('实出勤日', exact=True).fill('99')
        page.get_by_label('备注', exact=True).fill('合成人工覆盖，保留原值')
        page.evaluate("zsjkqtFixture.delayOperation='save';zsjkqtFixture.delayMs=900")
        page.get_by_role('button', name='保存并下一个', exact=True).click()
        expect(page.locator('.employee-card select').first).to_be_disabled()
        expect(page.get_by_role('button', name='返回草稿箱', exact=True)).to_be_disabled()
        expect(page.get_by_role('button', name='另存副本', exact=True)).to_be_disabled()
        expect(page.locator('.employee-card input:not([type])').first).to_have_value('合成员工乙')
        wait_saved()
        assert page.evaluate('zsjkqtFixture.drafts[0].snapshot.project.employees[0].summary.actualAttendanceDays') == 99
        assert page.evaluate('zsjkqtFixture.drafts[0].snapshot.project.employees[0].manualSummaryOverrides.actualAttendanceDays') is True
        assert page.evaluate('zsjkqtFixture.drafts[0].snapshot.project.employees[0].confirmed') is True
        assert page.evaluate('Object.keys(zsjkqtFixture.profiles).length') == 1
        page.get_by_role('button', name='上一位', exact=True).click()
        expect(page.get_by_label('实出勤日', exact=True)).to_have_value('99')
        expect(page.locator('.employee-card select').nth(1)).to_have_value('事假')
        page.locator('.detail-button').first.hover()
        expect(page.get_by_role('tooltip').first).to_be_visible()
        assert page.locator('#zsjkqt-isolation-sentinel button').evaluate('(e)=>({background:getComputedStyle(e).backgroundColor,font:getComputedStyle(e).fontFamily})') == sentinel
        page.screenshot(path=str(OUT / 'review-1600.png'), full_page=True)
        close()
        expect(page.get_by_text('1/2', exact=True)).to_be_visible()
        open_name('2026年6月考勤')
        expect(page.get_by_label('实出勤日', exact=True)).to_have_value('99')
        expect(page.get_by_label('备注', exact=True)).to_have_value('合成人工覆盖，保留原值')

        page.evaluate("zsjkqtFixture.failOperation='save'")
        page.get_by_label('实出勤日', exact=True).fill('98')
        expect(page.get_by_text('保存失败，修改已保留', exact=True)).to_be_visible()
        expect(page.get_by_label('实出勤日', exact=True)).to_have_value('98')
        page.get_by_role('button', name='保存草稿／重试', exact=True).click()
        wait_saved()
        assert page.evaluate('zsjkqtFixture.drafts[0].snapshot.project.employees[0].summary.actualAttendanceDays') == 98

        page.evaluate('zsjkqtFixture.bumpVersion(zsjkqtFixture.drafts[0].id)')
        page.get_by_label('实出勤日', exact=True).fill('97')
        expect(page.get_by_text('草稿已被其他人修改，请重新加载或另存副本', exact=True)).to_be_visible()
        expect(page.get_by_label('实出勤日', exact=True)).to_have_value('97')
        page.get_by_role('button', name='重新加载最新草稿', exact=True).click()
        modal_ok()
        expect(page.get_by_label('实出勤日', exact=True)).to_have_value('98')
        page.evaluate('zsjkqtFixture.bumpVersion(zsjkqtFixture.drafts[0].id)')
        page.get_by_label('实出勤日', exact=True).fill('97')
        expect(page.get_by_text('草稿已被其他人修改，请重新加载或另存副本', exact=True)).to_be_visible()
        page.get_by_role('button', name='另存副本', exact=True).click()
        expect(page.get_by_text('副本已保存', exact=True)).to_be_visible()
        assert page.evaluate('zsjkqtFixture.drafts.length') == 2
        assert page.evaluate('zsjkqtFixture.drafts.at(-1).snapshot.project.employees[0].summary.actualAttendanceDays') == 97

        with page.expect_download() as download:
            page.get_by_role('button', name='导出考勤汇总表', exact=True).click()
        assert download.value.suggested_filename.endswith('考勤汇总表.xlsx')
        with page.expect_download() as download:
            page.get_by_role('button', name='导出异常清单', exact=True).click()
        assert download.value.suggested_filename.endswith('异常清单.xlsx')
        assert page.evaluate('zsjkqtFixture.exports.length') == 2
        assert page.evaluate('zsjkqtFixture.maxWrites') == 1
        close()

        new()
        page.locator('input[type=file]').nth(1).set_input_files({**WORKBOOK, 'name': 'exported.xlsx'})
        expect(page.get_by_role('heading', name='2026年6月续改参数确认', exact=True)).to_be_visible()
        expect(page.get_by_text(re.compile('隐藏元数据'))).to_be_visible()
        wait_saved()
        close()
        open_name('2026年6月考勤')
        expect(page.get_by_text(re.compile('隐藏元数据'))).to_be_visible()
        page.get_by_role('button', name='进入员工确认', exact=True).click()
        expect(page.get_by_label('实出勤日', exact=True)).to_have_value('97')
        wait_saved()
        close()
        assert page.evaluate('zsjkqtFixture.drafts.length') == 3

        new()
        import_source()
        expect(page.get_by_role('dialog').get_by_text('发现相同源文件的草稿', exact=True).last).to_be_visible()
        modal_ok('打开已有草稿')
        expect(page.get_by_placeholder('搜索姓名或部门')).to_be_visible()
        close()

        new()
        import_source()
        page.get_by_role('dialog').get_by_role('button', name='新建副本', exact=True).click()
        expect(page.get_by_role('heading', name='2026年6月参数确认', exact=True)).to_be_visible()
        wait_saved()
        close()
        assert page.evaluate('zsjkqtFixture.drafts.length') == 4

        new()
        page.get_by_role('button', name='部门时段设置', exact=True).click()
        expect(page.get_by_role('heading', name='部门时段设置', exact=True)).to_be_visible()
        page.locator('input[type=time]').first.fill('08:45')
        page.get_by_role('button', name='返回草稿箱', exact=True).click()
        expect(page.get_by_role('dialog').get_by_text('部门时段尚未保存', exact=True).last).to_be_visible()
        modal_ok('取消')
        expect(page.locator('input[type=time]').first).to_have_value('08:45')
        page.get_by_role('button', name='保存设置', exact=True).click()
        expect(page.get_by_text('部门时段设置已保存。', exact=True)).to_be_visible()
        assert page.evaluate('zsjkqtFixture.config.value.default.morning.start') == '08:45'
        close()
        page.get_by_placeholder('搜索草稿名称').fill('副本')
        expect(page.get_by_role('button', name='打开', exact=True)).to_have_count(1)
        page.once('dialog', lambda dialog: dialog.accept('合成重命名草稿'))
        page.get_by_role('button', name='重命名', exact=True).click()
        page.get_by_placeholder('搜索草稿名称').fill('合成重命名')
        expect(page.get_by_text('合成重命名草稿', exact=True)).to_be_visible()
        page.get_by_role('button', name='删除', exact=True).click()
        modal_ok()
        expect(page.get_by_text('暂无考勤草稿，点击“新建考勤”开始', exact=True)).to_be_visible()
        expect(page.get_by_role('dialog')).to_have_count(0)
        assert page.evaluate('zsjkqtFixture.drafts.length') == 3
        page.get_by_placeholder('搜索草稿名称').fill('')
        expect(page.get_by_role('button', name='打开', exact=True)).to_have_count(3)
        page.screenshot(path=str(OUT / 'drafts-1600.png'), full_page=True)

        # Reopen a saved draft at mobile width: preserve desktop editor with contained horizontal scroll.
        # The newest duplicate is still on parameter setup; open the imported report draft.
        page.get_by_role('row').filter(has=page.get_by_text('合成续改.xlsx', exact=True)).get_by_role('button', name='打开', exact=True).click()
        expect(page.get_by_placeholder('搜索姓名或部门')).to_be_visible()
        for width in [1440, 390]:
            page.set_viewport_size({'width': width, 'height': 1000})
            page.wait_for_timeout(150)
            metrics = page.evaluate("({body:document.documentElement.scrollWidth,viewport:innerWidth,inner:document.querySelector('.zsjkqt-viewport').clientWidth,scroll:document.querySelector('.zsjkqt-viewport').scrollWidth})")
            assert metrics['body'] <= width + 1, metrics
            if width == 390:
                assert metrics['scroll'] >= 1160 and metrics['inner'] <= width, metrics
                page.locator('.zsjkqt-viewport').evaluate('(e)=>e.scrollLeft=e.scrollWidth')
                expect(page.get_by_role('button', name='导出异常清单', exact=True)).to_be_visible()
            page.screenshot(path=str(OUT / f'review-{width}.png'), full_page=True)

        # Parameter-stage drafts freeze the personnel inputs used for their later first calculation.
        page.set_viewport_size({'width': 1600, 'height': 1000})
        fresh()
        page.evaluate("zsjkqtFixture.profiles['synthetic-employee-0']={name:'合成旧资料姓名',department:'合成旧资料部门',entry_date:'2026-01-01',enabled:true};zsjkqtFixture.persist()")
        new()
        import_source()
        expect(page.get_by_role('heading', name='2026年6月参数确认', exact=True)).to_be_visible()
        page.get_by_label('应出勤日', exact=True).fill('21')
        wait_saved()
        assert page.evaluate("zsjkqtFixture.drafts[0].snapshot.personnelProfiles['synthetic-employee-0'].name") == '合成旧资料姓名'
        close()
        page.evaluate("zsjkqtFixture.profiles['synthetic-employee-0']={name:'合成新资料姓名',department:'合成新资料部门',entry_date:'2026-02-02',enabled:true};zsjkqtFixture.persist()")
        open_name('2026年6月考勤')
        expect(page.get_by_role('heading', name='2026年6月参数确认', exact=True)).to_be_visible()
        expect(page.get_by_text('已恢复草稿，可继续核对和修改。', exact=True)).to_be_visible()
        page.get_by_role('button', name='进入员工确认', exact=True).click()
        expect(page.locator('.employee-card input:not([type])').first).to_have_value('合成旧资料姓名')
        expect(page.locator('.employee-card input:not([type])').nth(1)).to_have_value('合成旧资料部门')
        expect(page.get_by_label('入职日期', exact=True)).to_have_value('2026-01-01')
        close()
        new()
        page.locator('input[type=file]').first.set_input_files({**WORKBOOK, 'name': 'different-profiles.xlsx'})
        expect(page.get_by_role('heading', name='2026年6月参数确认', exact=True)).to_be_visible()
        page.get_by_role('button', name='进入员工确认', exact=True).click()
        expect(page.locator('.employee-card input:not([type])').first).to_have_value('合成新资料姓名')
        expect(page.locator('.employee-card input:not([type])').nth(1)).to_have_value('合成新资料部门')
        expect(page.get_by_label('入职日期', exact=True)).to_have_value('2026-02-02')
        wait_saved()
        assert not errors, errors
        print('PASS browser: import/params/manual override/save-next/busy guard/autosave/reopen/retry/conflict/reload/copy/export/reimport-metadata-reopen/duplicate-open-and-copy/config-dirty-guard/rename/delete/denied/CSS isolation/1600-1440-390/frozen-personnel-restore/new-import-latest-personnel')
        print('Evidence:', OUT)
    except Exception:
        page.screenshot(path=str(OUT / 'failure.png'), full_page=True)
        print('Browser errors:', json.dumps(errors, ensure_ascii=True))
        raise
    finally:
        browser.close()
