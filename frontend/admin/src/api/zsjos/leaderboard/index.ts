import request from '@/config/axios'

export type LeaderboardType =
  | 'estimated_income'
  | 'withdrawn_amount'
  | 'lead_count'
  | 'valid_lead_count'
export type LeaderboardPeriod = 'today' | 'week' | 'month' | 'total'

export interface LeaderboardConfigVO {
  enabled: boolean
  includeEmployeeSubmitter: boolean
  employeeRoleCodes: string[]
  enabledTypes: LeaderboardType[]
  defaultType: LeaderboardType
  defaultPeriod: LeaderboardPeriod
}

export interface LeaderboardConfigSaveReqVO extends LeaderboardConfigVO {}

export const getLeaderboardConfig = () =>
  request.get<LeaderboardConfigVO>({ url: '/zsjos/partner/leaderboard-config/get' })

export const saveLeaderboardConfig = (data: LeaderboardConfigSaveReqVO) =>
  request.put({ url: '/zsjos/partner/leaderboard-config/save', data })

