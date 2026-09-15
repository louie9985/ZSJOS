import type { MediaContentVersion, MediaStudentDetail } from '../services/api'

export const publishedWorksForAccount = (contents: MediaStudentDetail['contents'], accountId: number) =>
  contents.filter(item => item.accountId === accountId && item.status === 'published')
    .sort((a, b) => (b.publishedAt || 0) - (a.publishedAt || 0) || b.id - a.id)

export function safeWorkUrl(value?: string) {
  try { return value && ['https:', 'http:'].includes(new URL(value).protocol) ? value : undefined }
  catch { return undefined }
}

export function publishedWorkCover(versions: MediaContentVersion[], versionNo?: number) {
  // Never use an unapproved newer draft as the cover of a published work.
  const version = versions.find(item => item.versionNo === versionNo)
  const files = version?.files || []
  return safeWorkUrl(files.find(file => file.fieldKey === 'cover' && file.contentType.startsWith('image/'))?.previewUrl)
    || safeWorkUrl(files.find(file => file.contentType.startsWith('image/'))?.previewUrl)
}
