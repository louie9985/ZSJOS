import request from './request'
import referenceRequest from './reference'
import type { DictItem } from '@/stores/app'
import type { ApiDateValue } from '@/utils/format'

export interface LeadSubmitterFeedback {
  id: number
  feedback: string
  salesName?: string
  submitterName?: string
  createTime: ApiDateValue
  attachments: Array<{ fileId: number; originalName: string; contentType: string; fileSize: number; url?: string }>
}

export function getLeadSubmitterFeedback(leadId: number, pageNo = 1) {
  return request.get<never, { list: LeadSubmitterFeedback[]; total: number }>(
    `/zsjos/lead/${leadId}/submitter-feedback/page`, { params: { pageNo, pageSize: 10 } })
}

export interface LeadCatalog {
  categoryTree: CategoryNode[]
  spus: SpuItem[]
  skus: SkuItem[]
}

export interface CategoryNode {
  id: number
  name: string
  children?: CategoryNode[]
}

export interface CategoryPathNode { id: number; name: string }
export interface ProductAttrValue { value: string; label: string }
export interface ProductAttr { attrKey: string; attrName: string; required: boolean; values: ProductAttrValue[] }

export interface SpuItem {
  categoryId: number
  categoryName: string
  categoryPath: CategoryPathNode[]
  level1CategoryId: number
  level1CategoryName: string
  level2CategoryId?: number
  level2CategoryName?: string
  spuRef: string
  spuName: string
  attrs: ProductAttr[]
}

export interface SkuItem {
  spuRef: string
  skuRef: string
  skuName: string
  attrValues: Record<string, string>
  price?: number
}

export interface LeadCreateParams {
  name: string
  mobile?: string
  wechatId?: string
  provinceCode: string
  cityCode: string
  intendedProducts: IntendedProduct[]
  sourceChannel: string
  leadCategory: string
  remark?: string
  attachments?: { infraFileId: number }[]
  dispatchMode: 'auto'
  idempotencyKey: string
}

export interface IntendedProduct {
  spuRef?: string
  skuRef?: string
  spuUnknown: boolean
  skuUnknown: boolean
  primary: boolean
}

export interface LeadCreateResult {
  leadId: number | null
  leadNo?: string | null
  reviewId?: number | null
  outcome: 'created' | 'activated' | 'review_pending' | 'duplicate_rejected' | 'duplicate_auto_closed'
  assignmentStatus: string
  pendingAssigneeUserId?: number | null
  existingLeadStatus?: string | null
  existingQualificationStatus?: string | null
  existingOperationalStatus?: string | null
}

export interface LeadListItem {
  id: number
  leadNo?: string | null
  submittedName: string
  submittedMobile?: string
  submittedWechatId?: string
  sourceType?: string
  sourceLabel?: string
  sourceChannel: string
  sourceChannelLabelSnapshot?: string
  leadCategory: string
  leadCategoryLabelSnapshot?: string
  remarkHistory?: Array<{ id: string; kind: 'submission' | 'supplement' | 'legacy'; content: string; occurredAt?: number; operatorName?: string }>
  remarkHistoryIncomplete?: boolean
  status: string
  assignmentStatus: string
  handlingStage?: string
  qualificationStatus?: string
  followUpStatus?: string
  operationalStatus?: string
  sourceUserName?: string
  providerOwnerType?: 'system_user' | 'partner'
  providerOwnerId?: number
  providerOwnerNameSnapshot?: string
  contributionUserIdSnapshot?: number
  contributionUserNameSnapshot?: string
  contributionSupervisorUserIdSnapshot?: number
  contributionSupervisorNameSnapshot?: string
  contributionDeptIdSnapshot?: number
  contributionDeptNameSnapshot?: string
  countedAt?: ApiDateValue
  ownerUserName?: string
  pendingAssigneeUserName?: string
  pendingExpiresAt?: ApiDateValue
  dispatchMode?: string
  assignmentAttemptCount?: number
  publicPoolAt?: ApiDateValue
  submittedAt: ApiDateValue
  lastActivityAt?: ApiDateValue
  nextFollowUpAt?: ApiDateValue
  qualifiedAt?: ApiDateValue
  convertedAt?: ApiDateValue
  salesOrderSubmittedAt?: ApiDateValue
  currentAssignmentFirstFollowUpAt?: ApiDateValue
  currentAssignmentFirstFollowUpDeadlineAt?: ApiDateValue
  qualificationStartedAt?: ApiDateValue
  qualificationDeadlineAt?: ApiDateValue
  suspendedAt?: ApiDateValue
  qualifiedByUserName?: string
  validDescription?: string
  recycleSourceOwnerUserName?: string
  appealDeadlineAt?: ApiDateValue
  closedAt?: ApiDateValue
  closeReason?: string
  createTime?: ApiDateValue
  updateTime?: ApiDateValue
  relationTypes?: string[]
  overviewVisible?: boolean
  visibleTabs?: string[]
  identityMaskMode?: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  remark?: string
  intendedProducts: LeadProductItem[]
  attachments: LeadAttachmentItem[]
  availableActions: LeadAction[]
  invalidReason?: string
  invalidReasonLabelSnapshot?: string
  invalidDescription?: string
  invalidEvidence?: Array<{
    infraFileId: number
    fileUrl?: string | null
    originalName: string
    contentType: string
    fileSize: number
    sort?: number
  }>
  activeSalesOrderId?: number
  activeSalesOrderStatus?: string
  primaryProduct?: LeadProductItem
  opportunity?: { id: number; status: string; nextFollowUpAt?: ApiDateValue; wonAt?: ApiDateValue }
}

