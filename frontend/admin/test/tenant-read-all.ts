// UTF-8. Synthetic responses only; all HTTP requests intercepted.
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import dayjs from 'dayjs'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { useUserStore } from '../src/store/modules/user'
const params = new URLSearchParams(location.search)
const fixture = { requests: [] as { url: string; params: Record<string, unknown> }[], writes: 0, mode: 'success' }
Object.assign(window, { tenantReadFixture: fixture })
service.defaults.adapter = async config => {
  if (config.method !== 'get') { fixture.writes++; throw new Error('unexpected mutation') }
  const url = config.url || '', query = { ...Object.fromEntries(new URL(url, location.origin).searchParams), ...config.params }
  fixture.requests.push({ url, params: query })
  if (fixture.mode === 'error' && !url.includes('simple-list')) throw new Error('测试读取失败')
  const self = !query.readScope || query.readScope === 'SELF'
  let data: unknown = []
  if (url.includes('/system/user/simple-list')) data = [{ id: 20, nickname: '停用测试人员', status: 1 }]
  else if (fixture.mode === 'empty') data = url.includes('page') ? { list: [], total: 0 } : []
  else if (url.includes('/personal-calendar')) data = [{ id: 1, title: self ? '本人日程' : '他人日程', ownerName: '测试人员', ownerUserId: 20,
    startTime: dayjs().hour(9).format('YYYY-MM-DDTHH:mm:ss'), endTime: dayjs().hour(10).format('YYYY-MM-DDTHH:mm:ss') }]
  else if (url.includes('my-task-page')) data = { list: [{ id: 1, title: self ? '本人任务' : '他人任务', status: 'pending', assigneeName: '测试人员' }], total: 1 }
  else if (url.includes('/withdrawal/') && url.includes('page')) data = { list: [{ id: 1, withdrawalNo: 'WD-TEST', applicantUserId: 20, applicationAmount: 10, status: 'pending_review', maskedCardNumber: '****1234' }], total: 1 }
  else if (url.includes('/student/my-page')) data = { list: [], total: 0 }
  else if (url.includes('todo-page')) data = { list: [], total: 0 }
  return { config, data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' } }
}
const bootstrap = createApp({}); setupStore(bootstrap)
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const { default: Page } = params.get('view') === 'tasks' ? await import('../src/views/zsjos/todayTask/index.vue') : params.get('view') === 'students' ? await import('../src/views/zsjos/my-students.vue') : params.get('view') === 'withdrawal' ? await import('../src/views/zsjos/withdrawal/index.vue') : await import('../src/views/zsjos/personalCalendar/index.vue')
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Page }] })
const app = createApp({ render: () => h('main', { style: 'padding:16px' }, h(Page)) })
setupStore(app)
useUserStore().permissions = new Set(['zsjos:personal-calendar:query','zsjos:personal-calendar:create','zsjos:personal-calendar:update','zsjos:personal-calendar:delete','zsjos:business-task:query','zsjos:withdrawal:my-query','zsjos:withdrawal:apply'])
useUserStore().dataAccess = { tenantReadAll: !params.has('ordinary') }
await setupI18n(app)
app.use(ElementPlus); app.use(router); hasPermi(app)
app.component('ContentWrap', { render() { return h('section', {}, this.$slots.default?.()) } })
await router.push('/'); await router.isReady(); app.mount('#app')
