import { http, unwrap, type PageResult } from './api'

export type AssistAttachment = { infraFileId: number; name: string; type?: string; size?: number; url?: string }
export type AssistHistory = {
  id: number; leadNo: string; status: string; version?: number
  problem: string; expectedAssistance: string; remark?: string
  requesterName?: string; submitterName?: string; assigneeName?: string
  requestedAt?: string | number; responseRemark?: string; responderName?: string; respondedAt?: string | number
  requestAttachments?: AssistAttachment[]; responseAttachments?: AssistAttachment[]
}
export const leadAssistApi = {
  page: async (leadId: number, pageNo = 1) => unwrap<PageResult<AssistHistory>>(await http.get(`/zsjos/lead/${leadId}/submitter-assist/history/page`, { params: { pageNo, pageSize: 10 } })),
  reply: async (leadId: number, requestId: number, data: { remark: string; attachments: Array<{ infraFileId: number }>; version: number; idempotencyKey: string }) => unwrap<boolean>(await http.post(`/zsjos/lead/${leadId}/submitter-assist/${requestId}/reply`, data)),
}
