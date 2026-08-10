import request from '@/config/axios'

export interface AreaVO {
  id: number
  name: string
  selectionCode: string
  type: number
  parentId: number
  sort: number
  status: number
  leafSelectable: boolean
  createTime?: string
  updateTime?: string
  children?: AreaVO[]
}

// 获得地区树
export const getAreaTree = async () => {
  return await request.get({ url: '/system/area/tree' })
}

// 获得 IP 对应的地区名
export const getAreaByIp = async (ip: string) => {
  return await request.get({ url: '/system/area/get-by-ip?ip=' + ip })
}

export const getAreaList = async (params?: { name?: string; status?: number }) => {
  return await request.get({ url: '/system/area/list', params })
}

export const getArea = async (id: number) => {
  return await request.get({ url: '/system/area/get?id=' + id })
}

export const createArea = async (data: AreaVO) => {
  return await request.post({ url: '/system/area/create', data })
}

export const updateArea = async (data: AreaVO) => {
  return await request.put({ url: '/system/area/update', data })
}

export const updateAreaStatus = async (id: number, status: number) => {
  return await request.put({ url: '/system/area/update-status', data: { id, status } })
}
