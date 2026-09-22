import request from '@/config/axios'

export interface DeliveryClass {
  id: number
  classNo: string
  className: string
  systemClass: boolean
  categoryId?: number
  categoryNameSnapshot?: string
  categoryPathSnapshot?: string
  examScheduleId?: number
  examScheduleSnapshot?: string
  homeroomUserId?: number
  homeroomUserNameSnapshot?: string
  deptId?: number
  deptNameSnapshot?: string
  status: string
  studentCount: number
  version: number
  productId?: number
  productNameSnapshot?: string
  selectedAttrs?: Record<string, string>
  selectedSpecs?: ProductSpec[]
  selectedSkus?: ProductSku[]
}
export interface ProductSpec { attrKey: string; attrName: string; value: string; label: string; labelMissing: boolean }
export interface ProductAttr { attrKey: string; attrName: string; required: boolean; values: { value: string; label: string }[] }
export interface ProductSku { id: number; skuRef: string; skuName: string; attrValues: Record<string, string>; specs: ProductSpec[] }
export interface ProductOption { productId: number; productRef: string; productName: string; categoryId: number; attrs: ProductAttr[]; skus: ProductSku[] }
export interface DeliveryClassStudent {
  serviceRelationId: number
  personId: number
  personNo?: string
  studentName?: string
  categoryId?: number
  categoryName?: string
  serviceStatus: string
  acceptanceStatus?: string
  ownerUserId?: number
  ownerUserName?: string
  version: number
}
export interface DeliveryClassOption {
  id: number
  classNo: string
  className: string
  systemClass: boolean
  categoryId?: number
  categoryName?: string
  homeroomUserId?: number
  homeroomUserName?: string
}
export interface HomeroomCandidate {
  id: number
  name: string
  deptId?: number
  deptName?: string
}
export interface ExamOption {
  id: number
  scheduleType: string
  displayName: string
  productId?: number
  categoryId: number
  frozenSkus: ProductSku[]
}
export interface ClassTransfer {
  id: number
  serviceRelationId: number
  fromClassName: string
  targetClassName: string
  status: string
  reason: string
  submittedAt: string
  resolutionReason?: string
}
export interface PageResult<T> {
  list: T[]
  total: number
}

export const getDeliveryClassPage = (params: Record<string, unknown>, mine = false) =>
  request.get<PageResult<DeliveryClass>>({
    url: mine ? '/zsjos/delivery-class/my-page' : '/zsjos/delivery-class/page',
    params
  })
export const getDeliveryClassStudents = (
  id: number,
  params: { pageNo: number; pageSize: number }
) =>
  request.get<PageResult<DeliveryClassStudent>>({
    url: `/zsjos/delivery-class/${id}/students`,
    params
  })
export const getDeliveryClassOptions = (categoryId?: number, includePending = true) =>
  request.get<DeliveryClassOption[]>({
    url: '/zsjos/delivery-class/options',
    params: { categoryId, includePending }
  })
export const getHomeroomCandidates = () =>
  request.get<HomeroomCandidate[]>({ url: '/zsjos/delivery-class/homeroom-candidates' })
export const getExamOptions = (categoryId: number, productId?: number, selectedAttrsJson?: string, selectedSkuIds?: number[]) =>
  request.get<ExamOption[]>({ url: '/zsjos/delivery-class/exam-options', params: { categoryId, productId, selectedAttrsJson, selectedSkuIdsJson: selectedSkuIds?.length ? JSON.stringify(selectedSkuIds) : undefined } })
export const getProductOptions = () =>
  request.get<ProductOption[]>({ url: '/zsjos/delivery-class/product-options' })
export const createDeliveryClass = (data: {
  className?: string
  productId: number
  selectedAttrs?: Record<string, string>
  selectedSkuIds?: number[]
  categoryId: number
  examScheduleId: number
  homeroomUserId: number
}) => request.post<number>({ url: '/zsjos/delivery-class/create', data })
export const updateDeliveryClass = (
  id: number,
  data: {
    className?: string
    productId: number
    selectedAttrs?: Record<string, string>
    selectedSkuIds?: number[]
    categoryId: number
    examScheduleId: number
    homeroomUserId: number
    version: number
  }
) => request.put<boolean>({ url: `/zsjos/delivery-class/${id}`, data })
export const completeDeliveryClass = (id: number) =>
  request.post<boolean>({ url: `/zsjos/delivery-class/${id}/complete` })
export const directTransfer = (
  relationId: number,
  data: { targetClassId: number; version: number; reason: string }
) =>
  request.post<boolean>({
    url: `/zsjos/delivery-class/service/${relationId}/direct-transfer`,
    data
  })
export const requestTransfer = (
  relationId: number,
  data: { targetClassId: number; version: number; reason: string }
) => request.post<number>({ url: `/zsjos/class-transfer/service/${relationId}`, data })
export const getMyTransferPage = (params = { pageNo: 1, pageSize: 20 }) =>
  request.get<PageResult<ClassTransfer>>({ url: '/zsjos/class-transfer/my-page', params })
