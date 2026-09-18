import dayjs from 'dayjs'
import type { PurchaseIntent } from '../services/api'

export function canSharePaymentQr(intent: PurchaseIntent, now = Date.now()) {
  const expires = intent.paymentExpiresAt == null ? NaN : dayjs(intent.paymentExpiresAt).valueOf()
  return intent.collectionMode === 'online_link' && !!intent.paymentUrl && !intent.paymentCancelPending
    && (intent.paymentStatus === 'created' || intent.paymentStatus === 'waiting')
    && Number.isFinite(expires) && expires > now
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图片加载失败，请重试'))
    image.src = src
  })
}

export async function renderPaymentQrCard(svg: SVGSVGElement, logoUrl: string, intent: PurchaseIntent): Promise<Blob> {
  const svgUrl = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' }))
  try {
    const [qr, logo] = await Promise.all([loadImage(svgUrl), loadImage(logoUrl)])
    const canvas = document.createElement('canvas')
    canvas.width = 800
    canvas.height = 1120
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器无法生成图片')
    // Customer-shared PNGs keep a white background and black modules regardless of workbench theme.
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(logo, 160, 40, 480, 480 * logo.height / logo.width)
    context.textAlign = 'center'
    context.fillStyle = '#111111'
    context.font = 'bold 32px sans-serif'
    context.fillText('订单确认与付款', 400, 215)
    context.font = 'bold 48px sans-serif'
    context.fillText(new Intl.NumberFormat('zh-CN', { style: 'currency', currency: intent.currency }).format(intent.totalAmount), 400, 282, 700)
    context.font = '22px sans-serif'
    context.fillText(`订单编号：${intent.purchaseIntentNo}`, 400, 326, 700)
    context.drawImage(qr, 80, 350, 640, 640)
    // Use the symbol portion of the existing official logo; limit occlusion to 14% of the QR width.
    context.fillStyle = '#ffffff'
    context.fillRect(355, 625, 90, 90)
    context.drawImage(logo, 20, 20, 216, 140, 360, 644, 80, 52)
    context.fillStyle = '#111111'
    context.font = '24px sans-serif'
    context.fillText('微信长按识别，核对订单后付款', 400, 1030)
    context.font = '20px sans-serif'
    context.fillText(`有效期至 ${dayjs(intent.paymentExpiresAt).format('YYYY-MM-DD HH:mm:ss')}`, 400, 1070)
    return await new Promise<Blob>((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('生成图片失败，请重试')), 'image/png'))
  } finally {
    URL.revokeObjectURL(svgUrl)
  }
}
