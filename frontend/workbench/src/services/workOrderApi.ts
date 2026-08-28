import { createIdempotencyKey } from './idempotency'
import { http, unwrap, type PageResult } from './api'

export type WorkOrderStatus = 'PENDING_ACCEPT' | 'AVAILABLE' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'COMPLETED' | 'REJECTED_INVALID' | 'WITHDRAWN' | 'TERMINATED_UNQUALIFIED'
export type WorkOrderField = { key: string; label: string; type: string; required?: boolean; dictionaryType?: string }
export type WorkOrderTimeline = { operation: string; fromStatus?: string; toStatus: string; operatorName?: string; reason?: string; resultRemark?: string; attachmentIds?: number[]; roundNo?: number; operatedAt?: string }
export type WorkOrder = { id: number; orderNo: string; businessType?: string; businessId?: number; sceneCode: string; sceneName?: string; status: WorkOrderStatus; sourceName?: string; targetName?: string; targetDeptId?: number; remark?: string; completionRemark?: string; currentRound: number; fields?: WorkOrderField[]; values?: Record<string, unknown>; attachmentIds?: number[]; requestAttachments?: WorkOrderFile[]; completionAttachmentIds?: number[]; timeline?: WorkOrderTimeline[]; availableActions?: string[]; version: number; createTime?: string; claimedAt?: string; completedAt?: string; acceptedAt?: string }
export type WorkOrderTemplate = { id: number; code: string; name: string; remark?: string; categoryValue?: string; categoryLabel?: string; processorType?: string; fields: WorkOrderField[]; allowedAssignmentTypes?: string[]; targetQualificationMode?: string; status?: number; lifecycleStatus?: string; version?: number }
export type WorkOrderCandidate = { id: number; name: string; deptId?: number }
export type WorkOrderDepartment = { id: number; name: string }
export type WorkOrderUser = { id: number; nickname: string }
export type WorkOrderDictOption = { dictType: string; value: string; label: string }
export type WorkOrderFile = { id: number; name: string; url?: string; type?: string; size?: number }
export type WorkOrderAccount = { id: number; accountNo?: string; nickname?: string; platformLabelSnapshot?: string }

const action = async (id: number, path: string, version: number, body: Record<string, unknown> = {}) => unwrap<boolean>(await http.post(`/zsjos/work-order/${id}/${path}`, { version, idempotencyKey: createIdempotencyKey(), ...body }))

export const workOrderApi = {
  upload: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<WorkOrderFile>(await http.post('/zsjos/work-order/file/upload', data)) },
  templates: async () => unwrap<PageResult<WorkOrderTemplate>>(await http.get('/zsjos/work-order/catalog', { params: { pageNo: 1, pageSize: 100 } })),
  template: async (code: string) => {
    const page = unwrap<PageResult<WorkOrderTemplate>>(await http.get('/zsjos/work-order/catalog', { params: { pageNo: 1, pageSize: 100 } }))
    const item = page.list.find(candidate => candidate.code === code)
    if (!item) throw new Error('该工单模板当前不可发起')
    return item
  },
  candidates: async (sceneCode: string, keyword?: string) => unwrap<PageResult<WorkOrderCandidate>>(await http.get('/zsjos/work-order/candidate-page', { params: { sceneCode, keyword, pageNo: 1, pageSize: 100 } })),
  candidateDepartments: async (sceneCode: string) => unwrap<PageResult<WorkOrderCandidate>>(await http.get('/zsjos/work-order/candidate-department-page', { params: { sceneCode, pageNo: 1, pageSize: 100 } })),
  departments: async () => unwrap<WorkOrderDepartment[]>(await http.get('/system/dept/simple-list')),
  users: async () => unwrap<WorkOrderUser[]>(await http.get('/system/user/simple-list')),
  dictionaries: async () => unwrap<WorkOrderDictOption[]>(await http.get('/system/dict-data/simple-list')),
  accounts: async () => unwrap<PageResult<WorkOrderAccount>>(await http.get('/zsjos/media-account/page', { params: { pageNo: 1, pageSize: 100 } })),
  create: async (data: { sceneCode: string; relatedAccountId?: number; targetUserId?: number; targetDeptId?: number; remark: string; values: Record<string, unknown>; attachmentIds?: number[] }) => unwrap<number>(await http.post('/zsjos/work-order/create', { ...data, idempotencyKey: createIdempotencyKey() })),
  available: async (pageNo = 1, pageSize = 20) => unwrap<PageResult<WorkOrder>>(await http.get('/zsjos/work-order/pool', { params: { pageNo, pageSize } })),
  mine: async (pageNo = 1, pageSize = 20, view?: string, status?: string) => unwrap<PageResult<WorkOrder>>(await http.get('/zsjos/work-order/my-page', { params: { pageNo, pageSize, view, status } })),
  detail: async (id: number) => unwrap<WorkOrder>(await http.get(`/zsjos/work-order/${id}`)),
  take: (id: number, version: number) => action(id, 'take', version),
  claim: (id: number, version: number) => action(id, 'claim', version),
  reject: (id: number, version: number, reason: string) => action(id, 'reject', version, { reason }),
  withdraw: (id: number, version: number, reason: string) => action(id, 'withdraw', version, { reason }),
  complete: (id: number, version: number, resultRemark: string, attachmentIds: number[] = []) => action(id, 'complete', version, { resultRemark, attachmentIds }),
  accept: (id: number, version: number) => action(id, 'accept', version),
  terminate: (id: number, version: number, reason: string) => action(id, 'terminate', version, { reason }),
  rework: (id: number, version: number, reason: string) => action(id, 'return', version, { reason })
}
