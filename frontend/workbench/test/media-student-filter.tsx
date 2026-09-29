// UTF-8. Synthetic API adapter; exercises the real page without external data or writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import { WorkbenchPageNavigation, useWorkbenchPageGuard } from '../src/components/WorkbenchPageNavigation'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { api, http, type AdvancedFilterGroup, type AdvancedFilterTemplate } from '../src/services/api'
import '../src/styles/index.css'

const students = Array.from({ length: 46 }, (_, i) => ({ personId: i + 1, personNo: `TEST-${i + 1}`,
  name: `筛选学员${i + 1}`, inServicePeriod: i !== 1, services: [], accounts: [],
  owner: i === 1 ? '21' : '20', platform: i === 2 ? 'wx' : 'dy', status: 'good' }))
const condition = (key: string, value: string) => ({ fieldKey: `mediaAccount.${key}`, operator: 'in', value: [value] })
const operatorFilter: AdvancedFilterGroup = { logic: 'AND', conditions: [condition('ownerOperatorUserId', '20')], groups: [] }
const combined: AdvancedFilterGroup = { ...operatorFilter, conditions: [...operatorFilter.conditions, condition('platform', 'dy')] }
const fixture = { failure: location.search.includes('catalog-error') ? 'catalog' : location.search.includes('dict-error') ? 'dict' : '', nextDelay: 0, allowNavigation: true, queries: [] as Record<string, unknown>[], detailRequests: [] as number[],
  templates: [{ id: 1, scene: 'media_student', pageKey: 'media_students', scope: 'personal', name: '运营甲', filter: operatorFilter },
    { id: 2, scene: 'media_student', pageKey: 'media_students', scope: 'personal', name: '甲的抖音', filter: combined }] as AdvancedFilterTemplate[] }
Object.assign(window, { mediaFilterFixture: fixture, invalidateFilterDict: api.invalidateDictDataCache })
function matches(student: typeof students[number], group?: AdvancedFilterGroup): boolean {
  if (!group) return true
  const values: Record<string, string> = { 'mediaAccount.ownerOperatorUserId': student.owner, 'mediaAccount.platform': student.platform, 'mediaAccount.currentStatus': student.status }
  const results = [...group.conditions.map(c => c.operator === 'in' ? (c.value as string[]).includes(values[c.fieldKey]) : c.operator === 'not_in' ? !(c.value as string[]).includes(values[c.fieldKey]) : c.operator === 'is_empty' ? !values[c.fieldKey] : !!values[c.fieldKey]), ...group.groups.map(g => matches(student, g))]
  return group.logic === 'OR' ? results.some(Boolean) : results.every(Boolean)
}
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/media-students/page') || url.endsWith('/media-students/search-page')) {
    const query = config.method === 'post' ? JSON.parse(config.data) : config.params
    fixture.queries.push({ ...query, method: config.method })
    const delay = fixture.nextDelay; fixture.nextDelay = 0
    const filtered = students.filter(x => (query.inServicePeriod === undefined || x.inServicePeriod === query.inServicePeriod)
      && (!query.keyword || x.name.includes(query.keyword)) && matches(x, query.advancedFilter))
    if (delay) await new Promise(resolve => setTimeout(resolve, delay))
    if (fixture.failure === 'list') throw new Error('筛选查询失败，请重试')
    const start = (query.pageNo - 1) * query.pageSize
    data = { list: filtered.slice(start, start + query.pageSize), total: filtered.length }
  } else if (/media-students\/\d+$/.test(url)) {
    const id = Number(url.split('/').pop()); fixture.detailRequests.push(id)
    data = { student: students.find(x => x.personId === id), canUpdateServicePeriod: false,
      accounts: [], contents: [], positioningCards: [], positioningDrafts: [] }
  } else if (url.endsWith('/advanced-filter/catalog')) {
    if (fixture.failure === 'catalog') throw new Error('目录不可用')
    const field = (key: string, label: string, source?: string) => ({ fieldKey: `mediaAccount.${key}`, label, group: '账号', valueType: 'select', operators: ['in', 'not_in', 'is_empty', 'is_not_empty'], optionSource: source,
      options: source ? [] : [{ value: '20', label: '运营甲' }, { value: '21', label: '运营乙' }] })
    data = { fields: [field('ownerOperatorUserId', '责任运营'), field('platform', '账号平台', 'dict:zsjos_account_platform'), field('currentStatus', '账号状态', 'dict:zsjos_media_account_current_status')] }
  } else if (url.endsWith('/advanced-filter-template/visible-list')) {
    data = fixture.templates
  } else if (url.endsWith('/advanced-filter-template/personal')) {
    fixture.templates.push({ ...JSON.parse(config.data), id: fixture.templates.length + 1, scope: 'personal' }); data = fixture.templates.length
  } else if (url.includes('/dict-data/')) {
    if (fixture.failure === 'dict') throw new Error('字典不可用')
    data = [{ dictType: 'zsjos_media_account_current_status', value: 'good', label: '状态良好' }, { dictType: 'zsjos_account_platform', value: 'dy', label: '抖音' }, { dictType: 'zsjos_account_platform', value: 'wx', label: '视频号' }]
  } else if (config.method !== 'get') throw new Error(`Unexpected fixture write: ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function FixtureGuard() { useWorkbenchPageGuard('/zsjos/media-students', async () => fixture.allowNavigation); return null }
createRoot(document.getElementById('root')!).render(<ConfigProvider><ThemeProvider><App><MemoryRouter initialEntries={['/zsjos/media-students']}>
  <WorkbenchPageNavigation canOpen={() => true}><FixtureGuard /><div style={{ height: '100vh' }}><MediaStudentsPage permissions={['zsjos:media-student:query-my']} /></div></WorkbenchPageNavigation>
</MemoryRouter></App></ThemeProvider></ConfigProvider>)
