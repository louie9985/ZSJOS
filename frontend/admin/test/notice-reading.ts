// UTF-8. Synthetic transport, no business services.
import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import 'element-plus/dist/index.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const params = new URLSearchParams(location.search)
const fixture = { fail: false, denied: false, queries: [] as unknown[], writes: 0 }
Object.assign(window, { noticeFixture: fixture })
service.defaults.adapter = async config => {
  const path = config.url || ''
  let data: unknown
  if (config.method !== 'get') { fixture.writes++; throw new Error('Read-only fixture') }
  if (fixture.denied) return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' }, data: { code: 403, msg: '无权查看阅读情况' } }
  if (fixture.fail) throw new Error('统计加载失败')
  if (path.endsWith('/dict-data/simple-list')) data = [{ dictType: 'system_notice_type', value: '2', label: '公告' }]
  else if (path.includes('/get')) data = { id: 1, title: '公告阅读验收', content: '<p>真实正文展示测试</p>', type: 2, publishStatus: params.has('draft') ? 'DRAFT' : 'PUBLISHED', attachments: [] }
  else if (path.endsWith('/read-summary')) data = { published: !params.has('draft'), rosterComplete: !params.has('legacy'), expectedCount: 100, readCount: 80, unreadCount: 20, readRate: 0.8, extraReadCount: 5, actualReadCount: 85, departments: [{ id: 10, name: '发布部门' }], extraDepartments: [{ id: 20, name: '当前部门' }] }
  else if (path.endsWith('/read-page')) {
    const query = config.params; fixture.queries.push(query)
    const count = query.scope === 'UNREAD' ? 20 : query.scope === 'EXTRA' ? 5 : query.scope === 'ACTUAL' ? 85 : query.scope === 'READ' ? 80 : 100
    const all = Array.from({ length: count }, (_, i) => ({ userId: i + 1, userName: '员工' + (i + 1), deptId: 10, deptName: query.scope === 'EXTRA' ? '当前部门' : '发布部门', profileSource: query.scope === 'EXTRA' || params.has('legacy') ? 'CURRENT' : 'SNAPSHOT', accountStatus: 0, accountDeleted: i === 0, readTime: query.scope === 'UNREAD' ? undefined : 1790000000000 }))
    const filtered = all.filter(row => !query.name || row.userName.includes(query.name))
    data = { list: filtered.slice((query.pageNo - 1) * query.pageSize, query.pageNo * query.pageSize), total: filtered.length }
  } else throw new Error('Unexpected fixture request ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' }, data: { code: 0, data } }
}
const boot = createApp({}); setupStore(boot)
const { default: Detail } = await import('../src/views/system/notice/NoticeDetail.vue')
const app = createApp({ setup() { const detail = ref(); return () => h('main', { style: 'padding:16px' }, [h('button', { onClick: () => detail.value.open(1) }, '打开公告'), h(Detail, { ref: detail })]) } })
setupStore(app); await setupI18n(app); app.use(ElementPlus); app.use(VueDOMPurifyHTML); app.mount('#app')
