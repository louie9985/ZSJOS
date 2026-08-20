import request from '@/config/axios'

export interface CategoryVO {
  id?: number
  parentId: number
  name: string
  code: string
  sort: number
  status: number
  managementMode: number
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
  return await request.upload<CategoryImportRespVO>({ url, data })
}

export const previewImport = async (file: File) => {
  return await uploadImport('/eam/category/import/preview', file)
}

export const commitImport = async (file: File) => {
  return await uploadImport('/eam/category/import/commit', file)
}
