import request from '@/config/axios'
import { approveTask, rejectTask, signCreateTask } from '@/api/bpm/task'
import type { ProductSpec } from '@/utils/productSpecs'

export const FINANCE_ORDER_PROCESS_KEY = 'zsjos_sales_order_finance_approval'
export type OrderApprovalEntry = 'business' | 'bpm'
export type OrderApprovalAction = 'approve' | 'reject' | 'sign'
export interface ApprovalTaskAction {
  taskId: string
  action: OrderApprovalAction
  reason: string
  type?: 'before' | 'after'
  userIds?: number[]
}
export interface ApprovalTaskRecord {
  id: string
  status: number
  assigneeName?: string
  ownerName?: string
  reason?: string
  createTime?: string
  endTime?: string
}
export interface OrderApprovalDetail extends Record<string, unknown> {
  orderNo: string
  processInstanceId: string
  approvalTask?: {
    id: string
    name: string
    canAct: boolean
    canSign: boolean
    reasonRequire: boolean
  }
  approvalProgress?: {
    nodes: Array<{ id: string; name: string; status: number; tasks?: ApprovalTaskRecord[] }>
  }
  approvalComments?: Array<{
    id: string
    message: string
    createTime?: string
    user?: { nickname: string }
  }>
  paymentVouchers?: Array<{ infraFileId: number; fileUrl?: string; originalName: string }>
  items?: Array<{
    id: number
    productName?: string
    skuName?: string
    categoryPath?: string[]
    attrValues?: Record<string, string>
    specs?: ProductSpec[]
    actualAmount?: number
  }>
  giftItemSnapshots?: Array<{ code: string; name: string; path?: string[] }>
}

export const getFinanceOrderApprovalDetail = (
  taskId: string,
  entry: OrderApprovalEntry,
  done: boolean
): Promise<OrderApprovalDetail> =>
  request.get({
    url:
      entry === 'business'
        ? '/zsjos/sales-order/approval/business-detail'
        : '/zsjos/sales-order/approval/native-detail',
    params: { taskId, done },
    preserveBusinessError: true
  })

export const getFinanceOrderSignCandidates = (
  taskId: string,
  entry: OrderApprovalEntry
): Promise<Array<{ id: number; nickname: string }>> =>
  request.get({
    url: '/zsjos/sales-order/approval/sign-candidates',
    params: { taskId, entry },
    preserveBusinessError: true
  })

export const actOnFinanceOrder = (entry: OrderApprovalEntry, data: ApprovalTaskAction) => {
  if (entry === 'business') {
    return request.put({
      url: '/zsjos/sales-order/approval/native-action',
      data,
      preserveBusinessError: true
    })
  }
  const { taskId: id, action, reason, type, userIds } = data
  if (action === 'sign') return signCreateTask({ id, reason, type, userIds }, true)
  return action === 'approve' ? approveTask({ id, reason }, true) : rejectTask({ id, reason }, true)
}

export const commentOnFinanceOrder = (entry: OrderApprovalEntry, taskId: string, message: string) =>
  request.post({
    url: '/zsjos/sales-order/approval/comment',
    params: { entry },
    data: { taskId, message },
    preserveBusinessError: true
  })
