import request from '@/config/axios'

export interface DirectorField {
  key: string
  title: string
  type: string
  enabled: boolean
  required: boolean
  systemField: boolean
  sort: number
  description?: string
  interviewNote?: string
  allowRemark?: boolean
  requireAttachment?: boolean
  studentVisible?: boolean
  dictType?: string
  multiple?: boolean
  minSelections?: number
  maxSelections?: number
  minValue?: number
  maxValue?: number
  maxLength?: number
  group?: string
  referenceFor?: string
  materialTypeCode?: 'viral_account' | 'viral_content'
  defaultPlatform?: string
  defaultStage?: string
  recommendedCount?: string
  filterAdjustable?: boolean
}
export interface TemplateVersion {
  id: number
  templateId: number
  versionNo: number
  status: string
  fields: DirectorField[]
  version: number
  publishedAt?: string
}
export interface DirectorTemplate {
  id: number
  scene: string
  templateCode: string
  name: string
  defaultTemplate: boolean
  status: string
  version: number
  published?: TemplateVersion
  draft?: TemplateVersion
  versions: TemplateVersion[]
}
export interface DirectorConfig {
  id: number
  interviewAppointmentHours: number
  positioningDueHours: number
  trialDays: number
  version: number
}

const prefix = (positioning: boolean) =>
  positioning ? '/zsjos/positioning-template' : '/zsjos/positioning-interview-template'
export const getTemplates = (positioning: boolean) =>
  request.get({ url: `${prefix(positioning)}/list` })
export const copyDraft = (positioning: boolean, id: number, version: number) =>
  request.post({ url: `${prefix(positioning)}/${id}/draft/copy?version=${version}` })
export const saveDraft = (positioning: boolean, id: number, data: unknown) =>
  request.put({ url: `${prefix(positioning)}/${id}/draft`, data })
export const publish = (positioning: boolean, id: number, data: unknown) =>
  request.post({ url: `${prefix(positioning)}/${id}/publish`, data })
export const createPositioning = (data: unknown) =>
  request.post({ url: '/zsjos/positioning-template', data })
export const getDirectorConfig = (): Promise<DirectorConfig> =>
  request.get({ url: '/zsjos/director-config' })
export const updateDirectorConfig = (data: DirectorConfig) =>
  request.put({ url: '/zsjos/director-config', data })
