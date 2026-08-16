import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Empty, Select, Skeleton, Tag, Typography } from 'antd'
import { api, type SalesOrder, type SalesOrderListItem } from '../services/api'
import SalesOrderDetailCards, { SALES_ORDER_STATUS_COLORS, SALES_ORDER_STATUS_LABELS } from './SalesOrderDetailCards'
import { formatTimestamp } from '../services/time'

export default function LeadCustomerOrders({ leadId, onCount }: { leadId: number; onCount: (count: number) => void }) {
  const [items, setItems] = useState<SalesOrderListItem[]>([]), [selectedId, setSelectedId] = useState<number>()
  const [loading, setLoading] = useState(true), [error, setError] = useState(''), [detailError, setDetailError] = useState('')
  const [details, setDetails] = useState<Record<number, SalesOrder>>({})
  const requestVersion = useRef(0)
  const detailRequestVersion = useRef(0)
  const load = useCallback(async () => { const version = ++requestVersion.current; setLoading(true); setError(''); setDetails({}); try { const rows = await api.customerSalesOrders(leadId); if (version !== requestVersion.current) return; setItems(rows); setSelectedId(rows[0]?.id); onCount(rows.length) } catch (e) { if (version === requestVersion.current) setError(e instanceof Error ? e.message : '订单记录加载失败') } finally { if (version === requestVersion.current) setLoading(false) } }, [leadId, onCount])
  useEffect(() => { void load(); return () => { requestVersion.current++ } }, [load])
  const selected = useMemo(() => items.find(item => item.id === selectedId), [items, selectedId])
  useEffect(() => {
    const version = ++detailRequestVersion.current
    if (!selectedId || details[selectedId]) { setDetailError(''); return }
    setDetailError('')
    api.customerSalesOrder(leadId, selectedId)
      .then(order => { if (version === detailRequestVersion.current) setDetails(current => ({ ...current, [selectedId]: order })) })
      .catch(e => { if (version === detailRequestVersion.current) setDetailError(e instanceof Error ? e.message : '订单详情加载失败') })
  }, [details, leadId, selectedId])
  if (loading) return <Skeleton active paragraph={{ rows: 10 }}/>
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
  if (!items.length) return <Empty description="该客户暂无订单"/>
  const detail = selectedId ? details[selectedId] : undefined
  const detailNode = detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => { if (!selectedId) return; setDetails(current => { const next = { ...current }; delete next[selectedId]; return next }); setDetailError('') }}>重试</Button>}/>
    : detail ? <SalesOrderDetailCards order={detail} approvalContext={selected} mode="mine"/> : <Skeleton active paragraph={{ rows: 10 }}/>
  return <div className="lead-customer-orders"><div className="lead-customer-orders-mobile"><Select value={selectedId} onChange={setSelectedId} options={items.map(item => ({ value: item.id, label: `${item.orderNo} · ${item.orderType === 'repurchase' ? '复购' : '首购'} · ¥${Number(item.totalAmount).toFixed(2)}` }))}/></div>
    <aside className="lead-customer-orders-list">{items.map(item => <button type="button" key={item.id} className={item.id === selectedId ? 'active' : ''} onClick={() => setSelectedId(item.id)}><strong>{item.orderNo}</strong><span><Tag>{item.orderType === 'repurchase' ? '复购' : '首购'}</Tag><Tag color={SALES_ORDER_STATUS_COLORS[item.status]}>{SALES_ORDER_STATUS_LABELS[item.status]}</Tag></span><Typography.Text>¥{Number(item.totalAmount).toFixed(2)}</Typography.Text><small>{formatTimestamp(item.submittedAt)}</small></button>)}</aside>
    <main>{detailNode}</main></div>
}
