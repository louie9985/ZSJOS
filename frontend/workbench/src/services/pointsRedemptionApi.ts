import { http, unwrap, type PageResult } from './api'
import type { Timestamp } from './time'
import { APP_ROUTES } from '../constants'
export const POINTS_ROUTE = APP_ROUTES.POINTS_REDEMPTION
export const POINTS_PACKAGE_ROUTE = APP_ROUTES.POINTS_PACKAGES
const BASE = '/zsjos/points-redemption'
export type PointsPackage = { id: number; name: string; creditsAmount: number; validDays?: number | null; enabled: boolean }
export type PointsAttachment = { id: number; name: string; type?: string; size?: number; url?: string }
export type PointsRequest = { id: number; requestNo: string; applicantName: string; packageName: string; creditsAmount: number;
  validDays?: number; reason: string; approvalStatus: string; issueStatus: string; processInstanceId: string;
  codeStatus?: string; lastError?: string; canReveal: boolean; submittedAt: Timestamp; expiresAt?: Timestamp;
  redeemedAt?: Timestamp; syncedAt?: Timestamp; attachments?: PointsAttachment[] }
export type PointsCreate = { packageId: number; reason: string; attachmentIds: number[]; submissionKey: string }
export type PointsApproval = {
  availability: 'AVAILABLE' | 'UNAVAILABLE'; unavailableReason?: string; canUrge: boolean; nextUrgeAt?: number
  progress?: { status: number; currentTasks: Array<{ id: string; name: string; assigneeName?: string }>; nodes: Array<{
    id: string; name: string; status?: number; candidates: Array<{ id: number; name?: string }>
    tasks: Array<{ id: string; status?: number; assigneeName?: string; ownerName?: string; createTime?: Timestamp; endTime?: Timestamp; reason?: string }>
  }> }
}
export const pointsApi = {
  approval: async (id: number) => unwrap<PointsApproval>(await http.get(`${BASE}/approval`, { params: { id } })),
  urge: async (id: number, idempotencyKey: string) => unwrap<boolean>(await http.post(`${BASE}/urge`, { idempotencyKey }, { params: { id } })),
  page: async (pageNo: number, management: boolean) => unwrap<PageResult<PointsRequest>>(await http.get(`${BASE}/page`, { params: { pageNo, pageSize: 20, management } })),
  get: async (id: number) => unwrap<PointsRequest>(await http.get(`${BASE}/get`, { params: { id } })),
  packages: async (management = false) => unwrap<PointsPackage[]>(await http.get(`${BASE}/${management ? 'package-list' : 'packages'}`)),
  savePackage: async (data: Omit<PointsPackage, 'id'> & { id?: number }) => unwrap<number>(await http.post(`${BASE}/package-save`, data)),
  create: async (data: PointsCreate) => unwrap<number>(await http.post(`${BASE}/create`, data)),
  reveal: async (id: number) => unwrap<string>(await http.post(`${BASE}/reveal`, null, { params: { id } })),
  action: async (id: number, action: 'cancel' | 'retry' | 'refresh' | 'disable') => unwrap<boolean>(await http.post(`${BASE}/${action}`, null, { params: { id } })),
  upload: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<PointsAttachment>(await http.post(`${BASE}/upload`, data)) }
}
export function pointsState(row: PointsRequest) {
  if (row.approvalStatus === 'pending') return '审批中'
  if (row.approvalStatus === 'rejected') return '已驳回'
  if (row.approvalStatus === 'cancelled') return '已取消'
  if (row.issueStatus === 'failed') return '审批已通过，发码失败'
  if (row.issueStatus !== 'issued') return '审批已通过，兑换码生成中'
  return ({ available: '可兑换', redeemed: '已兑换', disabled: '已作废', expired: '已过期' } as Record<string,string>)[row.codeStatus || ''] || '已生成'
}
