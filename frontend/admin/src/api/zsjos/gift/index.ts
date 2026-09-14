import request from '@/config/axios'

export interface GiftConfigVO { id: number; parentId?: number; name: string; code: string; status: number; sort: number; children?: GiftConfigVO[] }
export interface GiftConfigSaveReq { id?: number; parentId?: number; name: string; code: string; status: number; sort: number }
export interface GiftPurchaseVO { id: number; orderId: number; orderNo: string; studentName: string; studentMobile?: string; studentWechatId?: string; giftItemsJson?: string; shippingAddress?: string; generatedAt?: string }
export const getGiftConfigList = (status?: number) => request.get({ url: '/zsjos/gift-config/list', params: status === undefined ? undefined : { status } })
export const createGiftConfig = (data: GiftConfigSaveReq) => request.post({ url: '/zsjos/gift-config/create', data })
export const updateGiftConfig = (data: GiftConfigSaveReq) => request.put({ url: '/zsjos/gift-config/update', data })
export const deleteGiftConfig = (id: number) => request.delete({ url: `/zsjos/gift-config/delete?id=${id}` })
export const getGiftPurchasePage = (params: Record<string, unknown>) => request.get({ url: '/zsjos/gift-purchase/page', params })
