import { http, unwrap, type PageResult, type SimpleDept, type SimpleUser } from './api'
import type { Timestamp } from './time'
import type { ImpersonationSession } from './impersonation'

export type { ImpersonationSession } from './impersonation'

export type PersonnelState = { userId: number; state: 'enabled' | 'disabled' | 'departed'; reason?: string; changedAt?: Timestamp }
export type Partner = { id: number; partnerNo: string; name: string; mobile: string; status: 'enabled' | 'disabled' | 'converted'; boundSystemUserId: number; channelId?: string }
export type PartnerCreate = { partnerNo: string; name: string; mobile: string; username: string; password: string; channelId?: string }
export type BusinessAudit = { id: number; operatorNameSnapshot: string; operatorRoleSnapshot: string; actionCode: string; targetType: string; targetId: string; sourceIp?: string; occurredAt: Timestamp }
export type ImpersonationAudit = { id: number; sessionId: number; administratorUserId: number; targetUserId: number; httpMethod: string; requestPath: string; occurredAt: Timestamp }
export type Cashback = { id: number; cashbackNo: string; type: 'valid' | 'deal'; status: 'pending_settlement' | 'available' | 'withdrawing' | 'withdrawn' | 'cancelled'; beneficiaryUserId: number; productNameSnapshot: string; amount: number; generatedAt: Timestamp; availableAt: Timestamp }
export type Withdrawal = { id: number; withdrawalNo: string; applicantUserId: number; status: string; verificationStatus: string; applicationAmount: number; accountNameSnapshot: string; maskedCardNumber: string; cardNumber?: string; bankNameSnapshot: string; branchNameSnapshot?: string; submittedAt: Timestamp; rejectionReason?: string; bankTransactionNo?: string; proofUrl?: string; paidAt?: Timestamp }
export type RelationScene = { id?: number; name: string; code: string; sourceLabel: string; targetLabel: string; sourcePostCode: string; targetPostCode: string; status: number; remark?: string }
export type RelationUser = { id: number; nickname: string; maskedMobile?: string; deptId?: number; deptName?: string; status: number }
export type UserRelation = RelationUser & { targetUsers: RelationUser[]; validTargetCount: number; invalidTargetCount: number; updateTime?: Timestamp }
export type UserRelationLog = { id: number; sourceUsers: string; targetUsers: string; actionType: 'append' | 'replace' | 'remove'; operatorName: string; createTime: Timestamp }
export type SimplePost = { id: number; name: string; code: string }
export type NotifyScene = { code: string; name: string; recipientRoles: Array<{ code: string; name: string }>; allowedActions: Array<'none' | 'message_detail' | 'business_detail'>; timed?: boolean }
export type NotifyTemplate = { id: number; name: string; code: string; sceneCode?: string; channelCode?: string }
export type NotifyRule = { id?: number; name: string; sceneCode: string; channelCode: 'in_app' | 'websocket' | 'wecom' | 'sms'; templateId?: number; recipientRoles: string[]; specifiedUserIds: number[]; actionType: 'none' | 'message_detail' | 'business_detail'; timingStage?: 'advance' | 'due' | 'overdue'; timingOffsetMinutes?: number; status: number }

