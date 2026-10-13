import { http, unwrap, type PositioningCard, type PositioningFile, type PositioningServiceOverview } from './api'
import type { AccountProfile } from './mediaAccountProfile'
import type { InterviewContext } from './positioningInterviewApi'
export type ReviewStudent = {
  personId: number; personNo: string; name: string; mobile?: string; wechatId?: string
  accounts: Array<{ id: number; accountNo: string; nickname?: string; platformLabel?: string; serviceRelationId?: number }>
}
const base = (id: number) => `/zsjos/content-review/batch/${id}/student-read`
export const contentReviewStudentApi = {
  get: async (id: number, personId: number) => unwrap<ReviewStudent>(await http.get(base(id), { params: { personId } })),
  profile: async (id: number, accountId: number) => unwrap<AccountProfile>(await http.get(`${base(id)}/accounts/${accountId}/profile`)),
  positioning: async (id: number, accountId: number) => unwrap<PositioningCard[]>(await http.get(`${base(id)}/accounts/${accountId}/positioning`)),
  positioningOverview: async (id: number, accountId: number) => unwrap<{
    account: { current?: PositioningCard; effective?: PositioningCard; history: PositioningCard[] }
    service: PositioningServiceOverview
  }>(await http.get(`${base(id)}/accounts/${accountId}/positioning-overview`)),
  interview: async (id: number, relationId: number) => unwrap<InterviewContext>(await http.get(`${base(id)}/services/${relationId}/interview`)),
  file: async (id: number, accountId: number, kind: 'profile' | 'interview' | 'positioning' | 'evidence', fileId: number, submissionId?: number) => {
    const params = { kind, fileId, submissionId }
    const file = unwrap<PositioningFile>(await http.get(`${base(id)}/accounts/${accountId}/file`, { params }))
    const response = await http.get<Blob>(`${base(id)}/accounts/${accountId}/file-content`, { params, responseType:'blob' })
    if (response.data.type.includes('json')) throw new Error('附件读取权限已失效，请刷新审批状态')
    return { ...file, url: URL.createObjectURL(new Blob([response.data], { type:file.type || 'application/octet-stream' })) }
  },
}