export interface LeadFollowUpSummary {
  followUpPendingCount: number
  unreachableCount: number
  invalidCount: number
}

export interface LeadFollowUpItem {
  id: number
  leadId: number
  assignmentHistoryId?: number
  opportunityId?: number
  recordScope?: string
  occurredAt: ApiDateValue
  firstInAssignment: boolean
  result: string
  resultLabel: string
  method: string
  methodLabel: string
  categoryBefore?: string
  categoryBeforeLabel?: string
  categoryAfter?: string
  categoryAfterLabel?: string
  remark?: string
  nextFollowUpAt?: ApiDateValue
  images: Array<{
    infraFileId: number
    originalName: string
    contentType: string
    fileSize: number
    sort: number
    url?: string
  }>
}

export type PartnerLeadActivityTone = 'default' | 'primary' | 'success' | 'warning' | 'danger'

export interface PartnerLeadCurrentStatus {
  code: string
  text: string
  description?: string
  tone: PartnerLeadActivityTone
  updatedAt?: ApiDateValue
}

export interface LeadTimelineItem {
  id: number | string
  type?: string
  title: string
  description?: string
  occurredAt: ApiDateValue
  tone?: PartnerLeadActivityTone
  current?: boolean
}

export interface LeadCashbackActivityItem {
  id: number
  typeText: string
  statusText: string
  amount: number
  availableAt?: ApiDateValue
}

export interface LeadRightsActivityItem {
  id: number
  recordNo: string
  status: string
  statusText: string
  content: string
  result?: string
  createdAt: ApiDateValue
  attachments?: LeadAttachmentItem[]
}

export interface LeadOrderActivityItem {
  id: number
  orderNo: string
  status: string
  statusText: string
  purchaseTypeText?: string
  totalAmount: number
  createdAt: ApiDateValue
}

export interface PartnerLeadActivity {
  currentStatus?: PartnerLeadCurrentStatus
  followUps: LeadFollowUpItem[]
  timeline: LeadTimelineItem[]
  cashbackItems: LeadCashbackActivityItem[]
  complaints: LeadRightsActivityItem[]
  orders: LeadOrderActivityItem[]
}

export interface LeadAction {
  code: string
  enabled: boolean
}

export interface LeadProductItem {
  spuRef: string
  spuName: string
  skuRef?: string
  skuName?: string
  selectedAttrValues?: string
  price?: number
  categoryName?: string
  primary: boolean
}

export interface LeadAttachmentItem {
  id: number
  fileUrl?: string | null
  originalName: string
  contentType: string
  fileSize: number
}

export interface UploadResult {
  infraFileId: number
  fileUrl: string
  originalName: string
  contentType: string
  fileSize: number
}

export interface SupplementParams {
  provinceCode: string
  cityCode: string
  leadCategory: string
  intendedProducts: IntendedProduct[]
  remark?: string
  idempotencyKey: string
}

export interface LeadFilterOption {
  value: string
  label: string
}

export interface PartnerLeadFilterOptions {
  appealStatuses: LeadFilterOption[]
  orderReviewStatuses: LeadFilterOption[]
}

/** 获取字典数据 */
export function getDictByType(type: string) {
  return referenceRequest.get<never, DictItem[]>('/system/dict-data/type', { params: { type } })
}

export interface LeadComplaintItem {
  id: number
  leadId: number
  leadNo?: string | null
  reason: string
  complainantUserName?: string
  salesUserName?: string
  evidence?: LeadAppealEvidence[]
  status: 'pending' | 'handled'
  result?: 'founded' | 'unfounded'
  handlerOpinion?: string
  handlerUserName?: string
  handlerEvidence?: LeadAppealEvidence[]
  handledAt?: ApiDateValue
  createTime: ApiDateValue
}

export interface LeadAppealEvidence {
  infraFileId: number
  fileUrl?: string
  originalName: string
  contentType?: string
  fileSize?: number
  sort?: number
}

