import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Modal, QRCode, Space, Spin, message } from 'antd'
import { CopyOutlined, DownloadOutlined, QrcodeOutlined } from '@ant-design/icons'
import type { PurchaseIntent } from '../services/api'
import logoUrl from '../assets/payment-logo.png'
import { canSharePaymentQr, renderPaymentQrCard } from './paymentQrImage'

export default function PaymentQrCard({ intent }: { intent: PurchaseIntent }) {
  const [open, setOpen] = useState(false)
  const [now, setNow] = useState(Date.now())
  const [image, setImage] = useState<{ url: string; blob: Blob }>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const qrRef = useRef<HTMLDivElement>(null)
  const available = canSharePaymentQr(intent, now)
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  useEffect(() => {
    setImage(undefined)
    setError('')
    if (!open || !available) return
    let disposed = false
    let url: string | undefined
    const svg = qrRef.current?.querySelector('svg')
    if (!svg) { setError('二维码未就绪，请重试'); return }
    void renderPaymentQrCard(svg, logoUrl, intent).then(blob => {
      if (disposed) return
      url = URL.createObjectURL(blob)
      setImage({ url, blob })
    }).catch(reason => { if (!disposed) setError(reason instanceof Error ? reason.message : '生成失败，请重试') })
    return () => { disposed = true; if (url) URL.revokeObjectURL(url) }
  }, [open, available, intent, attempt])
  const ready = () => {
    if (!canSharePaymentQr(intent)) { setNow(Date.now()); message.warning('付款入口已失效，请刷新支付状态'); return false }
    return !!image
  }
  const copy = async () => {
    if (!ready() || !image) return
    if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
      message.warning('当前浏览器不支持复制图片，请下载后发送'); return
    }
    try {
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': image.blob })])
      message.success('付款二维码已复制，可粘贴到微信发送')
    } catch { message.error('复制图片失败，请下载图片后发送') }
  }
  const download = () => {
    if (!ready() || !image) return
    const link = document.createElement('a')
    link.href = image.url
    link.download = `付款二维码-${intent.purchaseIntentNo.replace(/[^\w-]/g, '_')}.png`
    link.click()
  }
  return <>
    <Button size="small" icon={<QrcodeOutlined />} disabled={!available} onClick={() => setOpen(true)}>付款二维码</Button>
    <Modal title="付款二维码" open={open} onCancel={() => setOpen(false)} width={440} style={{ top: 24 }} footer={
      <Space wrap><Button onClick={() => setOpen(false)}>关闭</Button>
        <Button icon={<DownloadOutlined />} disabled={!available || !image} onClick={download}>下载二维码</Button>
        <Button type="primary" icon={<CopyOutlined />} disabled={!available || !image} onClick={() => void copy()}>复制二维码</Button></Space>
    }>
      {!available ? <Alert type="warning" showIcon title="付款入口已失效或不可分享，请刷新支付状态" />
        : error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => setAttempt(value => value + 1)}>重试</Button>} />
          : image ? <img src={image.url} alt="中世健订单付款二维码，含金额、订单编号及有效期" style={{ display: 'block', width: '100%' }} />
            : <Spin tip="正在生成付款二维码"><div style={{ minHeight: 240 }} /></Spin>}
    </Modal>
    <div ref={qrRef} style={{ display: 'none' }} aria-hidden="true">
      {open && available && <QRCode value={intent.paymentUrl!} type="svg" size={640} errorLevel="H" marginSize={4} color="#000000" bgColor="#ffffff" bordered={false} />}
    </div>
  </>
}
