import { createIdempotencyKey } from './idempotency'
import { http, unwrap, type PageResult } from './api'
import type { Timestamp } from './time'

export type FeedbackType = 'REQUIREMENT' | 'BUG' | 'SUPPORT'
export type FeedbackStatus =
  | 'APPROVING'
  | 'APPROVAL_REJECTED'
  | 'WAITING'
  | 'IN_PROGRESS'
  | 'COMPLETED'

export type FeedbackAttachment = {
  id: number
  name?: string
  type?: string
  size?: number
  url?: string
}

export type FeedbackField = {
  key: string
  label: string
  type: 'text' | 'textarea' | 'date' | 'dictionary' | 'upload' | 'image' | 'rating'
  required: boolean
  dictionaryType?: string
  maxRating?: number
  maxLength?: number
  options?: Array<{ value: string; label: string }>
}

export type FeedbackForm = {
  feedbackType: FeedbackType
  formId: number
  formName: string
  titleFieldKey: string
  configVersion: number
  open: boolean
  unavailableReason?: string
  fields: FeedbackField[]
}

export type FeedbackReply = {
  id: number
  authorUserId: number
  authorName?: string
  authorType: 'EMPLOYEE' | 'ADMIN'
  content: string
  attachmentIds: number[]
  attachments: FeedbackAttachment[]
  createTime: Timestamp
}

export type FeedbackRecord = {
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
  lastActivityAt: Timestamp
  unread: boolean
  version: number
  createTime: Timestamp
  canResubmit: boolean
  canReply: boolean
  canSubmitSurvey: boolean
  fields?: FeedbackField[]
  values?: Record<string, unknown>
  supportTypeValue?: string
  supportTypeLabel?: string
  approvalRoundNo?: number
  rejectReason?: string
  completedResult?: string
  resultAttachments?: FeedbackAttachment[]
  replies?: FeedbackReply[]
  survey?: {
    status: 'PENDING' | 'SUBMITTED'
    fields: FeedbackField[]
    values?: Record<string, unknown>
    requestedAt: Timestamp
    submittedAt?: Timestamp
  }
}

export type FeedbackPortal = {
  entries: Array<{
    feedbackType: FeedbackType
    title: string
    description: string
    open: boolean
    unavailableReason?: string
  }>
  recent: FeedbackRecord[]
}

const createPath: Record<FeedbackType, string> = {
  REQUIREMENT: 'requirement',
  BUG: 'bug',
  SUPPORT: 'support'
}

export const feedbackApi = {
  portal: async () => unwrap<FeedbackPortal>(await http.get('/zsjos/feedback/portal')),
  form: async (type: FeedbackType) =>
    unwrap<FeedbackForm>(await http.get('/zsjos/feedback/form', { params: { type } })),
  create: async (type: FeedbackType, configVersion: number, values: Record<string, unknown>) =>
    unwrap<number>(
      await http.post(`/zsjos/feedback/${createPath[type]}/create`, {
        configVersion,
        values,
        idempotencyKey: createIdempotencyKey()
      })
    ),
  resubmit: async (
    id: number,
    version: number,
    configVersion: number,
    values: Record<string, unknown>
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/feedback/${id}/resubmit`, {
        version,
        configVersion,
        values,
        idempotencyKey: createIdempotencyKey()
      })
    ),
  myPage: async (params: {
    pageNo: number
    pageSize: number
    feedbackType?: FeedbackType
    status?: FeedbackStatus
  }) => unwrap<PageResult<FeedbackRecord>>(await http.get('/zsjos/feedback/my-page', { params })),
  detail: async (id: number) => unwrap<FeedbackRecord>(await http.get(`/zsjos/feedback/${id}`)),
  markRead: async (id: number, version: number) =>
    unwrap<boolean>(
      await http.put(`/zsjos/feedback/${id}/read`, {
        version,
        idempotencyKey: createIdempotencyKey()
      })
    ),
  reply: async (id: number, version: number, content: string, attachmentIds: number[]) =>
    unwrap<boolean>(
      await http.post(`/zsjos/feedback/${id}/reply`, {
        version,
        content,
        attachmentIds,
        idempotencyKey: createIdempotencyKey()
      })
    ),
  submitSurvey: async (id: number, version: number, values: Record<string, unknown>) =>
    unwrap<boolean>(
      await http.post(`/zsjos/feedback/${id}/survey`, {
        version,
        values,
        idempotencyKey: createIdempotencyKey()
      })
    ),
  upload: async (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return unwrap<FeedbackAttachment>(await http.post('/zsjos/feedback/file/upload', data))
  }
}
