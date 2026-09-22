// UTF-8. Isolated browser fixture: all HTTP requests use test data only.
import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/mySalesOrder/index.vue'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const params = new URLSearchParams(location.search)
const mode = params.get('mode') || 'offline'
const order = { id: 1, orderNo: 'TEST-REVISION', studentName: '测试学员', canRevise: true, status: 'revision_required', totalAmount: 500, collectionMode: mode === 'online' ? 'online_link' : mode === 'unknown' ? undefined : 'offline_paid', transactionLocked: mode === 'online', paymentStatus: 'paid', items: [{ id: 1, productRef: 'spu-1', skuRef: 'sku-1', productName: '测试课程', skuName: '原规格', actualAmount: 500 }], paymentVouchers: [], studentNatureLabelSnapshot: '测试', servicePeriodLabelSnapshot: '测试', studentSourceLabelSnapshot: '测试', feeModeLabelSnapshot: '测试', paymentMethodLabelSnapshot: '测试' }
service.defaults.adapter = async config => {
  const url = config.url || ''
  if (config.method !== 'get') throw new Error('Fixture blocks writes')
  let data: unknown = []
  if (url.endsWith('/management-page')) data = { list: [order], total: 1 }
  else if (url.endsWith('/sales-order/management/1')) data = order
  else if (url.includes('catalog')) data = { fields: [], operators: [] }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config, request: { responseType: 'json' } }
}
const app = createApp({ render: () => h('main', { style: 'padding:16px' }, [h('div', { id: 'result', role: 'status' }, '测试请求尚未提交'), h(Page)]) })
setupStore(app)
await setupI18n(app)
app.use(ElementPlus)
app.component('ContentWrap', { render() { return h('div', this.$slots.default?.()) } })
app.directive('hasPermi', {})
app.mount('#app')
