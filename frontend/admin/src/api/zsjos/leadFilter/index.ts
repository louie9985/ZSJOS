import request from '@/config/axios'
import type { Timestamp } from '../types'

export type LeadFilterAudience = 'submitter' | 'owner' | 'reviewer' | 'agingPool'
export interface LeadFilterConditionVO {
  field: string
  values: string[]
}
export interface LeadFilterOptionVO {
  key: string
  label: string
  sort: number
  enabled: boolean
  conditions: LeadFilterConditionVO[]
}
export interface LeadFilterGroupVO {
  key: string
  label: string
  sort: number
  enabled: boolean
  sectionLabel?: string
  conditions: LeadFilterConditionVO[]
  options: LeadFilterOptionVO[]
}
export interface LeadFilterAdminVO {
  audience: LeadFilterAudience
  audienceLabel: string
  draftGroups: LeadFilterGroupVO[]
  publishedGroups: LeadFilterGroupVO[]
  publishedVersion: number
  publishedAt?: Timestamp
  updateTime?: Timestamp
}
export interface LeadFilterCapabilityVO {
  field: string
  label: string
  values: Array<{ value: string; label: string }>
}
export interface LeadFilterVersionVO {
  versionNo: number
  publishedBy: number
  publishedAt: Timestamp
}

export const getConfig = (audience: LeadFilterAudience): Promise<LeadFilterAdminVO> =>
  request.get({ url: '/zsjos/lead/inbox-filter/get', params: { audience } })

export const getCapabilities = (audience: LeadFilterAudience): Promise<LeadFilterCapabilityVO[]> =>
  request.get({ url: '/zsjos/lead/inbox-filter/capabilities', params: { audience } })

export const getVersions = (audience: LeadFilterAudience): Promise<LeadFilterVersionVO[]> =>
  request.get({ url: '/zsjos/lead/inbox-filter/versions', params: { audience } })

export const saveDraft = (audience: LeadFilterAudience, groups: LeadFilterGroupVO[]) =>
  request.put({ url: '/zsjos/lead/inbox-filter/draft', data: { audience, groups } })

export const publish = (audience: LeadFilterAudience): Promise<number> =>
  request.post({ url: '/zsjos/lead/inbox-filter/publish', params: { audience } })

export const rollback = (audience: LeadFilterAudience, versionNo: number): Promise<number> =>
  request.post({ url: '/zsjos/lead/inbox-filter/rollback', params: { audience, versionNo } })
