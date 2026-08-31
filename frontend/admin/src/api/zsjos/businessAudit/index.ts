import request from '@/config/axios'

export interface BusinessAuditVO {
  id: number
  operatorUserId?: number
  operatorNameSnapshot: string
  operatorRoleSnapshot: string
  categoryCode: string
  actionCode: string
  targetType: string
  targetId?: string
  detailJson: string
  sourceIp?: string
  sourceType: 'ADMIN' | 'PARTNER' | 'PUBLIC_CALLBACK' | 'SYSTEM' | 'EXPLICIT'
  traceId?: string
  requestMethod?: string
  requestPath?: string
  resultStatus: 'STARTED' | 'SUCCESS' | 'FAILURE'
  resultCode?: number
  resultMessage?: string
  occurredAt: string
  finishedAt?: string
  durationMs?: number
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
  params: PageParam & {
    categoryCode?: string
    actionCode?: string
    targetType?: string
    sourceType?: string
    resultStatus?: string
    operatorUserId?: number
    occurredAt?: string[]
  }
) => request.get<PageResult<BusinessAuditVO[]>>({ url: '/zsjos/business-audit/page', params })

export const getImpersonationAuditPage = (params: PageParam & { sessionId?: number }) =>
  request.get<PageResult<ImpersonationAuditVO[]>>({
    url: '/zsjos/business-audit/impersonation-page',
    params
  })
