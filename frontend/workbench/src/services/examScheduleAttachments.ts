import { http, unwrap } from './api'
import type { DeferredUploadItem } from './deferredUpload'

export type ExamAttachment = { fileId: number; name: string; type: string; size: number; url?: string }
export type ExamAttachmentItem = DeferredUploadItem<ExamAttachment>
export const EXAM_ATTACHMENT_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx'
export const EXAM_ATTACHMENT_HINT = '支持图片、PDF、Word、Excel、PPT，每个不超过100MB，最多10个。保存时上传；失败后再次保存可重试。'

export function examAttachmentError(file: Pick<File, 'name' | 'size'>) {
  if (!/\.(png|jpe?g|gif|webp|pdf|docx?|xlsx?|pptx?)$/i.test(file.name)) return '仅支持图片、PDF、Word、Excel、PPT文件'
  if (!file.size || file.size > 100 * 1024 * 1024) return '附件不能为空且不能超过100MB'
}

export const examScheduleAttachments = {
  read: async (scheduleId: number, fileId: number) => unwrap<ExamAttachment>(
    await http.get(`/zsjos/exam-calendar/attachment/${scheduleId}/${fileId}`)),
  upload: async (file: File) => {
    const error = examAttachmentError(file)
    if (error) throw new Error(error)
    const data = new FormData(); data.append('file', file)
    return unwrap<ExamAttachment>(await http.post('/zsjos/exam-calendar/attachment/upload', data))
  }
}

export const examAttachmentItems = (files: ExamAttachment[] = []): ExamAttachmentItem[] =>
  files.map(file => ({ uid: String(file.fileId), name: file.name, type: file.type, size: file.size,
    url: file.url, uploaded: file, status: 'done' }))
