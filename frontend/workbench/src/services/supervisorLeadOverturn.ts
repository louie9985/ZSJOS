import { http, unwrap, type LeadAttachment } from './api'

export type SupervisorOverturnRequest = {
  reason: string
  qualificationToken: string
  idempotencyKey: string
  attachments: { infraFileId: number }[]
}

export const overturnLeadValid = async (leadId: number, data: SupervisorOverturnRequest) =>
  unwrap<boolean>(await http.post(`/zsjos/subordinate-sales/leads/${leadId}/overturn-valid`, data))

export const uploadOverturnImage = async (file: File) => {
  const data = new FormData()
  data.append('file', file)
  return unwrap<LeadAttachment>(await http.post('/zsjos/subordinate-sales/overturn-attachment/upload', data))
}
