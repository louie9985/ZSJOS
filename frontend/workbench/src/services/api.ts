import axios, { type AxiosRequestConfig } from 'axios'
import { APP_CONFIG, STORAGE_KEYS } from '../constants'
import type { Timestamp } from './time'

export type User = { id: number; nickname: string; avatar?: string; username?: string }
export type RawMenu = { id: number; name: string; path?: string; icon?: string; component?: string; componentName?: string; visible?: boolean; keepAlive?: boolean; alwaysShow?: boolean; type?: number; sort?: number; parentId: number; children?: RawMenu[] }
export type WorkbenchMenu = Omit<RawMenu, 'children' | 'path'> & { path: string; hidden: boolean; noCache: boolean; alwaysShow: boolean; children: WorkbenchMenu[] }
export type PermissionInfo = { user: User; roles: string[]; permissions: string[]; menus: RawMenu[] }
export type DictData = { label: string; value: string; dictType: string; colorType?: string; cssClass?: string }
export type SalesUser = { id: number; nickname: string; maskedMobile?: string; deptName?: string; avatar?: string }
export type AssignmentUser = SalesUser & { deptId?: number; status: number }
export type AssignmentRelation = AssignmentUser & { salesUsers: AssignmentUser[]; validSalesCount: number; invalidSalesCount: number; updateTime?: Timestamp }
export type AssignmentLog = { id: number; sourceUsers: string; targetUsers: string; actionType: 'append' | 'replace' | 'remove'; operatorName: string; createTime: Timestamp }
export type PageResult<T> = { list: T[]; total: number }
export type AreaNode = {
  id: number
  name: string
  selectionCode: string
  leafSelectable: boolean
  children?: AreaNode[]
}
export type LeadCategoryNode = { id: number; name: string; children: LeadCategoryNode[] }
export type LeadCatalogItem = {
  categoryId: number; categoryName: string; categoryPath: Array<{ id: number; name: string }>
  level1CategoryId?: number; level1CategoryName?: string; level2CategoryId?: number; level2CategoryName?: string
  spuRef: string; spuName: string
  attrs: Array<{ attrKey: string; attrName: string; required: boolean; values: Array<{ value: string; label: string }> }>
}
export type LeadCatalogSku = { spuRef: string; skuRef: string; skuName: string; attrValues: Record<string, string>; price: number }
export type LeadCatalog = { categoryTree: LeadCategoryNode[]; spus: LeadCatalogItem[]; skus: LeadCatalogSku[] }
export type LeadAttachment = { infraFileId: number; fileUrl: string; originalName: string; contentType: string; fileSize: number }
export type LeadCreateRequest = {
  name: string; mobile?: string; wechatId?: string; provinceCode: string; cityCode: string
  intendedProducts: Array<{ spuRef?: string; skuRef?: string; spuUnknown: boolean; skuUnknown: boolean; primary: boolean }>; sourceChannel: string; leadCategory: string
  remark?: string; attachments: Array<{ infraFileId: number }>; dispatchMode: 'auto' | 'specified'
  specifiedSalesUserId?: number; idempotencyKey: string
}
export type LeadCreateResult = { leadId: number; outcome: 'created' | 'activated'; assignmentStatus: string; pendingAssigneeUserId?: number }
export type PendingLead = {
  id: number; dispatchMode: 'auto' | 'specified'; maskedName: string; maskedMobile?: string; maskedWechatId?: string
  provinceName: string; cityName: string; intendedProducts: string[]; primaryIntendedProduct?: string
  sourceChannel: string; leadCategory: string; remark?: string; attachmentUrls: string[]
  submittedAt: Timestamp; expiresAt?: Timestamp
  remainingSeconds?: number; rejectable: boolean; deferrable: boolean; assignmentHistoryId?: number
}
export type SalesDispatchStatus = {
  eligible: boolean
  presence: 'online' | 'offline'
  mode: 'accepting' | 'paused'
  effectiveStatus: 'online' | 'busy' | 'offline'
}
export type ManagedLeadProduct = {
  id: number; spuRef?: string; spuName?: string; skuRef?: string; skuName?: string
  selectedAttrValues?: string; price?: number; categoryName?: string; primary: boolean
}
export type ManagedLeadAttachment = { id: number; fileUrl: string; originalName: string; contentType: string; fileSize: number }
export type ManagedLead = {
  id: number; personId: number; submittedName: string; submittedMobile?: string; submittedWechatId?: string
  sourceType: string; sourceUserId?: number; sourceUserName?: string; sourceChannel?: string
  provinceCode?: string; provinceName?: string; cityCode?: string; cityName?: string; leadCategory?: string
  remark?: string; status: string; assignmentStatus: string; handlingStage: string
  qualificationStatus: 'pending' | 'valid' | 'invalid'
  followUpStatus?: 'first_follow_pending' | 'following' | 'deal_pending_approval' | 'won'
  operationalStatus: 'active' | 'suspended'; dispatchMode?: string
  ownerUserId?: number; ownerUserName?: string; pendingAssigneeUserId?: number; pendingAssigneeUserName?: string
  pendingExpiresAt?: Timestamp; assignmentAttemptCount?: number; publicPoolAt?: Timestamp; submittedAt: Timestamp
  currentAssignmentFirstFollowUpAt?: Timestamp; currentAssignmentFirstFollowUpDeadlineAt?: Timestamp
  qualificationStartedAt?: Timestamp; qualificationDeadlineAt?: Timestamp; suspendedAt?: Timestamp
  qualifiedByUserId?: number; qualifiedByUserName?: string; qualifiedAt?: Timestamp; validDescription?: string
  convertedAt?: Timestamp; invalidReason?: string; invalidReasonLabelSnapshot?: string; invalidDescription?: string
  invalidEvidence?: LeadAppealEvidence[]
  recycleSourceOwnerUserId?: number; recycleSourceOwnerUserName?: string
  appealDeadlineAt?: Timestamp; closedAt?: Timestamp; closeReason?: string
  createTime: Timestamp; updateTime: Timestamp; relationTypes: Array<'submitter' | 'owner'>
  primaryProduct?: ManagedLeadProduct; intendedProducts?: ManagedLeadProduct[]; attachments?: ManagedLeadAttachment[]
  opportunity?: { id: number; status: string; nextFollowUpAt?: Timestamp }
  availableActions?: Array<{ code: 'EDIT_BASIC_INFO' | 'ADD_FOLLOW_UP' | 'JUDGE_VALID' | 'JUDGE_INVALID' | 'ENTER_DEAL'; enabled: boolean }>
}
export type LeadQualificationException = {
  id: number; submittedName: string; submittedMobile?: string; status: string; assignmentStatus: string
  handlingStage: string; ownerUserId?: number; ownerUserName?: string
  recycleSourceOwnerUserId?: number; recycleSourceOwnerUserName?: string
  qualificationDeadlineAt?: Timestamp; suspendedAt?: Timestamp
}
export type LeadInboxFilterOption = { key: string; label: string; count: number }
export type LeadInboxFilterSection = { key: string; label: string; options: LeadInboxFilterOption[] }
export type LeadInboxFilterGroup = { key: string; label: string; count: number; sections: LeadInboxFilterSection[] }
export type LeadInboxFilterProfile = { groups: LeadInboxFilterGroup[] }
export type ManagedLeadPageParams = {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
  inboxGroup?: string
  inboxStage?: string
}
export type LeadFollowUpImage = { infraFileId: number; originalName: string; contentType: string; fileSize: number; sort: number; url?: string }
export type LeadFollowUp = {
  id: number; leadId: number; assignmentHistoryId?: number; opportunityId?: number; recordScope: 'lead' | 'opportunity'; operatorUserId: number; operatorName?: string
  occurredAt: Timestamp; firstInAssignment: boolean; method: string; methodLabel: string; result: string; resultLabel: string
  categoryBefore?: string; categoryBeforeLabel?: string; categoryAfter?: string; categoryAfterLabel?: string
  remark?: string; nextFollowUpAt?: Timestamp; images: LeadFollowUpImage[]
}
export type LeadFollowUpCreateRequest = {
  method: string; result: string; leadCategory?: string; remark?: string; nextFollowUpAt?: Timestamp
  images: Array<{ infraFileId: number }>; idempotencyKey: string
}
export type LeadBasicInfoUpdateRequest = {
  name: string; mobile?: string; wechatId?: string; provinceCode: string; cityCode: string; leadCategory?: string
  intendedProducts: LeadCreateRequest['intendedProducts']; reason: string
}
export type LeadAppealEvidence = { infraFileId: number; fileUrl?: string; originalName: string; contentType: string; fileSize: number; sort?: number }
export type LeadAppeal = {
  id: number; leadId: number; leadName: string; roundNo: number; reviewStage: 'sales_manager' | 'quality' | 'chairman'
  status: 'sales_manager_reviewing' | 'quality_reviewing' | 'chairman_reviewing' | 'overturned' | 'upheld' | 'withdrawn'
  applicantUserId: number; applicantUserName?: string; reason: string; evidence: LeadAppealEvidence[]
  invalidReasonSnapshot?: string; invalidDescriptionSnapshot?: string; invalidEvidenceSnapshot: LeadAppealEvidence[]
  processInstanceId?: string; taskId?: string; reviewerUserId?: number; reviewerUserName?: string
  decisionReason?: string; decisionEvidence: LeadAppealEvidence[]; submittedAt: Timestamp; decidedAt?: Timestamp
  canSubmitNextRound: boolean
}
export type BusinessTaskBucket = 'unscheduled' | 'overdue' | 'today' | 'future'
export type BusinessTaskSummary = Record<BusinessTaskBucket, number>
export type BusinessTask = {
  id: number; taskType: string; bizType: string; bizId: number; title: string; summary?: string
  status: 'pending' | 'completed' | 'cancelled'; dueAt?: Timestamp; remindAt?: Timestamp
  completedAt?: Timestamp; cancelledAt?: Timestamp; createTime: Timestamp; overdue: boolean
  actionCode?: 'OPEN_LEAD_ASSIGNMENT' | 'OPEN_LEAD_FOLLOW_UP' | 'OPEN_WORK_PLAN_ITEM' | 'REVIEW_WORK_PLAN_ITEM'
  actionable: boolean
}
export type BpmTask = {
  id: string; name: string; createTime: Timestamp; endTime?: Timestamp; status: number; reason?: string
  processInstanceId: string; processInstance?: { id: string; name: string; createTime: Timestamp; startUser?: { id: number; nickname: string } }
}
export type SimpleUser = { id: number; nickname: string; avatar?: string; deptId?: number; deptName?: string }
export type SimpleDept = { id: number; name: string; parentId?: number }
export type WorkPlanAttachmentUpload = { infraFileId: number; originalName: string; contentType?: string; fileSize?: number }
export type WorkPlanChange = { id: number; subjectType: string; subjectId: number; changeType: string; beforeSnapshot?: string; afterSnapshot?: string; reason: string; operatorUserId: number; changedAt: Timestamp }
export type WorkReport = {
  id: number; revisionNo: number; completionSummary: string; submitterUserId: number; submittedAt: Timestamp
  confirmationDecision?: 'auto_confirmed' | 'confirmed' | 'returned'; confirmationComment?: string
  confirmedByUserId?: number; confirmedAt?: Timestamp; infraFileIds: number[]; reportFields?: Record<string, unknown>
}
export type WorkTask = {
  id: number; planId?: number; parentTaskId?: number; title: string; description?: string; deliverableRequirement?: string
  assigneeUserId: number; assigneeDeptId?: number; assignerUserId: number; dueAt?: Timestamp; remindAt?: Timestamp
  confirmationRequired: boolean; confirmerUserId?: number
  status: 'draft' | 'pending' | 'awaiting_confirmation' | 'completed' | 'cancelled'; reportedAt?: Timestamp
  completedAt?: Timestamp; cancelledAt?: Timestamp; cancelReason?: string; version: number; blockedByChildren: boolean
  completedChildCount: number; totalChildCount: number; taskFields?: Record<string, unknown>; reports: WorkReport[]
  availableActions: Array<'assign' | 'complete' | 'review' | 'cancel' | 'decompose'>
}
export type WorkPlan = {
  id: number; title: string; periodType: 'day' | 'month' | 'week' | 'quarter' | 'year' | 'custom'; startDate: string; endDate: string
  planTypeId: number; templateId: number; templateVersionId: number; ownerUserId: number; ownerDeptId?: number
  objective?: string; keyRequirements?: string; status: 'draft' | 'active' | 'completed' | 'cancelled'; summaryReady: boolean
  creatorUserId: number; publishedAt?: Timestamp; completedAt?: Timestamp; cancelledAt?: Timestamp; cancelReason?: string; version: number
  availableActions: Array<'update' | 'publish' | 'assign' | 'close' | 'cancel'>; fieldDefinitions: WorkPlanTemplateField[]
  planFields?: Record<string, unknown>; tasks: WorkTask[]; summary?: WorkPlanSummary; changes: WorkPlanChange[]
}
export type WorkTaskInput = {
  id?: number; parentTaskId?: number; title: string; description?: string; deliverableRequirement?: string; assigneeUserId: number
  dueAt?: string; remindAt?: string; confirmationRequired: boolean; confirmerUserId?: number
  taskFields?: Record<string, unknown>; version?: number; reason?: string
}
export type WorkPlanInput = {
  title: string; periodType: WorkPlan['periodType']; startDate: string; endDate: string; templateVersionId: number; ownerUserId: number
  objective?: string; keyRequirements?: string; planFields?: Record<string, unknown>; supplementalFields?: WorkPlanTemplateField[]
  version?: number; reason?: string; tasks?: WorkTaskInput[]
}
export type WorkPlanType = { id: number; code: string; name: string; description?: string; status: number; sort: number }
export type WorkPlanTemplateField = { id?: number; fieldKey?: string; label: string; section: 'plan' | 'task' | 'report' | 'summary'; fieldType: string; required?: boolean; unit?: string; placeholder?: string; filterable?: boolean; exportable?: boolean; optionsJson?: string; defaultValueJson?: string; sort?: number }
export type WorkPlanTemplateTask = { title: string; description?: string; deliverableRequirement?: string; dueOffsetDays?: number; dueOffsetBasis?: string; confirmationRequired?: boolean; sort?: number }
export type WorkPlanTemplate = { id: number; typeId: number; code: string; name: string; description?: string; status: string; currentVersionNo: number; versionId?: number; versionStatus?: string; periodMode?: WorkPlan['periodType']; fields?: WorkPlanTemplateField[]; applicableDeptIds?: number[]; includeChildDepartments?: boolean; presetItems?: WorkPlanTemplateTask[] }
export type WorkPlanSummary = { id: number; summary: string; submitterUserId: number; submittedAt: Timestamp; infraFileIds: number[]; summaryFields?: Record<string, unknown> }
export type NotifyMessage = {
  id: number
  templateNickname: string
  templateTitle?: string
  templateSummary?: string
  templateContent: string
  templateType: number
  readStatus: boolean
  readTime?: Timestamp
  createTime: Timestamp
  notifyRuleId?: number
  sceneCode?: string
  actionType?: 'none' | 'message_detail' | 'business_detail'
  bizType?: string
  bizId?: number
}

