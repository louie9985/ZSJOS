# -*- coding: utf-8 -*-
"""Current attendance UI regressions with isolated synthetic HTTP and separate browser contexts."""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('ZSJKQT_FIXTURE_URL', 'http://127.0.0.1:5174/test/zsjkqt.html')
OUT = Path(gettempdir()) / 'zsjos-attendance-review-fixes-browser'
OUT.mkdir(exist_ok=True)
FILE = {'name': 'synthetic.xlsx', 'mimeType': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'buffer': b'isolated adapter fixture'}

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    try:
        for width in [1440, 390]:
            context = browser.new_context(viewport={'width': width, 'height': 1000}, accept_downloads=True)
            page = context.new_page()
            page.set_default_timeout(15000)
            errors = []
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.goto(BASE)
            page.wait_for_function('window.zsjkqtFixture !== undefined')
            page.evaluate("""() => {
              const c=zsjkqtFixture.config.value;
              c.default={afternoon:c.default.afternoon,morning:c.default.morning};
              zsjkqtFixture.persist();
              const employee=(department,name='同名人员')=>({name,account:'',rule:'',department,position:'',jobNo:'',summaries:{},dayDetails:{1:'正常'}});
              zsjkqtFixture.sourceOverride={year:2026,month:6,startDate:'2026-06-01',endDate:'2026-06-30',sourceFileName:'synthetic.xlsx',sourceFileHash:'review-fixes',employees:[employee('合成测试部'),employee('合成研发部'),employee('合成第三部','第三位')]};
            }""")
            page.get_by_role('button', name=re.compile('新建考勤$')).click()
            expect(page.get_by_role('heading', name='选择企业微信月报', exact=True)).to_be_visible()
            page.get_by_role('button', name='返回草稿箱', exact=True).click()
            expect(page.get_by_role('heading', name='中世健考勤通 · 草稿箱', exact=True)).to_be_visible()
            expect(page.get_by_role('dialog')).to_have_count(0)
            page.get_by_role('button', name=re.compile('新建考勤$')).click()
            page.locator('input[type=file]').first.set_input_files(FILE)
            page.get_by_role('button', name='进入员工确认', exact=True).click()
            names = page.locator('.employee-card input:not([type])')
            names.first.fill('仅第一人修订')
            page.get_by_label('备注', exact=True).fill('第一行人工内容')
            page.wait_for_function("zsjkqtFixture.drafts[0]?.snapshot?.project?.employees[0]?.summary?.remark==='第一行人工内容'")
            assert page.evaluate('zsjkqtFixture.drafts[0].snapshot.project.employees.map(e=>e.displayName)') == ['仅第一人修订', '同名人员', '第三位']
            page.get_by_role('button', name='未确认', exact=True).click()
            page.get_by_role('button', name='保存并下一个', exact=True).click()
            expect(names.first).to_have_value('同名人员')
            expect(names.nth(1)).to_have_value('合成研发部')
            assert page.evaluate("Object.keys(zsjkqtFixture.profiles)") == ['同名人员']
            assert page.evaluate('zsjkqtFixture.drafts[0].snapshot.project.employees.map(e=>e.confirmed)') == [True, False, False]

            # Long text stays editable and is never silently cut down to an exportable prefix.
            page.get_by_label('备注', exact=True).fill('文' * 32768)
            expect(page.get_by_role('alert').filter(has_text='备注不能超过')).to_be_visible()
            page.get_by_role('button', name='保存草稿／重试', exact=True).click()
            expect(page.get_by_text('考勤内容不能超过32767个字符，请缩短后保存或导出', exact=True).first).to_be_visible()
            assert len(page.get_by_label('备注', exact=True).input_value()) == 32768
            page.get_by_label('备注', exact=True).fill('第二行已修订')
            page.get_by_role('button', name='保存草稿／重试', exact=True).click()
            expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('已保存')
            page.get_by_role('button', name='返回草稿箱', exact=True).click()
            expect(page.get_by_role('heading', name='中世健考勤通 · 草稿箱', exact=True)).to_be_visible()
            # Reopen a legacy draft with duplicate row keys; values and matching configuration keys survive.
            page.evaluate("""() => {
              const row=zsjkqtFixture.drafts[0];row.name='名'.repeat(120);
              row.snapshot.filter='all';row.snapshot.activeIndex=0;
              for(const employee of row.snapshot.project.employees){employee.key=employee.profileKey;delete employee.profileKey;}
              zsjkqtFixture.persist();
            }""")
            page.get_by_role('button', name=re.compile(r'^刷\s*新$')).click()
            page.get_by_role('button', name=re.compile(r'^打\s*开$')).click()
            expect(names.first).to_have_value('仅第一人修订')
            expect(page.get_by_label('备注', exact=True)).to_have_value('第一行人工内容')
            page.wait_for_function('new Set(zsjkqtFixture.drafts[0].snapshot.project.employees.map(e=>e.key)).size===3')
            page.get_by_role('button', name='另存副本', exact=True).click()
            expect(page.get_by_text('副本已保存', exact=True)).to_be_visible()
            assert page.evaluate('zsjkqtFixture.drafts.length') == 2
            assert page.evaluate('zsjkqtFixture.drafts[1].name.length') == 120
            assert page.evaluate("zsjkqtFixture.drafts[1].name.endsWith('（副本）')")
            assert page.evaluate('zsjkqtFixture.drafts[0].name.length') == 120
            page.screenshot(path=str(OUT / f'attendance-fixed-{width}.png'), full_page=True)
            assert not errors, errors
            print(json.dumps({'width': width, 'rows': 'independent', 'next': 'second employee', 'copyName': 'valid', 'errors': errors}, ensure_ascii=False))
            context.close()
    finally:
        browser.close()
