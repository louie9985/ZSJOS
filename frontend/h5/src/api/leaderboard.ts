import request from './request'

export type LeaderboardPeriod = 'today' | 'week' | 'month' | 'total'
export type LeaderboardType = 'estimated_income' | 'withdrawn_amount' | 'lead_count' | 'valid_lead_count'
export type LeaderboardValueUnit = 'money' | 'count'

export interface LeaderboardTypeOption {
  key: LeaderboardType
  label: string
  valueLabel: string
  valueUnit: LeaderboardValueUnit
  ruleText: string
}

export interface LeaderboardConfig {
  enabled: boolean
  enabledTypes: LeaderboardType[]
  defaultType: LeaderboardType
  defaultPeriod: LeaderboardPeriod
  pageSize: number
  maskName: boolean
  typeOptions: LeaderboardTypeOption[]
}

export interface LeaderboardMember {
  partnerId: number
  displayName: string
  rank: number
  value: number
  isMe: boolean
  gapToPrevious?: number | null
}

export interface LeaderboardGap {
  targetRank: number
  value: number
  displayValue: string
  targetReached: boolean
}

export interface LeaderboardData {
  period: LeaderboardPeriod
  periodLabel: string
  type: LeaderboardType
  typeLabel: string
  valueLabel: string
  valueUnit: LeaderboardValueUnit
  ruleText: string
  total: number
  pageNo: number
  pageSize: number
  list: LeaderboardMember[]
  top3: LeaderboardMember[]
  myRank?: LeaderboardMember | null
  previousGap?: LeaderboardGap | null
  top10Gap?: LeaderboardGap | null
  nearbyRanks?: LeaderboardMember[]
}

export function getLeaderboardConfig() {
  return request.get<never, LeaderboardConfig>('/zsjos/partner/leaderboard/config')
}

export function getLeaderboard(params: {
  period: LeaderboardPeriod
  type: LeaderboardType
  pageNo: number
  pageSize: number
}) {
  return request.get<never, LeaderboardData>('/zsjos/partner/leaderboard', { params })
}
