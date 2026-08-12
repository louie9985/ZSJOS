import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Button, Drawer, Empty, Input, Skeleton, Spin, Tabs, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type SalesOrder, type SalesOrderListItem, type SalesOrderStatusCounts } from '../services/api'
import SalesOrderDetailCards, { SALES_ORDER_STATUS_COLORS, SALES_ORDER_STATUS_LABELS } from '../components/SalesOrderDetailCards'
import SalesOrderEntryModal, { type SalesOrderEntryLead } from '../components/SalesOrderEntryModal'
import { formatTimestamp } from '../services/time'
import { mergeSalesOrderListItems } from '../services/salesOrder'

const PAGE_SIZE = 20
type StatusTab = 'all' | SalesOrder['status']
const emptyCounts: SalesOrderStatusCounts = { total: 0, pendingApproval: 0, revisionRequired: 0, effective: 0 }

export default function MySalesOrderPage() {
  const [status, setStatus] = useState<StatusTab>('all')
  const [keyword, setKeyword] = useState('')
  const [items, setItems] = useState<SalesOrderListItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [selectedId, setSelectedId] = useState<number>()
  const [detail, setDetail] = useState<SalesOrder>()
  const [counts, setCounts] = useState(emptyCounts)
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [countsError, setCountsError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [revisionOpen, setRevisionOpen] = useState(false)
  const listVersion = useRef(0)
  const detailVersion = useRef(0)
  const activePages = useRef(new Set<string>())

  const loadCounts = useCallback(async () => {
    setCountsError('')
    try { setCounts(await api.mySalesOrderStatusCounts()) }
    catch (loadError) { setCountsError(loadError instanceof Error ? loadError.message : '订单数量加载失败') }
  }, [])

  const loadPage = useCallback(async (targetPage: number, replace: boolean, version: number) => {
    const key = `${version}:${targetPage}`
    if (activePages.current.has(key)) return
    activePages.current.add(key); setLoading(true); setError('')
    try {
      const result = await api.mySalesOrderPage({ pageNo: targetPage, pageSize: PAGE_SIZE, status: status === 'all' ? undefined : status, keyword: keyword || undefined })
      if (version !== listVersion.current) return
      setItems(current => replace ? result.list : mergeSalesOrderListItems(current, result.list)); setTotal(result.total); setPageNo(targetPage)
      if (replace) setSelectedId(current => current && result.list.some(item => item.id === current) ? current : result.list[0]?.id)
    } catch (loadError) {
      if (version === listVersion.current) { setError(loadError instanceof Error ? loadError.message : '我的订单加载失败'); if (replace) { setItems([]); setSelectedId(undefined) } }
    } finally { activePages.current.delete(key); if (version === listVersion.current) setLoading(false) }
  }, [keyword, status])

  const reload = useCallback(() => {
    const version = ++listVersion.current
    setItems([]); setTotal(0); setPageNo(1); setSelectedId(undefined)
    void Promise.all([loadPage(1, true, version), loadCounts()])
  }, [loadCounts, loadPage])

  useEffect(() => { reload() }, [reload])

  const loadDetail = useCallback(async (id: number) => {
    const version = ++detailVersion.current
    setDetailLoading(true); setDetailError('')
    try { const result = await api.mySalesOrder(id); if (version === detailVersion.current) setDetail(result) }
    catch (loadError) { if (version === detailVersion.current) { setDetail(undefined); setDetailError(loadError instanceof Error ? loadError.message : '订单详情加载失败') } }
    finally { if (version === detailVersion.current) setDetailLoading(false) }
  }, [])
  useEffect(() => { if (selectedId) void loadDetail(selectedId); else setDetail(undefined) }, [loadDetail, selectedId])

  const selectedItem = useMemo(() => items.find(item => item.id === selectedId), [items, selectedId])
  const detailContent = detailLoading ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail ? <SalesOrderDetailCards order={detail} approvalContext={selectedItem} mode="mine" onRevise={() => setRevisionOpen(true)}/>
        : <Empty description="从左侧选择一条订单"/>
  const hasMore = items.length < total
  const revisionLead: SalesOrderEntryLead | undefined = detail ? {
    id: detail.leadId, submittedName: detail.studentName, submittedMobile: detail.studentMobile, submittedWechatId: detail.studentWechatId,
    provinceCode: detail.provinceCode, provinceName: detail.provinceName, cityCode: detail.cityCode, cityName: detail.cityName,
    primaryProduct: detail.items[0] ? { spuRef: detail.items[0].productRef, skuRef: detail.items[0].skuRef } : undefined
  } : undefined

  return <section className="workspace-page sales-order-inbox-page">
    <header className="sales-order-inbox-header">
      <div><Typography.Title level={3}>我的订单</Typography.Title><Typography.Text type="secondary">查看本人提交的全部成交订单及当前状态</Typography.Text></div>
      <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </header>
    {countsError && <Alert
      className="sales-order-inbox-error" type="warning" showIcon message={countsError}
      action={<Button size="small" onClick={() => void loadCounts()}>重试</Button>}/>
    }
    <Tabs activeKey={status} onChange={key => setStatus(key as StatusTab)} items={[
      { key: 'all', label: `全部 ${counts.total}` }, { key: 'pending_approval', label: `待审核 ${counts.pendingApproval}` },
      { key: 'revision_required', label: `已驳回待修改 ${counts.revisionRequired}` }, { key: 'effective', label: `已通过 ${counts.effective}` }
    ]}/>
    <div className="sales-order-inbox-layout">
      <aside className="sales-order-list-pane">
        <Input.Search allowClear placeholder="搜索订单号 / 学员姓名 / 手机号" onSearch={value => setKeyword(value.trim())}/>
        {error && <Alert
          className="sales-order-inbox-error" type="error" showIcon message={error}
          action={<Button size="small" onClick={reload}>重试</Button>}/>
        }
        <div className="sales-order-list-scroll" onScroll={event => { const node = event.currentTarget; if (!loading && hasMore && node.scrollHeight - node.scrollTop - node.clientHeight < 80) void loadPage(pageNo + 1, false, listVersion.current) }}>
          {!loading && !items.length && !error ? <Empty description="暂无订单"/> : items.map(item => <button key={item.id} type="button" className={`sales-order-list-item${item.id === selectedId ? ' active' : ''}`} onClick={() => { setSelectedId(item.id); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
            <div className="sales-order-list-main"><Avatar>{item.studentName.slice(0, 1)}</Avatar><div className="sales-order-list-copy"><div><strong>{item.studentName}</strong><Tag color={SALES_ORDER_STATUS_COLORS[item.status]}>{SALES_ORDER_STATUS_LABELS[item.status]}</Tag></div><span>{item.orderNo}</span><span>¥{Number(item.totalAmount).toFixed(2)} · 第 {item.approvalRoundNo || 1} 轮</span></div></div>
            <div className="sales-order-list-meta">{formatTimestamp(item.submittedAt)}</div>
          </button>)}
          {loading && <div className="sales-order-list-loading"><Spin size="small"/> 加载中</div>}
          {!loading && items.length > 0 && !hasMore && <Typography.Text type="secondary" className="sales-order-list-end">已加载全部 {total} 条订单</Typography.Text>}
        </div>
      </aside>
      <main className="sales-order-detail-pane">{detailContent}</main>
    </div>
    <Drawer className="sales-order-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="订单详情" width="100%">{detailContent}</Drawer>
    {revisionLead && <SalesOrderEntryModal lead={revisionLead} orderId={detail?.id} open={revisionOpen} onClose={() => setRevisionOpen(false)}
      onSubmitted={id => { setRevisionOpen(false); reload(); setSelectedId(id) }}/>}
  </section>
}
