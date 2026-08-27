import request from '@/config/axios'

export type FeedbackType = 'REQUIREMENT' | 'BUG' | 'SUPPORT' | 'SURVEY'
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

export interface FeedbackReply {
  id: number
  authorUserId: number
  authorName?: string
  authorType: 'EMPLOYEE' | 'ADMIN'
  content: string
  attachmentIds: number[]
  attachments: FeedbackAttachment[]
  createTime: number
}

export interface FeedbackRecord {
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
  lastActivityAt: number
  unread: boolean
  version: number
  createTime: number
  canReply: boolean
  canComplete: boolean
  canSurvey: boolean
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
  survey?: {
    status: 'PENDING' | 'SUBMITTED'
    formId: number
    fields: FeedbackField[]
    values?: Record<string, unknown>
    requestedAt: number
    submittedAt?: number
  }
}

export interface FeedbackPageParams {
  pageNo: number
  pageSize: number
  status?: FeedbackStatus
  assigneeUserId?: number
  keyword?: string
  createTime?: string[]
}

export interface FeedbackConfig {
  feedbackType: FeedbackType
  formId: number
  formName?: string
  titleFieldKey: string
  dispatcherUserIds: number[]
  approvalEnabled: boolean
  bpmProcessDefinitionKey?: string
  version: number
  incompatibleFields: string[]
}

export interface FeedbackFormOption {
  id: number
  name: string
  incompatibleFields: string[]
  requiredTextFieldKeys: string[]
  requiredRatingFieldKeys: string[]
}

export interface FeedbackProcessOption {
  id: string
  key: string
  name: string
  version: number
}

export interface FeedbackUserOption {
  id: number
  nickname: string
}

const typePath: Record<Exclude<FeedbackType, 'SURVEY'>, string> = {
  REQUIREMENT: 'requirement',
  BUG: 'bug',
  SUPPORT: 'support'
}

export const getFeedbackPage = (
  type: Exclude<FeedbackType, 'SURVEY'>,
  params: FeedbackPageParams
) => request.get({ url: `/zsjos/feedback-management/${typePath[type]}/page`, params })

export const getFeedback = (id: number) =>
  request.get<FeedbackRecord>({ url: `/zsjos/feedback-management/${id}` })

export const assignFeedback = (id: number, assigneeUserId: number, version: number) =>
  request.put({
    url: `/zsjos/feedback-management/${id}/assign`,
    data: { assigneeUserId, version, idempotencyKey: crypto.randomUUID() }
  })

export const replyFeedback = (
  id: number,
  content: string,
  attachmentIds: number[],
  version: number
) =>
  request.post({
    url: `/zsjos/feedback-management/${id}/reply`,
    data: { content, attachmentIds, version, idempotencyKey: crypto.randomUUID() }
  })

export const completeFeedback = (
  id: number,
  result: string,
  attachmentIds: number[],
  version: number
) =>
  request.post({
    url: `/zsjos/feedback-management/${id}/complete`,
    data: { result, attachmentIds, version, idempotencyKey: crypto.randomUUID() }
  })

export const requestFeedbackSurvey = (id: number, version: number) =>
  request.post({
    url: `/zsjos/feedback-management/${id}/survey`,
    data: { version, idempotencyKey: crypto.randomUUID() }
  })

export const uploadFeedbackFile = (file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request.post<FeedbackAttachment>({
    url: '/zsjos/feedback/file/upload',
    data,
    headersType: 'multipart/form-data'
  })
}

export const getFeedbackConfigs = () =>
  request.get<FeedbackConfig[]>({ url: '/zsjos/feedback-management/settings/list' })

export const saveFeedbackConfig = (config: FeedbackConfig) =>
  request.put({
    url: '/zsjos/feedback-management/settings',
    data: {
      feedbackType: config.feedbackType,
      formId: config.formId,
      titleFieldKey: config.titleFieldKey,
      dispatcherUserIds: config.dispatcherUserIds,
      approvalEnabled: config.approvalEnabled,
      bpmProcessDefinitionKey: config.bpmProcessDefinitionKey,
      version: config.version,
      idempotencyKey: crypto.randomUUID()
    }
  })

export const getFeedbackCandidates = (type: Exclude<FeedbackType, 'SURVEY'>) =>
  request.get<FeedbackUserOption[]>({
    url: '/zsjos/feedback-management/settings/candidates',
    params: { type }
  })

export const getFeedbackFormOptions = () =>
  request.get<FeedbackFormOption[]>({ url: '/zsjos/feedback-management/settings/form-options' })

export const getFeedbackProcessOptions = () =>
  request.get<FeedbackProcessOption[]>({
    url: '/zsjos/feedback-management/settings/process-options'
  })
