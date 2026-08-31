import request from '@/config/axios'

export interface StatisticsItemVO {
  key: string
  name: string
  count: number
}

export interface StatisticsVO {
  totalCount: number
  totalOriginalValue: number
  statusStats: StatisticsItemVO[]
  categoryStats: StatisticsItemVO[]
  deptStats: StatisticsItemVO[]
}

// 查询资产统计概览
export const getStatistics = async () => {
  return await request.get({ url: '/eam/statistics/overview' })
}
