# -*- coding: utf-8 -*-
from playwright.sync_api import sync_playwright, expect
with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True, executable_path='C:/Users/EDY/AppData/Local/ms-playwright/chromium_headless_shell-1223/chrome-headless-shell-win64/chrome-headless-shell.exe')
    page = browser.new_page(viewport={'width':1440,'height':1000})
    page.route('**/admin-api/**', lambda r:r.fulfill(json={'code':0,'data':[]}))
    def fixture(route):
        response=route.fetch()
        text=response.text()
        start=text.index('api.allLeadPage =')
        end=text.index('api.managedLead =',start)
        text=text[:start]+'''window.sortQueries=[];
api.allLeadPage = async query => {
window.sortQueries.push(query);
const start=(query.pageNo-1)*20;
return {list:Array.from({length:20},(_,i)=>({...lead(start+i+1),currentAssignmentFirstFollowUpAt:1700000000000+(start+i)*1000, pendingTasks:i===19?[{id:1,title:'Test',status:'pending'}]:[]})),total:60};
};
'''+text[end:]
        route.fulfill(response=response,body=text)
    page.route('**/test/lead-follow-up-refresh.tsx*',fixture)
    page.goto('http://localhost:5174/test/lead-follow-up-refresh.html?layout=table&leadId=999')
    rows=page.locator('tbody tr[data-row-key]')
    expect(rows).to_have_count(20)
    assert page.locator('tbody tr[data-row-key="999"]').count()==0
    page.locator(".ant-drawer-close").click()
    for n in [2,3]:
        page.locator(f'.ant-pagination-item-{n}').first.click()
        page.wait_for_function(f'sortQueries.some(q=>q.pageNo==={n})')
        expect(rows).to_have_count(20)
        expect(page.locator(f'tbody tr[data-row-key="{(n-1)*20+1}"]')).to_have_count(1)
    header=page.locator('th').filter(has_text='首次跟进时间').first
    header.click()
    page.wait_for_function("sortQueries.some(q=>q.sortField==='currentAssignmentFirstFollowUpAt' && q.sortOrder && q.pendingTasksFirst===false)")
    expect(rows).to_have_count(20)
    assert rows.first.get_attribute('data-row-key')=='1', 'Pending task reordered explicit sort'
    print('PASS: deep link excludes extra row; pages 1/2/3 have 20 rows; explicit time sort disables task priority and preserves API order')
    browser.close()