export interface LeadAppealItem {
  id: number
  leadId: number
  leadNo?: string | null
  roundNo: number
  reviewStage?: string
  status: string
  reason: string
  applicantUserName?: string
  invalidReasonSnapshot?: string
  invalidDescriptionSnapshot?: string
  invalidEvidenceSnapshot?: LeadAppealEvidence[]
  reviewerUserName?: string
  decisionReason?: string
  decisionEvidence?: LeadAppealEvidence[]
  submittedAt: ApiDateValue
  decidedAt?: ApiDateValue
  evidence?: LeadAppealEvidence[]
  canSubmitNextRound?: boolean
}

interface AreaApiNode {
  id: number
  name: string
  selectionCode?: string
  leafSelectable?: boolean
  children?: AreaApiNode[]
}

export interface LeadAreaNode {
  code: string
  name: string
  leafSelectable?: boolean
  children?: LeadAreaNode[]
}

function normalizeAreaNode(node: AreaApiNode): LeadAreaNode {
  return {
    code: node.selectionCode || String(node.id),
    name: node.name,
    leafSelectable: node.leafSelectable,
    children: node.children?.map(normalizeAreaNode)
  }
}

export async function getAreaTree() {
  const nodes = await referenceRequest.get<never, AreaApiNode[]>('/system/area/tree')
  return nodes.map(normalizeAreaNode)
}

/** 获取课程目录 */
export function getLeadCatalog() {
  return request.get<never, LeadCatalog>('/zsjos/lead/product/catalog')
}

/** Partner 高级筛选状态选项 */
export function getPartnerLeadFilterOptions() {
  return request.get<never, PartnerLeadFilterOptions>('/zsjos/lead/partner-filter-options')
}

/** 上传客资附件 */
export async function uploadLeadAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const result = await request.post<never, UploadResult>('/zsjos/lead/attachment/upload', formData, {
    timeout: 120000
  })
  if (!Number.isSafeInteger(result.infraFileId) || result.infraFileId <= 0) {
    throw new TypeError('图片上传结果缺少有效的文件编号')
  }
  if (typeof result.fileUrl !== 'string' || !result.fileUrl.trim()) {
    throw new TypeError('图片上传结果缺少预览地址')
  }
  return result
}

/** 提交客资 */
export function createLead(data: LeadCreateParams) {
  return request.post<never, LeadCreateResult>('/zsjos/lead/create', data)
}

/** 获取我的客资列表 */
export function getMyLeadPage(params: {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
  simpleStatus?: string
  assignmentStatus?: string
  sourceChannel?: string
  leadCategory?: string
  submittedAt?: [string, string]
  mainProductRef?: string
  appealStatus?: string
  orderReviewStatus?: string
  view?: 'follow_up_pending' | 'unreachable' | 'invalid'
}) {
  return request.get<never, { list: LeadListItem[]; total: number }>(
    '/zsjos/lead/inbox/submitted/page',
    { params }
  )
}

/** 获取当前兼职伙伴的客资跟进提醒摘要 */
export function getLeadFollowUpSummary() {
  return request.get<never, LeadFollowUpSummary>('/zsjos/lead/inbox/submitted/summary')
}

/** 获取客资详情 */
export function getLeadDetail(id: number) {
  return request.get<never, LeadListItem>('/zsjos/lead/get', { params: { id } })
}

/** 查询 Partner 本人可见的客资业务流转聚合 */
export function getPartnerLeadActivity(id: number) {
  return request.get<never, PartnerLeadActivity>(`/zsjos/lead/${id}/partner-activity`)
}

/** 补充客资 */
export function supplementLead(id: number, data: SupplementParams) {
  return request.put<never, void>(`/zsjos/lead/${id}/submitter-supplement`, data)
}

/** 催办客资 */
export function urgeLead(id: number, reason: string) {
  return request.post<never, void>(`/zsjos/lead/${id}/urge`, { reason })
}

/** 创建投诉 */
export function createComplaint(leadId: number, data: { reason: string; evidenceFileIds: number[]; idempotencyKey: string }) {
  return request.post<never, void>(`/zsjos/lead-complaint/lead/${leadId}`, data)
}

/** 查询投诉历史 */
export function getMyComplaints(params: { pageNo: number; pageSize: number; status?: string }) {
  return request.get<never, { list: LeadComplaintItem[]; total: number }>('/zsjos/lead-complaint/my-page', { params })
}

/** 查询客资申诉记录 */
export function getLeadAppeals(leadId: number) {
  return request.get<never, LeadAppealItem[]>(`/zsjos/lead/appeal/lead/${leadId}/list`)
}

/** 上传申诉附件 */
export function uploadAppealAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, UploadResult>('/zsjos/lead/appeal/attachment/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 提交申诉 */
export function submitAppeal(leadId: number, data: { reason: string; idempotencyKey: string; attachments?: { infraFileId: number }[] }) {
  return request.post<never, void>(`/zsjos/lead/appeal/lead/${leadId}/submit`, data)
}
