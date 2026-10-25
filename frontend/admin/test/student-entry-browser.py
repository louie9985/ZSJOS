# -*- coding: utf-8 -*-
"""Production Vue page/form with synthetic requests, no shared business writes."""
import tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
OUT = Path(tempfile.gettempdir()) / 'zsjos-student-entry-browser'
OUT.mkdir(exist_ok=True)
BASE = 'http://127.0.0.1:5297/test/student-entry.html'
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    errors = []; page.on('pageerror', lambda e: errors.append(str(e)))
    def fill(mobile='13800000000'):
        page.get_by_label('学员姓名', exact=True).fill('管理端新增学员')
        page.get_by_label('手机号', exact=True).fill(mobile)
    def select(label, value):
        page.locator('.el-form-item').filter(has=page.get_by_text(label, exact=True)).locator('.el-select').click()
        page.get_by_role('option', name=value, exact=True).click()
    def creations():
        return page.evaluate("entryFixture.requests.filter(r=>r.url.endsWith('/create')).map(r=>r.data)")
    page.goto(BASE + '?denied=1')
    expect(page.get_by_text('暂无添加学员权限')).to_be_visible()
    assert page.evaluate('entryFixture.requests.length') == 0
    page.goto(BASE + '?fail=1')
    page.get_by_role('button', name='重试', exact=True).click()
    fill(); select('责任编导', '责任编导甲')
    assert not page.get_by_role('checkbox', name='同时建立教学服务，加入待分班').is_checked()
    page.screenshot(path=str(OUT / 'vue-media-desktop.png'), full_page=True)
    page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_label('学员姓名', exact=True)).to_have_value('')
    assert creations()[-1]['entryType'] == 'media' and not creations()[-1]['teachingDelivery']
    fill(); select('责任编导', '责任编导甲')
    page.get_by_text('同时建立教学服务，加入待分班', exact=True).click()
    select('课程 SKU（选填）', '测试课程 / 标准规格')
    page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_label('学员姓名', exact=True)).to_have_value('')
    assert creations()[-1]['teachingDelivery'] and creations()[-1]['skuRef'] == 'sku1'
    page.get_by_text('教学学员', exact=True).click()
    fill(); select('班级', '测试正式班 · 班主任甲')
    page.evaluate('entryFixture.createFail = true')
    page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_text('模拟响应丢失，请重试')).to_be_visible()
    before = creations()[-1]
    committed = page.evaluate('entryFixture.committedCreates')
    checks = page.evaluate("entryFixture.requests.filter(r=>r.url.endsWith('/check')).length")
    page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_label('学员姓名', exact=True)).to_have_value('')
    assert creations()[-1] == before and before['classId'] == 8
    assert page.evaluate('entryFixture.committedCreates') == committed
    assert page.evaluate("entryFixture.requests.filter(r=>r.url.endsWith('/check')).length") == checks
    fill('13800000001'); select('班级', '待分班')
    size = len(creations()); page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_text('原服务姓名 · 服务中')).to_be_visible()
    assert len(creations()) == size
    page.get_by_text('确认复用此人员并新增服务', exact=True).click()
    page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_label('学员姓名', exact=True)).to_have_value('')
    assert creations()[-1]['reusePersonId'] == 11
    fill('13800000002'); select('班级', '待分班')
    size = len(creations()); page.get_by_role('button', name='添加学员', exact=True).click()
    expect(page.get_by_text('联系方式已被占用或存在冲突，请由有权人员核实')).to_be_visible()
    assert len(creations()) == size
    page.set_viewport_size({'width': 390, 'height': 1000}); page.get_by_role('heading', name='学员录入').click()
    page.screenshot(path=str(OUT / 'vue-teaching-mobile.png'), full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
    assert not errors, errors
    browser.close()
print('PASS Vue media/shared/teaching/formal/pending/reuse/denied/retry/idempotency/privacy/desktop+mobile', OUT)
