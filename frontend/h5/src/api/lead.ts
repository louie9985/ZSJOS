import request from './request'
import referenceRequest from './reference'
import type { DictItem } from '@/stores/app'
import type { ApiDateValue } from '@/utils/format'

export interface LeadCatalog {
  categoryTree: CategoryNode[]
  spus: SpuItem[]
  skus: SkuItem[]
}

export interface CategoryNode {
  categoryId: string
  categoryName: string
  children?: CategoryNode[]
}

export interface SpuItem {
  categoryId: string
  categoryName: string
  categoryPath: string
  level1CategoryId: string
  level1CategoryName: string
  level2CategoryId?: string
  level2CategoryName?: string
  spuRef: string
  spuName: string
  attrs?: string
}

export interface SkuItem {
  spuRef: string
  skuRef: string
  skuName: string
  attrValues?: string
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
  leadId: number
  leadNo?: string | null
  reviewId?: number | null
  outcome: 'activated' | 'review_pending' | 'duplicate_rejected' | 'duplicate_auto_closed'
  assignmentStatus: string
  pendingAssigneeUserId?: number | null
}

export interface LeadListItem {
  id: number
  leadNo?: string | null
  submittedName: string
  submittedMobile?: string
  sourceChannel: string
  leadCategory: string
  status: string
  assignmentStatus: string
  ownerUserName?: string
  submittedAt: ApiDateValue
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  remark?: string
  intendedProducts: LeadProductItem[]
  attachments: unknown[]
  availableActions: LeadAction[]
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
  primary: boolean
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

/** 获取字典数据 */
export function getDictByType(type: string) {
  return referenceRequest.get<never, DictItem[]>('/system/dict-data/type', { params: { type } })
}

export interface LeadComplaintItem {
  id: number
  leadId: number
  leadNo?: string | null
  reason: string
  status: 'pending' | 'handled'
  result?: 'founded' | 'unfounded'
  handlerOpinion?: string
  handledAt?: ApiDateValue
  createTime: ApiDateValue
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

/** 上传客资附件 */
export function uploadLeadAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, UploadResult>('/zsjos/lead/attachment/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
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
  assignmentStatus?: string
  sourceChannel?: string
  leadCategory?: string
  submittedAt?: ApiDateValue
}) {
  return request.get<never, { list: LeadListItem[]; total: number }>(
    '/zsjos/lead/inbox/submitted/page',
    { params }
  )
}

/** 获取客资详情 */
export function getLeadDetail(id: number) {
  return request.get<never, LeadListItem>('/zsjos/lead/get', { params: { id } })
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
  return request.get<never, unknown[]>(`/zsjos/lead/appeal/lead/${leadId}/list`)
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
