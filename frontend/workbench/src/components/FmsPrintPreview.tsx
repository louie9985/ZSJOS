import { Modal } from 'antd'
import { useCallback, useRef, useState } from 'react'

interface FmsPrintPreviewProps {
  open: boolean
  onClose: () => void
  html: string
  title?: string
}

/**
 * FMS 打印预览弹窗。
 * 全屏 Modal + iframe srcDoc，对齐 admin 端 FmsPrintPreview.vue 的行为。
 */
export default function FmsPrintPreview({ open, onClose, html, title = '打印预览' }: FmsPrintPreviewProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null)

  const handlePrint = useCallback(() => {
    iframeRef.current?.contentWindow?.print()
  }, [])

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={title}
      width="96vw"
      styles={{ body: { height: 'calc(100vh - 180px)', padding: 0, overflow: 'hidden' } }}
      okText="打印"
      onOk={handlePrint}
      cancelText="关闭"
      destroyOnClose
    >
      <iframe
        ref={iframeRef}
        srcDoc={html}
        style={{ width: '100%', height: '100%', border: 'none' }}
        title={title}
      />
    </Modal>
  )
}

/** 打印预览状态 hook */
export function usePrintPreview() {
  const [open, setOpen] = useState(false)
  const [html, setHtml] = useState('')

  const show = useCallback((printHtml: string) => {
    setHtml(printHtml)
    setOpen(true)
  }, [])

  const close = useCallback(() => {
    setOpen(false)
    setHtml('')
  }, [])

  return { open, html, show, close }
}