export type NotifyMessagePageParams = {
  pageNo: number
  pageSize: number
  readStatus?: boolean
}

const http = axios.create({ baseURL: APP_CONFIG.API_BASE_URL, timeout: 30000 })
let refreshing: Promise<string | null> | null = null

export class AuthenticationError extends Error {
  readonly code = 401
  constructor(message = '账号未登录') {
    super(message)
    this.name = 'AuthenticationError'
  }
}

export class ApiError extends Error {
  constructor(readonly code: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export const clearAuthStorage = () => {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.EXPIRES_TIME)
}

http.interceptors.request.use(config => {
  config.headers['tenant-id'] = APP_CONFIG.DEFAULT_TENANT_ID
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const isAuthEndpoint = (url?: string) => ['/system/auth/login', '/system/auth/logout', '/system/auth/refresh-token']
  .some(path => url?.includes(path))

const retryAfterRefresh = async (config: AxiosRequestConfig, originalError: unknown) => {
  const request = config as AxiosRequestConfig & { _retry?: boolean }
  if (request._retry || isAuthEndpoint(request.url)) return Promise.reject(originalError)
  request._retry = true
  refreshing ??= refreshToken().finally(() => { refreshing = null })
  const token = await refreshing
  if (!token) {
    clearAuthStorage()
    return Promise.reject(new AuthenticationError())
  }
  request.headers = { ...request.headers, Authorization: `Bearer ${token}` }
  return http(request)
}

http.interceptors.response.use(async response => {
  if (response.data?.code === 401) return retryAfterRefresh(response.config, new AuthenticationError(response.data.msg))
  return response
}, async error => {
  const original = error.config as AxiosRequestConfig & { _retry?: boolean } | undefined
  if (error.response?.status !== 401 || !original) return Promise.reject(error)
  return retryAfterRefresh(original, error)
})

export const unwrap = <T,>(response: { data: any }): T => {
  const payload = response.data
  if (payload && typeof payload.code === 'number') {
    if (payload.code === 401) throw new AuthenticationError(payload.msg)
    if (payload.code !== 0) throw new ApiError(payload.code, payload.msg || `请求失败（${payload.code}）`)
    return payload.data as T
  }
  return payload as T
}

async function refreshToken(): Promise<string | null> {
  const refresh = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)
  if (!refresh) return null
  try {
    const response = await axios.post(`${APP_CONFIG.API_BASE_URL}/system/auth/refresh-token?refreshToken=${encodeURIComponent(refresh)}`, undefined, { headers: { 'tenant-id': APP_CONFIG.DEFAULT_TENANT_ID }, timeout: 30000 })
    const result = unwrap<{ accessToken: string; refreshToken: string }>(response)
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.accessToken)
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.refreshToken)
    return result.accessToken
  } catch { return null }
}

