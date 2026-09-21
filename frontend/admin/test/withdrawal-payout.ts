// UTF-8. Isolated browser fixture: synthetic transport, never shared business writes.
import { createApp } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
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
Object.assign(window, { payoutFixture: fixture })
const rows = [1, 2, 3].map((id) => ({
  id,
  withdrawalNo: `TEST-${id}`,
  status: id === 3 ? 'paid' : 'approved',
  applicationAmount: 20,
  accountNameSnapshot: '验收账户',
  bankNameSnapshot: '验收银行',
  maskedCardNumber: '****0000',
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
    if (url.includes('/withdrawal/')) {
      if (config.method === 'put') {
        fixture.calls.push({ url, body: JSON.parse(config.data) })
        if (fixture.delay) await new Promise((resolve) => setTimeout(resolve, fixture.delay))
        if (fixture.fail) {
          code = 1900010001
          msg = '提现状态已变化，请刷新后重试'
        } else data = true
      } else if (url.endsWith('/page')) {
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
  const app = createApp(Page)
  setupStore(app)
  await setupI18n(app)
  setupGlobCom(app)
  setupElementPlus(app)
  app.use(createRouter({ history: createMemoryHistory(), routes: [] }))
  useUserStore().permissions = new Set([
    'zsjos:withdrawal:finance-query',
    ...(location.search.includes('no-payout') ? [] : ['zsjos:withdrawal:payout'])
  ])
  const { setupAuth } = await import('../src/directives')
  setupAuth(app)
  app.mount('#app')
}
void mount()
