import { http, unwrap, type PageResult } from './api'
import type { Timestamp } from './time'

export type LeadFeedbackAttachment = {
  fileId: number; originalName: string; contentType: string; fileSize: number; url?: string
}
export type LeadSubmitterFeedback = {
  id: number; feedback: string; salesName?: string; submitterName?: string
  createTime: Timestamp; attachments: LeadFeedbackAttachment[]
}
export const leadFeedbackApi = {
  page: async (leadId: number, pageNo = 1) => unwrap<PageResult<LeadSubmitterFeedback>>(
    await http.get(`/zsjos/lead/${leadId}/submitter-feedback/page`, { params: { pageNo, pageSize: 10 } })),
  create: async (leadId: number, data: { feedback: string; attachmentIds: number[]; version: number; idempotencyKey: string }) =>
    unwrap<number>(await http.post(`/zsjos/lead/${leadId}/submitter-feedback`, data)),
  upload: async (leadId: number, file: File) => {
    const data = new FormData(); data.append('file', file)
    return unwrap<LeadFeedbackAttachment>(await http.post(`/zsjos/lead/${leadId}/submitter-feedback/attachment/upload`, data))
  }
}
