import request from './request'
import type { ApiDateValue } from '@/utils/format'

export type FeedbackStatus = 'submitted' | 'processing' | 'need_more_info' | 'resolved' | 'closed'

export interface FeedbackOption {
  value: string
  label: string
}

export interface FeedbackOptions {
  categories: FeedbackOption[]
  severities: FeedbackOption[]
}

export interface FeedbackAttachment {
  infraFileId: number
  fileUrl: string
  originalName: string
  contentType?: string
  fileSize?: number
}

export interface FeedbackEvent {
  eventId: number
  eventType: string
  eventTypeText: string
  content?: string
  createdAt: ApiDateValue
}

export interface FeedbackItem {
  id: number
  feedbackNo: string
  category: string
  categoryText: string
  severity: string
  severityText: string
  title: string
  description: string
  reproduceSteps?: string
  status: FeedbackStatus
  statusText: string
  publicReply?: string
  attachments: FeedbackAttachment[]
  events: FeedbackEvent[]
  createdAt: ApiDateValue
  updatedAt: ApiDateValue
}

export interface FeedbackCreateParams {
  category: string
  severity: string
  title: string
  description: string
  reproduceSteps?: string
  attachmentFileIds?: number[]
  clientContext: Record<string, string>
  idempotencyKey: string
}

export function getFeedbackOptions() {
  return request.get<never, FeedbackOptions>('/zsjos/feedback/options')
}

export function getFeedbackPage(params: {
  pageNo: number
  pageSize: number
  status?: FeedbackStatus
  keyword?: string
}) {
  return request.get<never, { list: FeedbackItem[]; total: number }>('/zsjos/feedback/my-page', { params })
}

export function getFeedbackDetail(id: number) {
  return request.get<never, FeedbackItem>(`/zsjos/feedback/my/${id}`)
}

export function createFeedback(data: FeedbackCreateParams) {
  return request.post<never, FeedbackItem>('/zsjos/feedback/create', data)
}

export function supplementFeedback(id: number, data: {
  content: string
  attachmentFileIds?: number[]
  idempotencyKey: string
}) {
  return request.post<never, FeedbackItem>(`/zsjos/feedback/my/${id}/supplement`, data)
}

export function uploadFeedbackAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, FeedbackAttachment>('/zsjos/feedback/attachment/upload', formData, {
    timeout: 120000
  })
}
