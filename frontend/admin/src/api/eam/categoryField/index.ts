import request from '@/config/axios'

export interface CategoryFieldVO {
  id?: number
  categoryId: number
  fieldKey: string
  fieldName: string
  fieldType: number
  options?: string[]
  optionSource?: 'STATIC' | 'SYSTEM_DICT'
  dictType?: string
  required: boolean
  adminVisible: boolean
  collectionVisible: boolean
  collectionRequired: boolean
  conditionRule?: Record<string, any>
  sort: number
  /** 该字段是否继承自父分类，继承字段不可在当前分类就地编辑 */
  inherited?: boolean
}

/** 分类自定义字段类型 */
export const FieldType = {
  TEXT: 1,
  TEXTAREA: 2,
  NUMBER: 3,
  DATE: 4,
  SELECT: 5,
  FILE: 6
} as const

// 查询分类【直接定义】的字段列表
export const getFieldList = async (categoryId: number) => {
  return await request.get({ url: '/eam/category-field/list?categoryId=' + categoryId })
}

// 查询分类【生效】的字段列表（含从父分类继承），资产表单据此渲染动态字段
export const getEffectiveFieldList = async (categoryId: number) => {
  return await request.get({ url: '/eam/category-field/effective-list?categoryId=' + categoryId })
}

// 新增自定义字段
export const createField = async (data: CategoryFieldVO) => {
  return await request.post({ url: '/eam/category-field/create', data })
}

// 修改自定义字段
export const updateField = async (data: CategoryFieldVO) => {
  return await request.put({ url: '/eam/category-field/update', data })
}

// 删除自定义字段
export const deleteField = async (id: number) => {
  return await request.delete({ url: '/eam/category-field/delete?id=' + id })
}
