// UTF-8. Synthetic transport only; uses real Vue management/detail/editor components.
import { createApp, h, ref } from 'vue'
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
const fixture = { writes: [] as unknown[], queries: [] as unknown[], optionCalls: 0 }
Object.assign(window, { noticeOriginFixture: fixture })
const notice = { id: 1, title: '十月考试工作安排', type: 2, content: '<p>请及时查看考试安排。</p>', publishStatus: 'PUBLISHED', audienceType: params.has('target') ? 'TARGET' : 'ALL', targetDeptIds: [20], targetUserIds: [], attachments: [], publishTime: 1791388800000,
  ...(params.has('legacy') ? {} : { sourceDeptId: 10, sourceDeptName: '考务部', publisherName: '测试发布人', audienceSummary: params.has('target') ? '综合行政部（含子部门）' : '全体员工' }) }
const treeOptions = {
  defaultSourceDeptId: 10,
  departments: [{ id: 10, parentId: 0, name: '测试中心' }, { id: 20, parentId: 10, name: '测试一部' },
    { id: 30, parentId: 10, name: '测试二部' }, { id: 40, parentId: 20, name: '测试小组' }],
  users: [{ id: 101, deptId: 10, nickname: '中心员工', selectable: true },
    { id: 102, deptId: 20, nickname: '一部员工', selectable: true },
    { id: 103, deptId: 30, nickname: '二部员工', selectable: true },
    { id: 104, deptId: 40, nickname: '小组员工', selectable: true },
    { id: 105, deptId: 20, nickname: '禁用员工', selectable: false, disabledReason: '未获得公告阅读权限' },
    { id: 106, nickname: '未分配员工', selectable: true }]
}
if (params.has('recipient-tree')) {
  Object.assign(notice, { publishStatus: 'DRAFT', audienceType: 'TARGET', targetDeptIds: [10], targetUserIds: [], sourceDeptId: 10 })
  if (params.has('saved')) Object.assign(notice, JSON.parse(sessionStorage.getItem('notice-tree-saved') || '{}'))
}
service.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/dict-data/simple-list')) data = [{ dictType: 'system_notice_type', value: '2', label: '公告' }]
  else if (url.endsWith('/recipient-options')) {
    fixture.optionCalls++
    if (params.has('error') && fixture.optionCalls === 1) throw new Error('部门选项加载失败')
    data = params.has('recipient-tree') ? treeOptions : { defaultSourceDeptId: 10, departments: params.has('empty') ? [] : [{ id: 10, parentId: 0, name: '考务部' }, { id: 20, parentId: 0, name: '综合行政部' }], users: [] }
  } else if (url.endsWith('/create') || url.endsWith('/update') || url.includes('/publish')) { fixture.writes.push({ url, data: config.data }); if (params.has('recipient-tree') && config.data) sessionStorage.setItem('notice-tree-saved', config.data); data = 1 }
  else if (url.includes('/get')) data = notice
  else if (url.endsWith('/page')) {
    fixture.queries.push(config.params)
    const keyword = config.params?.title
    const list = !keyword || ['考务部', '测试发布人', notice.title].some(text => text.includes(keyword)) ? [notice] : []
    data = { list, total: list.length }
  } else throw new Error(`Unexpected fixture request ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: 'json' }, data: { code: 0, data } }
}
const boot = createApp({}); setupStore(boot)
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const { default: Detail } = await import('../src/views/system/notice/NoticeDetail.vue')
const { default: Editor } = await import('../src/views/system/notice/NoticeEditor.vue')
const { default: Page } = await import('../src/views/system/notice/index.vue')
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Page }] })
const app = createApp({ setup() { const detail = ref(); return () => h('main', { style: 'padding:16px' }, params.has('editor') ? [h(Editor, { id: params.has('draft') || params.has('saved') ? 1 : undefined })] : params.has('detail') ? [h('button', { onClick: () => detail.value.open(1) }, '打开公告'), h(Detail, { ref: detail })] : [h(Page)]) } })
setupStore(app); useUserStore().permissions = new Set(['query', 'create', 'update', 'publish'].map(action => `system:notice:${action}`))
await setupI18n(app); app.use(ElementPlus); app.use(VueDOMPurifyHTML); app.use(router); hasPermi(app)
app.component('ContentWrap', { render() { return h('section', this.$slots.default?.()) } })
await router.isReady(); app.mount('#app')
