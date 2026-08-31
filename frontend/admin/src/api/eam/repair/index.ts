import request from '@/config/axios'

export interface RepairVO {
  id?: number
  assetId: number
  assetName?: string
  assetCode?: string
  faultDesc: string
  repairVendor?: string
  cost?: number
  startTime?: Date
  endTime?: Date
  result?: string
}

export interface RepairFinishVO {
  id: number
  endTime?: Date
  cost?: number
  result?: string
}

// 查询维修记录分页
export const getRepairPage = async (params: any) => {
  return await request.get({ url: '/eam/repair/page', params })
}

// 查询维修记录详情
export const getRepair = async (id: number) => {
  return await request.get({ url: '/eam/repair/get?id=' + id })
}

// 查询某资产的维修记录
export const getRepairListByAsset = async (assetId: number) => {
  return await request.get({ url: '/eam/repair/list-by-asset?assetId=' + assetId })
}

// 送修
export const createRepair = async (data: RepairVO) => {
  return await request.post({ url: '/eam/repair/create', data })
}

// 维修完成
export const finishRepair = async (data: RepairFinishVO) => {
  return await request.put({ url: '/eam/repair/finish', data })
}

// 删除维修记录
export const deleteRepair = async (id: number) => {
  return await request.delete({ url: '/eam/repair/delete?id=' + id })
}
