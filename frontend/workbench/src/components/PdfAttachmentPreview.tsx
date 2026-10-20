import { useEffect, useState } from 'react'
import { Alert, Button, Spin } from 'antd'
import { loadPdfPreview } from '../services/pdfAttachmentPreview'

export default function PdfAttachmentPreview({ url, name }: { url?: string; name: string }) {
  const [result, setResult] = useState<{ source: string; url?: string; error?: string }>()
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!url) return
    const controller = new AbortController()
    let objectUrl: string | undefined
    setResult(undefined)
    void loadPdfPreview(url, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob)
      setResult({ source: url, url: objectUrl })
    }).catch(cause => {
      if (!controller.signal.aborted) setResult({ source: url, error: cause instanceof Error ? cause.message : 'PDF 读取失败，请重试' })
    })
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [url, attempt])
  const current = result?.source === url ? result : undefined
  if (current?.error) return <Alert type="error" showIcon message={current.error} action={<Button onClick={() => setAttempt(value => value + 1)}>重试预览</Button>} />
  if (!current?.url) return <Spin tip="正在加载 PDF"><div style={{ height: '65vh' }} /></Spin>
  // Chromium blocks its native PDF viewer in sandboxed frames. Only validated,
  // application/pdf blobs from loadPdfPreview may enter this frame.
  return <iframe title={name} src={current.url} style={{ width: '100%', height: '65vh', border: 0 }} />
}
