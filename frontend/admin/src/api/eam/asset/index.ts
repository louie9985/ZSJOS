import request from '@/config/axios'

export interface AssetVO {
  id?: number
  assetCode?: string
  name: string
  categoryId: number
  categoryName?: string
  managementMode?: number
  quantity?: number
  unit?: string
  status?: number
  brand?: string
  specification?: string
  sn?: string
  barcode?: string
  originalValue?: number
  netValue?: number
  purchaseDate?: string
  source?: number
  sourceLabelSnapshot?: string
  warrantyDate?: string
  useDeptId?: number
  useDeptName?: string
  useEmployeeId?: number
  useEmployeeName?: string
  useEmployeeNameSnapshot?: string
  location?: string
  expectedLife?: number
  remark?: string
  fileUrls?: string[]
  extFields?: Record<string, any>
  extFieldLabels?: Record<string, string>
  extFieldDictTypes?: Record<string, string>
  createTime?: Date
}

export interface AssetChangeLogVO {
  id: number
  assetId: number
  changeType: number
  beforeStatus?: number
  afterStatus?: number
  beforeEmployeeId?: number
  afterEmployeeId?: number
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

export interface AssetImportRowVO {
  rowNum: number
  assetCode?: string
  name: string
  categoryName: string
  managementMode: number
  quantity: number
  useUserName?: string
  supervisorName?: string
  matchedUserName?: string
  matchedSupervisorName?: string
  action: 'CREATE' | 'UPDATE' | 'SKIP_EXISTING' | 'SKIP_SAME_FILE' | 'ERROR'
  mappedFields: Record<string, any>
  defaultedFields: string[]
  warnings: string[]
  errors: string[]
}

export interface AssetImportPreviewRespVO {
  fileHash: string
  totalRows: number
  createCount: number
  updateCount: number
  skipCount: number
  warningCount: number
  errorCount: number
  batchId?: number
  rows: AssetImportRowVO[]
}

const uploadLedger = async (url: string, file: File, updateExisting: boolean) => {
  const data = new FormData()
  data.append('file', file)
  data.append('updateExisting', String(updateExisting))
  return await request.upload<AssetImportPreviewRespVO>({ url, data })
}

export const previewLedgerImport = async (file: File, updateExisting = false) => {
  return await uploadLedger('/eam/asset/import/preview', file, updateExisting)
}

export const commitLedgerImport = async (file: File, updateExisting = false) => {
  return await uploadLedger('/eam/asset/import/commit', file, updateExisting)
}

// 资产二维码地址，直接用于 <img :src="...">
export const getQrCodeUrl = (id: number, size = 300) => {
  return `/admin-api/eam/asset/qrcode?id=${id}&size=${size}`
}

// Download the PNG through axios so auth, tenant and impersonation headers are included.
export const downloadQrCode = async (id: number, size = 300) => {
  return await request.download({ url: '/eam/asset/qrcode', params: { id, size } })
}

export interface PublicEditCodeVO { employeeId: number; code: string }
export const getMyPublicEditCode = async () => request.get<PublicEditCodeVO>({ url: '/eam/public-edit-code/me' })
export const generateMyPublicEditCode = async () => request.post<PublicEditCodeVO>({ url: '/eam/public-edit-code/generate' })
export const updateMyPublicEditCode = async (code: string) => request.put<PublicEditCodeVO>({ url: '/eam/public-edit-code/me', data: { code } })
export const resetPublicEditCode = async (userId: number) => request.put<PublicEditCodeVO>({ url: '/eam/public-edit-code/reset/' + userId })
