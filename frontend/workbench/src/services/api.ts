import axios, { type AxiosRequestConfig } from 'axios'
import { APP_CONFIG, STORAGE_KEYS } from '../constants'
import type { Timestamp } from './time'

export type User = { id: number; nickname: string; avatar?: string; username?: string }
export type RawMenu = { id: number; name: string; path?: string; icon?: string; component?: string; componentName?: string; visible?: boolean; keepAlive?: boolean; alwaysShow?: boolean; type?: number; sort?: number; parentId: number; children?: RawMenu[] }
export type WorkbenchMenu = Omit<RawMenu, 'children' | 'path'> & { path: string; hidden: boolean; noCache: boolean; alwaysShow: boolean; children: WorkbenchMenu[] }
export type PermissionInfo = { user: User; roles: string[]; permissions: string[]; menus: RawMenu[]; defaultAvatar?: string }
export type DictData = { label: string; value: string; dictType: string; colorType?: string; cssClass?: string }
export type SalesUser = { id: number; nickname: string; maskedMobile?: string; deptName?: string; avatar?: string }
export type AssignmentUser = SalesUser & { deptId?: number; status: number }
export type AssignmentRelation = AssignmentUser & { salesUsers: AssignmentUser[]; validSalesCount: number; invalidSalesCount: number; updateTime?: Timestamp }
export type AssignmentLog = { id: number; sourceUsers: string; targetUsers: string; actionType: 'append' | 'replace' | 'remove'; operatorName: string; createTime: Timestamp }
export type PageResult<T> = { list: T[]; total: number }
export type AdvancedFilterCondition = { fieldKey: string; operator: string; value?: unknown; valueFrom?: unknown; valueTo?: unknown }
export type AdvancedFilterGroup = { logic: 'AND' | 'OR'; conditions: AdvancedFilterCondition[]; groups: AdvancedFilterGroup[] }
export type AdvancedFilterField = { fieldKey: string; group: string; label: string; valueType: 'text' | 'select' | 'number' | 'date'; operators: string[]; optionSource?: string; options: Array<{ value: string | number; label: string }>; optionsLoading?: boolean; optionsError?: boolean; retryOptions?: () => void }
export type AdvancedFilterCatalog = { fields: AdvancedFilterField[] }
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
export type LeadCreateResult = {
  leadId?: number; leadNo?: string; reviewId?: number; outcome: 'created' | 'activated' | 'review_pending' | 'duplicate_rejected'
  assignmentStatus?: string; pendingAssigneeUserId?: number; existingLeadStatus?: string
  existingQualificationStatus?: string; existingOperationalStatus?: string
}
export type LeadDuplicateReview = {
  id: number; status: 'pending' | 'completed'; submitterUserId?: number; submissionSnapshot: string
  matchRules: string; candidateSnapshot: string; resultType?: string; reviewOpinion?: string
  selectedSalesUserId?: number; reviewerUserId?: number; reviewedAt?: Timestamp; createTime: Timestamp
}
export type LeadDuplicateReviewDecision = {
  resultType: 'new_person' | 'reuse_person' | 'reactivate_lead' | 'notify_owner'
  matchedPersonId?: number; matchedLeadId?: number; selectedSalesUserId?: number
  opinion: string; attachments: Array<{ infraFileId: number }>; idempotencyKey: string
}
export type PendingLead = {
  id: number; leadNo: string; dispatchMode: 'auto' | 'specified'; maskedName: string; maskedMobile?: string; maskedWechatId?: string
  provinceName: string; cityName: string; intendedProducts: string[]; primaryIntendedProduct?: string
  sourceChannel: string; sourceChannelLabel?: string; leadCategory: string; leadCategoryLabel?: string
  remark?: string; attachmentUrls: string[]
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
  id: number; leadNo: string; personId: number; submittedName: string; submittedMobile?: string; submittedWechatId?: string
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
  activeSalesOrderId?: number; activeSalesOrderStatus?: 'pending_approval' | 'revision_required'
  availableActions?: Array<{ code: 'EDIT_BASIC_INFO' | 'ADD_FOLLOW_UP' | 'JUDGE_VALID' | 'JUDGE_INVALID' | 'ENTER_DEAL' | 'ENTER_REPURCHASE' | 'REVISE_DEAL' | 'SUBMITTER_SUPPLEMENT' | 'SUBMITTER_URGE' | 'SUBMITTER_COMPLAINT'; enabled: boolean }>
}
export type LeadComplaint = {
  id: number; leadId: number; leadNo: string; complainantUserId: number; salesUserId: number; reason: string
  evidenceRefs?: string; status: 'pending' | 'handled'; result?: 'founded' | 'unfounded'
  handlerUserId?: number; handlerOpinion?: string; handlerEvidenceRefs?: string; handledAt?: Timestamp; createTime: Timestamp
}
export type LeadQualificationException = {
  id: number; leadNo: string; submittedName: string; submittedMobile?: string; status: string; assignmentStatus: string
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
  advancedFilter?: AdvancedFilterGroup
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
  id: number; leadId: number; leadNo: string; leadName: string; roundNo: number; reviewStage: 'sales_manager' | 'quality' | 'chairman'
  status: 'sales_manager_reviewing' | 'quality_reviewing' | 'chairman_reviewing' | 'overturned' | 'upheld' | 'withdrawn'
  applicantUserId: number; applicantUserName?: string; reason: string; evidence: LeadAppealEvidence[]
  invalidReasonSnapshot?: string; invalidDescriptionSnapshot?: string; invalidEvidenceSnapshot: LeadAppealEvidence[]
  processInstanceId?: string; taskId?: string; reviewerUserId?: number; reviewerUserName?: string
  decisionReason?: string; decisionEvidence: LeadAppealEvidence[]; submittedAt: Timestamp; decidedAt?: Timestamp
  canSubmitNextRound: boolean
}
export type SalesOrderVoucher = LeadAttachment
export type SalesOrderSubmitRequest = {
  buyerName?: string; studentName: string; studentNature: string; studentMobile?: string; studentWechatId?: string
  provinceCode: string; provinceName: string; cityCode: string; cityName: string
  agreedExamTime?: string; classType?: string; servicePeriod: string; studentSource: string
  customerPaidAt: Timestamp; feeMode: string; paymentMethod: string; remark?: string
  studentSpecialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ spuRef: string; skuRef: string; actualAmount: number }>
  paymentVouchers: Array<{ infraFileId: number }>; idempotencyKey: string
}
export type SalesOrder = {
  id: number; orderNo: string; leadId?: number; opportunityId?: number; personId: number; orderType: 'first_purchase' | 'repurchase'
  status: 'pending_approval' | 'revision_required' | 'effective' | 'terminated'
  submitterUserId: number; formalSalesUserId?: number; buyerName: string; studentName: string; studentNature: string
  studentMobile?: string; studentWechatId?: string; provinceCode: string; provinceName: string; cityCode: string; cityName: string
  agreedExamTime?: string; classType?: string; servicePeriod: string; studentSource: string; totalAmount: number
  customerPaidAt: Timestamp; feeMode: string; paymentMethod: string; remark?: string
  studentSpecialRequirements?: string; materialDeliveryContact?: string
  items: Array<{ id: number; productRef: string; skuRef: string; productName: string; skuName: string; categoryPath: string[]; attrValues: Record<string, string>; actualAmount: number }>
  paymentVouchers: SalesOrderVoucher[]; approvalRoundNo: number; approvalRoundStatus: string
  processInstanceId?: string; taskId?: string; taskDefinitionKey?: 'registrationReview' | 'financeReview'
  taskStatus?: number; taskReason?: string; taskCreateTime?: Timestamp; taskEndTime?: Timestamp; decisionReason?: string; canRevise?: boolean; canTerminate?: boolean
  version: number; currentApprovalRoundId: number; approvalRoundVersion: number; repurchaseReason?: string; terminationReason?: string
  canRequestSupervisorConfirmation?: boolean
  submittedAt: Timestamp; effectiveAt?: Timestamp
  registrationApproval?: SalesOrderApprovalStatus
  financeApproval?: SalesOrderApprovalStatus
  registrationSupervisorConfirmation?: SalesOrderSupervisorConfirmation
  financeSupervisorConfirmation?: SalesOrderSupervisorConfirmation
}
export type SalesOrderSupervisorConfirmation = {
  id: number; status: 'pending' | 'confirmed' | 'rejected' | 'cancelled'; requesterUserId: number
  requesterUserName?: string; requestReason: string; decisionReason?: string; requestedAt?: Timestamp; decidedAt?: Timestamp
}
export type SalesOrderApprovalStatus = {
  status: 'pending' | 'approved' | 'rejected' | 'cancelled'
  reviewerUserId?: number; reviewerUserName?: string; createTime?: Timestamp; endTime?: Timestamp
}
export type SalesOrderListItem = Pick<SalesOrder, 'id' | 'orderNo' | 'leadId' | 'status' | 'studentName' | 'studentMobile' | 'totalAmount' | 'approvalRoundNo' | 'submittedAt' | 'effectiveAt'> & {
  personId?: number; orderType?: SalesOrder['orderType']
  taskId?: string; taskDefinitionKey?: 'registrationReview' | 'financeReview'; taskStatus?: number
  taskReason?: string; taskCreateTime?: Timestamp; taskEndTime?: Timestamp
  supervisorConfirmationId?: number; supervisorConfirmationStatus?: string; supervisorRequesterName?: string
}
export type SalesOrderSupervisorInboxItem = {
  id: number; orderId: number; orderNo: string; studentName: string; approvalRoundId: number
  taskDefinitionKey: 'registrationReview' | 'financeReview'; taskId: string; requesterUserId: number
  requesterUserName?: string; supervisorUserId: number; requestReason: string; decisionReason?: string
  status: 'pending' | 'confirmed' | 'rejected' | 'cancelled'; requestedAt?: Timestamp; decidedAt?: Timestamp
  version: number; orderVersion: number; roundVersion: number
}
export type SalesOrderStatusCounts = { total: number; pendingApproval: number; revisionRequired: number; effective: number }
export type SalesOrderApprovalFilterOption = { key: string; label: string; count: number }
export type SalesOrderApprovalFilterSection = { key: string; label: string; options: SalesOrderApprovalFilterOption[] }
export type SalesOrderApprovalFilterGroup = { key: string; label: string; count: number; sections: SalesOrderApprovalFilterSection[] }
export type SalesOrderApprovalCenter = { key: 'registration' | 'finance'; label: string }
export type SalesOrderApprovalFilterProfile = { groups: SalesOrderApprovalFilterGroup[]; centers: SalesOrderApprovalCenter[] }
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
export type LeadAssignmentRule = { id: number; code: string; name: string; strategyType: 'global_round_robin'; acceptTimeoutSeconds: number; maxAttempts: number; dailyClaimLimit: number; status: number }
export type LeadFollowUpRule = { id: number; code: string; name: string; firstFollowUpTimeoutMinutes: number; qualificationTimeoutMinutes: number; agingPoolTimeoutDays: number; noProgressWarningDays: number; noProgressGraceDays: number; status: number; version: number }
export type LeadFilterAudience = 'submitter' | 'owner' | 'reviewer'
export type LeadFilterCondition = { field: string; values: string[] }
export type LeadFilterOptionConfig = { key: string; label: string; sort: number; enabled: boolean; conditions: LeadFilterCondition[] }
export type LeadFilterGroupConfig = { key: string; label: string; sort: number; enabled: boolean; sectionLabel?: string; conditions: LeadFilterCondition[]; options: LeadFilterOptionConfig[] }
export type LeadFilterAdmin = { audience: LeadFilterAudience; audienceLabel: string; draftGroups: LeadFilterGroupConfig[]; publishedGroups: LeadFilterGroupConfig[]; publishedVersion: number; publishedAt?: Timestamp; updateTime?: Timestamp }
export type LeadFilterVersion = { versionNo: number; publishedBy: number; publishedAt: Timestamp }
export type ProductCategory = { id: number; parentId: number; level: number; name: string; status: number; sort: number; children?: ProductCategory[] }
export type ProductCategorySaveRequest = { id?: number; parentId?: number; name: string; status: number; sort: number; remark?: string }
export type ProductConfig = { id: number; productRef: string; name: string; subtitle?: string; categoryId: number; categoryName?: string; status: number; sort: number; updateTime?: Timestamp }
export type ProductSaveRequest = { id?: number; categoryId: number; name: string; subtitle?: string; description?: string; targetAudience?: string; studyDuration?: string; studyMode?: string; coverImage?: string; status: number; sort: number; remark?: string }
export type ProductSku = { id: number; spuId: number; skuRef: string; skuName: string; attrValues: Record<string, string>; price: number; status: number; sort: number; remark?: string; updateTime?: Timestamp }
export type ProductSkuSaveRequest = { id?: number; spuId: number; skuName: string; attrValues: Record<string, string>; price: number; status: number; sort: number; remark?: string }
export type ProductAttribute = { attrKey?: string; attrName: string; required: boolean; sort: number; values: Array<{ value: string; label: string; sort: number }> }
export type WorkPlanTemplateSaveRequest = { typeId: number; code?: string; name: string; description?: string; periodMode: NonNullable<WorkPlanTemplate['periodMode']>; fields: WorkPlanTemplateField[]; applicableDeptIds: number[]; includeChildDepartments: boolean; presetItems: WorkPlanTemplateTask[] }
export type WorkPlanSummary = { id: number; summary: string; submitterUserId: number; submittedAt: Timestamp; infraFileIds: number[]; summaryFields?: Record<string, unknown> }
export type SubordinateCategoryCount = { value: string; label: string; count: number; configured: boolean }
export type SubordinateSales = {
  userId: number; name: string; avatar?: string; username: string; mobile?: string; accountStatus: number
  presence: 'online' | 'offline'; accepting: boolean; eligible: boolean; canReceiveNewLeads: boolean
  newcomerPoolStatus: 'not_available'; todayPendingCount: number; todayFollowUpStatus: 'completed' | 'incomplete'
  firstFollowTimeoutCount: number; suspendedLeadCount: number; categoryCounts: SubordinateCategoryCount[]
  validLeadCount: number; convertedLeadCount: number; effectiveOrderCount: number; effectiveOrderAmount: number
}
export type LeadAgingPoolStatus = 'waiting_assignment' | 'assigned' | 'deal_pending'
export type LeadAgingPoolItem = {
  cycleId: number; leadId: number; leadNo: string; cycleNo: number; status: LeadAgingPoolStatus
  originalOwnerUserId: number; originalOwnerUserName?: string; collaboratorUserId?: number; collaboratorUserName?: string
  frozenDeptId: number; frozenDeptName?: string; submittedName: string; submittedMobile?: string; submittedWechatId?: string
  leadCategory?: string; sourceChannel?: string; ownershipStartedAt: Timestamp; dueAt: Timestamp; enteredAt: Timestamp
  assignedAt?: Timestamp; lastFollowUpAt?: Timestamp; nextFollowUpAt?: Timestamp
  activeSalesOrderId?: number; activeSalesOrderStatus?: 'pending_approval' | 'revision_required'
  availableActions: Array<'ASSIGN' | 'EXIT' | 'REQUEST_TRANSFER' | 'ADD_FOLLOW_UP' | 'ENTER_DEAL' | 'REVISE_DEAL'>
}
export type SubordinateTask = { id: number; taskType: string; leadId: number; leadNo: string; leadName?: string; dueAt?: Timestamp; overdue: boolean }
export type SubordinateBatchItem = { leadId: number; leadNo?: string; success: boolean; code: string; message: string }
export type SubordinateBatchResult = { successCount: number; failureCount: number; items: SubordinateBatchItem[] }
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
  localStorage.removeItem(STORAGE_KEYS.CLIENT_ID)
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
    const clientId = localStorage.getItem(STORAGE_KEYS.CLIENT_ID)
    const clientIdParam = clientId ? `&clientId=${encodeURIComponent(clientId)}` : ''
    const response = await axios.post(`${APP_CONFIG.API_BASE_URL}/system/auth/refresh-token?refreshToken=${encodeURIComponent(refresh)}${clientIdParam}`, undefined, { headers: { 'tenant-id': APP_CONFIG.DEFAULT_TENANT_ID }, timeout: 30000 })
    const result = unwrap<{ accessToken: string; refreshToken: string; clientId?: string }>(response)
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.accessToken)
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.refreshToken)
    if (result.clientId) localStorage.setItem(STORAGE_KEYS.CLIENT_ID, result.clientId)
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
  login: async (username: string, password: string, platform: 'PC' | 'MOBILE' = 'PC') => {
    const result = unwrap<{ accessToken: string; refreshToken: string; expiresTime: string; clientId?: string }>(await http.post('/system/auth/login', { username, password, platform }))
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, result.accessToken)
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, result.refreshToken)
    localStorage.setItem(STORAGE_KEYS.EXPIRES_TIME, result.expiresTime)
    localStorage.setItem(STORAGE_KEYS.CLIENT_ID, result.clientId || (platform === 'MOBILE' ? 'zsjos-mobile' : 'zsjos-pc'))
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
  createSelfSourcedLead: async (data: LeadCreateRequest) => unwrap<LeadCreateResult>(await http.post('/zsjos/lead/self-sourced/create', data)),
  duplicateReviewPage: async (status: 'pending' | 'completed') =>
    unwrap<PageResult<LeadDuplicateReview>>(await http.get('/zsjos/lead-duplicate-review/page', { params: { status, pageNo: 1, pageSize: 100 } })),
  duplicateReviewSalesCandidates: async () =>
    unwrap<AssignmentUser[]>(await http.get('/zsjos/lead-duplicate-review/sales-candidates')),
  uploadDuplicateReviewAttachment: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadAttachment>(await http.post('/zsjos/lead-duplicate-review/attachment/upload', data))
  },
  decideDuplicateReview: async (id: number, data: LeadDuplicateReviewDecision) =>
    unwrap<boolean>(await http.post(`/zsjos/lead-duplicate-review/${id}/decision`, data)),
  myPendingLeads: async () => unwrap<PendingLead[]>(await http.get('/zsjos/lead/assignment/my-pending')),
  myDispatchStatus: async () => unwrap<SalesDispatchStatus>(await http.get('/zsjos/lead/dispatch-status/my')),
  dispatchHeartbeat: async () => unwrap<SalesDispatchStatus>(await http.post('/zsjos/lead/dispatch-status/heartbeat')),
  updateDispatchMode: async (accepting: boolean) => unwrap<SalesDispatchStatus>(
    await http.put('/zsjos/lead/dispatch-status/mode', { accepting })
  ),
  dispatchOffline: async () => unwrap<SalesDispatchStatus>(await http.post('/zsjos/lead/dispatch-status/offline')),
  acceptLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/accept`)),
  rejectLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/reject`)),
  claimPoolPage: async (params: { pageNo: number; pageSize: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<PendingLead>>(await http.post('/zsjos/lead/claim-pool/search-page', params))
      : unwrap<PageResult<PendingLead>>(await http.get('/zsjos/lead/claim-pool/page', { params })),
  claimLead: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/claim`)),
  managedLeadInboxPage: async (audience: 'submitter' | 'owner', params: ManagedLeadPageParams) =>
    params.advancedFilter ? unwrap<PageResult<ManagedLead>>(await http.post(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/search-page`, params))
      : unwrap<PageResult<ManagedLead>>(await http.get(`/zsjos/lead/inbox/${audience === 'submitter' ? 'submitted' : 'owned'}/page`, { params })),
  managedLead: async (id: number) => unwrap<ManagedLead>(await http.get('/zsjos/lead/get', { params: { id } })),
  agingPoolPage: async (params: { pageNo: number; pageSize: number; keyword?: string; status?: LeadAgingPoolStatus; inboxGroup?: string; inboxStage?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<LeadAgingPoolItem>>(await http.post('/zsjos/lead/aging-pool/search-page', params))
      : unwrap<PageResult<LeadAgingPoolItem>>(await http.get('/zsjos/lead/aging-pool/page', { params })),
  agingPoolCounts: async () => unwrap<Record<string, number>>(await http.get('/zsjos/lead/aging-pool/counts')),
  agingPoolFilterProfile: async () => unwrap<LeadInboxFilterProfile>(await http.get('/zsjos/lead/aging-pool/filter-profile')),
  agingPoolCandidates: async (cycleId: number) =>
    unwrap<Array<{ id: number; nickname: string }>>(await http.get(`/zsjos/lead/aging-pool/${cycleId}/candidates`)),
  assignAgingPool: async (cycleId: number, salesUserId: number) => unwrap<boolean>(
    await http.post(`/zsjos/lead/aging-pool/${cycleId}/assign`, { salesUserId, idempotencyKey: crypto.randomUUID() })
  ),
  exitAgingPool: async (cycleId: number, reason: string) => unwrap<boolean>(
    await http.post(`/zsjos/lead/aging-pool/${cycleId}/exit`, { reason, idempotencyKey: crypto.randomUUID() })
  ),
  requestAgingPoolTransfer: async (cycleId: number, reason: string) => unwrap<number>(
    await http.post(`/zsjos/lead/aging-pool/${cycleId}/transfer-request`, { reason, idempotencyKey: crypto.randomUUID() })
  ),
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
  supplementLead: async (id: number, data: { provinceCode: string; cityCode: string; leadCategory: string; intendedProducts: LeadCreateRequest['intendedProducts']; remark?: string; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/${id}/submitter-supplement`, data)),
  urgeLead: async (id: number, reason: string) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/urge`, { reason })),
  createLeadComplaint: async (id: number, reason: string, evidenceFileIds: number[]) => unwrap<number>(
    await http.post(`/zsjos/lead-complaint/lead/${id}`, { reason, evidenceFileIds, idempotencyKey: crypto.randomUUID() })
  ),
  leadComplaintPage: async (status: 'pending' | 'handled') => unwrap<PageResult<LeadComplaint>>(
    await http.get('/zsjos/lead-complaint/page', { params: { status, pageNo: 1, pageSize: 100 } })
  ),
  decideLeadComplaint: async (id: number, result: 'founded' | 'unfounded', opinion: string, evidenceFileIds: number[]) => unwrap<boolean>(
    await http.post(`/zsjos/lead-complaint/${id}/decision`, { result, opinion, evidenceFileIds, idempotencyKey: crypto.randomUUID() })
  ),
  qualificationExceptionPage: async (type: 'suspended' | 'recycle_pending', params: { pageNo: number; pageSize: number; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<LeadQualificationException>>(await http.post('/zsjos/lead/qualification-exception/search-page', { type, ...params }))
      : unwrap<PageResult<LeadQualificationException>>(await http.get('/zsjos/lead/qualification-exception/page', { params: { type, ...params } })),
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
  salesOrderCatalog: async () => unwrap<LeadCatalog>(await http.get('/zsjos/sales-order/product/catalog')),
  submitSalesOrder: async (leadId: number, data: SalesOrderSubmitRequest) =>
    unwrap<number>(await http.post(`/zsjos/sales-order/lead/${leadId}/submit`, data)),
  submitSystemRepurchase: async (leadId: number, repurchaseReason: string, order: SalesOrderSubmitRequest) =>
    unwrap<number>(await http.post(`/zsjos/sales-order/lead/${leadId}/repurchase`, { repurchaseReason, order })),
  submitExternalRepurchase: async (data: { customerName: string; customerMobile?: string; customerWechatId?: string; repurchaseReason: string; order: SalesOrderSubmitRequest }) =>
    unwrap<number>(await http.post('/zsjos/sales-order/external-repurchase', data)),
  customerSalesOrders: async (leadId: number) => unwrap<SalesOrderListItem[]>(await http.get(`/zsjos/sales-order/lead/${leadId}/customer-orders`)),
  resubmitSalesOrder: async (orderId: number, data: SalesOrderSubmitRequest) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/resubmit`, data)),
  salesOrder: async (orderId: number) => unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/${orderId}`)),
  mySalesOrder: async (orderId: number) => unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/my/${orderId}`)),
  mySalesOrderPage: async (params: { pageNo: number; pageSize: number; status?: SalesOrder['status']; keyword?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<SalesOrderListItem>>(await http.post('/zsjos/sales-order/my-search-page', params))
      : unwrap<PageResult<SalesOrderListItem>>(await http.get('/zsjos/sales-order/my-page', { params })),
  mySalesOrderStatusCounts: async () =>
    unwrap<SalesOrderStatusCounts>(await http.get('/zsjos/sales-order/my-status-counts')),
  salesOrderApprovalFilterProfile: async () => unwrap<SalesOrderApprovalFilterProfile>(await http.get('/zsjos/sales-order/approval/filter-profile')),
  salesOrderApprovalInbox: async (params: { pageNo: number; pageSize: number; center?: 'registration' | 'finance'; groupKey?: string; optionKey?: string; keyword?: string; handled?: boolean; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<SalesOrderListItem>>(await http.post('/zsjos/sales-order/approval/search-page', params))
      : unwrap<PageResult<SalesOrderListItem>>(await http.get('/zsjos/sales-order/approval/inbox-page', { params })),
  advancedFilterCatalog: async (scene: 'lead' | 'order') =>
    unwrap<AdvancedFilterCatalog>(await http.get('/zsjos/advanced-filter/catalog', { params: { scene } })),
  decideSalesOrder: async (orderId: number, decision: 'approve' | 'reject', data: { taskId: string; reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/${decision}`, data)),
  requestSalesOrderSupervisor: async (orderId: number, data: { taskId: string; reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/supervisor-confirmation/request`, data)),
  salesOrderSupervisorInbox: async (params: { pageNo: number; pageSize: number; handled: boolean; keyword?: string }) =>
    unwrap<PageResult<SalesOrderSupervisorInboxItem>>(await http.get('/zsjos/sales-order/supervisor-confirmation/inbox-page', { params })),
  decideSalesOrderSupervisor: async (orderId: number, decision: 'confirm' | 'reject', data: { confirmationId: number; taskId: string; reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; confirmationVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/supervisor-confirmation/${decision}`, data)),
  terminateSalesOrder: async (orderId: number, data: { reason: string; approvalRoundId: number; orderVersion: number; roundVersion: number; idempotencyKey: string }) =>
    unwrap<boolean>(await http.put(`/zsjos/sales-order/${orderId}/terminate`, data)),
  uploadSalesOrderVoucher: async (file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<SalesOrderVoucher>(await http.post('/zsjos/sales-order/voucher/upload', data))
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
  workPlanConfigTemplates: async (typeId?: number) => unwrap<WorkPlanTemplate[]>(await http.get('/zsjos/work-plan-config/templates', { params: { typeId } })),
  copyWorkPlanTemplateVersion: async (id: number) => unwrap<number>(await http.post(`/zsjos/work-plan-config/templates/${id}/versions/copy`)),
  publishWorkPlanTemplate: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/work-plan-config/templates/${id}/publish`)),
  disableWorkPlanTemplate: async (id: number) => unwrap<boolean>(await http.post(`/zsjos/work-plan-config/templates/${id}/disable`)),
  leadAssignmentRule: async () => unwrap<LeadAssignmentRule>(await http.get('/zsjos/lead/assignment-rule/get')),
  updateLeadAssignmentRule: async (data: Pick<LeadAssignmentRule, 'acceptTimeoutSeconds' | 'maxAttempts' | 'dailyClaimLimit'>) => unwrap<boolean>(await http.put('/zsjos/lead/assignment-rule/update', data)),
  leadFollowUpRule: async () => unwrap<LeadFollowUpRule>(await http.get('/zsjos/lead-follow-up-rule/get')),
  updateLeadFollowUpRule: async (data: Pick<LeadFollowUpRule, 'firstFollowUpTimeoutMinutes' | 'qualificationTimeoutMinutes' | 'agingPoolTimeoutDays' | 'noProgressWarningDays' | 'noProgressGraceDays'>) => unwrap<boolean>(await http.put('/zsjos/lead-follow-up-rule/update', data)),
  leadFilterConfig: async (audience: LeadFilterAudience) => unwrap<LeadFilterAdmin>(await http.get('/zsjos/lead/inbox-filter/get', { params: { audience } })),
  leadFilterVersions: async (audience: LeadFilterAudience) => unwrap<LeadFilterVersion[]>(await http.get('/zsjos/lead/inbox-filter/versions', { params: { audience } })),
  publishLeadFilter: async (audience: LeadFilterAudience) => unwrap<number>(await http.post('/zsjos/lead/inbox-filter/publish', undefined, { params: { audience } })),
  rollbackLeadFilter: async (audience: LeadFilterAudience, versionNo: number) => unwrap<number>(await http.post('/zsjos/lead/inbox-filter/rollback', undefined, { params: { audience, versionNo } })),
  saveLeadFilterDraft: async (audience: LeadFilterAudience, groups: LeadFilterGroupConfig[]) => unwrap<boolean>(await http.put('/zsjos/lead/inbox-filter/draft', { audience, groups })),
  productConfigPage: async (params: { pageNo: number; pageSize: number; name?: string; status?: number }) => unwrap<PageResult<ProductConfig>>(await http.get('/zsjos/product/page', { params })),
  productConfig: async (id: number) => unwrap<ProductSaveRequest & { id: number }>(await http.get('/zsjos/product/get', { params: { id } })),
  createProductConfig: async (data: ProductSaveRequest) => unwrap<number>(await http.post('/zsjos/product/create', data)),
  updateProductConfig: async (data: ProductSaveRequest & { id: number }) => unwrap<boolean>(await http.put('/zsjos/product/update', data)),
  deleteProductConfig: async (id: number) => unwrap<boolean>(await http.delete('/zsjos/product/delete', { params: { id } })),
  productCategoryTree: async () => unwrap<ProductCategory[]>(await http.get('/zsjos/product/category/tree')),
  createProductCategory: async (data: ProductCategorySaveRequest) => unwrap<number>(await http.post('/zsjos/product/category/create', data)),
  updateProductCategory: async (data: ProductCategorySaveRequest & { id: number }) => unwrap<boolean>(await http.put('/zsjos/product/category/update', data)),
  updateProductConfigStatus: async (id: number, status: number) => unwrap<boolean>(await http.put('/zsjos/product/update-status', { id, status })),
  productSkus: async (spuId: number) => unwrap<ProductSku[]>(await http.get('/zsjos/product/sku/list', { params: { spuId } })),
  createProductSku: async (data: ProductSkuSaveRequest) => unwrap<number>(await http.post('/zsjos/product/sku/create', data)),
  updateProductSku: async (data: ProductSkuSaveRequest & { id: number }) => unwrap<boolean>(await http.put('/zsjos/product/sku/update', data)),
  deleteProductSku: async (id: number) => unwrap<boolean>(await http.delete('/zsjos/product/sku/delete', { params: { id } })),
  updateProductSkuStatus: async (id: number, status: number) => unwrap<boolean>(await http.put('/zsjos/product/sku/update-status', { id, status })),
  productAttributes: async (spuId: number) => unwrap<ProductAttribute[]>(await http.get('/zsjos/product/sku/attrs', { params: { spuId } })),
  saveProductAttributes: async (spuId: number, attrs: ProductAttribute[]) => unwrap<boolean>(await http.put('/zsjos/product/sku/attrs', { spuId, attrs })),
  createWorkPlanTemplate: async (data: WorkPlanTemplateSaveRequest) => unwrap<number>(await http.post('/zsjos/work-plan-config/templates', data)),
  updateWorkPlanTemplate: async (id: number, data: WorkPlanTemplateSaveRequest) => unwrap<boolean>(await http.put(`/zsjos/work-plan-config/templates/${id}`, data)),
  subordinateSalesPage: async (params: { pageNo: number; pageSize: number; keyword?: string; accountStatus?: number; presence?: string; accepting?: boolean }) =>
    unwrap<PageResult<SubordinateSales>>(await http.get('/zsjos/subordinate-sales/page', { params })),
  subordinateSalesOverview: async (salesUserId: number) =>
    unwrap<SubordinateSales>(await http.get(`/zsjos/subordinate-sales/${salesUserId}/overview`)),
  subordinateSalesLeads: async (salesUserId: number, params: { pageNo: number; pageSize: number; keyword?: string; status?: string; advancedFilter?: AdvancedFilterGroup }) =>
    params.advancedFilter ? unwrap<PageResult<ManagedLead>>(await http.post(`/zsjos/subordinate-sales/${salesUserId}/leads/search-page`, params))
      : unwrap<PageResult<ManagedLead>>(await http.get(`/zsjos/subordinate-sales/${salesUserId}/leads`, { params })),
  subordinateSalesTasks: async (salesUserId: number, params: { pageNo: number; pageSize: number; bucket?: BusinessTaskBucket }) =>
    unwrap<PageResult<SubordinateTask>>(await http.get(`/zsjos/subordinate-sales/${salesUserId}/tasks`, { params })),
  subordinateTransferCandidates: async () =>
    unwrap<AssignmentUser[]>(await http.get('/zsjos/subordinate-sales/transfer-candidates')),
  updateSubordinateAccountStatus: async (salesUserId: number, status: number, reason: string) =>
    unwrap<boolean>(await http.put(`/zsjos/subordinate-sales/${salesUserId}/account-status`, { status, reason })),
  updateSubordinateDispatchMode: async (salesUserId: number, accepting: boolean, reason: string) =>
    unwrap<boolean>(await http.put(`/zsjos/subordinate-sales/${salesUserId}/dispatch-mode`, { accepting, reason })),
  batchTransferSubordinateLeads: async (leadIds: number[], targetUserId: number, reason: string) =>
    unwrap<SubordinateBatchResult>(await http.post('/zsjos/subordinate-sales/leads/batch-transfer', { leadIds, targetUserId, reason })),
  batchReleaseSubordinateLeads: async (leadIds: number[], collaboratorUserId: number | undefined, reason: string) =>
    unwrap<SubordinateBatchResult>(await http.post('/zsjos/subordinate-sales/leads/batch-public-sea', { leadIds, collaboratorUserId, reason })),
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
