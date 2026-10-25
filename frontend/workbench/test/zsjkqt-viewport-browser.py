# -*- coding: utf-8 -*-
"""Attendance viewport regression in real Chrome with synthetic HTTP only.

The original fixture deliberately stands alone. This harness gives its existing
page the same bounded content area as Workbench: fixed header/tabs above it and
desktop navigation beside it. Only the host gets fixture CSS; product layout
must size and scroll itself without a document-level vertical scrollbar.
"""
import json
import os
import re
from pathlib import Path
from tempfile import gettempdir

from playwright.sync_api import expect, sync_playwright

BASE = os.environ.get('ZSJKQT_FIXTURE_URL', 'http://127.0.0.1:5174/test/zsjkqt.html')
OUT = Path(gettempdir()) / 'zsjos-attendance-viewport-browser'
OUT.mkdir(exist_ok=True)
WORKBOOK = {
    'name': 'synthetic-viewport.xlsx',
    'mimeType': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'buffer': b'isolated adapter fixture; contains no business data',
}

HOST = """() => {
  const host = document.querySelector('#zsjkqt-isolation-sentinel').parentElement;
  host.id = 'zsjkqt-viewport-test-host';
  host.classList.add('ant-layout-content');
  const style = document.createElement('style');
  style.textContent = `
    html, body { margin: 0; height: 100%; }
    #zsjkqt-isolation-sentinel { display: none; }
    #zsjkqt-viewport-test-host {
      position: fixed; top: 84px; right: 0; bottom: 0; left: 272px;
      width: auto !important; height: auto; padding: 0 !important;
      min-height: 0; min-width: 0; overflow: auto;
    }
    #zsjkqt-test-header {
      position: fixed; inset: 0 0 auto; height: 84px;
      background: #fff; border-bottom: 1px solid #e8edf3;
      padding: 16px 20px; box-sizing: border-box; color: #24364a;
    }
    #zsjkqt-test-nav {
      position: fixed; top: 84px; bottom: 0; left: 0; width: 272px;
      background: #f9fbfd; border-right: 1px solid #e8edf3;
      padding: 24px; box-sizing: border-box; color: #24364a;
    }
    @media(max-width: 700px) {
      #zsjkqt-viewport-test-host { left: 0; }
      #zsjkqt-test-nav { display: none; }
    }
  `;
  document.head.append(style);
  for(const [id,text] of [
    ['zsjkqt-test-header','中世健 · 工作台高度验收（合成数据）'],
    ['zsjkqt-test-nav','HRM 人力资源 / 中世健考勤通']
  ]) {
    const element = document.createElement('div'); element.id=id;
    element.textContent=text; document.body.append(element);
  }
  zsjkqtFixture.sourceOverride = {
    year: 2026, month: 6, startDate: '2026-06-01', endDate: '2026-06-30',
    sourceFileName: 'synthetic-viewport.xlsx', sourceFileHash: 'viewport-65-employees',
    employees: Array.from({length:65}, (_, index) => ({
      name:`合成员工${String(index+1).padStart(2,'0')}`,
      account:`synthetic-viewport-${index}`, rule:'合成规则', department:'合成测试部',
      position:'', jobNo:'', summaries:{'应出勤天数(天)':21,'实际出勤天数(天)':21},
      dayDetails:Object.fromEntries(Array.from({length:30}, (_,d)=>[
        d+1,index<3&&d===0?'未知明细':'正常- 08:30;18:00'
      ]))
    }))
  };
}"""


def geometry(page):
    return page.evaluate("""() => {
      const rect = selector => {
        const e=document.querySelector(selector), r=e.getBoundingClientRect();
        return {top:r.top,bottom:r.bottom,height:r.height,width:r.width,
          clientHeight:e.clientHeight,scrollHeight:e.scrollHeight,scrollTop:e.scrollTop,
          clientWidth:e.clientWidth,scrollWidth:e.scrollWidth};
      };
      return {height:innerHeight,width:innerWidth,
        documentHeight:document.documentElement.scrollHeight,
        documentWidth:document.documentElement.scrollWidth,
        host:rect('#zsjkqt-viewport-test-host'),viewport:rect('.zsjkqt-viewport'),
        workspace:rect('.workspace'),footer:rect('.page-action-bar'),
        list:rect('.employee-scroll'),card:rect('.employee-card'),summary:rect('.summary-list')};
    }""")


