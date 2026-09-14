import { http, unwrap, type DirectorTemplateSnapshot } from './api'

export type InterviewItem = { fieldKey: string; status?: string; remark?: string; value?: string }
export type InterviewAttachment = { fileId: number; fileName: string; mimeType?: string; fileSize: number; url?: string }
export type InterviewContext = {
  id?: number; studentPersonId: number; serviceRelationId: number; studentName: string; studentNo: string
  status: 'empty' | 'draft' | 'completed'; version: number; templateId: number; templateVersionId: number
  fields: DirectorTemplateSnapshot['fields']; items: InterviewItem[]; attachments: InterviewAttachment[]
  statusOptions: Record<string, string>; availableActions: string[]; collectedAt?: string
  interviewAt?: number; completedAt?: number; legacyInterviewSnapshotJson?: string
}
export type InterviewCommand = {
  studentPersonId?: number; serviceRelationId?: number
  version: number; idempotencyKey: string; templateVersionId: number; collectedAt?: string
  items: InterviewItem[]; attachmentIds: number[]
}
const path = (relationId: number) => `/zsjos/student/service/${relationId}/positioning-interview`
export const positioningInterviewApi = {
  context: async (id: number) => unwrap<InterviewContext>(await http.get(`${path(id)}/context`)),
  save: async (id: number, command: InterviewCommand, complete = false) =>
    unwrap<InterviewContext>(await http.post(`${path(id)}/${complete ? 'complete' : 'draft'}`, command)),
  upload: async (id: number, file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<InterviewAttachment>(await http.post(`${path(id)}/attachments`, data))
  },
  download: async (id: number, fileId: number) => unwrap<InterviewAttachment>(await http.get(`${path(id)}/attachments/${fileId}`)),
  remove: async (id: number, fileId: number, version: number, idempotencyKey: string) =>
    unwrap<boolean>(await http.delete(`${path(id)}/attachments/${fileId}`, { params: { version, idempotencyKey } })),
}
export function interviewMissingFields(context: Pick<InterviewContext, 'fields' | 'statusOptions'>, items: InterviewItem[]) {
  const answers = new Map(items.map(item => [item.fieldKey, item.status]))
  return context.fields.filter(field => field.enabled && field.required && !field.systemField
    && !Object.prototype.hasOwnProperty.call(context.statusOptions, answers.get(field.key) || ''))
}
