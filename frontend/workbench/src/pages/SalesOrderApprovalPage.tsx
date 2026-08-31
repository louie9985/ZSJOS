import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Badge, Button, Empty, Form, Input, Modal, Result, Segmented, Skeleton, Spin, Tabs, Tag, Typography, message } from 'antd'
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, type AdvancedFilterGroup, type SalesOrder, type SalesOrderApprovalFilterProfile, type SalesOrderListItem } from '../services/api'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import SalesOrderDetailCards, { SALES_ORDER_STATUS_COLORS, SALES_ORDER_STATUS_LABELS, SALES_ORDER_TASK_LABELS } from '../components/SalesOrderDetailCards'
import { formatTimestamp } from '../services/time'
import { mergeSalesOrderListItems, salesOrderDetailToListItem, salesOrderTaskKey } from '../services/salesOrder'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from '../components/IrreversiblePopconfirm'
import SalesOrderSupervisorInbox from '../components/SalesOrderSupervisorInbox'
import { resolveSalesOrderApprovalAccess, type SalesOrderApprovalWorkType } from '../services/salesOrderApprovalAccess'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable } from '@ant-design/pro-components'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'
import { buildSalesOrderTableColumns } from '../components/SalesOrderTableColumns'

const PAGE_SIZE = 20

export default function SalesOrderApprovalPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { canReview, canConfirmSupervisor, defaultWorkType, showWorkTypeSwitch } = resolveSalesOrderApprovalAccess(permissions)
  const requestedWorkTypeValue = searchParams.get('workType')
  const requestedWorkType: SalesOrderApprovalWorkType | undefined = requestedWorkTypeValue === 'approval' || requestedWorkTypeValue === 'supervisor'
    ? requestedWorkTypeValue : undefined
  const requestedOrderId = Number(searchParams.get('orderId')) || undefined
  const requestedTaskId = searchParams.get('taskId') || undefined
  const requestedConfirmationId = Number(searchParams.get('confirmationId')) || undefined
  const [workType, setWorkType] = useState<SalesOrderApprovalWorkType>(requestedWorkType || defaultWorkType || 'approval')
  const [profile, setProfile] = useState<SalesOrderApprovalFilterProfile>({ groups: [], centers: [] })
  const [center, setCenter] = useState<'registration' | 'finance'>()
  const [groupKey, setGroupKey] = useState<string>()
  const [optionKey, setOptionKey] = useState('all')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [items, setItems] = useState<SalesOrderListItem[]>([])
  const [cursor, setCursor] = useState<string>()
  const [hasMore, setHasMore] = useState(true)
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
  const [supervisorOpen, setSupervisorOpen] = useState(false)
  const [reason, setReason] = useState('')
  const { submitting: saving, run: runDecision, resetIntent } = useSubmissionGuard()
  const requestVersion = useRef(0)
  const detailVersion = useRef(0)
  const deepLinkVersion = useRef(0)
  const inflightPages = useRef(new Set<string>())
  const profileRequest = useRef<Promise<void> | undefined>(undefined)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const { useTableLayout } = useInboxTableLayout()
  const closeDecision = () => { setConfirmOpen(false); setDecision(undefined); resetIntent() }

  const loadProfile = useCallback(() => {
    if (!canReview) { setProfileLoading(false); return Promise.resolve() }
    if (profileRequest.current) return profileRequest.current
    const request = (async () => {
      setProfileLoading(true); setProfileError('')
      try {
        const next = await api.salesOrderApprovalFilterProfile()
        setProfile(next)
        setCenter(current => current && next.centers.some(item => item.key === current) ? current : next.centers[0]?.key)
        setGroupKey(current => current && next.groups.some(group => group.key === current) ? current : next.groups[0]?.key)
      } catch (loadError) { setProfileError(loadError instanceof Error ? loadError.message : '审批筛选方案加载失败') }
      finally { setProfileLoading(false) }
    })()
    profileRequest.current = request
    void request.finally(() => { if (profileRequest.current === request) profileRequest.current = undefined })
    return request
  }, [canReview])

  const loadPage = useCallback(async (targetCursor: string | undefined, replace: boolean) => {
    const requestKey = `${targetCursor || 'first'}:${center}:${groupKey}:${optionKey}:${keyword.trim()}:${JSON.stringify(advancedFilter)}`
    if (inflightPages.current.has(requestKey)) return
    inflightPages.current.add(requestKey)
    const version = ++requestVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.salesOrderApprovalCursor({ cursor: targetCursor, limit: PAGE_SIZE, center, groupKey, optionKey, keyword: keyword.trim() || undefined, advancedFilter })
      if (version !== requestVersion.current) return
      setItems(current => replace ? result.list : mergeSalesOrderListItems(current, result.list, salesOrderTaskKey)); setCursor(result.nextCursor); setHasMore(result.hasMore)
      if (replace) setSelectedKey(current => current && result.list.some(item => salesOrderTaskKey(item) === current) ? current : result.list[0] ? salesOrderTaskKey(result.list[0]) : undefined)
    } catch (loadError) {
      if (version === requestVersion.current) setError(loadError instanceof Error ? loadError.message : '成交审批加载失败')
    } finally {
      inflightPages.current.delete(requestKey)
      if (version === requestVersion.current) setLoading(false)
    }
  }, [advancedFilter, center, groupKey, keyword, optionKey])

  useEffect(() => { void loadProfile() }, [loadProfile])
  useEffect(() => {
    if (requestedWorkType) setWorkType(requestedWorkType)
  }, [requestedWorkType])
  useEffect(() => {
    if (workType === 'approval' && !canReview && canConfirmSupervisor) setWorkType('supervisor')
    if (workType === 'supervisor' && !canConfirmSupervisor && canReview) setWorkType('approval')
  }, [canConfirmSupervisor, canReview, workType])
  useEffect(() => { if (!profileLoading && !profileError && groupKey && center) void loadPage(undefined, true) }, [center, groupKey, optionKey, keyword, profileLoading, profileError, loadPage])

  useEffect(() => {
    if (workType !== 'approval' || !requestedOrderId) return
    const version = ++deepLinkVersion.current
    const targetRequest = requestedTaskId && !requestedConfirmationId
      ? api.salesOrderApprovalTaskTarget(requestedTaskId) : Promise.resolve(undefined)
    void Promise.all([api.salesOrder(requestedOrderId), targetRequest]).then(([order, target]) => {
      if (version !== deepLinkVersion.current) return
      const item = { ...salesOrderDetailToListItem(order), taskId: target?.taskId || requestedTaskId,
        taskDefinitionKey: target?.taskDefinitionKey || order.supervisorApproval?.taskDefinitionKey }
      setItems(current => mergeSalesOrderListItems([item], current, salesOrderTaskKey))
      ++detailVersion.current
      setSelectedKey(salesOrderTaskKey(item)); setDetail(order)
      if (target) setCenter(target.center)
    }).catch(loadError => {
      if (version !== deepLinkVersion.current) return
      ++detailVersion.current; setSelectedKey(undefined); setDetail(undefined)
      setDetailError(loadError instanceof Error ? loadError.message : '审批任务定位失败')
    })
  }, [requestedConfirmationId, requestedOrderId, requestedTaskId, workType])

  const selectedItem = useMemo(() => items.find(item => salesOrderTaskKey(item) === selectedKey), [items, selectedKey])
  const loadDetail = useCallback(async (id: number) => {
    const version = ++detailVersion.current
    setDetailLoading(true); setDetailError('')
    try { const order = await api.salesOrder(id); if (version === detailVersion.current) setDetail(order) }
    catch (loadError) { if (version === detailVersion.current) { setDetail(undefined); setDetailError(loadError instanceof Error ? loadError.message : '订单详情加载失败') } }
    finally { if (version === detailVersion.current) setDetailLoading(false) }
  }, [])
  useEffect(() => {
    if (selectedItem && detail?.id !== selectedItem.id) void loadDetail(selectedItem.id)
    else if (!selectedItem) setDetail(undefined)
  }, [detail?.id, loadDetail, selectedItem])

  const reload = () => { void loadProfile(); if (selectedItem) void loadDetail(selectedItem.id) }
  const exportFinanceOrders = () => Modal.confirm({
    title: '导出财务订单台账', content: '将导出符合当前关键字和高级筛选条件的全部记录。', okText: '加入导出队列',
    onOk: async () => {
      try {
        await api.createExportTask('finance_order', { keyword: keyword.trim() || undefined, advancedFilter })
        message.success({ content: <span>已加入导出队列 <Button type="link" size="small" onClick={() => navigate(APP_ROUTES.EXPORT_TASKS)}>查看导出任务</Button></span>, duration: 5 })
      } catch (exportError) {
        message.error(exportError instanceof Error ? exportError.message : '导出任务创建失败')
        throw exportError
      }
    }
  })
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
  const submitSupervisorRequest = async () => {
    if (!selectedItem?.taskId || !detail || !reason.trim()) { message.warning('请填写申请原因'); return }
    await runDecision(async ({ complete, idempotencyKey }) => {
      await api.requestSalesOrderSupervisor(detail.id, { taskId: selectedItem.taskId!, reason: reason.trim(),
        approvalRoundId: detail.currentApprovalRoundId, orderVersion: detail.version,
        roundVersion: detail.approvalRoundVersion, idempotencyKey })
      complete(); setSupervisorOpen(false); setReason(''); message.success('已申请主管确认'); reload()
    }).catch(saveError => message.error(saveError instanceof Error ? saveError.message : '申请失败'))
  }
  const detailContent = detailLoading ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => selectedItem && void loadDetail(selectedItem.id)}>重试</Button>}/>
      : detail ? <><div className="sales-order-detail-actions">{detail.leadId && <Button onClick={() => {
        const returnTo = `${location.pathname}${location.search}`
        navigate(`${APP_ROUTES.LEAD_MANAGEMENT}?leadId=${detail.leadId}&returnTo=${encodeURIComponent(returnTo)}`)
      }}>客户档案</Button>}</div>
        <SalesOrderDetailCards order={detail} approvalContext={selectedItem} mode={groupKey === 'done' ? 'approval-done' : 'approval-todo'}
          onApprove={() => { resetIntent(); setDecision('approve') }} onReject={() => { resetIntent(); setDecision('reject') }}
          onRequestSupervisor={() => { resetIntent(); setReason(''); setSupervisorOpen(true) }}/></>
        : <Empty description="从左侧选择一条订单"/>
  const workTypeSwitch = showWorkTypeSwitch ? <Segmented
    aria-label="成交审批任务类型"
    value={workType}
    onChange={value => setWorkType(value as SalesOrderApprovalWorkType)}
    options={[{ label: '订单审批', value: 'approval' }, { label: '主管确认', value: 'supervisor' }]}
  /> : null
  if (!canReview && !canConfirmSupervisor) return <Result status="403" title="无权访问成交订单审批"/>
  if (workType === 'supervisor') return <SalesOrderSupervisorInbox scopeControl={workTypeSwitch}
    requestedConfirmationId={requestedConfirmationId} requestedOrderId={requestedOrderId}/>
  return <section className={`workspace-page business-inbox-page sales-order-approval-page${useTableLayout ? ' business-inbox-table-page' : ''}`}>
    <header className="business-inbox-scope-bar">
      <div className="business-inbox-scope-row">{workTypeSwitch}<Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button></div>
      <div className="business-inbox-scope-tabs">
        {profileError && <Alert className="lead-inbox-metadata-error" type="warning" showIcon message={profileError} action={<Button type="link" size="small" onClick={() => void loadProfile()}>重试</Button>}/>}
        {profileLoading ? <Skeleton active title={false} paragraph={{ rows: 2 }}/> : profile.groups.length > 0 ? <>
          {profile.centers.length > 1 && <Tabs className="lead-inbox-center-tabs" activeKey={center} onChange={key => { setCenter(key as 'registration' | 'finance'); setOptionKey('all') }} items={profile.centers.map(item => ({ key: item.key, label: item.label }))}/>}
          <Tabs className="lead-inbox-group-tabs" activeKey={groupKey} onChange={key => { setGroupKey(key); setOptionKey('all') }} items={profile.groups.map(group => ({ key: group.key, label: group.label }))}/>
        </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用审批筛选配置"/>}
      </div>
    </header>
    {useTableLayout ? <ProTable<SalesOrderListItem> className="business-inbox-table" rowKey={salesOrderTaskKey} search={false} options={{ density: true, fullScreen: true, setting: true }} columnsState={{ persistenceKey: 'crm-sales-order-approval-table-columns', persistenceType: 'localStorage' }} loading={loading} dataSource={items} pagination={false} scroll={{ x: 6200 }} columns={buildSalesOrderTableColumns(item => { setSelectedKey(salesOrderTaskKey(item)); if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) })} /> : <div className="business-inbox-layout"><aside className="business-inbox-list-pane"><div className="business-inbox-toolbar"><AdvancedFilterToolbar scene="order" pageKey={center ? `sales_order_approval:${center}` : undefined} placeholder="搜索订单号 / 学员姓名 / 手机号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/>{center === 'finance' && permissions.includes('zsjos:export:finance-order') && <Button icon={<DownloadOutlined/>} onClick={exportFinanceOrders}>导出</Button>}</div>
      {error && <Alert className="business-inbox-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadPage(undefined, true)}>重试</Button>}/>}
      <div className="business-inbox-scroll" onScroll={event => { const node = event.currentTarget; if (!loading && hasMore && cursor && node.scrollHeight - node.scrollTop - node.clientHeight < 80) void loadPage(cursor, false) }}>
        {!loading && !items.length && !error ? <Empty description="暂无成交审批"/> : items.map(item => <button key={salesOrderTaskKey(item)} type="button" className={salesOrderTaskKey(item) === selectedKey ? 'business-inbox-item active' : 'business-inbox-item'} onClick={() => { setSelectedKey(salesOrderTaskKey(item)); if (window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true) }}>
          <div className="business-inbox-item-main"><Avatar>{item.studentName.slice(0, 1)}</Avatar><div className="business-inbox-item-copy"><div className="business-inbox-item-title"><strong>{item.studentName}</strong><Tag>{SALES_ORDER_TASK_LABELS[item.taskDefinitionKey || ''] || '成交审批'}</Tag></div><span>{item.orderNo}</span><span>¥{Number(item.totalAmount).toFixed(2)} · <Tag color={SALES_ORDER_STATUS_COLORS[item.status]}>{SALES_ORDER_STATUS_LABELS[item.status]}</Tag></span></div></div>
          <div className="business-inbox-item-meta"><Badge status="processing"/><span>{formatTimestamp(groupKey === 'done' ? item.taskEndTime : item.taskCreateTime || item.submittedAt)}</span></div>
        </button>)}
        {loading && <div className="lead-list-loading"><Spin size="small"/> 加载中</div>}
        {!loading && items.length > 0 && !hasMore && <Typography.Text type="secondary" className="lead-list-end">已加载全部审批</Typography.Text>}
      </div>
    </aside><main className="business-inbox-detail-pane">{detailContent}</main></div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="business-inbox-mobile-drawer sales-order-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="成交订单详情" placement="right" width="100%">{detailContent}</ResizableDetailDrawer>
    <Modal title={decision === 'approve' ? '通过成交订单' : '驳回成交订单'} open={Boolean(decision)} onCancel={closeDecision} footer={<><Button onClick={closeDecision}>取消</Button><IrreversiblePopconfirm action={`${decision === 'approve' ? '通过' : '驳回'}成交订单「${selectedItem?.orderNo || ''}」`} danger={decision === 'reject'} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submitDecision}><Button type="primary" danger={decision === 'reject'} loading={saving} onClick={prepareDecision}>提交审批</Button></IrreversiblePopconfirm></>}><Form.Item label="审批意见" required><Input.TextArea rows={5} maxLength={1000} showCount value={reason} onChange={event => setReason(event.target.value)} placeholder="填写审批意见"/></Form.Item></Modal>
    <Modal title="申请主管确认" open={supervisorOpen} onCancel={() => { setSupervisorOpen(false); setReason(''); resetIntent() }}
      onOk={() => void submitSupervisorRequest()} confirmLoading={saving} okText="提交申请">
      <Form.Item label="申请原因" required><Input.TextArea rows={5} maxLength={1000} showCount value={reason}
        onChange={event => setReason(event.target.value)} placeholder="说明需要主管确认的事项"/></Form.Item>
    </Modal>
  </section>
}
