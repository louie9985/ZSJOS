import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Button, Empty, Form, Input, Modal, Skeleton, Spin, Tabs, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type AdvancedFilterGroup, type SalesOrder, type SalesOrderListItem, type SalesOrderStatusCounts } from '../services/api'
import { AdvancedFilterToolbar, filterCount } from '../components/AdvancedFilter'
import SalesOrderDetailCards, { SALES_ORDER_STATUS_COLORS, SALES_ORDER_STATUS_LABELS } from '../components/SalesOrderDetailCards'
import SalesOrderEntryModal, { type SalesOrderEntryLead } from '../components/SalesOrderEntryModal'
import { formatTimestamp } from '../services/time'
import { mergeSalesOrderListItems, salesOrderDetailToListItem } from '../services/salesOrder'
import { useSubmissionGuard } from '../services/submissionGuard'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable } from '@ant-design/pro-components'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

const PAGE_SIZE = 20
type StatusTab = 'all' | SalesOrder['status']
const emptyCounts: SalesOrderStatusCounts = { total: 0, pendingApproval: 0, revisionRequired: 0, effective: 0, superseded: 0 }

export default function MySalesOrderPage({ team = false }: { team?: boolean }) {
  const navigate = useNavigate()
  const requestedOrderId = useRef(Number(new URLSearchParams(location.search).get('orderId')) || undefined)
  const [status, setStatus] = useState<StatusTab>('all')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [items, setItems] = useState<SalesOrderListItem[]>([])
  const [cursor, setCursor] = useState<string>()
  const [hasMore, setHasMore] = useState(true)
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
  const [terminateOpen, setTerminateOpen] = useState(false)
  const [terminationReason, setTerminationReason] = useState('')
  const { useTableLayout } = useInboxTableLayout()
  const { submitting: terminating, run: runTermination, resetIntent: resetTerminationIntent } = useSubmissionGuard()
  const listVersion = useRef(0)
  const detailVersion = useRef(0)
  const activePages = useRef(new Set<string>())

  const loadCounts = useCallback(async () => {
    setCountsError('')
    try { setCounts(await (team ? api.teamSalesOrderStatusCounts() : api.mySalesOrderStatusCounts())) }
    catch (loadError) { setCountsError(loadError instanceof Error ? loadError.message : '订单数量加载失败') }
  }, [team])

  const loadPage = useCallback(async (targetCursor: string | undefined, replace: boolean, version: number) => {
    const key = `${version}:${targetCursor || 'first'}`
    if (activePages.current.has(key)) return
    activePages.current.add(key); setLoading(true); setError('')
    try {
      const result = await (team ? api.teamSalesOrderCursor({ cursor: targetCursor, limit: PAGE_SIZE, status: status === 'all' ? undefined : status, keyword: keyword || undefined, advancedFilter }) : api.mySalesOrderCursor({ cursor: targetCursor, limit: PAGE_SIZE, status: status === 'all' ? undefined : status, keyword: keyword || undefined, advancedFilter }))
      if (version !== listVersion.current) return
      let nextItems = result.list
      const requestedId = replace ? requestedOrderId.current : undefined
      if (requestedId && !result.list.some(item => item.id === requestedId)) {
        const requestedOrder = await (team ? api.salesOrder(requestedId) : api.mySalesOrder(requestedId))
        if (version !== listVersion.current) return
        nextItems = [salesOrderDetailToListItem(requestedOrder), ...result.list]
      }
      setItems(current => replace ? nextItems : mergeSalesOrderListItems(current, nextItems)); setCursor(result.nextCursor); setHasMore(result.hasMore)
      if (replace) {
        setSelectedId(current => requestedId || (current && nextItems.some(item => item.id === current) ? current : nextItems[0]?.id))
        if (requestedId) {
          requestedOrderId.current = undefined
          window.history.replaceState({}, '', `${window.location.pathname}${window.location.hash}`)
        }
      }
    } catch (loadError) {
      if (version === listVersion.current) setError(loadError instanceof Error ? loadError.message : '我的订单加载失败')
    } finally { activePages.current.delete(key); if (version === listVersion.current) setLoading(false) }
  }, [advancedFilter, keyword, status, team])

  const reload = useCallback(() => {
    const version = ++listVersion.current
    setCursor(undefined); setHasMore(true)
    void Promise.all([loadPage(undefined, true, version), loadCounts()])
  }, [loadCounts, loadPage])

  useEffect(() => { reload() }, [reload])

  const loadDetail = useCallback(async (id: number) => {
    const version = ++detailVersion.current
    setDetailLoading(true); setDetailError('')
    try { const result = await (team ? api.salesOrder(id) : api.mySalesOrder(id)); if (version === detailVersion.current) setDetail(result) }
    catch (loadError) { if (version === detailVersion.current) { setDetail(undefined); setDetailError(loadError instanceof Error ? loadError.message : '订单详情加载失败') } }
    finally { if (version === detailVersion.current) setDetailLoading(false) }
  }, [team])
  useEffect(() => { if (selectedId) void loadDetail(selectedId); else setDetail(undefined) }, [loadDetail, selectedId])

  const selectedItem = useMemo(() => items.find(item => item.id === selectedId), [items, selectedId])
  const detailContent = detailLoading ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail ? <><div className="sales-order-detail-actions">{detail.supersedesOrderId && <Button onClick={() => setSelectedId(detail.supersedesOrderId)}>查看原订单</Button>}{detail.supersededByOrderId && <Button type="primary" onClick={() => setSelectedId(detail.supersededByOrderId)}>查看重提订单</Button>}{detail.leadId && <Button onClick={() => navigate(`${APP_ROUTES.LEAD_MANAGEMENT}?leadId=${detail.leadId}`)}>客户档案</Button>}</div>
        <SalesOrderDetailCards order={detail} approvalContext={selectedItem} mode={team ? "team" : "mine"}
          onRevise={team ? undefined : () => setRevisionOpen(true)}
          onTerminate={team ? undefined : () => { resetTerminationIntent(); setTerminateOpen(true) }}/></>
        : <Empty description="从左侧选择一条订单"/>
  const revisionLead: SalesOrderEntryLead | undefined = detail ? {
    id: detail.leadId || 0, submittedName: detail.studentName, submittedMobile: detail.studentMobile, submittedWechatId: detail.studentWechatId,
    provinceCode: detail.provinceCode, provinceName: detail.provinceName, cityCode: detail.cityCode, cityName: detail.cityName,
    primaryProduct: detail.items[0] ? { spuRef: detail.items[0].productRef, skuRef: detail.items[0].skuRef } : undefined
  } : undefined

  return <section className={`workspace-page sales-order-inbox-page${useTableLayout ? ' sales-order-table-page' : ''}`}>
    <div className="sales-order-inbox-actions">
      {filterCount(advancedFilter) === 0 && <Tabs activeKey={status} onChange={key => setStatus(key as StatusTab)} items={[
        { key: 'all', label: `全部 ${counts.total}` }, { key: 'pending_approval', label: `待审核 ${counts.pendingApproval}` },
        { key: 'revision_required', label: `已驳回待修改 ${counts.revisionRequired}` }, { key: 'effective', label: `已通过 ${counts.effective}` }, { key: 'superseded', label: `已被重提 ${counts.superseded}` }
      ]}/>}<Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </div>
    {countsError && <Alert
      className="sales-order-inbox-error" type="warning" showIcon message={countsError}
      action={<Button size="small" onClick={() => void loadCounts()}>重试</Button>}/>
    }
    {useTableLayout ? <ProTable<SalesOrderListItem>
      className="sales-order-inbox-table"
      rowKey="id"
      search={false}
      options={{ density: true, fullScreen: true, setting: true }}
      columnsState={{ persistenceKey: `crm-sales-order-${team ? 'team' : 'my'}-table-columns`, persistenceType: 'localStorage' }}
      loading={loading}
      dataSource={items}
      pagination={false}
      scroll={{ x: 980 }}
      locale={{ emptyText: <Empty description="暂无订单" /> }}
      columns={[
        { title: '订单号', dataIndex: 'orderNo', width: 170 },
        { title: '学员', dataIndex: 'studentName', width: 140 },
        { title: '金额', dataIndex: 'totalAmount', render: value => `¥${Number(value).toFixed(2)}` },
        { title: '状态', dataIndex: 'status', render: value => <Tag color={SALES_ORDER_STATUS_COLORS[value as SalesOrder['status']]}>{SALES_ORDER_STATUS_LABELS[value as SalesOrder['status']] || value}</Tag> },
        { title: '提交时间', dataIndex: 'submittedAt', render: (_, item) => formatTimestamp(item.submittedAt), width: 170 },
        { title: '操作', key: 'action', width: 88, fixed: 'right', render: (_, item) => <Button type="link" onClick={() => { setSelectedId(item.id); if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>详细</Button> }
      ]}
    /> : <div className="sales-order-inbox-layout">
      <aside className="sales-order-list-pane">
        <AdvancedFilterToolbar scene="order" pageKey={team ? "sales_order_team" : "sales_order_my"} placeholder="搜索订单号 / 学员姓名 / 手机号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/>
        {error && <Alert
          className="sales-order-inbox-error" type="error" showIcon message={error}
          action={<Button size="small" onClick={reload}>重试</Button>}/>
        }
        <div className="sales-order-list-scroll" onScroll={event => { const node = event.currentTarget; if (!loading && hasMore && cursor && node.scrollHeight - node.scrollTop - node.clientHeight < 80) void loadPage(cursor, false, listVersion.current) }}>
          {!loading && !items.length && !error ? <Empty description="暂无订单"/> : items.map(item => <button key={item.id} type="button" className={`sales-order-list-item${item.id === selectedId ? ' active' : ''}`} onClick={() => { setSelectedId(item.id); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
            <div className="sales-order-list-main"><Avatar>{item.studentName.slice(0, 1)}</Avatar><div className="sales-order-list-copy"><div><strong>{item.studentName}</strong><Tag color={SALES_ORDER_STATUS_COLORS[item.status]}>{SALES_ORDER_STATUS_LABELS[item.status]}</Tag></div><span>{item.orderNo}</span><span>¥{Number(item.totalAmount).toFixed(2)} · 第 {item.approvalRoundNo || 1} 轮</span></div></div>
            <div className="sales-order-list-meta">{formatTimestamp(item.submittedAt)}</div>
          </button>)}
          {loading && <div className="sales-order-list-loading"><Spin size="small"/> 加载中</div>}
          {!loading && items.length > 0 && !hasMore && <Typography.Text type="secondary" className="sales-order-list-end">已加载全部订单</Typography.Text>}
        </div>
      </aside>
      <main className="sales-order-detail-pane">{detailContent}</main>
    </div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="sales-order-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="订单详情" placement="right" width="100%">{detailContent}</ResizableDetailDrawer>
    {revisionLead && <SalesOrderEntryModal lead={revisionLead} orderId={detail?.id} open={revisionOpen} onClose={() => setRevisionOpen(false)}
      onSubmitted={id => { setRevisionOpen(false); reload(); setSelectedId(id) }}/>}<Modal title="终止订单审批" open={terminateOpen}
      onCancel={() => { setTerminateOpen(false); setTerminationReason(''); resetTerminationIntent() }} okText="确认终止"
      onOk={async () => { if (!detail || !terminationReason.trim()) { message.warning('请填写终止原因'); return }
        await runTermination(async ({ complete, idempotencyKey }) => {
          await api.terminateSalesOrder(detail.id, { reason: terminationReason.trim(), approvalRoundId: detail.currentApprovalRoundId,
            orderVersion: detail.version, roundVersion: detail.approvalRoundVersion, idempotencyKey })
          complete(); message.success('订单审批已终止'); setTerminateOpen(false); setTerminationReason(''); reload()
        }).catch(error => message.error(error instanceof Error ? error.message : '终止失败')) }}
      confirmLoading={terminating} okButtonProps={{ danger: true, disabled: terminating }}>
      <Form.Item label="终止原因" required><Input.TextArea rows={4} maxLength={1000} showCount value={terminationReason} onChange={event => setTerminationReason(event.target.value)} placeholder="填写终止原因"/></Form.Item>
    </Modal>
  </section>
}
