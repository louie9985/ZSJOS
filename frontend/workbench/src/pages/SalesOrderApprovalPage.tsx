import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Button, Drawer, Empty, Input, Modal, Skeleton, Spin, Tabs, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type SalesOrder, type SalesOrderListItem } from '../services/api'
import SalesOrderDetailCards, { SALES_ORDER_STATUS_COLORS, SALES_ORDER_STATUS_LABELS, SALES_ORDER_TASK_LABELS } from '../components/SalesOrderDetailCards'
import { formatTimestamp } from '../services/time'
import { mergeSalesOrderListItems, salesOrderTaskKey } from '../services/salesOrder'

const PAGE_SIZE = 20
export default function SalesOrderApprovalPage() {
  const [handled, setHandled] = useState(false)
  const [items, setItems] = useState<SalesOrderListItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [selectedKey, setSelectedKey] = useState<string>()
  const [detail, setDetail] = useState<SalesOrder>()
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [decision, setDecision] = useState<'approve' | 'reject'>()
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const listVersion = useRef(0)
  const detailVersion = useRef(0)
  const activePages = useRef(new Set<string>())

  const loadPage = useCallback(async (targetPage: number, replace: boolean, version: number) => {
    const key = `${version}:${targetPage}`; if (activePages.current.has(key)) return
    activePages.current.add(key); setLoading(true); setError('')
    try {
      const result = await api.salesOrderApprovalInbox(handled, { pageNo: targetPage, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(current => replace ? result.list : mergeSalesOrderListItems(current, result.list, salesOrderTaskKey)); setTotal(result.total); setPageNo(targetPage)
      if (replace) setSelectedKey(current => current && result.list.some(item => salesOrderTaskKey(item) === current) ? current : result.list[0] ? salesOrderTaskKey(result.list[0]) : undefined)
    } catch (loadError) { if (version === listVersion.current) { setError(loadError instanceof Error ? loadError.message : '成交审批加载失败'); if (replace) { setItems([]); setSelectedKey(undefined) } } }
    finally { activePages.current.delete(key); if (version === listVersion.current) setLoading(false) }
  }, [handled])
  const reload = useCallback(() => { const version = ++listVersion.current; setItems([]); setTotal(0); setPageNo(1); setSelectedKey(undefined); void loadPage(1, true, version) }, [loadPage])
  useEffect(() => { reload() }, [reload])

  const loadDetail = useCallback(async (id: number) => {
    const version = ++detailVersion.current; setDetailLoading(true); setDetailError('')
    try { const result = await api.salesOrder(id); if (version === detailVersion.current) setDetail(result) }
    catch (loadError) { if (version === detailVersion.current) { setDetail(undefined); setDetailError(loadError instanceof Error ? loadError.message : '订单详情加载失败') } }
    finally { if (version === detailVersion.current) setDetailLoading(false) }
  }, [])
  const selectedItem = useMemo(() => items.find(item => salesOrderTaskKey(item) === selectedKey), [items, selectedKey])
  useEffect(() => { if (selectedItem) void loadDetail(selectedItem.id); else setDetail(undefined) }, [loadDetail, selectedItem])

  const submitDecision = async () => {
    if (!selectedItem?.taskId || !reason.trim() || !decision) { message.warning('请填写审批意见'); return }
    setSaving(true)
    try { await api.decideSalesOrder(selectedItem.id, decision, { taskId: selectedItem.taskId, reason: reason.trim() }); message.success(decision === 'approve' ? '已通过' : '已驳回并退回销售补正'); setDecision(undefined); setReason(''); reload() }
    catch (saveError) { message.error(saveError instanceof Error ? saveError.message : '审批失败') }
    finally { setSaving(false) }
  }
  const detailContent = detailLoading ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => selectedItem && void loadDetail(selectedItem.id)}>重试</Button>}/>
      : detail ? <SalesOrderDetailCards order={detail} approvalContext={selectedItem} mode={handled ? 'approval-done' : 'approval-todo'} onApprove={() => setDecision('approve')} onReject={() => setDecision('reject')}/>
        : <Empty description="从左侧选择一条订单"/>
  const hasMore = items.length < total

  return <section className="workspace-page sales-order-inbox-page">
    <header className="sales-order-inbox-header"><Typography.Title level={3}>成交审批</Typography.Title><Tabs activeKey={handled ? 'done' : 'todo'} onChange={key => setHandled(key === 'done')} items={[{ key: 'todo', label: '待处理' }, { key: 'done', label: '已处理' }]}/><Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button></header>
    {error && <Alert
      className="sales-order-inbox-error" type="error" showIcon message={error}
      action={<Button size="small" onClick={reload}>重试</Button>}/>
    }
    <div className="sales-order-inbox-layout"><aside className="sales-order-list-pane"><div className="sales-order-list-scroll" onScroll={event => { const node = event.currentTarget; if (!loading && hasMore && node.scrollHeight - node.scrollTop - node.clientHeight < 80) void loadPage(pageNo + 1, false, listVersion.current) }}>
      {!loading && !items.length && !error ? <Empty description="暂无成交审批"/> : items.map(item => <button key={salesOrderTaskKey(item)} type="button" className={`sales-order-list-item${salesOrderTaskKey(item) === selectedKey ? ' active' : ''}`} onClick={() => { setSelectedKey(salesOrderTaskKey(item)); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
        <div className="sales-order-list-main"><Avatar>{item.studentName.slice(0, 1)}</Avatar><div className="sales-order-list-copy"><div><strong>{item.studentName}</strong><Tag>{SALES_ORDER_TASK_LABELS[item.taskDefinitionKey || ''] || '成交会签'}</Tag></div><span>{item.orderNo}</span><span>¥{Number(item.totalAmount).toFixed(2)} · <Tag color={SALES_ORDER_STATUS_COLORS[item.status]}>{SALES_ORDER_STATUS_LABELS[item.status]}</Tag></span></div></div>
        <div className="sales-order-list-meta">{formatTimestamp(handled ? item.taskEndTime : item.taskCreateTime || item.submittedAt)}</div>
      </button>)}
      {loading && <div className="sales-order-list-loading"><Spin size="small"/> 加载中</div>}
      {!loading && items.length > 0 && !hasMore && <Typography.Text type="secondary" className="sales-order-list-end">已加载全部 {total} 条审批</Typography.Text>}
    </div></aside><main className="sales-order-detail-pane">{detailContent}</main></div>
    <Drawer className="sales-order-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="成交订单详情" width="100%">{detailContent}</Drawer>
    <Modal title={decision === 'approve' ? '通过成交订单' : '驳回成交订单'} open={Boolean(decision)} confirmLoading={saving} onCancel={() => setDecision(undefined)} onOk={() => void submitDecision()} okText="提交审批"><Input.TextArea rows={5} maxLength={1000} showCount value={reason} onChange={event => setReason(event.target.value)} placeholder="审批意见（必填）"/></Modal>
  </section>
}
