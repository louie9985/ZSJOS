import request from '@/config/axios'

export interface CategoryVO {
  id?: number
  parentId: number
  name: string
  code: string
  sort: number
  status: number
  managementMode: number
  deliveryMode?: number
  custodyMode?: number
  effectiveDeliveryMode?: number
  effectiveCustodyMode?: number
  unit: string
  remark?: string
  createTime?: Date
}

export interface CategoryImportItemVO {
  kind: 'CATEGORY' | 'FIELD'
  code: string
  name: string
  action: 'CREATE' | 'UPDATE' | 'SKIP' | 'CONFLICT'
  message?: string
}

export interface CategoryImportRespVO {
  createCount: number
  updateCount: number
  skipCount: number
  conflictCount: number
  categoryCount: number
  leafCategoryCount: number
  fieldCount: number
  legacyFieldCount: number
  credentialFieldCount: number
  allManagementFieldsOptional: boolean
  items: CategoryImportItemVO[]
}

/** 兼容尚未部署新统计字段的旧版 EAM 后端响应。 */
const normalizeImportResponse = (response: Partial<CategoryImportRespVO>): CategoryImportRespVO => {
  const items = Array.isArray(response.items) ? response.items : []
  const numberOrZero = (value: unknown) => (typeof value === 'number' ? value : 0)
  return {
    createCount: numberOrZero(response.createCount),
    updateCount: numberOrZero(response.updateCount),
    skipCount: numberOrZero(response.skipCount),
    conflictCount: numberOrZero(response.conflictCount),
    categoryCount:
      numberOrZero(response.categoryCount) ||
      items.filter((item) => item.kind === 'CATEGORY').length,
    leafCategoryCount:
      numberOrZero(response.leafCategoryCount) ||
      items.filter((item) => item.kind === 'CATEGORY' && item.message?.includes('子分类')).length,
    fieldCount:
      numberOrZero(response.fieldCount) || items.filter((item) => item.kind === 'FIELD').length,
    legacyFieldCount: numberOrZero(response.legacyFieldCount),
    credentialFieldCount: numberOrZero(response.credentialFieldCount),
    allManagementFieldsOptional: response.allManagementFieldsOptional !== false,
    items
  }
}

// 查询资产分类列表
export const getCategoryList = async () => {
  return await request.get({ url: '/eam/category/list' })
}

// 查询资产分类详情
export const getCategory = async (id: number) => {
  return await request.get({ url: '/eam/category/get?id=' + id })
}

// 新增资产分类
export const createCategory = async (data: CategoryVO) => {
  return await request.post({ url: '/eam/category/create', data })
}

// 修改资产分类
export const updateCategory = async (data: CategoryVO) => {
  return await request.put({ url: '/eam/category/update', data })
}

// 删除资产分类
export const deleteCategory = async (id: number) => {
  return await request.delete({ url: '/eam/category/delete?id=' + id })
}

export const importTemplate = async () => {
  return await request.download({ url: '/eam/category/get-import-template' })
}

const uploadImport = async (url: string, file: File) => {
  const data = new FormData()
  data.append('file', file)
  const response = await request.upload<{ data?: Partial<CategoryImportRespVO> }>({ url, data })
  return response.data || {}
}

export const previewImport = async (file: File) => {
  return normalizeImportResponse(await uploadImport('/eam/category/import/preview', file))
}

export const commitImport = async (file: File) => {
  return normalizeImportResponse(await uploadImport('/eam/category/import/commit', file))
}
