import { http, unwrap } from './api'
export interface NoticeShare {
  active: boolean; version?: number; attachmentIds: number[]; url?: string
  openedAt?: number; closedAt?: number
}
const ROOT = '/system/notice-share'
export const noticeShareApi = {
  get: async (noticeId: number) => unwrap<NoticeShare>(await http.get(ROOT + '/get', { params: { noticeId } })),
  open: async (noticeId: number, attachmentIds: number[]) => unwrap<NoticeShare>(await http.post(ROOT + '/open', { noticeId, attachmentIds })),
  close: async (noticeId: number, version: number) => unwrap<boolean>(await http.put(ROOT + '/close', { noticeId, version }))
}
