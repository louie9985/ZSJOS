// UTF-8. Synthetic data only; all API traffic is intercepted.
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { useUserStore } from '../src/store/modules/user'

const params = new URLSearchParams(location.search)
const calls: string[] = []
let failed = false
const rows = ['DRAFT', 'PUBLISHED', 'OFFLINE'].map((publishStatus, index) => ({
  id: index + 1, title: ['测试草稿', '已发布测试公告', '已下线测试公告'][index], type: 2, publishStatus,
  content: '<p><strong>公告测试正文</strong></p><p>格式、链接与附件验证。</p><img src="x" onerror="document.body.dataset.unsafe=1">',
  audienceType: 'TARGET', targetDeptIds: [1], targetUserIds: [2], recipientCount: 2,
  publishTime: 1790000000000, highlightUntil: 1791000000000,
  attachments: [{ infraFileId: 9, fileName: '不可用测试附件.pdf', fileSize: 100, sort: 0 }]
}))
service.defaults.adapter = async config => {
  const url = config.url || ''
  calls.push(`${config.method} ${url}`)
  const node = document.getElementById('requests'); if (node) node.textContent = calls.join(' | ')
  let data: unknown
  if (url.includes('/notice/page')) data = { list: rows, total: rows.length }
  else if (url.includes('/notice/get')) {
    if (params.has('error') && !failed) { failed = true; throw new Error('测试详情加载失败') }
    const id = Number(url.split('id=')[1] || config.params?.id)
    data = rows.find(row => row.id === id)
  } else if (url.includes('/dict-data/simple-list')) data = [{ value: '2', label: '公告', dictType: 'system_notice_type' }]
  else throw new Error(`No fixture handler: ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config, request: { responseType: 'json' } }
}
const bootstrap = createApp({}); setupStore(bootstrap)
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const { default: Page } = await import('../src/views/system/notice/index.vue')
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Page }] })
const app = createApp({ render: () => h('main', { style: 'padding:16px' }, [h('details', [h('summary', '请求记录'), h('div', { id: 'requests' })]), h(Page)]) })
setupStore(app)
useUserStore().permissions = new Set(params.has('denied') ? [] : ['system:notice:query'])
await setupI18n(app)
app.use(ElementPlus); app.use(VueDOMPurifyHTML); app.use(router); hasPermi(app)
app.component('ContentWrap', { render() { return h('section', { style: 'margin:16px 0' }, this.$slots.default?.()) } })
await router.isReady(); app.mount('#app')
