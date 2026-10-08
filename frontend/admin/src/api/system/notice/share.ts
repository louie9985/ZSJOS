import request from '@/config/axios'
export interface NoticeShare {
  active: boolean; version?: number; attachmentIds: number[]; url?: string
  openedAt?: number; closedAt?: number
}
const ROOT = '/system/notice-share'
export const getNoticeShare = (noticeId: number) => request.get<NoticeShare>({
  url: ROOT + '/get', params: { noticeId }, preserveBusinessError: true
})
export const openNoticeShare = (noticeId: number, attachmentIds: number[]) => request.post<NoticeShare>({
  url: ROOT + '/open', data: { noticeId, attachmentIds }, preserveBusinessError: true
})
export const closeNoticeShare = (noticeId: number, version: number) => request.put<boolean>({
  url: ROOT + '/close', data: { noticeId, version }, preserveBusinessError: true
})
