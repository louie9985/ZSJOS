import request from '@/config/axios'

export interface GiftConfigVO { id: number; parentId?: number; name: string; code: string; status: number; sort: number; children?: GiftConfigVO[] }
export interface GiftConfigSaveReq { id?: number; parentId?: number; name: string; code: string; status: number; sort: number }
export interface GiftPurchaseVO { id: number; orderId: number; orderNo: string; studentName: string; studentMobile?: string; studentWechatId?: string; giftItemsJson?: string; giftItemSnapshots?: { code: string; name?: string; path?: string[]; snapshotAt?: string }[]; giftItemsInvalid?: boolean; shippingAddress?: string; generatedAt?: string }
export const getGiftConfigList = (status?: number) => request.get({ url: '/zsjos/gift-config/list', params: status === undefined ? undefined : { status } })
export const createGiftConfig = (data: GiftConfigSaveReq) => request.post({ url: '/zsjos/gift-config/create', data })
export const updateGiftConfig = (data: GiftConfigSaveReq) => request.put({ url: '/zsjos/gift-config/update', data })
export const deleteGiftConfig = (id: number) => request.delete({ url: `/zsjos/gift-config/delete?id=${id}` })
export const getGiftPurchasePage = (params: Record<string, unknown>) => request.get({ url: '/zsjos/gift-purchase/page', params })

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
