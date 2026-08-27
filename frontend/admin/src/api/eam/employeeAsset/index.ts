import request from '@/config/axios'

export interface EmployeeAssetItemVO {
  itemType: string
  holdingId?: number
  assetId?: number
  assetCode?: string
  stockBalanceId?: number
  name: string
  quantity: number
  unit?: string
  custodyMode?: number
  status: number
  signedAt?: Date
  returnAppliedAt?: Date
  returnResult?: number
}

export interface EmployeeAssetTaskVO {
  id: number
  type: number
  status: number
  processInstanceId?: string
  demandId?: number
  plannedLeaveTime?: Date
  remark?: string
  createTime?: Date
  items?: EmployeeAssetTaskItemVO[]
}

export interface EmployeeAssetTaskItemVO {
  id: number
  taskId: number
  assetId?: number
  holdingId?: number
  assetNameSnapshot: string
  action?: number
  transferToEmployeeId?: number
  status: number
  remark?: string
}

export interface EmployeeAssetSummaryVO {
  employeeId: number
  items: EmployeeAssetItemVO[]
  tasks: EmployeeAssetTaskVO[]
  pendingSignCount: number
  pendingReturnCount: number
  offboardingUncleared: boolean
}

export const getByEmployee = (employeeId: number) =>
  request.get<EmployeeAssetSummaryVO>({
    url: '/eam/employee-asset/get-by-employee',
    params: { employeeId }
  })

export const getTask = (id: number) =>
  request.get<EmployeeAssetTaskVO>({ url: '/eam/employee-asset/task/get', params: { id } })

export const submitProvisioning = (
  id: number,
  data: { demand: { employeeId?: number; reason?: string; items: any[] }; remark?: string }
) => request.post({ url: `/eam/employee-asset/task/${id}/provisioning`, data })

export const submitReview = (
  id: number,
  data: {
    items: Array<{ id: number; action: number; transferToEmployeeId?: number; remark?: string }>
    remark?: string
  }
) => request.post({ url: `/eam/employee-asset/task/${id}/review`, data })

export const inspectReturn = (id: number, data: { result: number; remark?: string }) =>
  request.put({ url: `/eam/employee-asset/holding/${id}/inspect-return`, data })
