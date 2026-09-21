// UTF-8. Isolated fixture: synthetic subjects only; every API call is intercepted.
import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { useUserStore } from '../src/store/modules/user'
const params = new URLSearchParams(location.search)
const rows = [
  { productId: 1, productName: '未配置课程' },
  { productId: 2, productName: '学校课程', paymentSubjectId: 10, subjectName: '测试学校', subjectCode: 'school' },
  { productId: 3, productName: '公司课程', paymentSubjectId: 20, subjectName: '测试公司', subjectCode: 'company' },
  { productId: 4, productName: '失效关联课程', paymentSubjectId: 99 }
]
let failed = false
service.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/product-payment-subject/page')) {
    if (params.has('error') && !failed) { failed = true; throw new Error('测试列表加载失败') }
    data = { list: params.has('empty') ? [] : rows, total: params.has('empty') ? 0 : rows.length }
  } else if (url.endsWith('/payment-subject/simple-list')) {
    data = [{ id: 10, subjectName: '测试学校', subjectCode: 'school', status: 0 }, { id: 20, subjectName: '测试公司', subjectCode: 'company', status: 0 }]
  } else if (url.endsWith('/batch-configure')) {
    const body = JSON.parse(config.data)
    document.getElementById('result')!.textContent = `测试配置请求：${JSON.stringify(body)}`
    data = true
  }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config, request: { responseType: 'json' } }
}
// Permission module reads the store at import time, so initialize before importing pages.
const bootstrap = createApp({})
setupStore(bootstrap)
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const { default: Page } = await import('../src/views/zsjos/payment/productSubject/index.vue')
const { default: SubjectForm } = await import('../src/views/zsjos/payment/subject/SubjectForm.vue')
const form = ref<InstanceType<typeof SubjectForm>>()
const app = createApp({ render: () => h('main', { style: `padding:16px;${params.has('narrow') ? 'width:390px' : ''}` }, [
  h('div', { id: 'result', role: 'status' }, '仅使用隔离测试数据'),
  h('button', { onClick: () => form.value?.open('create') }, '查看主体编码说明'),
  h(Page), h(SubjectForm, { ref: form })
]) })
setupStore(app)
useUserStore().permissions = new Set(params.has('denied') ? [] : ['zsjos:product-payment-subject:query', 'zsjos:product-payment-subject:configure'])
await setupI18n(app)
app.use(ElementPlus)
hasPermi(app)
app.component('ContentWrap', { render() { return h('section', { style: 'margin:16px 0' }, this.$slots.default?.()) } })
app.mount('#app')
