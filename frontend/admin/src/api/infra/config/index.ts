import request from '@/config/axios'

export interface ConfigVO {
  id: number | undefined
  category: string
  name: string
  key: string
  value: string
  type: number
  visible: boolean
  remark: string
  createTime: Date
}

// 查询参数列表
export const getConfigPage = (params: PageParam) => {
  return request.get({ url: '/infra/config/page', params })
}

// 查询参数详情
export const getConfig = (id: number) => {
  return request.get({ url: '/infra/config/get?id=' + id })
}

// 根据参数键名查询参数值
export const getConfigKey = (configKey: string) => {
  return request.get({ url: '/infra/config/get-value-by-key?key=' + configKey })
}

// 查询全平台默认员工头像
export const getDefaultUserAvatar = () => {
  return request.get({ url: '/infra/config/default-user-avatar' })
}

// 更新全平台默认员工头像
export const updateDefaultUserAvatar = (avatar?: string) => {
  return request.put({
    url: '/infra/config/default-user-avatar',
    data: { avatar: avatar?.trim() || null }
  })
}

// 新增参数
export const createConfig = (data: ConfigVO) => {
  return request.post({ url: '/infra/config/create', data })
}

// 修改参数
export const updateConfig = (data: ConfigVO) => {
  return request.put({ url: '/infra/config/update', data })
}

// 删除参数
export const deleteConfig = (id: number) => {
  return request.delete({ url: '/infra/config/delete?id=' + id })
}

// 批量删除参数
export const deleteConfigList = (ids: number[]) => {
  return request.delete({ url: '/infra/config/delete-list', params: { ids: ids.join(',') } })
}

// 导出参数
export const exportConfig = (params) => {
  return request.download({ url: '/infra/config/export-excel', params })
}
