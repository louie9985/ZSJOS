import request from '@/config/axios'
import type { Timestamp } from '../types'

export interface AssignmentUserVO {
  id: number
  nickname: string
  maskedMobile?: string
  deptId?: number
  deptName?: string
  avatar?: string
  status: number
}

export interface AssignmentRelationVO extends AssignmentUserVO {
  salesUsers: AssignmentUserVO[]
  validSalesCount: number
  invalidSalesCount: number
  updateTime?: Timestamp
}

export interface AssignmentLogVO {
  id: number
  sourceUsers: string
  targetUsers: string
  actionType: 'append' | 'replace' | 'remove'
  operatorUserId: number
  operatorName: string
  createTime: Timestamp
}

export const getRelationPage = (params: PageParam & Record<string, any>) =>
  request.get({ url: '/zsjos/lead-assignment/relation/page', params })

export const getEligibleSales = (): Promise<AssignmentUserVO[]> =>
  request.get({ url: '/zsjos/lead-assignment/eligible-sales' })

export const saveRelations = (data: {
  sourceUserIds: number[]
  targetUserIds: number[]
  mode: 'append' | 'replace' | 'remove'
}) => request.put({ url: '/zsjos/lead-assignment/relation/save', data })

export const getLogPage = (params: PageParam & Record<string, any>) =>
  request.get({ url: '/zsjos/lead-assignment/log/page', params })
