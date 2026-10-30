import type { Timestamp } from './types'
import request from '@/config/axios'
import type { AdvancedFilterGroup } from './advancedFilter'
export interface Calculation {
  name: string
  source: string
  aggregate: string
  field?: string
  period: string
  toDate: boolean
  customRange?: { start: Timestamp | string; end: Timestamp | string }
  comparison: string
}
export interface Operand {
  editorId?: string
  calculation: Calculation
  filter: AdvancedFilterGroup
  timeField?: string
}
export interface MetricSpec {
  dictionarySnapshots?: Record<string, string>
  code: string
  name: string
  description: string
  unit: string
  sort: number
  viewPermission: string
  userIds: number[]
  deptIds: number[]
  action: string
  formula: string
  percentage: boolean
  decimals: number
  operands: Operand[]
}
export interface MetricConfig {
  id: number
  version: number
  enabled: boolean
  publishedVersion?: number
  spec: MetricSpec
}
export interface Source {
  key: string
  label: string
  scene: string
  fields: { key: string; label: string; unit: string }[]
  timeFields: { key: string; label: string; unit: string }[]
  route: string
}
const base = '/zsjos/home-metric'
export const metricApi = {
  permissions: () =>
    request.get<{ permission: string; label: string }[]>({ url: base + '/permissions' }),
  list: () => request.get<MetricConfig[]>({ url: base + '/list' }),
  get: (id: number) => request.get<MetricConfig>({ url: base + '/get', params: { id } }),
  catalog: () => request.get<Source[]>({ url: base + '/catalog' }),
  options: () =>
    request.get<Record<string, { value: string; label: string }[]>>({ url: base + '/options' }),
  save: (data: { id?: number; version?: number; spec: MetricSpec }) =>
    request.post({ url: base + '/save', data }),
  command: (
    command: 'publish' | 'disable' | 'copy' | 'rollback',
    data: { id: number; version: number; targetVersion?: number }
  ) => request.post({ url: base + '/' + command, data }),
  versions: (id: number) =>
    request.get<{ version: number; spec: MetricSpec }[]>({
      url: base + '/versions',
      params: { id }
    }),
  preview: (data: MetricSpec) => request.post({ url: base + '/preview', data })
}
