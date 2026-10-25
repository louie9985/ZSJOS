// UTF-8. Isolated synthetic request adapter for production Vue components.
const query = new URLSearchParams(location.search)
const state = { requests: [] as { url: string; data?: any }[], fail: query.has('fail'), createFail: false, committedContact: '', committedCreates: 0 }
const commands = new Map<string, { personId: number; personNo: string; serviceRelationId: number; canRead: boolean }>()
Object.assign(window, { entryFixture: state })
export const checkPermi = () => !query.has('denied')
export const generateUUID = () => crypto.randomUUID()
export default {
  get: async ({ url }: { url: string }) => {
    state.requests.push({ url }); await new Promise(r => setTimeout(r, 40))
    if (state.fail) { state.fail = false; throw new Error('录入选项加载失败') }
    return { directors: [{ id: 22, name: '责任编导甲' }], classes: [{ id: 8, className: '测试正式班', homeroomUserId: 33, homeroomUserName: '班主任甲' }, { id: 7, className: '待分班', systemClass: true }], courses: [{ skuRef: 'sku1', name: '测试课程 / 标准规格' }] }
  },
  post: async ({ url, data }: { url: string; data: any }) => {
    state.requests.push({ url, data }); await new Promise(r => setTimeout(r, 40))
    if (url.endsWith('/check')) return data.mobile === '13800000001' || data.mobile === state.committedContact ? { status: 'reuse', personId: 11, personNo: 'XY-EXISTING', name: '已有学员', services: [{ serviceRelationId: 4, studentName: '原服务姓名', status: 'active', statusLabel: '服务中' }] }
      : { status: data.mobile === '13800000002' ? 'occupied' : 'new', services: [] }
    let result = commands.get(data.idempotencyKey)
    if (!result) { result = { personId: 11, personNo: 'XY-SYNTHETIC', serviceRelationId: 44 + state.committedCreates++, canRead: false }; commands.set(data.idempotencyKey, result) }
    if (state.createFail) { state.createFail = false; state.committedContact = data.mobile; throw new Error('模拟响应丢失，请重试') }
    return result
  }
}
