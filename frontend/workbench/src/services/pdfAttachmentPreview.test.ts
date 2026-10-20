import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadPdfPreview } from './pdfAttachmentPreview'
const url = 'https://files.example/notice.pdf'
const signal = () => new AbortController().signal
afterEach(() => vi.unstubAllGlobals())
describe('PDF preview content boundary', () => {
  it.each(['application/pdf', 'application/octet-stream', 'binary/octet-stream', ''])('normalizes valid PDF bytes served as %s', async type => {
    const fetcher = vi.fn().mockResolvedValue(new Response('%PDF-1.7\nfixture', { headers: { 'content-type': type } }))
    vi.stubGlobal('fetch', fetcher)
    const requestSignal = signal()
    const blob = await loadPdfPreview(url, requestSignal)
    expect(blob.type).toBe('application/pdf')
    expect(await blob.text()).toBe('%PDF-1.7\nfixture')
    expect(fetcher).toHaveBeenCalledWith(url, { signal: requestSignal, credentials: 'omit', referrerPolicy: 'no-referrer' })
  })
  it.each([[401, '访问已失效'], [403, '访问已失效'], [404, '不存在'], [410, '不存在'], [500, '读取失败']])('reports HTTP %s', async (status, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: Number(status) })))
    await expect(loadPdfPreview(url, signal())).rejects.toThrow(String(message))
  })
  it.each([['text/html', '%PDF-1.7<script>attack</script>', '实际类型'], ['application/json', '{}', '实际类型'], ['application/pdf', '<html>not PDF</html>', '内容无效'], ['application/pdf', '', '内容无效']])('rejects %s content', async (type, content, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(content, { headers: { 'content-type': type } })))
    await expect(loadPdfPreview(url, signal())).rejects.toThrow(message)
  })
  it('reports network or CORS failure without leaking the signed URL', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError(url)))
    await expect(loadPdfPreview(url, signal())).rejects.toThrow('网络或文件服务')
  })
  it('preserves cancellation', async () => {
    const controller = new AbortController(); controller.abort()
    const error = new DOMException('aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error))
    await expect(loadPdfPreview(url, controller.signal)).rejects.toBe(error)
  })
  it('rejects executable URLs before a request', async () => {
    const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher)
    await expect(loadPdfPreview('javascript:alert(1)', signal())).rejects.toThrow('地址不可用')
    expect(fetcher).not.toHaveBeenCalled()
  })
  it('supports the authorized blob loaders used by other attachment consumers', async () => {
    const objectUrl = URL.createObjectURL(new Blob(['%PDF-1.7 fixture'], { type: 'application/pdf' }))
    try {
      const result = await loadPdfPreview(objectUrl, signal())
      expect(await result.text()).toBe('%PDF-1.7 fixture')
      expect(result.type).toBe('application/pdf')
    } finally { URL.revokeObjectURL(objectUrl) }
  })
})
