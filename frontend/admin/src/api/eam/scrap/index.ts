import request from '@/config/axios'

export interface ScrapVO {
  id?: number
  no?: string
  assetId: number
  assetName?: string
  assetCode?: string
  reasonType: number
  reason?: string
  scrapDate?: string
  status?: number
  processInstanceId?: string
  applyUserId?: number
  applyUserName?: string
  applyTime?: Date
}

/** 报废单状态 */
export const ScrapStatus = {
  APPROVING: 0,
  SCRAPPED: 1,
  REJECTED: 2
} as const

// 查询报废单分页
export const getScrapPage = async (params: any) => {
  return await request.get({ url: '/eam/scrap/page', params })
}

// 查询报废单详情
export const getScrap = async (id: number) => {
  return await request.get({ url: '/eam/scrap/get?id=' + id })
}

// 申请报废
export const createScrap = async (data: ScrapVO) => {
  return await request.post({ url: '/eam/scrap/create', data })
}

// 审批通过
export const approveScrap = async (id: number) => {
  return await request.put({ url: '/eam/scrap/approve?id=' + id })
}

// 驳回
export const rejectScrap = async (id: number, reason?: string) => {
  return await request.put({
    url: `/eam/scrap/reject?id=${id}${reason ? '&reason=' + encodeURIComponent(reason) : ''}`
  })
}
