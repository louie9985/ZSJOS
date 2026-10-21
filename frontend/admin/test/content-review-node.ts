// UTF-8. Actual Admin message component; all transport is synthetic.
import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createRouter, createMemoryHistory, useRoute } from 'vue-router'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { service } from '../src/config/axios/service'

service.defaults.adapter = async config => {
  let data: unknown = []
  const url = config.url || ''
  if (url.includes('get-unread-count')) data = 1
  else if (url.includes('/my-get')) data = {
    id: 42, bizType: 'content_review', bizId: 42, actionType: 'business_detail',
    sceneCode: 'zsjos.content_review.final_completed', readStatus: false,
    templateTitle: '内容审批 · 终审通过', templateSummary: '批次 CR-NODE-42，共 6 条作品，已全部通过，待发布。',
    templateContent: '本轮审批结果', templateParams: { batchNo: 'CR-NODE-42', itemCount: 6, returnedCount: 0 },
    templateType: 2, createTime: Date.now()
  }
  return { config, request: { responseType: 'json' }, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const bootstrap = createApp({}); setupStore(bootstrap)
const { default: Message } = await import('../src/layout/components/Message/src/Message.vue')
const Page = { setup() {
  const route = useRoute()
  return () => h('main', [h(Message), h('p', { id: 'current-route' }, route.fullPath)])
} }
const router = createRouter({ history: createMemoryHistory(), routes: [
  { path: '/', component: Page }, { path: '/user/notify-message', component: Page }
] })
const app = createApp(Page); setupStore(app); await setupI18n(app); app.use(ElementPlus); app.use(router)
app.component('Icon', { render: () => h('span', '通知') })
await router.push('/'); await router.isReady(); app.mount('#app')
