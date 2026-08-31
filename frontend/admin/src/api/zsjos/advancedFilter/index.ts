import request from '@/config/axios'
import type { Timestamp } from '../types'

export type AdvancedFilterScene = 'lead' | 'order' | 'lead_appeal' | 'duplicate_review' | 'registration' | 'student' | 'subordinate_sales'
export interface AdvancedFilterCondition { fieldKey: string; operator: string; startFieldKey?: string; endFieldKey?: string; unit?: 'minute' | 'hour' | 'day'; value?: unknown; valueFrom?: unknown; valueTo?: unknown }
export interface AdvancedFilterGroup { logic: 'AND' | 'OR'; conditions: AdvancedFilterCondition[]; groups: AdvancedFilterGroup[] }
export interface AdvancedFilterOption { value: string | number; label: string }
export interface AdvancedFilterField { fieldKey: string; group: string; label: string; valueType: 'text' | 'select' | 'number' | 'date' | 'duration'; operators: string[]; optionSource?: string; options: AdvancedFilterOption[]; optionsLoading?: boolean; optionsError?: boolean }
export const getCatalog = (scene: AdvancedFilterScene) => request.get<{ fields: AdvancedFilterField[]; relativeDateOptions?: Array<{ value: string; label: string }> }>({ url: '/zsjos/advanced-filter/catalog', params: { scene } })

export interface AdvancedFilterTemplate {
  id: number
  scene: AdvancedFilterScene
  pageKey: string
  scope: 'personal' | 'system'
  name: string
  filter: AdvancedFilterGroup
  sort: number
  enabled: boolean
  defaultTemplate: boolean
  version?: number
  createTime?: Timestamp
  updateTime?: Timestamp
}

export interface AdvancedFilterTemplateSaveReq {
  id?: number
  scene: AdvancedFilterScene
  pageKey: string
  name: string
  filter: AdvancedFilterGroup
  sort: number
  enabled: boolean
  defaultTemplate: boolean
  version?: number
}
