import request from '@/config/axios'
import type { Timestamp } from '../types'

export interface LeadFollowUpImageVO {
  infraFileId: number
  originalName: string
  contentType: string
  fileSize: number
  sort: number
  url?: string
}

export interface LeadFollowUpVO {
  id: number
  operatorUserId: number
  operatorName?: string
  occurredAt: Timestamp
  firstInAssignment: boolean
  method: string
  methodLabel: string
  result: string
  resultLabel: string
  categoryBefore: string
  categoryBeforeLabel: string
  categoryAfter: string
  categoryAfterLabel: string
  remark?: string
  nextFollowUpAt?: Timestamp
  images: LeadFollowUpImageVO[]
}

export const getLeadFollowUpPage = (
  leadId: number,
  pageNo = 1,
  pageSize = 100
): Promise<PageResult<LeadFollowUpVO[]>> =>
  request.get({ url: `/zsjos/lead/${leadId}/follow-ups/page`, params: { pageNo, pageSize } })
