import request from '@/config/axios'

export type ForcedFormStatus = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN'
export type ForcedFormFieldType =
  | 'text'
  | 'textarea'
  | 'radio'
  | 'multi-select'
  | 'checkbox'
  | 'attachment'

export interface ForcedFormField {
  key: string
  type: ForcedFormFieldType
  label: string
  required?: boolean
  dictType?: string
  maxLength?: number
  maxCount?: number
  maxSizeMb?: number
  allowedExtensions?: string[]
}

export interface ForcedFormRecord {
  id: number
  name: string
  description?: string
  fieldsJson: string
  status: ForcedFormStatus
  version: number
  currentVersionId?: number
  recipientCount?: number
  completedCount?: number
  pendingCount?: number
  lastSentAt?: number
}

export interface ForcedFormPageParams {
  pageNo: number
  pageSize: number
  name?: string
  status?: string
}

export interface ForcedFormSaveReq {
  id?: number
  name: string
  description?: string
  fieldsJson: string
}

export interface ForcedFormSendReq {
  scopeType: 'ALL' | 'USERS' | 'DEPARTMENTS' | 'POSTS'
  userIds?: number[]
  deptIds?: number[]
  postIds?: number[]
}

export interface ForcedFormRecipientPreview {
  recipientCount: number
  skippedCompletedCount: number
  filteredCount: number
  recipients: Array<{
    userId: number
    nickname?: string
    deptName?: string
    postNames?: string
  }>
}

export interface ForcedFormSendResult {
  batchId: number
  recipientCount: number
  skippedCompletedCount: number
  filteredCount: number
}

export interface ForcedFormSubmission {
  id: number
  formId: number
  formName?: string
  versionId?: number
  version?: number
  userId: number
  userNickname?: string
  platform?: string
  createTime?: number
  fieldsSnapshotJson?: string
  answersJson?: string
  dictSnapshotJson?: string
}

export const getForcedFormPage = (params: ForcedFormPageParams) =>
  request.get({ url: '/zsjos/forced-form/page', params })

export const createForcedForm = (data: ForcedFormSaveReq) =>
  request.post({ url: '/zsjos/forced-form', data })

export const updateForcedForm = (id: number, data: ForcedFormSaveReq) =>
  request.put({ url: `/zsjos/forced-form/${id}`, data })

export const getForcedForm = (id: number) =>
  request.get<ForcedFormRecord>({ url: `/zsjos/forced-form/${id}` })

export const copyForcedForm = (id: number) =>
  request.post<ForcedFormRecord>({ url: `/zsjos/forced-form/${id}/copy` })

export const publishForcedForm = (id: number) =>
  request.post({ url: `/zsjos/forced-form/${id}/publish` })

export const withdrawForcedForm = (id: number) =>
  request.post({ url: `/zsjos/forced-form/${id}/withdraw` })

export const removeForcedForm = (id: number) =>
  request.delete({ url: `/zsjos/forced-form/${id}` })

export const previewForcedFormRecipients = (id: number, data: ForcedFormSendReq) =>
  request.post<ForcedFormRecipientPreview>({ url: `/zsjos/forced-form/${id}/recipient-preview`, data })

export const sendForcedForm = (id: number, data: ForcedFormSendReq) =>
  request.post<ForcedFormSendResult>({ url: `/zsjos/forced-form/${id}/send`, data })

export const getForcedFormSubmissionPage = (params: {
  pageNo: number
  pageSize: number
  formId?: number
  userId?: number
  platform?: string
}) => request.get({ url: '/zsjos/forced-form/submission/page', params })

export const getForcedFormSubmission = (id: number) =>
  request.get<ForcedFormSubmission>({ url: `/zsjos/forced-form/submission/${id}` })

export const exportForcedFormSubmissions = (data: {
  formId?: number
  userId?: number
  platform?: string
}) => request.postDownload({ url: '/zsjos/forced-form/submission/export', data })
