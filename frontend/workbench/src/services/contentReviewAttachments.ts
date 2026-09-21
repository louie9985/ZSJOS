import { api, type MediaContentVersionFile } from './api'
import { uploadDeferredFiles, type DeferredUploadItem } from './deferredUpload'

export type ContentReviewUpload = Awaited<ReturnType<typeof api.mediaContent.uploadVersionFile>>
export type ContentReviewAttachment = DeferredUploadItem<ContentReviewUpload>
export const CONTENT_REVIEW_ATTACHMENT_ACCEPT = 'image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx'
const documentTypes = new Set([
  'application/pdf', 'application/msword', 'application/vnd.ms-excel', 'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
])
export function contentReviewFileError(file: File, cover = false): string | undefined {
  if (!file.size || file.size > 1024 ** 3) return '文件不能为空，且单个文件不能超过 1GB'
  if (file.type.startsWith('image/')) return
  if (!cover && (file.type.startsWith('video/') || documentTypes.has(file.type))) return
  return cover ? '封面仅支持图片' : '请使用图片、视频、PDF、Word、Excel 或 PPT 文件'
}

export function restoreContentReviewFiles(files: Pick<MediaContentVersionFile, 'fieldKey' | 'infraFileId' | 'originalName' | 'contentType' | 'fileSize' | 'previewUrl'>[] = [], fieldKey: string): ContentReviewAttachment[] {
  return files.filter(file => file.fieldKey === fieldKey).map(file => ({
    uid: `saved-${file.infraFileId}`, name: file.originalName, type: file.contentType, size: file.fileSize,
    url: file.previewUrl, status: 'done',
    uploaded: { fileId: file.infraFileId, name: file.originalName, contentType: file.contentType, size: file.fileSize, previewUrl: file.previewUrl },
  }))
}

/** Keep successful file references in the form so a failed save never reuploads them. */
export async function prepareContentReviewWorks(
  works: Array<Record<string, unknown>>,
  update: (index: number, field: string, items: ContentReviewAttachment[]) => void,
  upload = api.mediaContent.uploadVersionFile,
): Promise<Array<Record<string, unknown>>> {
  const prepared: Array<Record<string, unknown>> = []
  for (const [index, work] of works.entries()) {
    const { coverItems, attachmentItems, coverPreviewUrl: _preview, ...request } = work
    for (const [field, raw] of [['coverItems', coverItems], ['attachmentItems', attachmentItems]] as const) {
      if (!Array.isArray(raw)) continue
      const result = await uploadDeferredFiles(raw as ContentReviewAttachment[], upload, items => update(index, field, items))
      update(index, field, result.items)
      if (result.failed) throw new Error(`作品 ${index + 1} 有文件上传失败，请重试后保存`)
      const ids = result.items.map(item => item.uploaded!.fileId)
      if (field === 'coverItems') request.coverFileId = ids[0]
      else request.deliverableSnapshotJson = JSON.stringify(ids)
    }
    prepared.push(request)
  }
  return prepared
}
