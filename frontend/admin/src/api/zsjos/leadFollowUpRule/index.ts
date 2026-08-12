import request from '@/config/axios'

export interface LeadFollowUpRuleVO {
  id: number
  code: string
  name: string
  firstFollowUpTimeoutMinutes: number
  qualificationTimeoutMinutes: number
  agingPoolTimeoutDays: number
  noProgressWarningDays: number
  noProgressGraceDays: number
  status: number
  version: number
}

export interface LeadFollowUpRuleUpdateReqVO {
  firstFollowUpTimeoutMinutes: number
  qualificationTimeoutMinutes: number
  agingPoolTimeoutDays: number
  noProgressWarningDays: number
  noProgressGraceDays: number
}

export const getRule = (): Promise<LeadFollowUpRuleVO> =>
  request.get({ url: '/zsjos/lead-follow-up-rule/get' })

export const updateRule = (data: LeadFollowUpRuleUpdateReqVO) =>
  request.put({ url: '/zsjos/lead-follow-up-rule/update', data })
