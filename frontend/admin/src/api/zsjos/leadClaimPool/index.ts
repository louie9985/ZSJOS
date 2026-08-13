import request from '@/config/axios'
import type { Timestamp } from '../types'
import type { AdvancedFilterGroup } from '../advancedFilter'

export interface LeadClaimPoolVO {
  id: number
  dispatchMode?: 'auto' | 'specified'
  maskedName: string
  maskedMobile?: string
  maskedWechatId?: string
  provinceName?: string
  cityName?: string
  intendedProducts: string[]
  primaryIntendedProduct?: string
  sourceChannel?: string
  leadCategory?: string
  remark?: string
  attachmentUrls: string[]
  submittedAt?: Timestamp
  expiresAt?: Timestamp
}

export interface LeadClaimPoolPageReqVO extends PageParam {
  keyword?: string
  advancedFilter?: AdvancedFilterGroup
}

export const getClaimPoolPage = (
  params: LeadClaimPoolPageReqVO
): Promise<{ list: LeadClaimPoolVO[]; total: number }> =>
  params.advancedFilter
    ? request.post({ url: '/zsjos/lead/claim-pool/search-page', data: params })
    : request.get({ url: '/zsjos/lead/claim-pool/page', params })
