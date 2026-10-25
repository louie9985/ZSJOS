// UTF-8. Synthetic transport only; never accesses shared business services.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import StudentEntryPage from '../src/pages/StudentEntryPage'
import { http } from '../src/services/api'
import '../src/styles/index.css'
const query = new URLSearchParams(location.search)
const fixture = { requests: [] as Record<string, unknown>[], optionsFail: query.has('fail'), createFail: false, committedContact: '', committedCreates: 0 }
const commands = new Map<string, { personId: number; personNo: string; serviceRelationId: number; canRead: boolean }>()
Object.assign(window, { entryFixture: fixture })
http.defaults.adapter = async config => {
  const data = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  fixture.requests.push({ url: config.url, data })
  await new Promise(resolve => setTimeout(resolve, 40))
  let result: unknown
  if (config.url?.endsWith('/options')) {
    if (fixture.optionsFail) { fixture.optionsFail = false; throw new Error('录入选项加载失败') }
    result = { directors: [{ id: 22, name: '责任编导甲' }], classes: [{ id: 8, classNo: 'TEST8', className: '测试正式班', homeroomUserId: 33, homeroomUserName: '班主任甲', systemClass: false }, { id: 7, className: '待分班', classNo: 'PENDING', systemClass: true, classKind: 'PENDING' }], courses: [{ skuRef: 'sku1', name: '测试课程 / 标准规格' }] }
  } else if (config.url?.endsWith('/check')) {
    result = data.mobile === '13800000001' || data.mobile === fixture.committedContact ? { status: 'reuse', personId: 11, personNo: 'XY-EXISTING', name: '已有学员', services: [{ serviceRelationId: 4, studentName: '原服务姓名', status: 'active', statusLabel: '服务中' }] }
      : { status: data.mobile === '13800000002' ? 'occupied' : 'new', services: [] }
  } else if (config.url?.endsWith('/create')) {
    result = commands.get(data.idempotencyKey)
    if (!result) {
      result = { personId: 11, personNo: 'XY-SYNTHETIC', serviceRelationId: 44 + fixture.committedCreates++, canRead: false }
      commands.set(data.idempotencyKey, result as NonNullable<ReturnType<typeof commands.get>>)
    }
    if (fixture.createFail) { fixture.createFail = false; fixture.committedContact = data.mobile; throw new Error('模拟响应丢失，请重试') }
  } else throw new Error(`Unexpected fixture request ${config.url}`)
  return { data: { code: 0, data: result, msg: '' }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<App><StudentEntryPage permissions={query.has('denied') ? [] : ['zsjos:student:create']} /></App>)
