import { http, unwrap, type PageResult } from './api'
import type { Timestamp } from './time'

export type ScopeNode = { key: string; parentKey?: string; title: string; scopeType: 'SELF' | 'USER' | 'DEPT' | 'CENTER'; scopeId?: number }
export type Target = { id?: number; scopeType: string; scopeId: number; name: string; periodStart: string; targetCount: number | null; actualCount: number; manual: boolean; version?: number }
export type PeriodStats = { key: string; label: string; total: number; valid: number; invalid: number; converted: number; validRate: number | null; convertedRate: number | null }
export type Member = { userId: number; name: string; targetCount: number | null; yesterday: number; yesterdayConverted: number; today: number; todayConverted: number; week: number; weekValid: number; weekConverted: number; lastWeek: number; lastWeekValid: number; lastWeekConverted: number; month: number; monthValid: number; monthConverted: number; lastMonth: number; lastMonthValid: number; lastMonthConverted: number; monthProgress: number | null }
export type CalendarDay = { date: string; total: number; valid: number; invalid: number; pending: number }
export type Funnel = { submitted: number; valid: number; converted: number }
export type MediaOrg = { deptId: number; centerId: number; kind: 'CENTER' | 'DEPT'; name: string; centerName: string; version?: number }
export type DeptOption = { id: number; name: string; parentId: number | null }
export type TargetRevision = { id: number; targetId: number; beforeJson: string | null; afterJson: string; reason: string; operatorId: number; createTime: string }
export type Detail = { leadNo: string; submittedAt: Timestamp; contributorName?: string; status: string; statusLabel: string; channelLabel: string; categoryLabel: string; converted: boolean; orderEffectiveAt?: Timestamp }
export type Group = { label: string; count: number }
export type Overview = { asOf: string; target: Target; periods: PeriodStats[]; members: Member[]; calendar: CalendarDay[]; funnel?: Funnel; currentMonthChannels: Group[]; lastMonthChannels: Group[]; currentMonthCategories: Group[]; lastMonthCategories: Group[] }
export type Query = { scopeType: 'SELF' | 'USER' | 'DEPT' | 'CENTER'; scopeId?: number; start: string; end: string }

async function get<T>(path: string, params?: object, signal?: AbortSignal) {
  return unwrap<T>(await http.get<T>(path, { params, signal }))
}
export const mediaLeadApi = {
  tree: (signal?: AbortSignal) => get<ScopeNode[]>('/zsjos/media-lead-analysis/tree', undefined, signal),
  overview: (query: Query, signal?: AbortSignal) => get<Overview>('/zsjos/media-lead-analysis/overview', query, signal),
  details: (query: Query, signal?: AbortSignal) => get<Detail[]>('/zsjos/media-lead-analysis/details', query, signal),
  detailPage: (query: Query & { pageNo: number; pageSize: number }, signal?: AbortSignal) => get<PageResult<Detail>>('/zsjos/media-lead-analysis/detail-page', query, signal),
  targets: (periodStart: string, signal?: AbortSignal) => get<Target[]>('/zsjos/media-lead-target/list', { periodStart }, signal),
  saveTargets: async (items: Array<{ scopeType: string; scopeId: number; periodStart: string; targetCount?: number; version?: number; reason: string; restoreAutomatic: boolean }>) => unwrap<boolean>(await http.put('/zsjos/media-lead-target/batch', items)),
  revisions: (id: number, signal?: AbortSignal) => get<TargetRevision[]>(`/zsjos/media-lead-target/${id}/revisions`, undefined, signal),
  organizations: (signal?: AbortSignal) => get<MediaOrg[]>('/zsjos/media-lead-target/orgs', undefined, signal),
  departments: (signal?: AbortSignal) => get<DeptOption[]>('/zsjos/media-lead-target/departments', undefined, signal),
  saveOrganization: async (item: { deptId: number; centerId: number; kind: 'CENTER' | 'DEPT'; version?: number }) => unwrap<boolean>(await http.put('/zsjos/media-lead-target/org', item)),
  unsetOrganization: async (item: { deptId: number; version: number }) => unwrap<boolean>(await http.put('/zsjos/media-lead-target/org/unset', item))
}

export const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`
