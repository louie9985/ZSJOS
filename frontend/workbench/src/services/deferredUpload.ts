import { createIdempotencyKey } from './idempotency'

export type DeferredUploadStatus = 'pending' | 'uploading' | 'done' | 'error'

export type DeferredUploadItem<T> = {
  uid: string
  name: string
  type?: string
  size?: number
  file?: File
  previewUrl?: string
  url?: string
  status: DeferredUploadStatus
  uploaded?: T
  error?: string
}

export function createDeferredUploadItem<T>(file: File): DeferredUploadItem<T> {
  return {
    uid: createIdempotencyKey(), name: file.name, type: file.type, size: file.size,
    file, previewUrl: URL.createObjectURL(file), status: 'pending'
  }
}

export async function uploadDeferredFiles<T>(
  items: DeferredUploadItem<T>[],
  upload: (file: File) => Promise<T>,
  onUpdate: (items: DeferredUploadItem<T>[]) => void
): Promise<{ items: DeferredUploadItem<T>[]; failed: boolean }> {
  let next = items.map(item => ({ ...item }))
  let failed = false
  for (const item of next) {
    if (item.status === 'done' && item.uploaded) continue
    if (!item.file) { item.status = 'error'; item.error = '文件内容不可用'; failed = true; continue }
    item.status = 'uploading'; item.error = undefined; onUpdate(next.map(value => ({ ...value })))
    try {
      item.uploaded = await upload(item.file)
      item.status = 'done'
    } catch (error) {
      item.status = 'error'
      item.error = error instanceof Error ? error.message : '上传失败'
      failed = true
    }
    onUpdate(next.map(value => ({ ...value })))
  }
  return { items: next, failed }
}
