import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Badge, Button, Drawer, Empty, Input, Modal, Skeleton, Spin, Tabs, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type AdvancedFilterGroup, type SalesOrder, type SalesOrderApprovalFilterProfile, type SalesOrderListItem } from '../services/api'
import { AdvancedFilterToolbar, filterCount } from '../components/AdvancedFilter'
import SalesOrderDetailCards, { SALES_ORDER_STATUS_COLORS, SALES_ORDER_STATUS_LABELS, SALES_ORDER_TASK_LABELS } from '../components/SalesOrderDetailCards'
import { formatTimestamp } from '../services/time'
import { mergeSalesOrderListItems, salesOrderTaskKey } from '../services/salesOrder'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'

const PAGE_SIZE = 20

export default function SalesOrderApprovalPage() {
  const [profile, setProfile] = useState<SalesOrderApprovalFilterProfile>({ groups: [], centers: [] })
  const [center, setCenter] = useState<'registration' | 'finance'>()
  const [groupKey, setGroupKey] = useState<string>()
  const [optionKey, setOptionKey] = useState('all')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [items, setItems] = useState<SalesOrderListItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [selectedKey, setSelectedKey] = useState<string>()
  const [detail, setDetail] = useState<SalesOrder>()
  const [loading, setLoading] = useState(false)
  const [profileLoading, setProfileLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [profileError, setProfileError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [decision, setDecision] = useState<'approve' | 'reject'>()
  const [reason, setReason] = useState('')
  const { submitting: saving, run: runDecision, resetIntent } = useSubmissionGuard()
  const requestVersion = useRef(0)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const closeDecision = () => { setConfirmOpen(false); setDecision(undefined); resetIntent() }

  const loadProfile = useCallback(async () => {
    setProfileLoading(true); setProfileError('')
    try {
      const next = await api.salesOrderApprovalFilterProfile()
      setProfile(next)
      setCenter(current => current && next.centers.some(item => item.key === current) ? current : next.centers[0]?.key)
      setGroupKey(current => current && next.groups.some(group => group.key === current) ? current : next.groups[0]?.key)
    } catch (loadError) { setProfileError(loadError instanceof Error ? loadError.message : '审批筛选方案加载失败') }
    finally { setProfileLoading(false) }
  }, [])

  const loadPage = useCallback(async (targetPage: number, replace: boolean) => {
    const version = ++requestVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.salesOrderApprovalInbox({ pageNo: targetPage, pageSize: PAGE_SIZE, center, groupKey, optionKey, keyword: keyword.trim() || undefined, advancedFilter })
      if (version !== requestVersion.current) return
      setItems(current => replace ? result.list : mergeSalesOrderListItems(current, result.list, salesOrderTaskKey)); setTotal(result.total); setPageNo(targetPage)
      if (replace) setSelectedKey(current => current && result.list.some(item => salesOrderTaskKey(item) === current) ? current : result.list[0] ? salesOrderTaskKey(result.list[0]) : undefined)
    } catch (loadError) {
      if (version === requestVersion.current) setError(loadError instanceof Error ? loadError.message : '成交审批加载失败')
    } finally { if (version === requestVersion.current) setLoading(false) }
  }, [advancedFilter, center, groupKey, keyword, optionKey])

  useEffect(() => { void loadProfile() }, [loadProfile])
  useEffect(() => { if (!profileLoading && !profileError && groupKey && center) void loadPage(1, true) }, [center, groupKey, optionKey, keyword, profileLoading, profileError, loadPage])

  const selectedItem = useMemo(() => items.find(item => salesOrderTaskKey(item) === selectedKey), [items, selectedKey])
  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true); setDetailError('')
    try { setDetail(await api.salesOrder(id)) }
    catch (loadError) { setDetail(undefined); setDetailError(loadError instanceof Error ? loadError.message : '订单详情加载失败') }
    finally { setDetailLoading(false) }
  }, [])
  useEffect(() => { if (selectedItem) void loadDetail(selectedItem.id); else setDetail(undefined) }, [loadDetail, selectedItem])

  const reload = () => { void loadProfile(); if (groupKey && center) void loadPage(1, true) }
  const submitDecision = async () => {
    setConfirmOpen(false)
    if (!selectedItem?.taskId || !decision) return
    const order = selectedItem
    const nextDecision = decision
    await runDecision(async ({ complete, idempotencyKey }) => {
      if (!detail) return
      await api.decideSalesOrder(order.id, nextDecision, { taskId: order.taskId!, reason: reason.trim(),
        approvalRoundId: detail.currentApprovalRoundId, orderVersion: detail.version,
        roundVersion: detail.approvalRoundVersion, idempotencyKey })
      complete(); message.success(nextDecision === 'approve' ? '已通过' : '已驳回并退回销售补正');
      setConfirmOpen(false); setDecision(undefined); setReason(''); reload()
    }).catch(saveError => message.error(saveError instanceof Error ? saveError.message : '审批失败'))
  }
  const prepareDecision = () => {
    if (!selectedItem?.taskId || !reason.trim() || !decision) { message.warning('请填写审批意见'); return }
    setConfirmOpen(true)
  }
  const detailContent = detailLoading ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => selectedItem && void loadDetail(selectedItem.id)}>重试</Button>}/>
      : detail ? <SalesOrderDetailCards order={detail} approvalContext={selectedItem} mode={groupKey === 'done' ? 'approval-done' : 'approval-todo'}
        onApprove={() => { resetIntent(); setDecision('approve') }} onReject={() => { resetIntent(); setDecision('reject') }}/>
        : <Empty description="从左侧选择一条订单"/>
  const hasMore = items.length < total

  return <section className="workspace-page lead-management-page sales-order-approval-page">
    {filterCount(advancedFilter) === 0 && <header className="lead-inbox-filter-shell">
      {profileError && <Alert className="lead-inbox-metadata-error" type="warning" showIcon message={profileError} action={<Button type="link" size="small" onClick={() => void loadProfile()}>重试</Button>}/>}
      {profileLoading ? <Skeleton active title={false} paragraph={{ rows: 2 }}/> : profile.groups.length > 0 ? <>
        {profile.centers.length > 1 && <Tabs className="lead-inbox-center-tabs" activeKey={center} onChange={key => { setCenter(key as 'registration' | 'finance'); setOptionKey('all') }} items={profile.centers.map(item => ({ key: item.key, label: item.label }))}/>}
        <Tabs className="lead-inbox-group-tabs" activeKey={groupKey} onChange={key => { setGroupKey(key); setOptionKey('all') }} items={profile.groups.map(group => ({ key: group.key, label: group.label }))}/>
      </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用审批筛选配置"/>}
    </header>}
    <div className="lead-inbox-layout"><aside className="lead-inbox-list-pane"><div className="lead-inbox-toolbar"><AdvancedFilterToolbar scene="order" placeholder="搜索订单号 / 学员姓名 / 手机号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
      {error && <Alert className="lead-list-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadPage(1, true)}>重试</Button>}/>}
      <div className="lead-inbox-scroll" onScroll={event => { const node = event.currentTarget; if (!loading && hasMore && node.scrollHeight - node.scrollTop - node.clientHeight < 80) void loadPage(pageNo + 1, false) }}>
        {!loading && !items.length && !error ? <Empty description="暂无成交审批"/> : items.map(item => <button key={salesOrderTaskKey(item)} type="button" className={salesOrderTaskKey(item) === selectedKey ? 'lead-inbox-item active' : 'lead-inbox-item'} onClick={() => { setSelectedKey(salesOrderTaskKey(item)); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
          <div className="lead-inbox-item-main"><Avatar>{item.studentName.slice(0, 1)}</Avatar><div className="lead-inbox-item-copy"><div className="lead-inbox-item-title"><strong>{item.studentName}</strong><Tag>{SALES_ORDER_TASK_LABELS[item.taskDefinitionKey || ''] || '成交会签'}</Tag></div><span>{item.orderNo}</span><span>¥{Number(item.totalAmount).toFixed(2)} · <Tag color={SALES_ORDER_STATUS_COLORS[item.status]}>{SALES_ORDER_STATUS_LABELS[item.status]}</Tag></span></div></div>
          <div className="lead-inbox-item-meta"><Badge status="processing"/><span>{formatTimestamp(groupKey === 'done' ? item.taskEndTime : item.taskCreateTime || item.submittedAt)}</span></div>
        </button>)}
        {loading && <div className="lead-list-loading"><Spin size="small"/> 加载中</div>}
        {!loading && items.length > 0 && !hasMore && <Typography.Text type="secondary" className="lead-list-end">已加载全部 {total} 条审批</Typography.Text>}
      </div>
    </aside><main className="lead-inbox-detail-pane">{detailContent}</main></div>
    <Drawer className="sales-order-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="成交订单详情" width="100%">{detailContent}</Drawer>
    <Modal title={decision === 'approve' ? '通过成交订单' : '驳回成交订单'} open={Boolean(decision)} onCancel={closeDecision} footer={<><Button onClick={closeDecision}>取消</Button><IrreversiblePopconfirm action={`${decision === 'approve' ? '通过' : '驳回'}成交订单「${selectedItem?.orderNo || ''}」`} danger={decision === 'reject'} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submitDecision}><Button type="primary" danger={decision === 'reject'} loading={saving} onClick={prepareDecision}>提交审批</Button></IrreversiblePopconfirm></>}><div><Typography.Text strong>审批意见</Typography.Text><Input.TextArea rows={5} maxLength={1000} showCount value={reason} onChange={event => setReason(event.target.value)} placeholder="填写审批意见（必填）"/></div></Modal>
  </section>
}
