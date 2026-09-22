import { http, unwrap } from './api'
export type Scope = { scopeType: 'SELF' | 'USER' | 'DEPT' | 'CENTER'; scopeId?: number }
export type Query = Scope & { start?: string; end?: string; grain?: 'day' | 'week' | 'month'; year?: number; dimension?: string; groupKey?: string; calendar?: boolean; cumulative?: boolean; metric?: string; periodKey?: string; pageNo?: number; pageSize?: number }
export type OrgNode = Scope & { key: string; parentKey?: string; title: string; selectable: boolean }
export type Metric = { key: string; label: string; start: string; end: string; amount: number; orders: number; converted: number; denominator: number; rate: number | null; average: number | null }
export type Group = { key: string; label: string; amount: number; count: number; share: number | null }
export type Target = Scope & { id?: number; name: string; periodType: string; periodStart: string; automaticFloor: number | null; automaticSprint: number | null; floorAmount: number | null; sprintAmount: number | null; manual: boolean; complete: boolean; missing: number; version?: number }
export type TargetEdit = Scope & { id?: number; periodType: string; periodStart: string; floorAmount?: number; sprintAmount?: number; restoreAutomatic?: boolean; reason: string; version?: number }
export type Overview = { asOf: string; attributionAvailableSince: string | null; targets: { key: string; label: string; actual: Metric; target: Target }[]; performance: Metric[]; conversion: Metric[]; pending: Record<string, number>; missingAttributionOrders: number; missingAttributionAmount: number; canDetail: boolean }
export type Analysis = { asOf: string; start: string; end: string; averages: Metric[]; trend: Metric[]; sources: Group[]; products: Group[]; contributors: Group[]; contributionMetrics: { userId: number; name: string; actual: Metric; floorRate: number | null; sprintRate: number | null; share: number | null }[]; averageTrends: Record<string, Metric[]>; target: Target | null }
export type CalendarDay = { date: string; received: number; valid: number; invalid: number; pending: number; overdue: number; ended: number; lateCompleted: number; onTime: number; dueCount: number; unknown: number }
export type LeadReport = { asOf: string; start: string; end: string; workload: Record<string, number>; categories: Group[]; stages: Group[]; calendar: CalendarDay[]; funnel: Group[]; followUp: Group[]; categoryTrend: Group[] }
export type Detail = { id: number; number?: string; kind: string; label: string; occurredAt: string; amount?: number; state: string }
export type HistoryMonth = { month: number; amount: number; previousAmount: number }
const BASE = '/zsjos/sales-performance'
const TARGET = '/zsjos/sales-performance-target'
const timestampFields = new Set(['start', 'end', 'asOf', 'attributionAvailableSince', 'occurredAt', 'at'])
const timestampFormatter = new Intl.DateTimeFormat('en-GB', {
 timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
 hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
})
// Backend LocalDateTime values are epoch milliseconds. Keep LocalDate strings and
// nulls intact; normalize only timestamp fields within this feature's responses.
export function normalizePerformanceDates(value: unknown): unknown {
 if (Array.isArray(value)) return value.map(normalizePerformanceDates)
 if (value === null || typeof value !== 'object') return value
 return Object.fromEntries(Object.entries(value).map(([key, item]) => {
  if (timestampFields.has(key) && typeof item === 'number') {
   if (!Number.isFinite(item) || Number.isNaN(new Date(item).getTime())) throw new Error('业绩数据时间格式无效，请重试')
   const parts = Object.fromEntries(timestampFormatter.formatToParts(new Date(item)).map(part => [part.type, part.value]))
   return [key, `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}`]
  }
  return [key, normalizePerformanceDates(item)]
 }))
}
async function get<T>(path: string, params?: object, signal?: AbortSignal): Promise<T> {
 const response = await http.get<unknown>(path, { params, signal })
 return normalizePerformanceDates(unwrap<unknown>(response)) as T
}
export const performanceApi = {
 tree: (targets = false, signal?: AbortSignal) => get<OrgNode[]>(`${targets ? TARGET : BASE}/tree`, undefined, signal),
 overview: (query: Query, signal?: AbortSignal) => get<Overview>(`${BASE}/overview`, query, signal),
 analysis: (query: Query, signal?: AbortSignal) => get<Analysis>(`${BASE}/analysis`, query, signal),
 leads: (query: Query, signal?: AbortSignal) => get<LeadReport>(`${BASE}/leads`, query, signal),
 history: (query: Query, signal?: AbortSignal) => get<HistoryMonth[]>(`${BASE}/history`, query, signal),
 details: (query: Query, signal?: AbortSignal) => get<{ list: Detail[]; total: number }>(`${BASE}/details`, query, signal),
 targets: (periodType: string, periodStart: string, signal?: AbortSignal) => get<Target[]>(`${TARGET}/list`, { periodType, periodStart }, signal),
 save: async (items: TargetEdit[]) => unwrap<boolean>(await http.put(`${TARGET}/batch`, { items })),
 candidates: () => get<OrgNode[]>(`${TARGET}/organization-candidates`),
 organizations: () => get<{ deptId: number; centerId: number; kind: 'DEPT' | 'CENTER' }[]>(`${TARGET}/organizations`),
 saveOrg: async (value: { deptId: number; centerId: number; kind: string }) => unwrap<boolean>(await http.put(`${TARGET}/organization`, value)),
 revisions: (id: number) => get<{ id: number; reason: string; before: string; after: string; operatorName: string; at: string }[]>(`${TARGET}/history`, { id })
}
export const money = (value?: number | null) => value == null ? '—' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(value)
export const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(2)}%`
export function dateText(date: Date) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}` }
export function periodRange(key: string, now = new Date()): { start: string; end: string; cumulative?: boolean; periodKey?: string } {
 const start = new Date(now), end = new Date(now)
 if (key === 'week') start.setDate(start.getDate() - (start.getDay()+6)%7)
 else if (key === 'month') start.setDate(1)
 else if (key === 'quarter') { start.setMonth(Math.floor(start.getMonth()/3)*3,1) }
 else if (key === 'year') start.setMonth(0,1)
 else if (key === 'all') return { start: '', end: dateText(end), cumulative: true }
 else if (key.startsWith('last')) start.setDate(start.getDate()-Number(key.slice(4))+1)
 return { start: dateText(start), end: dateText(end), periodKey: key }
}
