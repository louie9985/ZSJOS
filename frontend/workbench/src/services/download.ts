import { http } from './api'

/**
 * 请求后端返回的二进制流文件（Excel/PDF 等）。
 *
 * ⚠️ api.ts 的响应拦截器读 response.data?.code 判 401，但 responseType:'blob' 时
 * data 是 Blob 拿不到 code，token 过期时自动刷新不会触发、导出会静默失败。
 * 因此这里自己识别「HTTP 200 但 body 是错误 JSON」的情况。
 */
export async function requestBlob(url: string, params?: Record<string, unknown>): Promise<Blob> {
  const response = await http.get(url, { params, responseType: 'blob' })
  const blob: Blob = response.data
  // 后端返回 JSON 错误体（包装在 blob 里）
  if (blob.type && blob.type.includes('json')) {
    const text = await blob.text()
    let parsed: { code?: number; msg?: string } | undefined
    try { parsed = JSON.parse(text) } catch { /* not JSON after all */ }
    if (parsed?.code) {
      throw new Error(parsed.msg || `请求失败 (code: ${parsed.code})`)
    }
  }
  return blob
}

/** 触发浏览器下载一个 Blob 文件 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 请求 blob 并直接触发下载（常见的 Excel 导出快捷方式） */
export async function downloadBlob(url: string, fileName: string, params?: Record<string, unknown>): Promise<void> {
  const blob = await requestBlob(url, params)
  saveBlob(blob, fileName)
}
