import request from '@/config/axios'

export interface GiftConfigVO { id: number; parentId?: number; name: string; code: string; status: number; sort: number; children?: GiftConfigVO[] }
export interface GiftConfigSaveReq { id?: number; parentId?: number; name: string; code: string; status: number; sort: number }
export interface GiftSnapshot { code: string; name?: string; path?: string[]; snapshotAt?: string }
export interface OrderGiftProjection { giftItems?: string; giftItemCodes?: string[]; giftItemSnapshots?: GiftSnapshot[]; giftItemsInvalid?: boolean; giftShippingAddress?: string }
export interface GiftPurchaseVO {
  id: number
  orderId?: number
  orderNo?: string
  studentName: string
  studentMobile?: string
  studentWechatId?: string
  giftItemsJson?: string
  giftItemSnapshots?: GiftSnapshot[]
  giftItemsInvalid?: boolean
  shippingAddress?: string
  generatedAt?: string | number
  purchased: boolean
  submitterUserId?: number
  submitterUserName?: string
  sourceType?: 'ORDER' | 'MANUAL' | 'UNKNOWN'
  sourceUserId?: number
  sourceUserName?: string
  purchasedByUserId?: number
  purchasedByName?: string
  purchasedAt?: string | number
  operatorName?: string
  operatedAt?: string | number
}
export type GiftPurchaseOption = Pick<GiftConfigVO, 'id' | 'parentId' | 'name' | 'code' | 'status'>
export interface GiftPurchaseSaveReq {
  id?: number
  studentName: string
  studentMobile?: string
  studentWechatId?: string
  shippingAddress: string
  giftItemCodes: string[]
}
export interface GiftPurchaseOperation {
  action: 'CREATE' | 'GENERATE' | 'UPDATE' | 'PURCHASE' | 'UNPURCHASE' | 'DELETE'
  operatorName?: string
  occurredAt: string | number
}

export const getGiftConfigList = (status?: number): Promise<GiftConfigVO[]> => request.get({ url: '/zsjos/gift-config/list', params: status === undefined ? undefined : { status } })
export const createGiftConfig = (data: GiftConfigSaveReq) => request.post({ url: '/zsjos/gift-config/create', data })
export const updateGiftConfig = (data: GiftConfigSaveReq) => request.put({ url: '/zsjos/gift-config/update', data })
export const deleteGiftConfig = (id: number) => request.delete({ url: `/zsjos/gift-config/delete?id=${id}` })
export const getGiftPurchasePage = (params: Record<string, unknown>): Promise<{ list: GiftPurchaseVO[]; total: number }> => request.get({ preserveBusinessError: true, url: '/zsjos/gift-purchase/page', params })
export const getGiftPurchase = (id: number): Promise<GiftPurchaseVO> => request.get({ preserveBusinessError: true, url: '/zsjos/gift-purchase/get', params: { id } })
export const getGiftPurchaseOperations = (id: number): Promise<GiftPurchaseOperation[]> => request.get({ preserveBusinessError: true, url: '/zsjos/gift-purchase/operations', params: { id } })
export const getGiftPurchaseOptions = (): Promise<GiftPurchaseOption[]> => request.get({ preserveBusinessError: true, url: '/zsjos/gift-purchase/gift-options' })
export const createGiftPurchase = (data: GiftPurchaseSaveReq) => request.post({ preserveBusinessError: true, url: '/zsjos/gift-purchase/create', data })
export const updateGiftPurchase = (data: GiftPurchaseSaveReq) => request.put({ preserveBusinessError: true, url: '/zsjos/gift-purchase/update', data })
export const setGiftPurchased = (id: number, purchased: boolean) => request.put({ preserveBusinessError: true, url: `/zsjos/gift-purchase/${purchased ? 'purchase' : 'unpurchase'}`, params: { id } })
export const deleteGiftPurchase = (id: number) => request.delete({ preserveBusinessError: true, url: '/zsjos/gift-purchase/delete', params: { id } })

export const formatGiftPurchase = (row: Pick<GiftPurchaseVO, 'giftItemsJson' | 'giftItemSnapshots' | 'giftItemsInvalid'>): string => {
  if (row.giftItemsInvalid) return '历史礼品信息无法读取'
  try {
    const raw: unknown = row.giftItemSnapshots ?? (row.giftItemsJson?.trim() ? JSON.parse(row.giftItemsJson) : [])
    if (!Array.isArray(raw)) return '历史礼品信息无法读取'
    return raw.map(value => {
      const item = typeof value === 'string' ? JSON.parse(value) : value
      if (!item || typeof item !== 'object' || typeof item.code !== 'string') throw new Error('invalid gift snapshot')
      return typeof item.name === 'string' && item.name ? item.name : '历史名称未记录'
    }).join('、') || '无礼品'
  } catch { return '历史礼品信息无法读取' }
}

// Legacy payloads omitted orderId; only positive evidence identifies a manual source.
export const giftPurchaseSourceType = (row: GiftPurchaseVO): 'ORDER' | 'MANUAL' | 'UNKNOWN' => {
  if (row.sourceType) return row.sourceType
  return row.orderId != null || row.orderNo?.trim() ? 'ORDER' : 'UNKNOWN'
}
export const formatGiftPurchaseOrigin = (row: GiftPurchaseVO): string => {
  const type = giftPurchaseSourceType(row)
  if (type === 'ORDER') return row.orderNo ? `订单提交（${row.orderNo}）` : '订单提交'
  return type === 'MANUAL' ? '手工创建' : '来源待确认'
}
export const formatGiftPurchaseSourcePerson = (row: GiftPurchaseVO): string =>
  row.sourceUserName || (giftPurchaseSourceType(row) === 'ORDER' ? row.submitterUserName : undefined) || '历史未记录'
export const formatGiftPurchaseMarker = (row: GiftPurchaseVO): string =>
  row.purchased ? row.purchasedByName || '历史未记录' : '-'
