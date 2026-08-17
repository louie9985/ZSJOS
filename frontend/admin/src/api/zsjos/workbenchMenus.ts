import request from '@/config/axios'

export interface WorkbenchListItem {
  id: number
  leadNo?: string
  name?: string
  submittedName?: string
  studentName?: string
  status?: string
  assignmentStatus?: string
  ownerUserName?: string
  createTime?: number
  submittedAt?: number
  [key: string]: unknown
}
export interface WorkbenchListResult {
  list: WorkbenchListItem[]
  total: number
}

export const page = (
  endpoint: string,
  params: Record<string, unknown>
): Promise<WorkbenchListResult> => request.get({ url: endpoint, params })
export const detail = (endpoint: string, id: number) =>
  request.get({ url: endpoint, params: { id } })
export const leadCatalog = () => request.get({ url: '/zsjos/lead/product/catalog' })
export const leadSalesCandidates = (): Promise<Array<{ id: number; nickname: string }>> =>
  request.get({ url: '/zsjos/lead/sales-user/simple-list' })
export const salesOrderCatalog = () => request.get({ url: '/zsjos/sales-order/product/catalog' })
export const createLead = (data: Record<string, unknown>, selfSourced = false) =>
  request.post({
    url: selfSourced ? '/zsjos/lead/self-sourced/create' : '/zsjos/lead/create',
    data
  })
export const createExternalRepurchase = (data: Record<string, unknown>) =>
  request.post({ url: '/zsjos/sales-order/external-repurchase', data })
export const uploadSalesOrderVoucher = (file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request.upload({ url: '/zsjos/sales-order/voucher/upload', data })
}
export const decideDuplicateReview = (id: number, data: Record<string, unknown>) =>
  request.post({ url: `/zsjos/lead-duplicate-review/${id}/decision`, data })
export const decideComplaint = (id: number, data: Record<string, unknown>) =>
  request.post({ url: `/zsjos/lead-complaint/${id}/decision`, data })
export const decideAppeal = (
  id: number,
  decision: 'overturn' | 'uphold',
  data: Record<string, unknown>
) => request.put({ url: `/zsjos/lead/appeal/${id}/${decision}`, data })
export const getSalesOrder = (id: number) => request.get({ url: `/zsjos/sales-order/${id}` })
export const getMySalesOrder = (id: number) => request.get({ url: `/zsjos/sales-order/my/${id}` })
export const decideSalesOrder = (
  id: number,
  decision: 'approve' | 'reject',
  data: Record<string, unknown>
) => request.put({ url: `/zsjos/sales-order/${id}/${decision}`, data })
export const terminateSalesOrder = (id: number, data: Record<string, unknown>) =>
  request.put({ url: `/zsjos/sales-order/${id}/terminate`, data })
export const resubmitSalesOrder = (id: number, data: Record<string, unknown>) =>
  request.put({ url: `/zsjos/sales-order/${id}/resubmit`, data })
export const bpmTodoPage = (params: Record<string, unknown>): Promise<WorkbenchListResult> =>
  request.get({ url: '/bpm/task/todo-page', params })
export const updateSubordinateAccount = (id: number, status: number, reason: string) =>
  request.put({ url: `/zsjos/subordinate-sales/${id}/account-status`, data: { status, reason } })
export const updateSubordinateDispatch = (id: number, accepting: boolean, reason: string) =>
  request.put({ url: `/zsjos/subordinate-sales/${id}/dispatch-mode`, data: { accepting, reason } })
export const subordinateLeads = (
  id: number,
  params: Record<string, unknown>
): Promise<WorkbenchListResult> =>
  request.get({ url: `/zsjos/subordinate-sales/${id}/leads`, params })
export const subordinateTransferCandidates = (): Promise<Array<{ id: number; nickname: string }>> =>
  request.get({ url: '/zsjos/subordinate-sales/transfer-candidates' })
export const batchTransferSubordinateLeads = (data: Record<string, unknown>) =>
  request.post({ url: '/zsjos/subordinate-sales/leads/batch-transfer', data })
export const batchReleaseSubordinateLeads = (data: Record<string, unknown>) =>
  request.post({ url: '/zsjos/subordinate-sales/leads/batch-public-sea', data })
