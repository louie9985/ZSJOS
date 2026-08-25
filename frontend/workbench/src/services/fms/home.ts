import { http, unwrap } from '../api'

export interface FmsHomeMetric {
  key: string
  name: string
  amount: number
}

export interface FmsHomeTrend {
  month: string
  metrics: FmsHomeMetric[]
  income: number
  operatingCost: number
  profit: number
  expense: number
  other: number
}

export interface FmsHome {
  currentMonth: string
  metrics: FmsHomeMetric[]
  trends: FmsHomeTrend[]
}

export interface FmsHomeMetricTrend {
  month: string
  amount: number
}

export interface FmsHomeMetricStructure {
  subjectId: number
  subjectCode: string
  subjectName: string
  amount: number
}

export interface FmsHomeMetricDetail {
  key: string
  name: string
  trends: FmsHomeMetricTrend[]
  structure: FmsHomeMetricStructure[]
}

export const fmsHomeApi = {
  getHome: async (accountSetId: number): Promise<FmsHome> =>
    unwrap<FmsHome>(await http.get('/fms/home/get', { params: { accountSetId } })),
  getMetricDetail: async (accountSetId: number, metricKey: string): Promise<FmsHomeMetricDetail> =>
    unwrap<FmsHomeMetricDetail>(await http.get('/fms/home/metric-detail', { params: { accountSetId, metricKey } }))
} as const
