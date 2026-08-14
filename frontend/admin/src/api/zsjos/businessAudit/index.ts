import request from '@/config/axios'

export interface BusinessAuditVO {
  id: number
  operatorUserId?: number
  operatorNameSnapshot: string
  operatorRoleSnapshot: string
  categoryCode: string
  actionCode: string
  targetType: string
  targetId: string
  detailJson: string
  sourceIp?: string
  occurredAt: string
}

export interface ImpersonationAuditVO {
  id: number
  sessionId: number
  administratorUserId: number
  targetUserId: number
  httpMethod: string
  requestPath: string
  occurredAt: string
}

export const getBusinessAuditPage = (
  params: PageParam & { actionCode?: string; targetType?: string }
) => request.get<PageResult<BusinessAuditVO>>({ url: '/zsjos/business-audit/page', params })

export const getImpersonationAuditPage = (params: PageParam & { sessionId?: number }) =>
  request.get<PageResult<ImpersonationAuditVO>>({
    url: '/zsjos/business-audit/impersonation-page',
    params
  })
