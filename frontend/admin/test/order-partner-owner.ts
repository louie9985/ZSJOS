// UTF-8. Isolated browser fixture: all HTTP requests use test data only.
import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/mySalesOrder/index.vue'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const params = new URLSearchParams(location.search)
const scenario = params.get('scenario') || 'assigned'
const sourceType = scenario === 'other' ? 'internal_new_media' : 'partner'
const partnerOwnerSnapshot = scenario === 'historical' ? undefined : scenario === 'unassigned' ? {} : { employeeUserId: 80, employeeName: '提交时新媒体员工甲' }
const order = { id: 1, orderNo: 'OR-OWNER-TEST', studentName: '快照测试学员', status: 'effective', totalAmount: 100, items: [], leadSourceType: sourceType, partnerOwnerSnapshot, leadProfile: { sourceType, partnerOwnerSnapshot } }
service.defaults.adapter = async config => {
  const url = config.url || ''
  if (config.method !== 'get') throw new Error('Fixture blocks writes')
  let data: unknown = []
  if (url.endsWith('/management-page')) data = { list: [order], total: 1 }
  else if (url.endsWith('/sales-order/management/1')) data = order
  else if (url.includes('catalog')) data = { fields: [], operators: [] }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config, request: { responseType: 'json' } }
}
const app = createApp({ render: () => h('main', { style: 'padding:16px' }, [h(Page)]) })
setupStore(app)
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
app.use(router)
await router.push('/')
await router.isReady()
await setupI18n(app)
app.use(ElementPlus)
app.component('ContentWrap', { render() { return h('div', this.$slots.default?.()) } })
app.directive('hasPermi', {})
app.mount('#app')
