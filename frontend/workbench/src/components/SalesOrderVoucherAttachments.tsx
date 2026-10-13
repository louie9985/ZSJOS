import { useRef } from 'react'
import { Empty } from 'antd'
import AttachmentCard from './AttachmentCard'
import type { SalesOrderVoucher } from '../services/api'
import '../styles/components/sales-order-voucher-attachments.css'

function VoucherCard({ item, reload }: { item: SalesOrderVoucher; reload: () => Promise<SalesOrderVoucher[]> }) {
  const initial = useRef(true)
  return <AttachmentCard name={item.originalName} load={async () => {
    // Keep the page's authorization boundary when renewing expiring attachment URLs.
    const useInitial = initial.current
    initial.current = false
    const current = useInitial ? item : (await reload()).find(file => file.infraFileId === item.infraFileId)
    if (!current) throw new Error('凭证不存在或已不可访问，请刷新订单详情')
    if (!current.fileUrl) throw new Error('凭证暂不可用，请重试或联系管理员')
    return { name: current.originalName, url: current.fileUrl, type: current.contentType, size: current.fileSize }
  }}/>
}

export default function SalesOrderVoucherAttachments({ orderId, items, reload }: {
  orderId: number; items: SalesOrderVoucher[]; reload: () => Promise<SalesOrderVoucher[]>
}) {
  if (!items.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无缴费凭证"/>
  return <div className="sales-order-voucher-attachments">{items.map(item => <VoucherCard
    key={`${orderId}:${item.infraFileId}:${item.fileUrl}`} item={item} reload={reload}/>)}</div>
}
