import { api, http, unwrap, type AnnouncementAttachment, type PageResult } from './api'
import type { TimestampValue } from './time'

const ROOT = '/system/notice'
export const NOTICE_TYPE_DICT = 'system_notice_type'
export const NOTICE_STATUSES = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' } as const
export type NoticeStatus = keyof typeof NOTICE_STATUSES
export type NoticeInput = {
  id?: number; title: string; type: number; content: string; audienceType: 'ALL' | 'TARGET'
  targetDeptIds: number[]; targetUserIds: number[]; highlightUntil?: number | null
  attachments: AnnouncementAttachment[]
}
export type ManagedNotice = Omit<NoticeInput, 'id' | 'highlightUntil'> & {
  id: number; publishStatus: NoticeStatus; highlightUntil?: TimestampValue
  publishTime?: TimestampValue; offlineTime?: TimestampValue; createTime?: TimestampValue
  highlighted?: boolean; recipientCount?: number
}
export type NoticeRecipients = {
  departments: { id: number; parentId: number; name: string }[]
  users: { id: number; nickname: string; deptId?: number; selectable: boolean; disabledReason?: string }[]
}
export function noticeDownloadUrl(url?: string) {
  if (!url) return undefined
  try { return ['http:', 'https:'].includes(new URL(url, window.location.origin).protocol) ? url : undefined }
  catch { return undefined }
}
export function noticePermission(permissions: string[], action: string) {
  return permissions.includes('*:*:*') || permissions.includes(`system:notice:${action}`)
}
export function noticeView(permissions: string[], requested: string | null, hasDeepLink: boolean) {
  const read = noticePermission(permissions, 'read')
  const manage = noticePermission(permissions, 'query')
  if (hasDeepLink) return read ? 'mine' : 'denied'
  if (requested === 'manage' && manage) return 'manage'
  return read ? 'mine' : manage ? 'manage' : 'denied'
}
export function noticeActions(permissions: string[], status: NoticeStatus) {
  return {
    edit: status === 'DRAFT' && noticePermission(permissions, 'update'),
    publish: status === 'DRAFT' && noticePermission(permissions, 'publish'),
    delete: status === 'DRAFT' && noticePermission(permissions, 'delete'),
    offline: status === 'PUBLISHED' && noticePermission(permissions, 'offline'),
    copy: status !== 'DRAFT' && noticePermission(permissions, 'create')
  }
}
export type RecipientNode = { key: string; title: string; disabled?: boolean; checkable?: boolean; children?: RecipientNode[] }
export function noticeRecipientTree(options: NoticeRecipients): RecipientNode[] {
  const nodes = new Map(options.departments.map(dept => [dept.id, { key: `d:${dept.id}`, title: dept.name, children: [] } as RecipientNode]))
  const roots: RecipientNode[] = []
  options.departments.forEach(dept => {
    const parent = nodes.get(dept.parentId)
    if (parent && dept.parentId !== dept.id) parent.children!.push(nodes.get(dept.id)!)
    else roots.push(nodes.get(dept.id)!)
  })
  const unassigned: RecipientNode[] = []
  options.users.forEach(user => {
    const node = { key: `u:${user.id}`, title: `${user.nickname}${user.selectable ? '' : `（${user.disabledReason || '不可接收'}）`}`, disabled: !user.selectable }
    const parent = user.deptId == null ? undefined : nodes.get(user.deptId)
    if (parent) parent.children!.push(node)
    else unassigned.push(node)
  })
  if (unassigned.length) roots.push({ key: 'unassigned', title: '未分配部门', checkable: false, children: unassigned })
  return roots
}
export const noticeManagement = {
  page: async (params: { pageNo: number; pageSize: number; title?: string; publishStatus?: NoticeStatus }) =>
    unwrap<PageResult<ManagedNotice>>(await http.get(`${ROOT}/page`, { params })),
  get: async (id: number) => unwrap<ManagedNotice>(await http.get(`${ROOT}/get`, { params: { id } })),
  create: async (data: NoticeInput) => unwrap<number>(await http.post(`${ROOT}/create`, data)),
  update: async (data: NoticeInput) => unwrap<boolean>(await http.put(`${ROOT}/update`, data)),
  publish: async (id: number) => unwrap<boolean>(await http.post(`${ROOT}/publish`, undefined, { params: { id } })),
  offline: async (id: number) => unwrap<boolean>(await http.post(`${ROOT}/offline`, undefined, { params: { id } })),
  copy: async (id: number) => unwrap<number>(await http.post(`${ROOT}/copy`, undefined, { params: { id } })),
  delete: async (id: number) => unwrap<boolean>(await http.delete(`${ROOT}/delete`, { params: { id } })),
  recipients: async () => unwrap<NoticeRecipients>(await http.get(`${ROOT}/recipient-options`)),
  types: () => api.dictDataByType(NOTICE_TYPE_DICT),
  upload: async (file: File, onProgress: (percent: number) => void) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<AnnouncementAttachment>(await http.post(`${ROOT}/attachment/upload`, data, {
      onUploadProgress: event => onProgress(Math.round((event.progress || 0) * 100))
    }))
  },
  uploadContent: async (file: File, kind: 'image' | 'video') => {
    const data = new FormData(); data.append('file', file); data.append('directory', `system-notice-content-${kind}`)
    return unwrap<string>(await http.post('/infra/file/upload', data))
  }
}
