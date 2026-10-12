# -*- coding: utf-8 -*-
"""Source relationship wording and both consumers; synthetic APIs only, no authenticated writes."""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUT = Path('D:/ZSJ-OS-backups/provider-identity-browser')
OUT.mkdir(parents=True, exist_ok=True)
WB = os.environ.get('PROVIDER_WORKBENCH_URL', 'http://127.0.0.1:5191')
ADMIN = os.environ.get('PROVIDER_ADMIN_URL', 'http://127.0.0.1:5183')

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1440, 390):
        page = browser.new_page(viewport={'width': width, 'height': 950})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto(WB + '/test/conversion-details.html')
        page.evaluate("""async () => {
          const {http} = await import('/src/services/api.ts'); const previous = http.defaults.adapter;
          http.defaults.adapter = async config => {
            const response = await previous(config);
            if (config.url.endsWith('/lead/get')) Object.assign(response.data.data, {
              sourceLabel:'新媒体提交',sourceUserName:'验证提供方',providerOwnerId:26,
              providerOwnerNameSnapshot:'验证提供方',providerIdentityLabel:'新媒体提供方',ownerUserName:'验证销售'});
            if (config.url.includes('/sales-order/management/')) Object.assign(response.data.data, {
              leadId:1,leadNo:'KZ-1',leadProfile:{leadNo:'KZ-1',sourceLabel:'新媒体提交',sourceUserName:'验证提供方'}});
            return response;
          };
        }""")
        page.locator('.performance-panel').filter(has=page.get_by_text('多周期成交率', exact=True)).locator('button').click()
        row = page.locator('tr').filter(has=page.get_by_text('KZ-1', exact=True))
        row.get_by_role('button', name='客资详情', exact=True).click()
        dialog = page.get_by_role('dialog')
        expect(dialog.get_by_text('业务来源', exact=True)).to_be_visible()
        expect(dialog.get_by_text('来源人员', exact=True)).to_be_visible()
        expect(dialog.get_by_text('验证提供方 · 新媒体提供方', exact=True)).to_be_visible()
        expect(dialog.get_by_text('验证销售', exact=True)).to_be_visible()
        expect(dialog.get_by_text('来源人员：验证提供方', exact=True)).to_be_visible()
        page.wait_for_function("document.querySelector('[role=dialog]').getBoundingClientRect().width <= innerWidth + .01")
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        page.screenshot(path=str(OUT / f'conversion-lead-{width}.png'), full_page=True)
        page.get_by_role('button', name='返回明细列表', exact=True).click()
        row.get_by_role('button', name='订单详情', exact=True).click()
        expect(dialog.get_by_text('业务来源', exact=True)).to_be_visible()
        expect(dialog.get_by_text('来源人员', exact=True)).to_be_visible()
        expect(dialog.get_by_text('验证提供方', exact=True)).to_be_visible()
        page.screenshot(path=str(OUT / f'conversion-order-{width}.png'), full_page=True)
        assert not errors, errors
        page.close()
    admin = browser.new_page()
    admin.goto(ADMIN + '/test/source-entry.html?sales')
    expect(admin.get_by_text('业务来源：本人自拓，无新媒体引流', exact=True)).to_be_visible()
    result = admin.evaluate("""async () => {
      const {service} = await import('/src/config/axios/service.ts');
      const {getLead} = await import('/src/api/zsjos/leadManagement/index.ts');
      let requested = null;
      service.defaults.adapter = async config => {
        requested = {url:config.url,id:config.params.id,method:config.method};
        return {config,request:{responseType:'json'},status:200,statusText:'OK',headers:{},
          data:{code:0,data:{id:1,leadNo:'KZ-VERIFY',sourceUserId:26,sourceUserName:'验证提供方',providerIdentityLabel:'新媒体提供方'}}};
      };
      const value = await getLead(1); return {requested,value};
    }""")
    assert result['requested'] == {'url': '/zsjos/lead/get', 'id': 1, 'method': 'get'}
    assert result['value']['sourceUserName'] == '验证提供方'
    assert result['value']['providerIdentityLabel'] == '新媒体提供方'
    browser.close()
print('PASS source wording in conversion lead/order at 1440/390; Admin GET transport compatibility')
