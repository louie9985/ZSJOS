// Signed URLs and authorized blob URLs are supplied by the owning business loader.
export async function loadPdfPreview(url: string, signal: AbortSignal): Promise<Blob> {
  if (!['http:', 'https:', 'blob:'].includes(new URL(url).protocol)) throw new Error('附件地址不可用')
  let response: Response
  try {
    response = await fetch(url, { signal, credentials: 'omit', referrerPolicy: 'no-referrer' })
  } catch (cause) {
    if (signal.aborted) throw cause
    throw new Error('PDF 读取失败，网络或文件服务暂时不可用，请重试或下载原文件查看')
  }
  if (response.status === 401 || response.status === 403) throw new Error('附件访问已失效或无权读取，请关闭后重新预览')
  if (response.status === 404 || response.status === 410) throw new Error('文件不存在或已移除，请刷新公告')
  if (!response.ok) throw new Error('PDF 读取失败，请重试或下载原文件查看')
  const type = response.headers.get('content-type')?.split(';')[0].trim().toLowerCase() || ''
  if (!['', 'application/pdf', 'application/octet-stream', 'binary/octet-stream'].includes(type)) {
    throw new Error('文件实际类型不是 PDF，请下载原文件核实')
  }
  const blob = await response.blob()
  if (!/^%PDF-[0-9][.][0-9]/.test(await blob.slice(0, 8).text())) throw new Error('PDF 文件内容无效，请下载原文件核实')
  // Force the browser PDF viewer; never navigate an unsandboxed frame to a remote
  // URL that could return HTML or redirect to an executable same-origin document.
  return new Blob([blob], { type: 'application/pdf' })
}
