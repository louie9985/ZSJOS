import { http, unwrap, uploadDirectFile, type PageResult } from './api'
import { createIdempotencyKey } from './idempotency'
import type { Timestamp } from './time'

export type MaterialFieldType =
  | 'text'
  | 'textarea'
  | 'rich-text'
  | 'number'
  | 'date'
  | 'datetime'
  | 'dict-single'
  | 'dict-multi'
  | 'employee'
  | 'department'
  | 'image'
  | 'video'
  | 'attachment'
  | 'https-link'
  | 'repeat-group'

export type MaterialFieldDefinition = {
  key: string
  label: string
  type: MaterialFieldType
  section?: 'ACCOUNT_DETAIL' | 'DIRECTOR_ANALYSIS' | 'BUILD_SUGGESTION'
  group?: string
  stageCode?: string
  required?: boolean
  searchable?: boolean
  multiple?: boolean
  recommendationDimension?: 'account_type' | 'profession' | 'account_stage'
  allowUnlimited?: boolean
  dictType?: string
  maxLength?: number
  min?: number
  max?: number
  minCount?: number
  maxCount?: number
  maxSizeMb?: number
  allowedExtensions?: string[]
  children?: MaterialFieldDefinition[]
  sort?: number
}

export type MaterialTemplate = {
  id: number
  materialTypeId: number
  versionNo: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  fields: MaterialFieldDefinition[]
  schemaHash: string
  publishedByUserId?: number
  publishedAt?: Timestamp
  version: number
}

export type MaterialType = {
  id: number
  name: string
  code: string
  description?: string
  status: number
  currentSchemaVersionId?: number
  bpmProcessDefinitionKey?: string
  allowManualCreate: boolean
  allowImport: boolean
  allowAutoCollect: boolean
  recommendationEnabled: boolean
  recommendationConfig?: Record<string, unknown>
  version: number
  currentSchema?: MaterialTemplate
}

export type MaterialFile = {
  id: number
  fieldKey: string
  groupIndex: number
  fileId: number
  name: string
  contentType: string
  size: number
  previewUrl?: string
}

export type MaterialVersion = {
  id: number
  materialId: number
  versionNo: number
  schemaVersionId: number
  status: 'DRAFT' | 'IN_APPROVAL' | 'EFFECTIVE' | 'REJECTED'
  title: string
  coverFileId?: number
  coverPreviewUrl?: string
  summary?: string
  values: Record<string, unknown>
  fields: MaterialFieldDefinition[]
  dictSnapshot: Record<string, unknown>
  files: MaterialFile[]
  processInstanceId?: string
  processDefinitionId?: string
  processDefinitionKey?: string
  processDefinitionVersion?: number
  businessKey?: string
  submittedByUserId?: number
  submittedAt?: Timestamp
  effectiveAt?: Timestamp
  rejectedAt?: Timestamp
  rejectionReason?: string
  version: number
}

export type Material = {
  id: number
  materialNo: string
  materialTypeId: number
  materialTypeName: string
  title: string
  coverFileId?: number
  coverPreviewUrl?: string
  summary?: string
  source: 'MANUAL' | 'IMPORT' | 'CONTENT_REVIEW'
  status: 'DRAFT' | 'IN_APPROVAL' | 'EFFECTIVE' | 'REJECTED' | 'DISABLED'
  currentEffectiveVersionId?: number
  currentDraftVersionId?: number
  ownerUserId: number
  ownerName?: string
  likeCount: number
  favoriteCount: number
  referenceCount: number
  liked: boolean
  favorited: boolean
  pinned: boolean
  priority: number
  disabledReason?: string
  disabledAt?: Timestamp
  version: number
  currentVersion?: MaterialVersion
  availableActions: string[]
  createTime?: Timestamp
  updateTime?: Timestamp
}

export type MaterialSaveRequest = {
  materialTypeId: number
  title?: string
  coverFileId?: number
  summary?: string
  values: Record<string, unknown>
  expectedMaterialVersion?: number
}

