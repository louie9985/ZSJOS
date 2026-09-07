import request from '@/config/axios'
import type { AxiosProgressEvent } from 'axios'

export interface NoticeVO {
  id: number | undefined
  title: string
  type: number
  content: string
  status: number
  remark: string
  publishStatus: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  publishTime?: number
  offlineTime?: number
  highlightUntil?: number
  audienceType?: 'ALL' | 'TARGET'
  targetDeptIds?: number[]
  targetUserIds?: number[]
  recipientCount?: number
  highlighted?: boolean
  attachments: NoticeAttachmentVO[]
  creator: string
  createTime: Date
}

export interface NoticeAttachmentVO {
  infraFileId: number
  fileName: string
  mimeType?: string
  fileSize: number
  sort: number
  downloadUrl?: string
}

export interface NoticeRecipientOptionsVO {
  departments: Array<{ id: number; parentId: number; name: string }>
  users: Array<{ id: number; nickname: string; deptId?: number; selectable: boolean; disabledReason?: string }>
}

// 查询公告列表
export const getNoticePage = (params: PageParam) => {
  return request.get({ url: '/system/notice/page', params })
}

// 查询公告详情
export const getNotice = (id: number) => {
  return request.get({ url: '/system/notice/get?id=' + id })
}

export const getNoticeRecipientOptions = () => request.get<NoticeRecipientOptionsVO>({ url: '/system/notice/recipient-options' })

// 新增公告
export const createNotice = (data: NoticeVO) => {
  return request.post({ url: '/system/notice/create', data })
}

// 修改公告
export const updateNotice = (data: NoticeVO) => {
  return request.put({ url: '/system/notice/update', data })
}

// 删除公告
export const deleteNotice = (id: number) => {
  return request.delete({ url: '/system/notice/delete?id=' + id })
}

// 批量删除公告
export const deleteNoticeList = (ids: number[]) => {
  return request.delete({ url: '/system/notice/delete-list', params: { ids: ids.join(',') } })
}

// 推送公告
export const pushNotice = (id: number) => {
  return request.post({ url: '/system/notice/publish?id=' + id })
}

export const publishNotice = (id: number) => request.post({ url: '/system/notice/publish?id=' + id })
export const offlineNotice = (id: number) => request.post({ url: '/system/notice/offline?id=' + id })
export const copyNotice = (id: number) => request.post({ url: '/system/notice/copy?id=' + id })
export const uploadNoticeAttachment = (
  file: File,
  onUploadProgress?: (event: AxiosProgressEvent) => void
) => {
  const data = new FormData()
  data.append('file', file)
  return request.upload<{ code: number; msg?: string; data: NoticeAttachmentVO }>({
    url: '/system/notice/attachment/upload', data, onUploadProgress
  })
}