def assert_layout(page):
    m = geometry(page)
    assert m['documentHeight'] <= m['height'] + 1, m
    assert m['documentWidth'] <= m['width'] + 1, m
    for selector in ['host', 'viewport']:
        assert m[selector]['scrollHeight'] <= m[selector]['clientHeight'] + 1, m
        assert m[selector]['scrollTop'] == 0, m
    assert m['footer']['bottom'] <= m['height'] + 1, m
    assert m['footer']['top'] >= m['workspace']['bottom'] - 1, m
    assert m['workspace']['height'] > 100, m
    assert m['workspace']['top'] - m['host']['top'] < 120, m
    assert m['summary']['scrollWidth'] <= m['summary']['clientWidth'] + 1, m
    assert page.locator('.hero-panel').count() == 0
    buttons = page.locator('.page-action-bar button').evaluate_all(
        '(els)=>els.map(e=>({text:e.textContent,top:e.getBoundingClientRect().top,bottom:e.getBoundingClientRect().bottom}))')
    assert all(m['host']['top'] <= button['top'] < button['bottom'] <= m['height'] for button in buttons), buttons
    details = page.locator('.detail-button').evaluate_all('(els)=>els.map(e=>({client:e.clientHeight,scroll:e.scrollHeight}))')
    assert all(item['scroll'] <= item['client'] for item in details), details
    clipped_inputs = page.locator('.summary-list input').evaluate_all('''els=>els.filter(e=>{
      const own=e.getBoundingClientRect(), parent=e.closest('.summary-list').getBoundingClientRect();
      return own.left < parent.left-1 || own.right > parent.right+1;
    }).map(e=>e.id)''')
    assert not clipped_inputs, clipped_inputs
    return m


def assert_inside(child, parent):
    c, p = child.bounding_box(), parent.bounding_box()
    assert c is not None and p is not None
    assert c['y'] >= p['y'] - 1 and c['y'] + c['height'] <= p['y'] + p['height'] + 1, (c, p)


def assert_counters(page, confirmed):
    total_issues = page.evaluate("""async () => {
      const {employeeReviewIssueCount} = await import('/src/features/zsjkqt/core/reviewIssues.ts');
      return zsjkqtFixture.drafts[0].snapshot.project.employees.reduce((n,e)=>n+employeeReviewIssueCount(e),0);
    }""")
    counters = page.get_by_label('考勤统计', exact=True)
    assert total_issues == 3
    expect(counters).to_contain_text(re.compile(r'已确认\s*' + f'{confirmed}/65'))
    expect(counters).to_contain_text(re.compile(r'异常\s*' + str(total_issues)))
    assert_inside(counters, page.locator('.zsjkqt-editor-toolbar'))


def assert_tooltip_accessible(page, button, filename):
    button.hover()
    tooltip = page.locator('[role="tooltip"]:visible').last
    expect(tooltip).to_be_visible()
    page.wait_for_function('''() => {
      const e=document.querySelector('.zsjkqt-detail-overlay:not(.ant-popover-hidden)');
      return e && getComputedStyle(e).opacity==='1' && !/appear|enter-active/.test(e.className);
    }''')
    clipping = tooltip.evaluate('''e=>{
      const r=e.getBoundingClientRect(), failures=[];
      if(r.top<0||r.left<0||r.bottom>innerHeight+1||r.right>innerWidth+1)failures.push('window');
      for(let p=e.parentElement;p&&p!==document.body;p=p.parentElement){
        const s=getComputedStyle(p), b=p.getBoundingClientRect();
        if(/auto|scroll|hidden|clip/.test(s.overflowY)&&(r.top<b.top-1||r.bottom>b.bottom+1))failures.push(p.className+':y');
        if(/auto|scroll|hidden|clip/.test(s.overflowX)&&(r.left<b.left-1||r.right>b.right+1))failures.push(p.className+':x');
      }
      return failures;
    }''')
    assert not clipping, clipping
    expect(tooltip.get_by_text('原始数据', exact=True)).to_be_visible()
    page.screenshot(path=str(OUT / filename))
    page.locator('.zsjkqt-editor-toolbar').hover(position={'x':10,'y':10})


