import request from '@/config/axios'

export interface InventoryVO {
  id?: number
  no?: string
  name: string
  scopeType: number
  scopeValue?: string
  status?: number
  totalCount?: number
  checkedCount?: number
  normalCount?: number
  abnormalCount?: number
  startTime?: Date
  endTime?: Date
  remark?: string
}

export interface InventoryDetailVO {
  id: number
  inventoryId: number
  assetId: number
  assetName?: string
  assetCode?: string
  expectUserId?: number
  expectUserName?: string
  expectDeptId?: number
  expectLocation?: string
  actualUserId?: number
  actualDeptId?: number
  actualLocation?: string
  result: number
  remark?: string
  checkUserId?: number
  checkTime?: Date
}

export interface InventoryCheckVO {
  detailId: number
  result: number
  actualUserId?: number
  actualDeptId?: number
  actualLocation?: string
  remark?: string
}

/** 盘点范围类型 */
export const ScopeType = {
  ALL: 1,
  DEPT: 2,
  CATEGORY: 3,
  LOCATION: 4
} as const

/** 盘点结果 */
export const InventoryResult = {
  UNCHECKED: 0,
  NORMAL: 1,
  LOCATION_MISMATCH: 2,
  NOT_FOUND: 3
} as const

// 查询盘点单分页
export const getInventoryPage = async (params: any) => {
  return await request.get({ url: '/eam/inventory/page', params })
}

// 查询盘点单详情
export const getInventory = async (id: number) => {
  return await request.get({ url: '/eam/inventory/get?id=' + id })
}

// 创建盘点单
export const createInventory = async (data: InventoryVO) => {
  return await request.post({ url: '/eam/inventory/create', data })
}

// 删除盘点单
export const deleteInventory = async (id: number) => {
  return await request.delete({ url: '/eam/inventory/delete?id=' + id })
}

// 查询盘点明细列表
export const getDetailList = async (inventoryId: number) => {
  return await request.get({ url: '/eam/inventory/detail-list?inventoryId=' + inventoryId })
}

// 录入盘点结果
export const checkDetail = async (data: InventoryCheckVO) => {
  return await request.put({ url: '/eam/inventory/check', data })
}

// 完成盘点
export const finishInventory = async (id: number) => {
  return await request.put({ url: '/eam/inventory/finish?id=' + id })
}

// 同步实盘归属回资产（处理位置不符）
export const syncDetailToAsset = async (detailId: number) => {
  return await request.put({ url: '/eam/inventory/sync-detail?detailId=' + detailId })
}

// 标记资产丢失（处理未找到）
export const markLost = async (detailId: number) => {
  return await request.put({ url: '/eam/inventory/mark-lost?detailId=' + detailId })
}
