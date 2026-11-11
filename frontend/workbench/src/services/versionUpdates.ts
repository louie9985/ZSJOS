import { http, unwrap, type PageResult } from './api'
import type { TimestampValue } from './time'

export const VERSION_UPDATE_PERMISSION = 'zsjos:version-update:'
export type VersionAttachment = { id: number; name: string; type?: string; size?: number; url?: string }
export type VersionUpdate = {
  id: number; title: string; content?: string; status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  publisherName?: string; publishTime?: TimestampValue; acknowledgedTime?: TimestampValue
  recipientCount?: number; acknowledgedCount?: number; targetUserIds?: number[]; attachments: VersionAttachment[]
}
export type VersionUpdateInput = { title: string; content: string; targetUserIds: number[]; attachmentIds: number[]; inlineFiles: VersionAttachment[] }
export type VersionRecipient = { userId: number; userName: string; deptId?: number; deptName?: string; acknowledgedTime?: TimestampValue }
export type VersionOptions = { departments: { id: number; name: string; parentId?: number }[]; users: { id: number; name: string; deptId?: number; selectable: boolean }[] }
export type VersionQuery = { pageNo: number; pageSize: number; keyword?: string; status?: string; pending?: boolean }
const ROOT = '/zsjos/version-update'
export const versionUpdates = {
  page: async (query: VersionQuery) => unwrap<PageResult<VersionUpdate>>(await http.get(`${ROOT}/page`, { params: query })),
  mine: async (query: VersionQuery) => unwrap<PageResult<VersionUpdate>>(await http.get(`${ROOT}/my-page`, { params: query })),
  get: async (id: number, mine = false) => unwrap<VersionUpdate>(await http.get(`${ROOT}/${mine ? 'my/' : ''}${id}`)),
  options: async () => unwrap<VersionOptions>(await http.get(`${ROOT}/recipient-options`)),
  create: async (data: VersionUpdateInput) => unwrap<number>(await http.post(`${ROOT}/create`, data)),
  update: async (id: number, data: VersionUpdateInput) => unwrap<boolean>(await http.put(`${ROOT}/${id}`, data)),
  publish: async (id: number) => unwrap<boolean>(await http.post(`${ROOT}/${id}/publish`)),
  offline: async (id: number) => unwrap<boolean>(await http.post(`${ROOT}/${id}/offline`)),
  acknowledge: async (id: number) => unwrap<boolean>(await http.post(`${ROOT}/my/${id}/acknowledge`)),
  recipients: async (id: number, query: VersionQuery) => unwrap<PageResult<VersionRecipient>>(await http.get(`${ROOT}/${id}/recipients`, { params: query })),
  upload: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<VersionAttachment>(await http.post(`${ROOT}/attachment`, data))
  }
}
export type VersionTreeNode = { key: string; title: string; disabled?: boolean; children?: VersionTreeNode[] }
export function versionRecipientTree(options: VersionOptions): VersionTreeNode[] {
  const departments = new Map(options.departments.map(row => [row.id, { key: `d:${row.id}`, title: row.name, children: [] } as VersionTreeNode]))
  const roots: VersionTreeNode[] = []
  for (const row of options.departments) {
    const parent = row.parentId === row.id ? undefined : departments.get(row.parentId ?? 0)
    if (parent) parent.children!.push(departments.get(row.id)!)
    else roots.push(departments.get(row.id)!)
  }
  const unassigned: VersionTreeNode = { key: 'unassigned', title: '未分配部门', children: [] }
  for (const user of options.users) {
    const node = { key: `u:${user.id}`, title: user.selectable ? user.name : `${user.name}（缺少更新记录或知悉权限）`, disabled: !user.selectable }
    const parent = departments.get(user.deptId ?? 0)
    ;(parent || unassigned).children!.push(node)
  }
  if (unassigned.children!.length) roots.push(unassigned)
  return roots
}
