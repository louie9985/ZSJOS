// UTF-8. Synthetic transport fixture only.
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import 'element-plus/dist/index.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const params = new URLSearchParams(location.search)
const state = { fail: false, detailFail: false, stale: false, denied: false, active: false, version: 0, selected: [] as number[], calls: [] as string[] }
Object.assign(window, { shareFixture: state })
const token = () => String(state.version).padStart(43, 'x')
const current = () => ({ active: state.active, version: state.version, attachmentIds: state.selected, url: state.active ? 'http://127.0.0.1:5199/public-api/system/notice-share/page?token=' + token() : undefined })
const notice = { id: 1, title: '中世健公开课程说明', content: '<p>面向学员、合作伙伴的课程介绍。</p>', type: 2, status: 0, remark: '', creator: 'fixture', createTime: new Date(),
  publishStatus: params.has('draft') ? 'DRAFT' : params.has('offline') ? 'OFFLINE' : 'PUBLISHED', audienceType: 'TARGET' as const, targetDeptIds: [1], targetUserIds: [], attachments: [
    { infraFileId: 101, fileName: '课程介绍.pdf', fileSize: 1024, mimeType: 'application/pdf', sort: 0 },
    { infraFileId: 102, fileName: '内部材料.zip', fileSize: 2048, mimeType: 'application/zip', sort: 1 }
  ] }
const adapter = async (config: any) => {
  const path = (config.url || '').split('?')[0]
  state.calls.push(path)
  if (state.fail && path.includes('notice-share')) throw new Error('分享配置加载失败')
  if (state.detailFail && path.endsWith('/system/notice/get')) throw new Error('公告加载失败，请重试')
  let data: unknown; let code = state.denied ? 403 : 0
  if (path.includes('notice-share')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    if (!code && path.endsWith('/open')) { state.active = true; state.version++; state.selected = body.attachmentIds }
    if (!code && path.endsWith('/close')) { state.active = false; state.version++; state.selected = [] }
    data = current()
  } else if (path.endsWith('/dict-data/simple-list')) data = []
  else if (path.endsWith('/system/notice/get')) data = { ...notice, publishStatus: state.stale ? 'OFFLINE' : notice.publishStatus }
  else if (path.endsWith('/system/notice/page')) data = { list: [{ ...notice, content: undefined, publishStatus: state.stale ? 'OFFLINE' : notice.publishStatus }], total: 1 }
  else throw new Error('Unexpected request ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' }, data: { code, msg: code ? '无权管理分享' : '', data } }
}

service.defaults.adapter = adapter
const boot = createApp({}); setupStore(boot)
const { useUserStore } = await import('../src/store/modules/user')
useUserStore().permissions = new Set(params.has('no-query') ? ['system:notice:share'] : params.has('denied') ? ['system:notice:query'] : ['system:notice:query', 'system:notice:share', 'system:notice:offline', 'system:notice:create'])
const { default: Page } = await import('../src/views/system/notice/index.vue')
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Page }] })
const app = createApp({ render: () => h('main', [h(Page)]) })
setupStore(app); await setupI18n(app); app.use(ElementPlus); app.use(VueDOMPurifyHTML); app.use(router); hasPermi(app)
app.component('ContentWrap', { render() { return h('section', this.$slots.default?.()) } })
await router.isReady(); app.mount('#app')
