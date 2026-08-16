import request from '@/config/axios'

export interface AssetVO {
  id?: number
  assetCode?: string
  name: string
  categoryId: number
  categoryName?: string
  status?: number
  brand?: string
  specification?: string
  sn?: string
  barcode?: string
  originalValue?: number
  netValue?: number
  purchaseDate?: string
  source?: number
  warrantyDate?: string
  useDeptId?: number
  useDeptName?: string
  useUserId?: number
  useUserName?: string
  location?: string
  expectedLife?: number
  remark?: string
  fileUrls?: string[]
  extFields?: Record<string, any>
  createTime?: Date
}

export interface AssetChangeLogVO {
  id: number
  assetId: number
  changeType: number
  beforeStatus?: number
  afterStatus?: number
  beforeUserId?: number
  afterUserId?: number
  beforeDeptId?: number
  afterDeptId?: number
  bizId?: number
  content?: string
  operatorId?: number
  operatorName?: string
  operateTime: Date
}

/** 资产状态 */
export const AssetStatus = {
  IDLE: 0,
  IN_USE: 1,
  LENT: 2,
  REPAIRING: 3,
  PENDING_SCRAP: 4,
  SCRAPPED: 5,
  LOST: 6,
  FROZEN: 7
} as const

// 查询资产分页
export const getAssetPage = async (params: any) => {
  return await request.get({ url: '/eam/asset/page', params })
}

// 查询资产详情
export const getAsset = async (id: number) => {
  return await request.get({ url: '/eam/asset/get?id=' + id })
}

// 新增资产
export const createAsset = async (data: AssetVO) => {
  return await request.post({ url: '/eam/asset/create', data })
}

// 修改资产
export const updateAsset = async (data: AssetVO) => {
  return await request.put({ url: '/eam/asset/update', data })
}

// 删除资产
export const deleteAsset = async (id: number) => {
  return await request.delete({ url: '/eam/asset/delete?id=' + id })
}

// 查询资产变更时间线
export const getChangeLogList = async (assetId: number) => {
  return await request.get({ url: '/eam/asset/change-log?assetId=' + assetId })
}

// 导出资产 Excel
export const exportAsset = async (params: any) => {
  return await request.download({ url: '/eam/asset/export-excel', params })
}

// 下载资产导入模板
export const importTemplate = async () => {
  return await request.download({ url: '/eam/asset/get-import-template' })
}

export interface AssetImportRespVO {
  createAssetCodes: string[]
  failures: { rowNum: number; name?: string; reason: string }[]
}

// 资产导入接口地址，供 el-upload 直接使用
export const getImportUrl = () => {
  return import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL + '/eam/asset/import'
}

// 资产二维码地址，直接用于 <img :src="...">
export const getQrCodeUrl = (id: number, size = 300) => {
  return `/admin-api/eam/asset/qrcode?id=${id}&size=${size}`
}
