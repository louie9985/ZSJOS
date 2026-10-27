import { ApiError, AuthenticationError, http, unwrap, type DeliveryClass, type DeliveryClassPageQuery, type DeliveryClassProject } from './api'

export type ClassGroup = { id: number; name: string; deptId: number; deptNameSnapshot?: string; referenceDate?: string; version: number; canCreate: boolean; canUpdate: boolean; canDelete: boolean }
export type ClassGroupNode = { key: string; type: 'CLASS' | 'GROUP' | 'SYSTEM'; name: string; group?: ClassGroup; single?: DeliveryClass; classCount: number; homeroomCount: number; serviceCount: number; servingCount: number; completedCount: number; examDate: string; projects: DeliveryClassProject[]; autoExpand: boolean }
export type GroupSave = { name: string; referenceDate?: string; version?: number }
export type GroupMode = 'NONE' | 'EXISTING' | 'NEW'
export type GroupedClassCreate = { className: string; startDate?: string; homeroomUserIds: number[]; groupMode: GroupMode; groupId?: number; newGroup?: GroupSave }
export type ClassSortField = 'name' | 'date' | 'count'
export type ClassSort = { sortField?: ClassSortField; sortOrder?: 'asc' | 'desc' }

type Page<T> = { list: T[]; total: number }
const base = '/zsjos/delivery-class/group'
export const classGroups = {
  page: async (params: DeliveryClassPageQuery & ClassSort, managed: boolean) => unwrap<Page<ClassGroupNode> & { classTotal: number }>(await http.get(`${base}/${managed ? 'page' : 'my-page'}`, { params })),
  children: async (key: string, params: DeliveryClassPageQuery & ClassSort, managed: boolean) => unwrap<Page<DeliveryClass>>(await http.get(`${base}/${managed ? 'children' : 'my-children'}`, { params: { ...params, key } })),
  options: async (keyword = '', pageNo = 1) => unwrap<Page<ClassGroup>>(await http.get(`${base}/options`, { params: { keyword: keyword || undefined, pageNo, pageSize: 12 } })),
  candidates: async (keyword = '', pageNo = 1) => unwrap<Page<DeliveryClass>>(await http.get(`${base}/candidates`, { params: { keyword: keyword || undefined, pageNo, pageSize: 12 } })),
  create: async (data: GroupSave) => unwrap<number>(await http.post(`${base}/create`, data)),
  update: async (id: number, data: GroupSave) => unwrap<boolean>(await http.put(`${base}/${id}`, data)),
  remove: async (id: number, version: number) => unwrap<boolean>(await http.delete(`${base}/${id}`, { params: { version } })),
  createClasses: async (data: GroupedClassCreate) => unwrap<{ groupId?: number; classIds: number[] }>(await http.post(`${base}/create-classes`, data)),
  move: async (rows: DeliveryClass[], targetGroupId?: number) => unwrap<boolean>(await http.post(`${base}/move`, { classes: rows.map(({ id, version }) => ({ id, version })), targetGroupId: targetGroupId ?? null })),
}

export const hasClassPermission = (permissions: string[], permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)
export const isClassGroupDenied = (error: unknown) => error instanceof AuthenticationError || error instanceof ApiError && [401, 403, 1091140002, 1900019007].includes(error.code)
export function classDate(row: DeliveryClass) {
  const start = row.startDate || row.exactDate
  return row.systemClass ? '—' : !start ? '未设置' : row.endDate && row.endDate !== start ? `${start} ~ ${row.endDate}` : start
}
