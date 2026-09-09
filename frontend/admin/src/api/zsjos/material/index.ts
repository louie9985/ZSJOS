import request from '@/config/axios'
import axios, { type AxiosProgressEvent } from 'axios'

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

export type RecommendationDimension = 'account_type' | 'profession' | 'account_stage'

export interface MaterialFieldDefinition {
  key: string
  label: string
  type: MaterialFieldType
  required?: boolean
  searchable?: boolean
  multiple?: boolean
  recommendationDimension?: RecommendationDimension
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

export interface MaterialTemplate {
  id: number
  materialTypeId: number
  versionNo: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  fields: MaterialFieldDefinition[]
  schemaHash: string
  publishedByUserId?: number
  publishedAt?: string
  version: number
}

export interface MaterialType {
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
  recommendationConfig?: {
    dimensions?: RecommendationDimension[]
    maxResults?: number
  }
  version: number
  currentSchema?: MaterialTemplate
  createTime?: string
  updateTime?: string
}

export interface MaterialTypeSaveReq {
  name: string
  code: string
  description?: string
  status: number
  allowManualCreate: boolean
  allowImport: boolean
  allowAutoCollect: boolean
  recommendationEnabled: boolean
  recommendationConfig: {
    dimensions: RecommendationDimension[]
    maxResults: number
  }
  bpmProcessDefinitionKey?: string
  version?: number
}

export interface MaterialProcessDefinition {
  id: string
  key: string
  name: string
  version: number
  category?: string
  description?: string
}

export interface MaterialFile {
  id: number
  fieldKey: string
  groupIndex: number
  fileId: number
  name: string
  contentType: string
  size: number
  previewUrl?: string
}

export interface MaterialVersion {
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
  submittedAt?: string
  effectiveAt?: string
  rejectedAt?: string
  rejectionReason?: string
  version: number
}

export interface Material {
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
  disabledAt?: string
  version: number
  currentVersion?: MaterialVersion
  availableActions: string[]
  createTime?: string
  updateTime?: string
}

export interface MaterialPageParams {
  pageNo: number
  pageSize: number
  keyword?: string
  materialTypeId?: number
  status?: string
  source?: string
  mine?: boolean
}

export interface MaterialSaveReq {
  materialTypeId: number
  title: string
  coverFileId?: number
  summary?: string
  values: Record<string, unknown>
  pinned?: boolean
  priority?: number
  expectedMaterialVersion?: number
}

export interface MaterialUpload {
  fileId: number
  name: string
  contentType: string
  size: number
  previewUrl?: string
}

export interface MaterialDirectUploadInit {
  uploadToken: string
  uploadUrl: string
  uploadHeaders: Record<string, string>
  expiresAt: string
}

export interface MaterialImportError {
  id: number
  sheetName: string
  rowNo: number
  fieldKey?: string
  errorCode: string
  errorMessage: string
  rowSnapshotJson?: string
}

export interface MaterialImportBatch {
  id: number
  batchNo: string
  materialTypeId: number
  materialTypeName: string
  schemaVersionId: number
  sourceFileName: string
  status: 'PREVIEWED' | 'COMMITTED'
  totalCount: number
  successCount: number
  failureCount: number
  createdByUserId: number
  confirmedByUserId?: number
  confirmedAt?: string
  version: number
  createTime?: string
  errors: MaterialImportError[]
}

export interface ContentReviewUserTask {
  key: string
  name: string
  nextUserTaskKeys: string[]
}

export interface ContentReviewProcessDefinition {
  id: string
  key: string
  name: string
  version: number
  category?: string
  suspended: boolean
  userTasks: ContentReviewUserTask[]
}

export interface ContentReviewConfig {
  id: number
  processDefinitionKey?: string
  directorTaskKey?: string
  finalTaskKey?: string
  productionMaterialTypeCode: string
  materialFieldMapping: Record<string, string>
  materialDefaultValues: Record<string, unknown>
  version: number
}

export const getMaterialTypeList = () =>
  request.get<MaterialType[]>({ url: '/zsjos/material-type/list' })

export const getMaterialType = (id: number) =>
  request.get<MaterialType>({ url: `/zsjos/material-type/${id}` })

export const createMaterialType = (data: MaterialTypeSaveReq) =>
  request.post<number>({ url: '/zsjos/material-type', data })

export const updateMaterialType = (id: number, data: MaterialTypeSaveReq) =>
  request.put<boolean>({ url: `/zsjos/material-type/${id}`, data })

export const getMaterialProcessDefinitions = () =>
  request.get<MaterialProcessDefinition[]>({ url: '/zsjos/material-type/process-definition/list' })

export const getMaterialSchemas = (materialTypeId: number) =>
  request.get<MaterialTemplate[]>({ url: `/zsjos/material-type/${materialTypeId}/schema/list` })

export const saveMaterialSchemaDraft = (
  materialTypeId: number,
  data: { id?: number; version?: number; fields: MaterialFieldDefinition[] }
) => request.put<number>({ url: `/zsjos/material-type/${materialTypeId}/schema/draft`, data })

export const publishMaterialSchema = (
  materialTypeId: number,
  data: { schemaVersionId: number; expectedSchemaVersion: number; expectedTypeVersion: number }
) => request.post<boolean>({ url: `/zsjos/material-type/${materialTypeId}/schema/publish`, data })

export const getMaterialPage = (params: MaterialPageParams) =>
  request.get<PageResult<Material[]>>({ url: '/zsjos/material/page', params })

export const getMaterial = (id: number) =>
  request.get<Material>({ url: `/zsjos/material/${id}` })

export const getMaterialVersions = (id: number) =>
  request.get<MaterialVersion[]>({ url: `/zsjos/material/${id}/version/list` })

export const getMaterialVersion = (versionId: number) =>
  request.get<MaterialVersion>({ url: `/zsjos/material/version/${versionId}` })

export const createMaterial = (data: MaterialSaveReq) =>
  request.post<number>({ url: '/zsjos/material', data })

export const updateMaterial = (id: number, data: MaterialSaveReq) =>
  request.put<number>({ url: `/zsjos/material/${id}`, data })

export const submitMaterial = (id: number, expectedVersion: number) =>
  request.post<boolean>({ url: `/zsjos/material/${id}/submit`, data: { expectedVersion } })

export const disableMaterial = (id: number, expectedVersion: number, reason: string) =>
  request.put<boolean>({ url: `/zsjos/material/${id}/disable`, data: { expectedVersion, reason } })

export const restoreMaterial = (id: number, expectedVersion: number) =>
  request.put<boolean>({ url: `/zsjos/material/${id}/restore`, data: { expectedVersion } })

export const uploadMaterialFile = async (
  file: File,
  onUploadProgress?: (event: AxiosProgressEvent) => void
): Promise<MaterialUpload> => {
  const init = await request.post<MaterialDirectUploadInit>({
    url: '/zsjos/material/upload/init',
    data: {
      name: file.name,
      contentType: file.type || 'application/octet-stream',
      size: file.size
    }
  })
  await axios.put(init.uploadUrl, file, {
    headers: init.uploadHeaders,
    timeout: 0,
    withCredentials: false,
    onUploadProgress
  })
  return request.post<MaterialUpload>({
    url: '/zsjos/material/upload/complete',
    data: { uploadToken: init.uploadToken }
  })
}

export const downloadMaterialImportTemplate = (materialTypeId: number) =>
  request.download<Blob>({ url: '/zsjos/material-import/template', params: { materialTypeId } })

export const previewMaterialImport = async (
  materialTypeId: number,
  idempotencyKey: string,
  file: File
): Promise<MaterialImportBatch> => {
  const data = new FormData()
  data.append('file', file)
  const response = await request.upload<{ data: MaterialImportBatch }>({
    url: '/zsjos/material-import/preview',
    params: { materialTypeId, idempotencyKey },
    data
  })
  return response.data
}

export const getMaterialImportPage = (params: {
  pageNo: number
  pageSize: number
  materialTypeId?: number
  status?: string
}) => request.get<PageResult<MaterialImportBatch[]>>({ url: '/zsjos/material-import/page', params })

export const getMaterialImport = (id: number) =>
  request.get<MaterialImportBatch>({ url: `/zsjos/material-import/${id}` })

export const commitMaterialImport = (id: number, expectedVersion: number) =>
  request.post<boolean>({ url: `/zsjos/material-import/${id}/commit`, data: { expectedVersion } })

export const downloadMaterialImportErrors = (id: number) =>
  request.download<Blob>({ url: `/zsjos/material-import/${id}/error-report` })

export const getContentReviewConfig = () =>
  request.get<ContentReviewConfig>({ url: '/zsjos/content-review/config' })

export const getContentReviewProcessDefinitions = () =>
  request.get<ContentReviewProcessDefinition[]>({ url: '/zsjos/content-review/process-definition/list' })

export const updateContentReviewConfig = (data: ContentReviewConfig) =>
  request.put<boolean>({ url: '/zsjos/content-review/config', data })
