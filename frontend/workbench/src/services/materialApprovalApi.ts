import { http, unwrap, type PageResult } from './api'
import type { MaterialVersion } from './materialApi'
import type { Timestamp } from './time'
export type MaterialApproval = {
  task: { id: string; status?: number; reason?: string; createTime?: Timestamp; endTime?: Timestamp }
  versionId: number
  materialNo: string
  title: string
  snapshotAvailable: boolean
  snapshot?: MaterialVersion
}
export const MATERIAL_APPROVAL_INVALID_TASK = 1_900_020_041
const ROOT = '/zsjos/material-approval'
export const materialApprovalApi = {
  types: async () => unwrap<Array<{ code: string; name: string }>>(await http.get(`${ROOT}/types`)),
  page: async (params: { typeCode: string; done: boolean; pageNo: number; pageSize: number }) =>
    unwrap<PageResult<MaterialApproval>>(await http.get(`${ROOT}/page`, { params })),
  get: async (versionId: number, taskId: string, done: boolean) =>
    unwrap<MaterialApproval>(await http.get(`${ROOT}/get`, { params: { versionId, taskId, done } })),
  decide: async (action: 'approve' | 'reject', versionId: number, taskId: string, reason: string) =>
    unwrap<boolean>(await http.post(`${ROOT}/${action}`, { versionId, taskId, reason }))
}
