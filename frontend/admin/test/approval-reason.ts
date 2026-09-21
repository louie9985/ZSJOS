// UTF-8. Isolated browser fixture: all HTTP requests use test data only.
import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/salesOrderApproval/index.vue'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const params = new URLSearchParams(location.search)
let targetCalls = 0
let failed = false
const order = { id: 1, orderNo: 'TEST-ORDER', studentName: '测试学员', taskId: 'task', items: [], version: 1, currentApprovalRoundId: 1, approvalRoundVersion: 1 }
service.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/inbox-page')) data = { list: [order], total: 1 }
  else if (url.endsWith('/task-target')) {
    targetCalls++
    data = { orderId: 1, taskId: 'task', approvalReasonRequired: params.has('missing') && targetCalls === 1 ? undefined : params.has('old') }
  } else if (url.endsWith('/sales-order/1')) data = order
  else if (/\/(approve|reject)$/.test(url)) {
    document.getElementById('result')!.textContent = `${url.endsWith('/approve') ? '通过' : '驳回'}提交：reason=${JSON.stringify(JSON.parse(config.data).reason)}`
    if (params.has('fail') && !failed) { failed = true; throw new Error('测试提交失败，请重试') }
    data = true
  }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config, request: { responseType: 'json' } }
}
const app = createApp({ render: () => h('main', { style: 'padding:16px' }, [h('div', { id: 'result', role: 'status' }, '测试请求尚未提交'), h(Page)]) })
setupStore(app)
await setupI18n(app)
app.use(ElementPlus)
app.component('ContentWrap', { render() { return h('div', this.$slots.default?.()) } })
app.mount('#app')
