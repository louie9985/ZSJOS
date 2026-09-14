import request from '@/config/axios'

export type FieldType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'date'
  | 'select'
  | 'multi_select'
  | 'boolean'
  | 'image'
  | 'attachment'
  | 'materials'
  | 'record'
  | 'url'

export interface AccountField {
  referenceFor?: string
  materialTypeCode?: string
  defaultPlatform?: string
  defaultAccountStage?: string
  recommendedCount?: string
  description?: string
  key: string
  label: string
  type: FieldType
  required: boolean
  enabled: boolean
  sort: number
  dictType?: string
  searchable: boolean
  ownerType: 'AUTO' | 'DIRECTOR' | 'OPERATOR' | 'UNASSIGNED'
  group: 'PROFILE' | 'POSITIONING' | 'STATUS' | 'METRICS' | 'REVIEW'
  sourceType: 'MANUAL' | 'ACCOUNT' | 'STUDENT' | 'PENDING'
  requiredForCreate: false
  requiredForComplete: boolean
  snapshotPolicy: 'ON_SELECTION'
}

export interface ConfigVersion {
  id: number
  versionNo: number
  status: string
  publishedAt?: string
  version: number
  fields: AccountField[]
}

export interface AccountFieldConfig {
  published?: ConfigVersion
  draft?: ConfigVersion
}

export const getAccountFieldConfig = () =>
  request.get<AccountFieldConfig>({ url: '/zsjos/media-account-field-config' })

export const copyAccountFieldDraft = (id: number, version: number) =>
  request.post<number>({
    url: '/zsjos/media-account-field-config/draft/copy',
    data: { id, version }
  })

export const saveAccountFieldDraft = (data: {
  id: number
  version: number
  fields: AccountField[]
}) => request.put<boolean>({ url: '/zsjos/media-account-field-config/draft', data })

export const publishAccountFieldConfig = (id: number, version: number) =>
  request.post<boolean>({ url: '/zsjos/media-account-field-config/publish', data: { id, version } })

export const reconcileAccountFieldDraft = (id: number, version: number) =>
  request.post<boolean>({
    url: '/zsjos/media-account-field-config/draft/reconcile',
    data: { id, version }
  })
