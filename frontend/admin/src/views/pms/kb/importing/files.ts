import CryptoJS from 'crypto-js'
import type { ImportEntry, ImportPolicy } from '@/api/pms/kb/importing'
// Incremental hashing also works on existing HTTP intranet deployments without SubtleCrypto.
export async function fingerprint(file: File): Promise<string> {
  const digest = CryptoJS.algo.SHA256.create()
  for (let offset = 0; offset < file.size; offset += 2 * 1024 * 1024) {
    const bytes = new Uint8Array(await file.slice(offset, offset + 2 * 1024 * 1024).arrayBuffer())
    const words: number[] = []
    for (let i = 0; i < bytes.length; i++)
      words[i >>> 2] = (words[i >>> 2] || 0) | (bytes[i] << (24 - (i % 4) * 8))
    digest.update(CryptoJS.lib.WordArray.create(words, bytes.length))
  }
  return digest.finalize().toString()
}
export async function manifest(
  files: File[],
  policy: ImportPolicy,
  progress: (name: string) => void
): Promise<ImportEntry[]> {
  if (!files.length) throw new Error('未读取到文件，空文件夹无法导入')
  if (files.length > policy.maxFiles || files.reduce((n, f) => n + f.size, 0) > policy.maxTotalSize)
    throw new Error('文件数量或总大小超限，请拆分导入')
  const entries: ImportEntry[] = []
  for (const file of [...files].sort((a, b) =>
    a.webkitRelativePath < b.webkitRelativePath
      ? -1
      : a.webkitRelativePath > b.webkitRelativePath
        ? 1
        : 0
  )) {
    const relativePath = file.webkitRelativePath
    if (!relativePath || relativePath.split('/').length - 1 > policy.maxDepth)
      throw new Error('目录路径无效或层级超限')
    progress(relativePath)
    const extension = file.name.split('.').pop()?.toLowerCase() || ''
    const digest =
      file.size > 0 && file.size <= policy.maxFileSize && policy.fileTypes.includes(extension)
        ? await fingerprint(file)
        : undefined
    entries.push({ relativePath, fileSize: file.size, lastModified: file.lastModified, digest })
  }
  return entries
}
export function transient(error: unknown): boolean {
  const e = error as { code?: string; response?: { status?: number } }
  return (
    ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(e?.code || '') ||
    [502, 503, 504].includes(e?.response?.status || 0)
  )
}
