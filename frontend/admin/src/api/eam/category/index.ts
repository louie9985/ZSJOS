import request from '@/config/axios'

export interface CategoryVO {
  id?: number
  parentId: number
  name: string
  code: string
  sort: number
  status: number
  remark?: string
  createTime?: Date
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