export type MaterialPageParams = {
  pageNo: number
  pageSize: number
  keyword?: string
  materialTypeId?: number
  accountId?: number
  status?: string
  source?: string
  mine?: boolean
  favorite?: boolean
  recommendation?: boolean
  accountType?: string
  profession?: string
  accountStage?: string
}

export type MaterialReferenceField = {
  sourceField: string
  targetField: string
  action: 'REPLACE' | 'APPEND' | 'SKIP'
}

export type MaterialRecommendationAccount = {
  id: number
  accountNo: string
  nickname?: string
}

export type MaterialReferenceTarget = {
  contentId: number
  contentVersionId: number
  contentNo: string
  title: string
  versionNo: number
  stage: string
}

export type ContentReviewItem = {
  id: number
  contentId: number
  contentVersionId: number
  contentRecordVersion: number
  sortNo: number
  contentSnapshot: Record<string, unknown>
  files: Array<{
    id: number
    fieldKey: string
    sortNo: number
    infraFileId: number
    fileUrlSnapshot?: string
    originalName: string
    contentType: string
    fileSize: number
    previewUrl?: string
  }>
  directorDecision?: 'APPROVED' | 'RETURNED'
  directorComment?: string
  finalDecision?: 'APPROVED' | 'RETURNED'
  finalComment?: string
  collectMaterial: boolean
  collectedMaterialId?: number
  collectedMaterialVersionId?: number
  resultStatus?: string
  publishedPlatformUrl?: string
  publishedAt?: Timestamp
  version: number
}

export type ContentReviewCandidate = {
  id: number
  contentNo: string
  accountId: number
  title: string
  topic?: string
  status: string
  currentVersionNo: number
  contentVersionId: number
}

export type ContentReviewBatch = {
  id: number
  batchNo: string
  accountId: number
  operatorUserId: number
  operatorName?: string
  directorUserId?: number
  directorName?: string
  relationSnapshot: Record<string, unknown>
  contextSnapshot: Record<string, unknown>
  status: string
  currentStage: string
  processInstanceId?: string
  currentTaskId?: string
  currentTaskKey?: string
  submittedAt?: Timestamp
  directorCompletedAt?: Timestamp
  finalCompletedAt?: Timestamp
  finalizedAt?: Timestamp
  version: number
  availableActions: string[]
  items: ContentReviewItem[]
}

export type PartnerStudentInvitation = {
  id: number
  inviteCode: string
  invitationScene: 'STUDENT_PARTNER'
  studentPersonId: number
  studentNameSnapshot?: string
  studentMobileSnapshot?: string
  initiatedByDirectorUserId: number
  name: string
  mobile: string
  status: string
  expiresAt: Timestamp
}

export const materialApi = {
  types: async () => unwrap<MaterialType[]>(await http.get('/zsjos/material-type/list')),
  page: async (params: MaterialPageParams) =>
    unwrap<PageResult<Material>>(await http.get('/zsjos/material/page', { params })),
  get: async (id: number) => unwrap<Material>(await http.get(`/zsjos/material/${id}`)),
  create: async (data: MaterialSaveRequest) => unwrap<number>(await http.post('/zsjos/material', data)),
  update: async (id: number, data: MaterialSaveRequest) => unwrap<number>(await http.put(`/zsjos/material/${id}`, data)),
  submit: async (id: number, expectedVersion: number) => unwrap<boolean>(
    await http.post(`/zsjos/material/${id}/submit`, { expectedVersion })
  ),
  uploadCover: async (file: File) => uploadDirectFile<{
    fileId: number
    name: string
    contentType: string
    size: number
    previewUrl?: string
  }>('/zsjos/material/upload/init', '/zsjos/material/upload/complete', file),
  version: async (versionId: number) =>
    unwrap<MaterialVersion>(await http.get(`/zsjos/material/version/${versionId}`)),
  versions: async (materialId: number) =>
    unwrap<MaterialVersion[]>(await http.get(`/zsjos/material/${materialId}/version/list`)),
  toggleLike: async (id: number) =>
    unwrap<{ active: boolean; count: number }>(await http.put(`/zsjos/material/${id}/like`)),
  toggleFavorite: async (id: number) =>
    unwrap<{ active: boolean; count: number }>(await http.put(`/zsjos/material/${id}/favorite`)),
  previewReference: async (materialId: number, versionId: number, targetContentVersionId: number,
    fields: MaterialReferenceField[]) => unwrap<{ before: Record<string, unknown>; after: Record<string, unknown> }>(
      await http.post(`/zsjos/material/${materialId}/version/${versionId}/reference/preview`, {
        targetContentVersionId,
        fields
      })
    ),
  reference: async (materialId: number, versionId: number, targetContentVersionId: number,
    fields: MaterialReferenceField[]) => unwrap<boolean>(
      await http.post(`/zsjos/material/${materialId}/version/${versionId}/reference`, {
        targetContentVersionId,
        fields,
        idempotencyKey: createIdempotencyKey()
      })
    ),
  recommendationAccounts: async (keyword?: string) => unwrap<MaterialRecommendationAccount[]>(
    await http.get('/zsjos/material/recommendation-account-candidates', { params: { keyword } })
  ),
  referenceTargets: async (params: { pageNo: number; pageSize: number; keyword?: string }) =>
    unwrap<PageResult<MaterialReferenceTarget>>(
      await http.get('/zsjos/material/reference-target-candidates', { params })
    )
}

