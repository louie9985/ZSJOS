import { ElMessage } from 'element-plus'
const query = new URLSearchParams(location.search)
const targets = [{ id: 10, className: '原服务班', classNo: 'BJ10', homeroomUserName: '原规划师' }, { id: 11, className: '新服务班', classNo: 'BJ11', homeroomUserName: '新规划师' }]
export const state = { version: 3, closed: query.has('closed'), requests: [] as { url: string; data: unknown }[], fail: query.has('fail'), targets, holdTargets: false, targetRequests: [] as Array<(rows: typeof targets) => void> }
Object.assign(window, { teachingAdminFixture: state })
export const checkPermi = () => !query.has('denied')
export const useMessage = () => ElMessage
export default {
  async get({ url }: { url: string }) {
    if (url.endsWith('/lifecycle')) return { closed: state.closed, previousClassId: 10, availableActions: [state.closed ? 'RESTORE_SERVICE' : 'CLOSE_SERVICE'], history: [] }
    if (url.endsWith('/restore-targets')) return state.holdTargets ? await new Promise<typeof targets>(resolve => state.targetRequests.push(resolve)) : targets
    if (url.endsWith('/delivery-class/page')) return { list: [{ id: 99, classNo: 'EX20', className: '异常结业', systemClass: true, classKind: 'EXCEPTION', homeroomUserNameSnapshot: '规划师', status: 'SERVING', studentCount: 1, version: 0 }], total: 1 }
    if (url.endsWith('/class-transfer/my-page')) return { list: [], total: 0 }
    if (url.endsWith('/99/students')) return { list: [{ serviceRelationId: 1, personId: 7, studentName: '测试学员', personNo: 'TEST-7', serviceStatus: 'closed', ownerUserName: '规划师', version: 4 }], total: 1 }
    throw Error(`Unexpected fixture GET ${url}`)
  },
  async post({ url, data }: { url: string; data: unknown }) {
    state.requests.push({ url, data })
    if (state.fail) { state.fail = false; throw Error('审批取消失败，请重试') }
    state.closed = url.endsWith('/close'); state.version++; return true
  }
}
