import request from './request'
import { resolveHomeStatisticsDetailMock } from './mock'

export type HomeStatisticsPeriod = 'today' | 'week' | 'month' | 'year' | 'total'

export interface HomeStatistics {
  period: HomeStatisticsPeriod
  leadCount: number
  withdrawnAmount: number
  validLeadCount: number
  convertedLeadCount: number
}

export type HomeStatisticsMetric = 'lead_count' | 'withdrawn_amount' | 'valid_lead_count' | 'converted_lead_count'

export interface HomeStatisticsTimelineItem {
  id: string
  title: string
  description?: string
  occurredAt: string
}

export interface HomeStatisticsLeadDetail {
  kind: 'lead'
  id: number
  mock?: boolean
  leadNo: string
  submittedName: string
  status: string
  courseName: string
  submittedAt: string
  sourceLabel?: string
  mobileMasked?: string
  location?: string
  timeline?: HomeStatisticsTimelineItem[]
}

export interface HomeStatisticsWithdrawalDetail {
  kind: 'withdrawal'
  id: number
  mock?: boolean
  withdrawalNo: string
  status: 'paid'
  applicationAmount: number
  approvedAmount: number
  submittedAt: string
  paidAt: string
  accountNameSnapshot?: string
  bankNameSnapshot: string
  maskedCardNumber: string
}

export type HomeStatisticsDetailItem = HomeStatisticsLeadDetail | HomeStatisticsWithdrawalDetail

export interface HomeStatisticsDetailPage {
  period: HomeStatisticsPeriod
  metric: HomeStatisticsMetric
  list: HomeStatisticsDetailItem[]
  total: number
  totalAmount?: number
}

export function getHomeStatistics(period: HomeStatisticsPeriod) {
  return request.get<never, HomeStatistics>('/zsjos/partner/home-statistics', { params: { period } })
}

export function getHomeStatisticsDetails(params: {
  period: HomeStatisticsPeriod
  metric: HomeStatisticsMetric
  pageNo: number
  pageSize: number
}) {
  if (import.meta.env.DEV) {
    const mock = resolveHomeStatisticsDetailMock({ method: 'get', url: '/zsjos/partner/home-statistics/details', params })
    if (mock) return Promise.resolve(mock.data as HomeStatisticsDetailPage)
  }
  return request.get<never, HomeStatisticsDetailPage>('/zsjos/partner/home-statistics/details', { params })
}
