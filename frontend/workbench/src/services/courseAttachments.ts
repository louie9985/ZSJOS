import { http, unwrap } from './api'
import type { DeferredUploadItem } from './deferredUpload'
export type CourseAttachment = { fileId: number; name: string; type: string; size?: number; url?: string }
export type CourseAttachmentItem = DeferredUploadItem<CourseAttachment>
export const COURSE_ATTACHMENT_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx'
export const COURSE_ATTACHMENT_HINT = '支持图片、PDF、Word、Excel、PPT，每个不超过100MB，最多20个。保存时上传；失败后再次保存可重试。'
export function courseAttachmentError(file: Pick<File, 'name' | 'size'>) {
 if (!/\.(png|jpe?g|gif|webp|pdf|docx?|xlsx?|pptx?)$/i.test(file.name)) return '仅支持图片、PDF、Word、Excel、PPT文件'
 if (!file.size || file.size > 100 * 1024 * 1024) return '附件不能为空且不能超过100MB'
}
export const courseAttachments = {
 read: async (courseId: number, fileId: number) => unwrap<CourseAttachment>(await http.get('/zsjos/course-calendar/attachment/' + courseId + '/' + fileId)),
 upload: async (file: File) => {
  const error = courseAttachmentError(file); if (error) throw new Error(error)
  const data = new FormData(); data.append('file', file)
  return unwrap<CourseAttachment>(await http.post('/zsjos/course-calendar/attachment/upload', data))
 }
}
export const courseAttachmentItems = (files: CourseAttachment[] = []): CourseAttachmentItem[] =>
 files.map(file => ({ uid: String(file.fileId), name: file.name, type: file.type, size: file.size, url: file.url, uploaded: file, status: 'done' }))
