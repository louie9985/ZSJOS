import request from '@/config/axios'

export interface DemandItemVO {
  id?: number
  demandId?: number
  name: string
  categoryId: number
  managementMode?: number
  deliveryMode?: number
  deliveryModeLabelSnapshot?: string
  custodyMode?: number
  custodyModeLabelSnapshot?: string
  quantity: number
  unit?: string
  extFields?: Record<string, any>
  extFieldLabels?: Record<string, string>
  extFieldDictTypes?: Record<string, string>
  reservedQuantity?: number
  purchasedQuantity?: number
  fulfilledQuantity?: number
  closedQuantity?: number
}

export interface DemandVO {
  id?: number
  no?: string
  employeeId?: number
  applicantUserId?: number
  status?: number
  processInstanceId?: string
  reason?: string
  createTime?: Date
  items: DemandItemVO[]
}

export interface PurchaseItemVO extends DemandItemVO {
  demandItemId?: number
  targetEmployeeId?: number
  purchaseId?: number
  receivedQuantity?: number
  returnedQuantity?: number
  shortClosedQuantity?: number
  shortCloseRemark?: string
  unitPrice?: number
}

export interface PurchaseCreateReqVO {
  paymentMode: number
  supplierName?: string
  supplierContact?: string
  estimatedAmount?: number
  expectedArrivalDate?: string
  remark?: string
  fileUrls?: string[]
  items: Array<{
    demandItemId?: number
    targetEmployeeId?: number
    name: string
    categoryId: number
    quantity: number
    unit?: string
    unitPrice?: number
    extFields?: Record<string, any>
  }>
}

export interface PurchaseVO {
  id?: number
  no?: string
  status?: number
  paymentMode?: number
  paymentModeLabelSnapshot?: string
  supplierNameSnapshot?: string
  supplierContactSnapshot?: string
  estimatedAmount?: number
  actualAmount?: number
  expectedArrivalDate?: string
  processInstanceId?: string
  expenseStatus?: number
  expenseProcessInstanceId?: string
  applicantUserId?: number
  fileUrls?: string[]
  remark?: string
  createTime?: Date
  items: PurchaseItemVO[]
}

export interface StockBalanceVO {
  id: number
  name: string
  categoryId: number
  managementMode: number
  deliveryMode: number
  custodyMode: number
  unit: string
  extFields?: Record<string, any>
  extFieldLabels?: Record<string, string>
  extFieldDictTypes?: Record<string, string>
  onHandQuantity: number
  reservedQuantity: number
  frozenQuantity: number
  availableQuantity: number
  minimumQuantity: number
  nextExpiryDate?: string
}

export interface StockCandidateVO {
  candidateType: 'SERIALIZED' | 'BATCH'
  assetId?: number
  assetCode?: string
  stockBalanceId?: number
  name: string
  categoryId: number
  availableQuantity: number
  unit?: string
}

export interface StockReserveReqVO {
  demandItemId: number
  assetId?: number
  stockBalanceId?: number
  quantity: number
}

export interface ReceiptItemReqVO {
  purchaseItemId: number
  stockBalanceId?: number
  quantity: number
  unitPrice?: number
  serialNumbers?: string[]
  actualExtFields?: Record<string, any>
}

export interface ReceiptCreateReqVO {
  remark?: string
  fileUrls?: string[]
  items: ReceiptItemReqVO[]
}

export interface ShortCloseReqVO {
  purchaseItemId: number
  quantity: number
  reason?: string
}

export interface ExpenseSubmitReqVO {
  actualAmount: number
  fileUrls?: string[]
}

export const getDemandList = () => request.get<DemandVO[]>({ url: '/eam/demand/list' })
export const createDemand = (data: DemandVO) => request.post({ url: '/eam/demand/create', data })
export const getStockCandidates = (demandItemId: number) =>
  request.get<StockCandidateVO[]>({ url: '/eam/demand/stock-candidates', params: { demandItemId } })
export const reserveAndAllocateStock = (data: StockReserveReqVO) =>
  request.put<number>({ url: '/eam/demand/reserve-and-allocate', data })
export const getPurchaseList = () => request.get<PurchaseVO[]>({ url: '/eam/purchase/list' })
export const createPurchase = (data: PurchaseCreateReqVO) =>
  request.post({ url: '/eam/purchase/create', data })
export const receivePurchase = (id: number, data: ReceiptCreateReqVO) =>
  request.post({ url: `/eam/purchase/${id}/receive`, data })
export const returnPurchase = (id: number, data: ReceiptCreateReqVO) =>
  request.post({ url: `/eam/purchase/${id}/supplier-return`, data })
export const shortClosePurchase = (id: number, data: ShortCloseReqVO) =>
  request.put({ url: `/eam/purchase/${id}/short-close`, data })
export const submitExpense = (id: number, data: ExpenseSubmitReqVO) =>
  request.post({ url: `/eam/purchase/${id}/expense`, data })
export const getStockList = () => request.get<StockBalanceVO[]>({ url: '/eam/stock/list' })
export const updateStockMinimum = (data: { id: number; minimumQuantity: number }) =>
  request.put({ url: '/eam/stock/minimum', data })
