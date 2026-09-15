import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Button, Modal, Space, Typography } from 'antd'
import { api } from '../../services/api'

/**
 * 审批签名板。节点配置 signEnable=true 时，通过/拒绝前必须先签名。
 * 画布使用指针事件，兼容鼠标与触屏。
 */
export default function BpmSignaturePad({
  open,
  onClose,
  onConfirm
}: {
  open: boolean
  onClose: () => void
  onConfirm: (signPicUrl: string) => void
}) {
  const { message } = App.useApp()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawingRef = useRef(false)
  const dirtyRef = useRef(false)
  const [uploading, setUploading] = useState(false)

  const resetCanvas = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const context = canvas.getContext('2d')
    if (!context) return
    // 画布导出为 PNG，需要不透明底色；沿用设计令牌而不是硬编码颜色。
    const styles = getComputedStyle(canvas)
    context.fillStyle = styles.backgroundColor || styles.getPropertyValue('--crm-bg-container').trim()
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.lineWidth = 2.5
    context.lineCap = 'round'
    context.lineJoin = 'round'
    context.strokeStyle = styles.color
    dirtyRef.current = false
  }, [])

  useEffect(() => {
    if (open) resetCanvas()
  }, [open, resetCanvas])

  const pointerPosition = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current
    if (!canvas) return { x: 0, y: 0 }
    const rect = canvas.getBoundingClientRect()
    // 画布的 CSS 尺寸与位图尺寸可能不同，坐标需按比例换算。
    return {
      x: (event.clientX - rect.left) * (canvas.width / rect.width),
      y: (event.clientY - rect.top) * (canvas.height / rect.height)
    }
  }

  const handlePointerDown = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const context = canvasRef.current?.getContext('2d')
    if (!context) return
    event.currentTarget.setPointerCapture(event.pointerId)
    drawingRef.current = true
    dirtyRef.current = true
    const { x, y } = pointerPosition(event)
    context.beginPath()
    context.moveTo(x, y)
  }

  const handlePointerMove = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawingRef.current) return
    const context = canvasRef.current?.getContext('2d')
    if (!context) return
    const { x, y } = pointerPosition(event)
    context.lineTo(x, y)
    context.stroke()
  }

  const handlePointerUp = () => {
    drawingRef.current = false
  }

  const handleConfirm = async () => {
    const canvas = canvasRef.current
    if (!canvas) return
    if (!dirtyRef.current) {
      message.warning('请先手写签名')
      return
    }
    setUploading(true)
    try {
      const blob = await new Promise<Blob | null>(resolve =>
        canvas.toBlob(result => resolve(result), 'image/png')
      )
      if (!blob) {
        message.error('签名生成失败，请重试')
        return
      }
      const url = await api.uploadBpmSignature(
        new File([blob], `signature-${Date.now()}.png`, { type: 'image/png' })
      )
      onConfirm(url)
      onClose()
    } catch {
      message.error('签名上传失败，请重试')
    } finally {
      setUploading(false)
    }
  }

  return <Modal
    open={open}
    title="手写签名"
    onCancel={onClose}
    destroyOnHidden
    footer={<Space>
      <Button onClick={resetCanvas} disabled={uploading}>清除</Button>
      <Button onClick={onClose} disabled={uploading}>取消</Button>
      <Button type="primary" loading={uploading} onClick={() => void handleConfirm()}>确认签名</Button>
    </Space>}
  >
    <Typography.Text type="secondary">在下方区域手写签名，确认后作为本次审批的签名记录。</Typography.Text>
    <canvas
      ref={canvasRef}
      width={520}
      height={220}
      className="bpm-signature-canvas"
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
    />
  </Modal>
}
