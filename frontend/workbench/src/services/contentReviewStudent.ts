import type { AttachmentResource } from '../components/AttachmentCard'
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
    const loadContent = async (): Promise<AttachmentResource> => {
      const response = await http.get<Blob>(`${base(id)}/accounts/${accountId}/file-content`, { params, responseType:'blob' })
      if (response.data.type.includes('json')) throw new Error('附件读取权限已失效，请刷新审批状态')
      // Only the content endpoint's detected type may enable a preview, never stored MIME/name.
      const detected = response.data.type.split(';')[0].toLowerCase()
      const safe = /^(application\/pdf|image\/(png|jpeg|gif|webp|bmp)|audio\/(mpeg|wav|x-wav|ogg|mp4|flac)|video\/(mp4|webm|quicktime))$/.test(detected)
      const type = safe ? detected : 'application/octet-stream'
      return { ...file, type, previewSafe: safe, loadContent,
        url: URL.createObjectURL(new Blob([response.data], { type })) }
    }
    return { ...file, loadContent }
  },
}
