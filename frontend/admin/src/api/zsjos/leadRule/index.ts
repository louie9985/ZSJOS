import request from '@/config/axios'

export interface LeadAssignmentRuleVO {
  id: number
  code: string
  name: string
  strategyType: 'global_round_robin'
  acceptTimeoutSeconds: number
  maxAttempts: number
  status: number
}

export interface LeadAssignmentRuleUpdateReqVO {
  acceptTimeoutSeconds: number
  maxAttempts: number
}

export const getRule = (): Promise<LeadAssignmentRuleVO> =>
  request.get({ url: '/zsjos/lead/assignment-rule/get' })

export const updateRule = (data: LeadAssignmentRuleUpdateReqVO) =>
  request.put({ url: '/zsjos/lead/assignment-rule/update', data })
