import { createApp } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { setupGlobCom } from '../src/components'
import { setupElementPlus } from '../src/plugins/elementPlus'
import { useUserStore } from '../src/store/modules/user'
import '../src/plugins/unocss'
import '../src/styles/index.scss'
// UTF-8. Synthetic transport for isolated browser acceptance, no live business writes.
const state = { fail: false, delay: 0, calls: [] as Array<{url: string; body: Record<string, unknown>}> }
Object.assign(window, { cashbackFixture: state })
const rows = [{ id: 1, version: 0, cashbackNo: 'CB-TEST-001', beneficiaryName: '验收受益人', partnerName: '验收合作方', type: 'valid', status: 'available', amount: 20, productNameSnapshot: '测试课程', generatedAt: '2026-09-27T09:00:00', availableAt: '2026-09-27T09:00:00', source: { leadAccess: 'denied', orderAccess: 'not_applicable' }, blockReason: undefined as string | undefined }]
for (let id = 2; id <= 22; id++) rows.push({ ...rows[0], id, cashbackNo: `CB-TEST-${String(id).padStart(3, '0')}`, partnerName: id <= 12 ? '搜索兼职姓名' : '其他兼职', beneficiaryName: id <= 12 ? '搜索兼职姓名' : '其他兼职' })
const history: Array<{ id: number; action: string; reason: string; operatorName: string; occurredAt: string }> = []
const adapter = async (config: any) => {
 const url = config.url || ''; let data: unknown = []
 if (url.includes('advanced-filter/catalog')) data = { scene: 'cashback', fields: [{ fieldKey: 'cashback.type', options: [{ value: 'valid', label: '有效返现' }] }, { fieldKey: 'cashback.status', options: [{ value: 'available', label: '可提现' }, { value: 'blocked', label: '不可提现' }] }] }
 else if (url.includes('filter-scheme')) data = { fields: [], conditions: [], groups: [] }
 else if (url.includes('/cashback/')) {
  if (config.method === 'put') {
   const body = JSON.parse(config.data); state.calls.push({ url, body });
   if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay))
   if (state.fail) throw new Error('返现状态已变化，请刷新后重试')
   const action = url.endsWith('/unblock') ? 'unblock' : 'block'
   rows[0].status = action === 'block' ? 'blocked' : 'available'; rows[0].version++; rows[0].blockReason = action === 'block' ? body.reason : undefined
   history.unshift({ id: history.length + 1, action, reason: body.reason, operatorName: '验收财务', occurredAt: '2026-09-27T10:00:00' }); data = true
  } else if (url.endsWith('/control-history')) data = { list: history, total: history.length }
  else if (url.endsWith('/withdrawals')) data = { list: [], total: 0 }
  else if (url.endsWith('/page') || url.endsWith('/search-page')) { const params = config.method === 'post' ? JSON.parse(config.data) : config.params; state.calls.push({ url, body: { ...params } }); if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay)); if (state.fail) throw new Error('搜索暂时失败，请重试'); const keyword = params?.keyword?.trim() || ''; const filtered = rows.filter(x => (!params?.status || x.status === params.status) && (!keyword || [x.cashbackNo, x.partnerName, x.beneficiaryName].some(value => value.includes(keyword)))); const start = ((params?.pageNo || 1) - 1) * (params?.pageSize || 10); data = { list: filtered.slice(start, start + (params?.pageSize || 10)), total: filtered.length } }
  else data = rows[0]
 }
 return { config, request: { responseType: 'json' }, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}

async function mount() {
 setupStore(createApp({}))
 const { service } = await import('../src/config/axios/service'); service.defaults.adapter = adapter
 const { default: Page } = await import('../src/views/zsjos/cashback/index.vue')
 const app = createApp(Page); setupStore(app); await setupI18n(app); setupGlobCom(app); setupElementPlus(app)
 const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] }); app.use(router); await router.isReady()
 useUserStore().permissions = new Set(['zsjos:cashback:finance-query', ...(location.search.includes('read-only') ? [] : ['zsjos:cashback:block','zsjos:cashback:unblock'])])
 const { setupAuth } = await import('../src/directives'); setupAuth(app); app.mount('#app')
}
void mount()