def run_viewport(browser, width, height):
    context = browser.new_context(viewport={'width':width,'height':height}, accept_downloads=True)
    page = context.new_page()
    page.set_default_timeout(15000)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    try:
        # A cold Vite optimizer may need longer than an individual interaction.
        page.goto(BASE, timeout=60000)
        page.wait_for_function('window.zsjkqtFixture !== undefined')
        page.evaluate(HOST)
        page.get_by_role('button', name=re.compile('新建考勤$')).click()
        page.locator('input[type=file]').first.set_input_files(WORKBOOK)
        page.get_by_label('应出勤日', exact=True).fill('21')
        page.get_by_label('计薪日', exact=True).fill('23')
        page.get_by_role('button', name='进入员工确认', exact=True).click()
        expect(page.locator('.employee-item')).to_have_count(65)
        page.wait_for_function('zsjkqtFixture.drafts[0]?.snapshot?.project?.employees.length===65')
        expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('已保存')
        initial = assert_layout(page)
        assert_counters(page, 0)
        page.screenshot(path=str(OUT / f'review-{width}x{height}.png'))
        if width == 1440 and height == 600:
            assert_tooltip_accessible(page, page.locator('.detail-button').first, 'review-tooltip-first-1440x600.png')

        # All three independently long regions remain reachable in a fixed host.
        last_employee = page.locator('.employee-item').last
        last_employee.scroll_into_view_if_needed()
        assert_inside(last_employee, page.locator('.employee-scroll'))
        assert page.locator('.employee-scroll').evaluate('(e)=>e.scrollTop') > 0
        last_employee.click()
        expect(page.locator('.employee-card input:not([type])').first).to_have_value('合成员工65')
        last_day = page.locator('.day-card').filter(has=page.locator('.day-number', has_text=re.compile('^30$')))
        last_day.scroll_into_view_if_needed()
        assert_inside(last_day, page.locator('.employee-card'))
        if width == 1440 and height == 600:
            assert_tooltip_accessible(page, last_day.locator('.detail-button'), 'review-tooltip-last-1440x600.png')
        remark = page.get_by_label('备注', exact=True)
        remark.scroll_into_view_if_needed()
        assert_inside(remark, page.locator('.summary-list'))
        assert page.locator('.summary-list').evaluate('(e)=>e.scrollTop') > 0
        remark.fill('合成末位员工备注')
        expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('已保存')
        assert_layout(page)

        # The footer remains usable after each panel has reached its bottom.
        page.get_by_role('button', name='保存并下一个', exact=True).click()
        page.wait_for_function('zsjkqtFixture.drafts[0].snapshot.project.employees[64].confirmed')
        expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('已保存')
        assert_counters(page, 1)
        assert_layout(page)

        if width == 1440 and height == 600:
            page.evaluate("zsjkqtFixture.failOperation='save';zsjkqtFixture.failMessage='合成保存失败，请检查网络后重试；当前人工修改仍保留。'")
            remark.fill('合成失败后仍保留的人工修改')
            expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('保存失败，修改已保留')
            expect(page.locator('.zsjkqt-savebar [role=alert]')).to_be_visible()
            assert_layout(page)
            page.screenshot(path=str(OUT / 'review-save-error-1440x600.png'))
            page.get_by_role('button', name='保存草稿／重试', exact=True).click()
            expect(page.locator('.zsjkqt-savebar [role=status]')).to_have_text('已保存')
            expect(remark).to_have_value('合成失败后仍保留的人工修改')
            assert_layout(page)

        if width == 1440 and height == 900:
            for resize_height in [600,1000,768]:
                page.set_viewport_size({'width':width,'height':resize_height})
                page.wait_for_function("Math.abs(document.querySelector('.zsjkqt-viewport').getBoundingClientRect().bottom-document.querySelector('#zsjkqt-viewport-test-host').getBoundingClientRect().bottom)<2")
                assert_layout(page)
            page.evaluate("document.querySelector('#zsjkqt-viewport-test-host').style.top='132px'")
            page.wait_for_function("Math.abs(document.querySelector('.zsjkqt-viewport').getBoundingClientRect().bottom-document.querySelector('#zsjkqt-viewport-test-host').getBoundingClientRect().bottom)<2")
            assert_layout(page)
        if width == 390:
            assert initial['viewport']['scrollWidth'] > initial['viewport']['clientWidth']
            page.locator('.zsjkqt-viewport').evaluate('(e)=>e.scrollLeft=e.scrollWidth')
            page.screenshot(path=str(OUT / 'review-mobile-summary-390x844.png'))
            assert_layout(page)
        assert not errors, errors
        print(json.dumps({'viewport':f'{width}x{height}','layout':initial,'checks':'65 employees/day30/remark/footer/save/resize','errors':errors}, ensure_ascii=False), flush=True)
    finally:
        context.close()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='chrome', headless=True)
    try:
        for dimensions in [(1920,1080),(1440,900),(1366,768),(1440,600),(390,844)]:
            run_viewport(browser, *dimensions)
    finally:
        browser.close()
