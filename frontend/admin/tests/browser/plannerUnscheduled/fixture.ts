import { ElMessage } from 'element-plus'
const query = new URLSearchParams(location.search)
export const rows = [
  { id: 1, classNo: 'PENDING', className: '待分班', classKind: 'PENDING', systemClass: true, status: 'SERVING', studentCount: 0, version: 0 },
  { id: 2, classNo: 'US20', className: '未确定考期', classKind: 'UNSCHEDULED', systemClass: true, homeroomUserId: 20, homeroomUserNameSnapshot: '规划师甲', status: 'SERVING', studentCount: 2, version: 0 },
  { id: 3, classNo: 'BJ3', className: '正式班', classKind: 'NORMAL', systemClass: false, homeroomUserId: 20, homeroomUserNameSnapshot: '规划师甲', status: 'SERVING', studentCount: 1, version: 0 },
  { id: 4, classNo: 'EX20', className: '异常结业', classKind: 'EXCEPTION', systemClass: true, homeroomUserId: 20, homeroomUserNameSnapshot: '规划师甲', status: 'SERVING', studentCount: 1, version: 0 },
]
const record = { id: 1, orderId: 99, orderItemId: 101, orderNo: 'TEST-99', studentName: '验收学员', productName: '履约产品', status: 'pending', version: 0, assignmentMode: 'class_per_item', assignable: true, completable: false, classAssignments: [{ orderItemId: 101, productName: '履约产品', classId: undefined as number | undefined }], items: [], routes: [] }
export const state = { requests: [] as unknown[], fail: query.has('fail') }
Object.assign(window, { unscheduledFixture: state })
export const useUserStore = () => ({ getPermissions: new Set(query.has('denied') ? [] : ['zsjos:registration:update']) })
export const useMessage = () => ElMessage
export const checkPermi = () => !query.has('denied')
export default {
  async get({ url }: { url: string }) {
    if (url.endsWith('/pool-page')) return { list: [record], total: 1 }
    if (url === '/zsjos/registration/1') return structuredClone(record)
    if (url.endsWith('/delivery-class/page')) return { list: rows, total: rows.length }
    if (url.endsWith('/delivery-class/options')) {
      if (state.fail) { state.fail = false; throw Error('班级候选加载失败') }
      return rows.filter(r => r.classKind !== 'EXCEPTION').map(r => ({ ...r, homeroomUserName: r.homeroomUserNameSnapshot }))
    }
    throw Error('Isolated unexpected GET '+url)
  },
  async put({ url, data }: { url: string; data: { assignments: { classId: number }[] } }) {
    if (!url.endsWith('/class-assignments')) throw Error('Unexpected mutation')
    state.requests.push(data); record.classAssignments[0].classId = data.assignments[0].classId
    return structuredClone(record)
  }
}
