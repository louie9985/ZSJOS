import request from '@/config/axios'

export interface PartnerVO {
  id: number
  partnerNo: string
  name: string
  mobile: string
  status: 'enabled' | 'disabled' | 'converted'
  boundSystemUserId: number
  channelId?: string
  enabledAt?: string
  disabledAt?: string
}

export interface PartnerCreateVO {
  partnerNo: string
  name: string
  mobile: string
  username: string
  password: string
  channelId?: string
}

export const getPartnerList = () => request.get<PartnerVO[]>({ url: '/zsjos/partner/list' })
export const createPartner = (data: PartnerCreateVO) =>
  request.post({ url: '/zsjos/partner/create', data })
export const disablePartner = (id: number, reason: string) =>
  request.put({ url: `/zsjos/partner/${id}/disable`, data: { reason } })
export const enablePartner = (id: number, reason: string) =>
  request.put({ url: `/zsjos/partner/${id}/enable`, data: { reason } })
export const convertPartner = (
  id: number,
  data: {
    targetType: string
    deptId: number
    migrateHistoricalOrganization: boolean
    reason: string
  }
) => request.post({ url: `/zsjos/partner/${id}/convert`, data })