export const managementApi = {
  users: async () => unwrap<SimpleUser[]>(await http.get('/system/user/simple-list')),
  departments: async () => unwrap<SimpleDept[]>(await http.get('/system/dept/simple-list')),
  posts: async () => unwrap<SimplePost[]>(await http.get('/system/post/simple-list')),
  personnelState: async (userId: number) => unwrap<PersonnelState>(await http.get(`/zsjos/personnel/${userId}/state`)),
  updatePersonnelState: async (userId: number, state: PersonnelState['state'], reason: string) => unwrap<boolean>(await http.put(`/zsjos/personnel/${userId}/state`, { state, reason })),
  partners: async () => unwrap<Partner[]>(await http.get('/zsjos/partner/list')),
  createPartner: async (data: PartnerCreate) => unwrap<number>(await http.post('/zsjos/partner/create', data)),
  setPartnerEnabled: async (id: number, enabled: boolean, reason: string) => unwrap<boolean>(await http.put(`/zsjos/partner/${id}/${enabled ? 'enable' : 'disable'}`, { reason })),
  convertPartner: async (id: number, data: { targetType: string; deptId: number; migrateHistoricalOrganization: boolean; reason: string }) => unwrap<boolean>(await http.post(`/zsjos/partner/${id}/convert`, data)),
  startImpersonation: async (targetUserId: number, reason: string) => unwrap<ImpersonationSession>(await http.post('/zsjos/impersonation/start', { targetUserId, reason })),
  endImpersonation: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/impersonation/${id}/end`, undefined, { params: { reason: 'manual' } })),
  businessAudits: async (params: { pageNo: number; pageSize: number; actionCode?: string; targetType?: string }) => unwrap<PageResult<BusinessAudit>>(await http.get('/zsjos/business-audit/page', { params })),
  impersonationAudits: async (params: { pageNo: number; pageSize: number; sessionId?: number }) => unwrap<PageResult<ImpersonationAudit>>(await http.get('/zsjos/business-audit/impersonation-page', { params })),
  cashbacks: async (own: boolean, params: { pageNo: number; pageSize: number; type?: string; status?: string }) => unwrap<PageResult<Cashback>>(await http.get(`/zsjos/cashback/${own ? 'my-' : ''}page`, { params })),
  withdrawals: async (own: boolean, params: { pageNo: number; pageSize: number; status?: string }) => unwrap<PageResult<Withdrawal>>(await http.get(`/zsjos/withdrawal/${own ? 'my-' : ''}page`, { params })),
  withdrawal: async (id: number, scope: 'own' | 'admin' | 'finance') => unwrap<Withdrawal>(await http.get(scope === 'own' ? `/zsjos/withdrawal/my/${id}` : scope === 'finance' ? `/zsjos/withdrawal/${id}/finance-detail` : `/zsjos/withdrawal/${id}`)),
  applyWithdrawal: async (data: { cashbackIds: number[]; accountName: string; cardNumber: string; bankName: string; branchName?: string; saveCard: boolean }) => unwrap<number>(await http.post('/zsjos/withdrawal/apply', data)),
  cancelWithdrawal: async (id: number) => unwrap<boolean>(await http.put(`/zsjos/withdrawal/${id}/cancel`)),
  rejectWithdrawal: async (id: number, reason: string) => unwrap<boolean>(await http.put(`/zsjos/withdrawal/${id}/reject-approved`, { reason })),
  uploadWithdrawalProof: async (file: File) => { const data = new FormData(); data.append('file', file); return unwrap<{ infraFileId: number }>(await http.post('/zsjos/withdrawal/proof/upload', data)) },
  payoutWithdrawal: async (id: number, data: { bankTransactionNo: string; proofFileId: number; remark?: string }) => unwrap<boolean>(await http.put(`/zsjos/withdrawal/${id}/payout`, data)),
  relationScenes: async (params: { pageNo: number; pageSize: number; name?: string; code?: string; status?: number }) => unwrap<PageResult<RelationScene>>(await http.get('/zsjos/user-relation/scene/page', { params })),
  relationScene: async (id: number) => unwrap<RelationScene>(await http.get('/zsjos/user-relation/scene/get', { params: { id } })),
  createRelationScene: async (data: RelationScene) => unwrap<number>(await http.post('/zsjos/user-relation/scene/create', data)),
  updateRelationScene: async (data: RelationScene) => unwrap<boolean>(await http.put('/zsjos/user-relation/scene/update', data)),
  deleteRelationScene: async (id: number) => unwrap<boolean>(await http.delete('/zsjos/user-relation/scene/delete', { params: { id } })),
  relations: async (sceneCode: string, pageNo = 1, pageSize = 20) => unwrap<PageResult<UserRelation>>(await http.get('/zsjos/user-relation/relation/page', { params: { sceneCode, pageNo, pageSize } })),
  relationTargets: async (sceneCode: string) => unwrap<RelationUser[]>(await http.get('/zsjos/user-relation/target/simple-list', { params: { sceneCode } })),
  saveRelations: async (data: { sceneCode: string; sourceUserIds: number[]; targetUserIds: number[]; mode: 'append' | 'replace' | 'remove' }) => unwrap<boolean>(await http.put('/zsjos/user-relation/relation/save', data)),
  relationLogs: async (sceneCode: string, pageNo = 1, pageSize = 20) => unwrap<PageResult<UserRelationLog>>(await http.get('/zsjos/user-relation/log/page', { params: { sceneCode, pageNo, pageSize } })),
  maintenance: async () => unwrap<{ enabled: boolean }>(await http.get('/system/maintenance-mode')),
  updateMaintenance: async (enabled: boolean) => unwrap<boolean>(await http.put('/system/maintenance-mode', { enabled })),
  notifyScenes: async () => unwrap<NotifyScene[]>(await http.get('/system/notify-scene/list')),
  notifyTemplates: async () => unwrap<NotifyTemplate[]>(await http.get('/system/notify-template/simple-list')),
  notifyRules: async (params: { pageNo: number; pageSize: number; name?: string; sceneCode?: string }) => unwrap<PageResult<NotifyRule>>(await http.get('/system/notify-rule/page', { params })),
  notifyRule: async (id: number) => unwrap<NotifyRule>(await http.get('/system/notify-rule/get', { params: { id } })),
  saveNotifyRule: async (data: NotifyRule) => unwrap<number | boolean>(data.id ? await http.put('/system/notify-rule/update', data) : await http.post('/system/notify-rule/create', data)),
  deleteNotifyRule: async (id: number) => unwrap<boolean>(await http.delete('/system/notify-rule/delete', { params: { id } })),
  updateNotifyRuleStatus: async (id: number, status: number) => unwrap<boolean>(await http.put('/system/notify-rule/update-status', { id, status }))
}
