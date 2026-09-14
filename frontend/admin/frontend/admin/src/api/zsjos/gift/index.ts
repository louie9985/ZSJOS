import request from '@/config/axios'
export interface GiftConfigVO { id:number; parentId:number; name:string; code:string; status:number; sort:number }
export interface GiftConfigSaveReqVO { id?:number; parentId:number; name:string; code:string; status:number; sort:number }
export const getGiftConfigList = (status?: number) => request.get({ url: '/zsjos/gift-config/list', params: status === undefined ? undefined : { status } })
export const createGiftConfig = (data: GiftConfigSaveReqVO) => request.post({ url: '/zsjos/gift-config/create', data })
export const updateGiftConfig = (data: GiftConfigSaveReqVO) => request.put({ url: '/zsjos/gift-config/update', data })
export const deleteGiftConfig = (id:number) => request.delete({ url: `/zsjos/gift-config/delete?id=${id}` })