export const contentReviewApi = {
  candidates: async (params: { pageNo: number; pageSize: number; keyword?: string }) =>
    unwrap<PageResult<ContentReviewCandidate>>(await http.get('/zsjos/content-review/candidate/page', { params })),
  page: async (params: { pageNo: number; pageSize: number; keyword?: string; status?: string; mine?: boolean }) =>
    unwrap<PageResult<ContentReviewBatch>>(await http.get('/zsjos/content-review/batch/page', { params })),
  get: async (id: number) => unwrap<ContentReviewBatch>(
    await http.get('/zsjos/content-review/batch/get', { params: { id } })
  ),
  create: async (contentVersionIds: number[]) => unwrap<number>(
    await http.post('/zsjos/content-review/batch/create', { contentVersionIds })
  ),
  submit: async (batchId: number, expectedVersion: number) => unwrap<boolean>(
    await http.post(`/zsjos/content-review/batch/${batchId}/submit`, { expectedVersion })
  ),
  cancel: async (batchId: number, expectedVersion: number) => unwrap<boolean>(
    await http.post(`/zsjos/content-review/batch/${batchId}/cancel`, { expectedVersion })
  ),
  directorDecision: async (batchId: number, itemId: number, data: {
    taskId: string; expectedVersion: number; decision: 'APPROVED' | 'RETURNED'; comment?: string
  }) => unwrap<boolean>(
    await http.put(`/zsjos/content-review/batch/${batchId}/item/${itemId}/director-decision`, data)
  ),
  finalDecision: async (batchId: number, itemId: number, data: {
    taskId: string; expectedVersion: number; decision: 'APPROVED' | 'RETURNED'; comment?: string;
    collectMaterial?: boolean
  }) => unwrap<boolean>(
    await http.put(`/zsjos/content-review/batch/${batchId}/item/${itemId}/final-decision`, data)
  ),
  completeDirector: async (batchId: number, data: { expectedVersion: number; taskId: string; reason: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/content-review/batch/${batchId}/complete-director`, data)),
  completeFinal: async (batchId: number, data: { expectedVersion: number; taskId: string; reason: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/content-review/batch/${batchId}/complete-final`, data)),
  registerPublished: async (batchId: number, itemId: number, data: {
    platformUrl: string; publishedAt: string; expectedContentVersion: number
  }) => unwrap<boolean>(
    await http.post(`/zsjos/content-review/batch/${batchId}/item/${itemId}/publish`, data)
  )
}

export const partnerStudentInvitationApi = {
  create: async (data: { studentPersonId: number; name: string; mobile: string }) =>
    unwrap<PartnerStudentInvitation>(await http.post('/zsjos/partner-invitation/student/create', data))
}
