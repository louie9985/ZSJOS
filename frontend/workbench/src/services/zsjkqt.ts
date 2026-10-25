import { http, unwrap } from './api'
import type { AttendanceMonth, AttendanceProject, ExportedAttendanceReport, PersonnelProfiles, RecoveredMonthlyParameters } from '../features/zsjkqt/core/models'
import type { DepartmentScheduleSettings } from '../features/zsjkqt/core/departmentSchedules'

export const ZSJKQT_ACCESS = 'zsjos:zsjkqt:access'
const BASE = '/zsjos/zsjkqt'
export interface ZsjkqtSnapshot {
  schemaVersion: 1
  step: 'home' | 'department-settings' | 'settings' | 'reimport-settings' | 'review'
  month: AttendanceMonth | null
  project: AttendanceProject | null
  restDays: number[]
  paidRestDays: number[]
  shouldAttendanceDays: number | null
  payrollDays: number | null
  departmentSchedules: DepartmentScheduleSettings
  personnelProfiles?: PersonnelProfiles
  recoveredParameters?: RecoveredMonthlyParameters
  activeIndex?: number
  query?: string
  filter?: 'all' | 'issues' | 'unconfirmed' | 'disabled'
  restDayKind?: 'paid' | 'rest'
  sourcePath?: string
}
export interface ZsjkqtDraft {
  id: number; clientId: string; name: string; year: number; month: number
  sourceFileName: string; sourceFileHash: string; employeeCount: number; confirmedCount: number
  version: number; updateTime?: number; updaterName?: string; snapshot: ZsjkqtSnapshot | null
}
export interface ZsjkqtConfig<T> { version: number; value: T }
export interface ZsjkqtPage { list: ZsjkqtDraft[]; total: number }
export type ZsjkqtSave = Pick<ZsjkqtDraft, 'clientId' | 'name' | 'version'> & { snapshot: ZsjkqtSnapshot }
export const zsjkqtApi = {
  list: async (params: { pageNo: number; pageSize: number; keyword?: string; year?: number; month?: number }) => unwrap<ZsjkqtPage>(await http.get(BASE + '/drafts', { params })),
  get: async (id: number) => unwrap<ZsjkqtDraft>(await http.get(`${BASE}/drafts/${id}`)),
  save: async (id: number | undefined, data: ZsjkqtSave) => unwrap<ZsjkqtDraft>(id == null ? await http.post(BASE + '/drafts', data) : await http.put(`${BASE}/drafts/${id}`, data)),
  rename: async (id: number, version: number, name: string) => unwrap<boolean>(await http.put(`${BASE}/drafts/${id}/name`, { version, name })),
  remove: async (id: number, version: number) => unwrap<boolean>(await http.delete(`${BASE}/drafts/${id}`, { params: { version } })),
  schedules: async () => unwrap<ZsjkqtConfig<DepartmentScheduleSettings>>(await http.get(BASE + '/config/schedules')),
  profiles: async () => unwrap<ZsjkqtConfig<PersonnelProfiles>>(await http.get(BASE + '/config/profiles')),
  saveSchedules: async (data: ZsjkqtConfig<DepartmentScheduleSettings>) => unwrap<ZsjkqtConfig<DepartmentScheduleSettings>>(await http.put(BASE + '/config/schedules', data)),
  profile: async (data: { key: string; name: string; department: string; entryDate: string; enabled: boolean }) => unwrap<boolean>(await http.put(BASE + '/profiles', data)),
  parseSource: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<{ attendance: AttendanceMonth; issues: unknown[] }>(await http.post(BASE + '/parse/source', data)) },
  parseReport: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<ExportedAttendanceReport>(await http.post(BASE + '/parse/report', data)) },
  export: async (id: number, version: number, kind: 'report' | 'issues', filename: string) => {
    const response = await http.get(`${BASE}/drafts/${id}/export/${kind}`, { params: { version }, responseType: 'blob' })
    const blob = response.data as Blob
    if (blob.type.includes('json')) { const error = JSON.parse(await blob.text()) as { msg?: string }; throw new Error(error.msg || '导出失败') }
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}
