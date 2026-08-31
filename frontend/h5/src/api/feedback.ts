import request from './request'
import type { ApiDateValue } from '@/utils/format'

export type FeedbackType = 'REQUIREMENT' | 'BUG' | 'SUPPORT'
export type FeedbackStatus =
  | 'APPROVING'
  | 'APPROVAL_REJECTED'
  | 'WAITING'
  | 'IN_PROGRESS'
  | 'COMPLETED'

export interface FeedbackAttachment {
  id: number
  name?: string
  type?: string
  size?: number
  url?: string
  previewUrl?: string
}

export interface FeedbackField {
  key: string
  label: string
  type: 'text' | 'textarea' | 'date' | 'dictionary' | 'upload' | 'image' | 'rating'
  required: boolean
  dictionaryType?: string
  maxRating?: number
  maxLength?: number
  options?: Array<{ value: string; label: string }>
}

export interface FeedbackForm {
  feedbackType: FeedbackType
  formId: number
  formName: string
  titleFieldKey: string
  configVersion: number
  open: boolean
  unavailableReason?: string
  fields: FeedbackField[]
}

export interface FeedbackReply {
  id: number
  authorUserId: number
  authorName?: string
  authorType: 'EMPLOYEE' | 'ADMIN' | 'PARTNER_ACCOUNT'
  content: string
  attachmentIds: number[]
  attachments: FeedbackAttachment[]
  createTime: ApiDateValue
}

export interface FeedbackItem {
  id: number
  feedbackType: FeedbackType
  feedbackNo: string
  title: string
  status: FeedbackStatus
  submitterUserId: number
  submitterName?: string
  assigneeUserId?: number
  assigneeName?: string
  latestReplySummary?: string
  lastActivityAt: ApiDateValue
  unread: boolean
  version: number
  createTime: ApiDateValue
  canResubmit: boolean
  canReply: boolean
  canComplete?: boolean
  canSurvey?: boolean
  canSubmitSurvey?: boolean
  fields?: FeedbackField[]
  values?: Record<string, unknown>
  supportTypeValue?: string
  supportTypeLabel?: string
  processInstanceId?: string
  approvalRoundNo?: number
  rejectReason?: string
  completedResult?: string
  resultAttachmentIds?: number[]
  resultAttachments?: FeedbackAttachment[]
  replies?: FeedbackReply[]
}

export interface FeedbackPortal {
  entries: Array<{
    feedbackType: FeedbackType
    title: string
    description: string
    open: boolean
    unavailableReason?: string
  }>
  recent: FeedbackItem[]
}

export interface FeedbackCreateParams {
  values: Record<string, unknown>
  configVersion: number
  idempotencyKey: string
}

const createPath: Record<FeedbackType, string> = {
  REQUIREMENT: 'requirement',
  BUG: 'bug',
  SUPPORT: 'support'
}

export function getFeedbackPortal() {
  return request.get<never, FeedbackPortal>('/zsjos/feedback/portal')
}

export function getFeedbackForm(type: FeedbackType) {
  return request.get<never, FeedbackForm>('/zsjos/feedback/form', { params: { type } })
}

export function getFeedbackPage(params: {
  pageNo: number
  pageSize: number
  feedbackType?: FeedbackType
  status?: FeedbackStatus
  keyword?: string
}) {
  return request.get<never, { list: FeedbackItem[]; total: number }>('/zsjos/feedback/my-page', { params })
}

export function getFeedbackDetail(id: number) {
  return request.get<never, FeedbackItem>(`/zsjos/feedback/${id}`)
}

export function createFeedback(type: FeedbackType, data: FeedbackCreateParams) {
  return request.post<never, number>(`/zsjos/feedback/${createPath[type]}/create`, data)
}

export function markFeedbackRead(id: number, data: { version: number; idempotencyKey: string }) {
  return request.put<never, boolean>(`/zsjos/feedback/${id}/read`, data)
}

export function replyFeedback(id: number, data: {
  content: string
  attachmentIds?: number[]
  version: number
  idempotencyKey: string
}) {
  return request.post<never, boolean>(`/zsjos/feedback/${id}/reply`, data)
}

export function uploadFeedbackAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, FeedbackAttachment>('/zsjos/feedback/file/upload', formData, {
    timeout: 120000
  })
}
