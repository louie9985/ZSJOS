import request from '@/config/axios'
import type { AdvancedFilterGroup } from '../advancedFilter'

export interface SalesOrderSupervisorInboxVO {
  id: number
  orderId: number
  orderNo: string
  studentName: string
  approvalRoundId: number
  taskDefinitionKey: 'registrationReview' | 'financeReview'
  taskId: string
  requesterUserId: number
  requesterUserName?: string
  supervisorUserId: number
  requestReason: string
  decisionReason?: string
  status: 'pending' | 'confirmed' | 'rejected' | 'cancelled'
  requestedAt?: number
  decidedAt?: number
  version: number
  orderVersion: number
  roundVersion: number
}

export interface SalesOrderDetailVO {
  id: number
  orderNo: string
  studentName: string
  studentMobile?: string
  orderType?: string
  status: string
  totalAmount?: number
  paidAmount?: number
  approvalRoundNo?: number
  submittedAt?: number
  [key: string]: unknown
}

export interface SupervisorDecisionReqVO {
  confirmationId: number
  taskId: string
  reason: string
  approvalRoundId: number
  orderVersion: number
  roundVersion: number
  confirmationVersion: number
  idempotencyKey: string
}

export const getInboxPage = (params: {
  pageNo: number
  pageSize: number
  handled: boolean
  keyword?: string
  advancedFilter?: AdvancedFilterGroup
}): Promise<{ list: SalesOrderSupervisorInboxVO[]; total: number }> =>
  params.advancedFilter
    ? request.post({ url: '/zsjos/sales-order/supervisor-confirmation/search-page', data: params })
    : request.get({ url: '/zsjos/sales-order/supervisor-confirmation/inbox-page', params })

export const getSalesOrder = (id: number): Promise<SalesOrderDetailVO> =>
  request.get({ url: `/zsjos/sales-order/${id}` })

export const decide = (
  orderId: number,
  decision: 'confirm' | 'reject',
  data: SupervisorDecisionReqVO
) => request.put({ url: `/zsjos/sales-order/${orderId}/supervisor-confirmation/${decision}`, data })
