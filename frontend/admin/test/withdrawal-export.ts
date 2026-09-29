// UTF-8. Isolated browser fixture: synthetic transport, never shared business writes.
import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { setupGlobCom } from '../src/components'
import { setupElementPlus } from '../src/plugins/elementPlus'
import { useUserStore } from '../src/store/modules/user'
import '../src/plugins/unocss'
import '../src/styles/index.scss'
const fixture = {
  calls: [] as Array<{ url: string; body: Record<string, unknown> }>,
  fail: false,
  empty: false,
  loadError: false,
  delay: 0
}
Object.assign(window, { exportFixture: fixture })
const rows = [1, 2, 3].map((id) => ({
  id,
  withdrawalNo: `TEST-${id}`,
  status: id === 3 ? 'paid' : 'approved',
  applicationAmount: 20,
  accountNameSnapshot: '验收账户',
  bankNameSnapshot: '验收银行',
  cardNumber: '0001234567890123456', maskedCardNumber: '****3456',
  submittedAt: '2026-09-21 09:00:00'
}))
async function mount() {
  setupStore(createApp({}))
  const { service } = await import('../src/config/axios/service')
  service.defaults.adapter = async (config) => {
    const url = config.url || ''
    let data: unknown = [],
      code = 0,
      msg = ''

    if (url === '/zsjos/export-task' && config.method === 'post') {
      fixture.calls.push({ url, body: JSON.parse(config.data) })
      if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
      if (fixture.fail) throw new Error('导出任务创建失败，请重试')
      data = 91
    } else if (url.includes('/advanced-filter/catalog')) {
      data = { fields: [{ fieldKey: 'withdrawal.status', label: '状态', options: [{ value: 'paid', label: '已打款' }, { value: 'approved', label: '待打款' }] }] }
    } else if (url === '/zsjos/advanced-filter-template/visible-list') {
      data = []
    } else if (url === '/zsjos/export-task/page') {
      data = { list: [{ id: 91, taskNo: 'EXP-TEST', exportType: 'withdrawal', status: 'ready', resultFileName: 'withdrawal-test.xlsx' }], total: 1 }
    } else if (url === '/zsjos/export-task/91/download-url') {
      data = '#test-download'
    }
    if (url.includes('/withdrawal/')) {
      if (config.method === 'put') {
        fixture.calls.push({ url, body: JSON.parse(config.data) })
        if (fixture.delay) await new Promise((resolve) => setTimeout(resolve, fixture.delay))
        if (fixture.fail) {
          code = 1900010001
          msg = '提现状态已变化，请刷新后重试'
        } else data = true
      } else if ((url.endsWith('/page') || url.endsWith('/my-page'))) {
        if (fixture.loadError) {
          code = 500
          msg = '提现列表加载失败'
        }
        data = { list: fixture.empty ? [] : rows, total: fixture.empty ? 0 : 3 }
      } else data = rows[0]
    }
    return {
      config,
      request: { responseType: 'json' },
      status: 200,
      statusText: 'OK',
      headers: {},
      data: { code, msg, data }
    }
  }
  const { default: Page } = await import('../src/views/zsjos/withdrawal/index.vue')
  const { default: ExportPage } = await import('../src/views/zsjos/exportTask/index.vue')
  const app = createApp({ render: () => h(RouterView) })
  setupStore(app)
  await setupI18n(app)
  setupGlobCom(app)
  setupElementPlus(app)
  app.use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Page }, { path: '/zsjos/export-task', component: ExportPage }] }))
  useUserStore().permissions = new Set([
    ...(location.search.includes('own') ? ['zsjos:withdrawal:my-query'] : [location.search.includes('admin-scope') ? 'zsjos:withdrawal:admin-query' : 'zsjos:withdrawal:finance-query']),
    ...(location.search.includes('no-export') ? [] : ['zsjos:export:withdrawal']), ...(location.search.includes('no-query') ? [] : ['zsjos:export:query'])
  ])
  const { setupAuth } = await import('../src/directives')
  setupAuth(app)
  app.mount('#app')
}
void mount()
