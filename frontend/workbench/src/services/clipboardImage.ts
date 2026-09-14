const pad = (value: number) => String(value).padStart(2, '0')
const SUPPORTED_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp'] as const

export type ClipboardImageReadErrorCode = 'unsupported' | 'permission-denied' | 'read-failed'

export class ClipboardImageReadError extends Error {
  constructor(public readonly code: ClipboardImageReadErrorCode, options?: ErrorOptions) {
    const message = code === 'permission-denied'
      ? '请允许浏览器读取剪贴板后重试'
      : '当前浏览器无法读取剪贴板，请使用文件上传'
    super(message, options)
    this.name = 'ClipboardImageReadError'
  }
}

export function clipboardImageFiles(event: ClipboardEvent): File[] {
  const items = Array.from(event.clipboardData?.items || [])
  return items
    .filter(item => item.kind === 'file' && SUPPORTED_IMAGE_TYPES.includes(item.type as typeof SUPPORTED_IMAGE_TYPES[number]))
    .map(item => item.getAsFile())
    .filter((file): file is File => Boolean(file))
    .map(file => new File([file], clipboardImageName(file.type), { type: file.type, lastModified: Date.now() }))
}

export async function readClipboardImageFiles(): Promise<File[]> {
  if (!globalThis.isSecureContext || !navigator.clipboard?.read) {
    throw new ClipboardImageReadError('unsupported')
  }

  let items: ClipboardItems
  try {
    items = await navigator.clipboard.read()
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'NotAllowedError') {
      throw new ClipboardImageReadError('permission-denied', { cause })
    }
    throw new ClipboardImageReadError('read-failed', { cause })
  }

  try {
    const files: File[] = []
    for (const item of items) {
      for (const type of SUPPORTED_IMAGE_TYPES) {
        if (!item.types.includes(type)) continue
        const blob = await item.getType(type)
        files.push(new File([blob], clipboardImageName(type), { type, lastModified: Date.now() }))
        break
      }
    }
    return files
  } catch (cause) {
    throw new ClipboardImageReadError('read-failed', { cause })
  }
}

export function clipboardImageName(type = 'image/png', now = new Date()) {
  const extension = type === 'image/jpeg' ? 'jpg' : type === 'image/webp' ? 'webp' : 'png'
  return `截图-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.${extension}`
}