const isUrl = (path: string) => /^https?:\/\//i.test(path)

// Keep this aligned with yudao-ui's pathResolve: child paths are relative to their parent.
export const resolveMenuPath = (parentPath: string, path?: string) => {
  if (path && isUrl(path)) return path
  if (!path) return parentPath
  const childPath = path.startsWith('/') ? path : `/${path}`
  return `${parentPath}${childPath}`.replace(/\/{2,}/g, '/')
}

export function buildMenuTree(rawMenus: RawMenu[], parentPath = '/'): WorkbenchMenu[] {
  return rawMenus.map(menu => {
    const path = resolveMenuPath(parentPath, menu.path)
    const children = buildMenuTree(menu.children || [], path)
    return {
      ...menu,
      path,
      hidden: !menu.visible,
      noCache: !menu.keepAlive,
      alwaysShow: children.length > 0 && (menu.alwaysShow ?? true),
      children
    }
  })
}

export const api = {
  login: async (username: string, password: string) => {
    const result = unwrap<{ accessToken: string; refreshToken: string; expiresTime: string }>(await http.post('/system/auth/login', { username, password }))
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.accessToken)
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.refreshToken)
    localStorage.setItem(STORAGE_KEYS.EXPIRES_TIME, result.expiresTime)
    return result
  },
  logout: async () => {
    try { await http.post('/system/auth/logout') } finally {
      clearAuthStorage()
    }
  },
  permissionInfo: async () => unwrap<PermissionInfo>(await http.get('/system/auth/get-permission-info')),
  dictDataByType: async (dictType: string) => {
    const dictData = unwrap<DictData[]>(await http.get('/system/dict-data/simple-list'))
    return dictData.filter(item => item.dictType === dictType)
  },
  areaTree: async () => unwrap<AreaNode[]>(await http.get('/system/area/tree')),
  leadCatalog: async () => unwrap<LeadCatalog>(await http.get('/zsjos/lead/product/catalog')),
  uploadLeadAttachment: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead/attachment/upload', data))
  },
  createLead: async (data: LeadCreateRequest) => unwrap<LeadCreateResult>(await http.post('/zsjos/lead/create', data)),
  myPendingLeads: async () => unwrap<PendingLead[]>(await http.get('/zsjos/lead/assignment/my-pending')),
  myDispatchStatus: async () => unwrap<SalesDispatchStatus>(await http.get('/zsjos/lead/dispatch-status/my')),
  dispatchHeartbeat: async () => unwrap<SalesDispatchStatus>(await http.post('/zsjos/lead/dispatch-status/heartbeat')),
  updateDispatchMode: async (accepting: boolean) => unwrap<SalesDispatchStatus>(
    await http.put('/zsjos/lead/dispatch-status/mode', { accepting })
  ),
  dispatchOffline: async () => unwrap<SalesDispatchStatus>(await http.post('/zsjos/lead/dispatch-status/offline')),
  acceptLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/accept`)),
  rejectLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/reject`)),
  claimPoolPage: async (params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<PendingLead>>(await http.get('/zsjos/lead/claim-pool/page', { params })),
  claimLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/claim`)),
  managedLeadInboxPage: async (audience: 'submitter' | 'owner', params: ManagedLeadPageParams) =>
    unwrap<PageResult<ManagedLead>>(await http.get(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/page`, { params })),
  managedLead: async (id: number) => unwrap<ManagedLead>(await http.get('/zsjos/lead/get', { params: { id } })),
  managedLeadStatusCounts: async () => unwrap<Record<string, number>>(await http.get('/zsjos/lead/status-counts')),
  judgeLeadValid: async (id: number, data: { leadCategory?: string; remark: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/judge-valid`, data)),
  judgeLeadInvalid: async (id: number, data: { reasonCode: string; description: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/judge-invalid`, data)),
  uploadLeadQualificationImage: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead/qualification/attachment/upload', data))
  },
  updateLeadBasicInfo: async (id: number, data: LeadBasicInfoUpdateRequest) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/${id}/basic-info`, data)),
  qualificationExceptionPage: async (type: 'suspended' | 'recycle_pending', params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<LeadQualificationException>>(await http.get('/zsjos/lead/qualification-exception/page', { params: { type, ...params } })),
  leadTransferCandidates: async (id: number) =>
    unwrap<AssignmentUser[]>(await http.get(`/zsjos/lead/${id}/transfer-candidates`)),
  restoreLead: async (id: number, data: { reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/restore`, data)),
  transferLead: async (id: number, data: { salesUserId: number; reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/transfer`, data)),
  recycleLead: async (id: number, data: { reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/recycle`, data)),
  releaseLeadToClaimPool: async (id: number, data: { reason: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/release-to-claim-pool`, data)),
  leadInboxFilterProfile: async (audience: 'submitter' | 'owner') =>
    unwrap<LeadInboxFilterProfile>(await http.get(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/filter-profile`)),
  leadFollowUpPage: async (leadId: number, params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<LeadFollowUp>>(await http.get(`/zsjos/lead/${leadId}/follow-ups/page`, { params })),
  createLeadFollowUp: async (leadId: number, data: LeadFollowUpCreateRequest) =>
    unwrap<LeadFollowUp>(await http.post(`/zsjos/lead/${leadId}/follow-ups`, data)),
  uploadLeadFollowUpImage: async (leadId: number, file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post(`/zsjos/lead/${leadId}/follow-up-image/upload`, data))
  },
  leadAppeals: async (leadId: number) =>
    unwrap<LeadAppeal[]>(await http.get(`/zsjos/lead/appeal/lead/${leadId}/list`)),
  submitLeadAppeal: async (leadId: number, data: { reason: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string }) =>
    unwrap<number>(await http.post(`/zsjos/lead/appeal/lead/${leadId}/submit`, data)),
  leadAppealInboxPage: async (handled: boolean, params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<LeadAppeal>>(await http.get('/zsjos/lead/appeal/inbox-page', { params: { handled, ...params } })),
  decideLeadAppeal: async (appealId: number, decision: 'overturn' | 'uphold', data: { taskId: string; reason: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/appeal/${appealId}/${decision}`, data)),
  uploadLeadAppealImage: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead/appeal/attachment/upload', data))
  },
  businessTaskSummary: async () => unwrap<BusinessTaskSummary>(await http.get('/zsjos/business-task/my-summary')),
  businessTaskPage: async (bucket: BusinessTaskBucket, params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<BusinessTask>>(await http.get('/zsjos/business-task/my-page', { params: { bucket, ...params } })),
  businessTaskList: async (params: { status: 'pending' | 'done'; bucket?: BusinessTaskBucket; pageNo: number; pageSize: number }) =>
    unwrap<PageResult<BusinessTask>>(await http.get('/zsjos/business-task/my-task-page', { params })),
  bpmTaskPage: async (view: 'todo' | 'done', params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<BpmTask>>(await http.get(`/bpm/task/${view}-page`, { params })),
  simpleUsers: async () => unwrap<SimpleUser[]>(await http.get('/system/user/simple-list')),
  simpleDepartments: async () => unwrap<SimpleDept[]>(await http.get('/system/dept/simple-list')),
  workPlanPage: async (params: { pageNo: number; pageSize: number; periodType?: WorkPlan['periodType']; status?: string; startDate?: string; endDate?: string }) =>
    unwrap<PageResult<WorkPlan>>(await http.get('/zsjos/work-plan/page', { params })),
  workPlan: async (id: number) => unwrap<WorkPlan>(await http.get('/zsjos/work-plan/get', { params: { id } })),
  workTask: async (id: number) => unwrap<WorkTask>(await http.get('/zsjos/work-plan/task/get', { params: { id } })),
  myWorkTaskPage: async (params: { pageNo: number; pageSize: number; status?: string }) =>
    unwrap<PageResult<WorkTask>>(await http.get('/zsjos/work-plan/task/my-page', { params })),
  createWorkPlan: async (data: WorkPlanInput) => unwrap<number>(await http.post('/zsjos/work-plan/create', data)),
  updateWorkPlan: async (id: number, data: WorkPlanInput) => unwrap<boolean>(await http.put(`/zsjos/work-plan/${id}`, data)),
  publishWorkPlan: async (id: number, version: number) => unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/publish`, { version })),
  cancelWorkPlan: async (id: number, version: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/cancel`, { version, reason })),
  createTemporaryTask: async (data: WorkTaskInput) => unwrap<number>(await http.post('/zsjos/work-plan/task/temporary', data)),
  addWorkTask: async (planId: number, data: WorkTaskInput) => unwrap<number>(await http.post(`/zsjos/work-plan/${planId}/task`, data)),
  adjustWorkTask: async (id: number, data: WorkTaskInput) => unwrap<boolean>(await http.put(`/zsjos/work-plan/task/${id}`, data)),
  submitWorkReport: async (id: number, data: { completionSummary: string; infraFileIds: number[]; version: number; reportFields?: Record<string, unknown> }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/task/${id}/report`, data)),
  uploadWorkPlanAttachment: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<WorkPlanAttachmentUpload>(await http.post('/zsjos/work-plan/attachment/upload', data))
  },
  confirmWorkReport: async (id: number, data: { decision: 'confirmed' | 'returned'; comment?: string; version: number }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/task/${id}/confirm`, data)),
  cancelWorkTask: async (id: number, data: { version: number; reason: string; cascadeChildren?: boolean }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/task/${id}/cancel`, data)),
  submitWorkPlanSummary: async (id: number, data: { version: number; summary: string; infraFileIds: number[]; summaryFields?: Record<string, unknown> }) =>
    unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/summary`, data)),
  workPlanTypes: async () => unwrap<WorkPlanType[]>(await http.get('/zsjos/work-plan-config/types')),
  workPlanTemplates: async () => unwrap<WorkPlanTemplate[]>(await http.get('/zsjos/work-plan/templates/available')),
  unreadNotifyCount: async () => unwrap<number>(await http.get('/system/notify-message/get-unread-count')),
  unreadNotifyMessages: async () => unwrap<NotifyMessage[]>(await http.get('/system/notify-message/get-unread-list')),
  myNotifyMessagePage: async (params: NotifyMessagePageParams) =>
    unwrap<PageResult<NotifyMessage>>(await http.get('/system/notify-message/my-page', { params })),
  myNotifyMessage: async (id: number) =>
    unwrap<NotifyMessage>(await http.get('/system/notify-message/my-get', { params: { id } })),
  markNotifyMessagesRead: async (ids: number[]) => {
    const params = new URLSearchParams()
    ids.forEach(id => params.append('ids', String(id)))
    return unwrap<boolean>(await http.put('/system/notify-message/update-read', undefined, { params }))
  },
  markAllNotifyMessagesRead: async () => unwrap<boolean>(await http.put('/system/notify-message/update-all-read')),
  salesUsers: async () => unwrap<SalesUser[]>(await http.get('/zsjos/lead/sales-user/simple-list')),
  assignmentRelationPage: async (params: { pageNo: number; pageSize: number; keyword?: string; configured?: boolean }) =>
    unwrap<PageResult<AssignmentRelation>>(await http.get('/zsjos/lead-assignment/relation/page', { params })),
  eligibleSalesUsers: async () => unwrap<AssignmentUser[]>(await http.get('/zsjos/lead-assignment/eligible-sales')),
  saveAssignmentRelations: async (data: { sourceUserIds: number[]; targetUserIds: number[]; mode: 'append' | 'replace' | 'remove' }) =>
    unwrap<boolean>(await http.put('/zsjos/lead-assignment/relation/save', data)),
  assignmentLogPage: async (params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<AssignmentLog>>(await http.get('/zsjos/lead-assignment/log/page', { params }))
}
