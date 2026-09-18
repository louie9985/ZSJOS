// UTF-8. Synthetic acceptance fixture, excluded from production entrypoints.
import { createApp } from 'vue'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { setupGlobCom } from '../src/components'
import { setupElementPlus } from '../src/plugins/elementPlus'
import { useUserStore } from '../src/store/modules/user'


import '../src/styles/index.scss'

const fixture = { mode: 'success', bound: false, calls: 0, writes: 0, delay: 0, lastBinding: {} }
Object.assign(window, { bindingFixture: fixture })
async function mount() {
  setupStore(createApp({}))
  const { service } = await import('../src/config/axios/service')
  service.defaults.adapter = async config => {
  fixture.calls++
  let data: unknown = []
  let code = 0, msg = ''
  const url = config.url || ''
  const student = (id: number) => ({ personId: id, name: `测试学员${id}`, mobile: '13800000000', services: [], activatedAt: '' })
  if (url.endsWith('/student/my-page')) data = { list: [student(1), student(2)], total: 2 }
  else if (/\/student\/my\/\d+$/.test(url)) data = student(Number(url.split('/').pop()))
  else if (url.endsWith('/partner-student-link/student')) {
    const bound = fixture.bound
    if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
    if (fixture.mode === 'status-error') throw new Error('绑定状态加载失败')
    data = { bound, partnerNo: bound ? 'P-2' : undefined, partnerName: bound ? '测试兼职2' : undefined, startedAt: bound ? 1789729200000 : undefined }
  } else if (url.endsWith('/partner/page')) {
    if (fixture.mode === 'list-error') throw new Error('兼职列表加载失败')
    if (fixture.mode === 'denied') { code = 403; msg = '无权查询兼职' }
    const rows = Array.from({ length: 12 }, (_, i) => ({ id: i+1, partnerNo: `P-${i+1}`, name: `测试兼职${i+1}`, mobile: '13800000000', status: 'enabled' })).filter(row => !config.params.keyword || row.name.includes(config.params.keyword))
    data = { list: fixture.mode === 'empty' ? [] : rows.slice((config.params.pageNo-1)*10, config.params.pageNo*10), total: fixture.mode === 'empty' ? 0 : rows.length }
  } else if (url.endsWith('/partner-student-link/bind')) {
    fixture.writes++
    if (fixture.delay) await new Promise(resolve => setTimeout(resolve, fixture.delay))
    if (fixture.mode === 'conflict') { code = 1900014007; msg = '兼职账号或学员已绑定其他身份' }
    else if (fixture.mode === 'bind-denied') { code = 403; msg = '无权操作该学员' }
    else { fixture.lastBinding = config.params; fixture.bound = true; data = true }
  } else if (config.method !== 'get') throw new Error('Unexpected fixture write')
  return { config, request: { responseType: 'json' }, status: 200, statusText: 'OK', headers: {}, data: { code, msg, data } }
}
  const { default: Page } = await import('../src/views/zsjos/my-students.vue')
  const app = createApp(Page)
  setupStore(app); await setupI18n(app); setupGlobCom(app); setupElementPlus(app)
  useUserStore().permissions = new Set(location.search.includes('no-permission') ? ['zsjos:student:query-my'] : ['zsjos:student:query-my', 'zsjos:partner:manage-all'])
  const { setupAuth } = await import('../src/directives')
  setupAuth(app); app.mount('#app')
}
void mount()
