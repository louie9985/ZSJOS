import request from '@/config/axios'

export interface CodeRuleVO {
  id?: number
  categoryId?: number
  prefix?: string
  useCategoryCode: boolean
  dateFormat?: string
  serialLength: number
  separator?: string
  currentSerial?: number
}

// 查询编号规则列表
export const getCodeRuleList = async () => {
  return await request.get({ url: '/eam/code-rule/list' })
}

// 查询编号规则详情
export const getCodeRule = async (id: number) => {
  return await request.get({ url: '/eam/code-rule/get?id=' + id })
}

// 新增编号规则
export const createCodeRule = async (data: CodeRuleVO) => {
  return await request.post({ url: '/eam/code-rule/create', data })
}

// 修改编号规则
export const updateCodeRule = async (data: CodeRuleVO) => {
  return await request.put({ url: '/eam/code-rule/update', data })
}

// 删除编号规则
export const deleteCodeRule = async (id: number) => {
  return await request.delete({ url: '/eam/code-rule/delete?id=' + id })
}
