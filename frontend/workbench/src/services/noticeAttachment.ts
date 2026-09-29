import type { AnnouncementAttachment } from './api'

export function noticeAttachmentResource(file?: AnnouncementAttachment) {
  if (!file?.downloadUrl) throw new Error('文件不可用，请重试或联系公告发布人')
  const url = new URL(file.downloadUrl, window.location.origin)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('附件地址不可用')
  return { name: file.fileName, url: url.href, type: file.mimeType, size: file.fileSize }
}
