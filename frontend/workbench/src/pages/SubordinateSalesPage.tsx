import { useCallback, useEffect, useState } from 'react'
import {
  Alert, Button, Descriptions, Empty, Form, Input, List, Modal, Pagination, Popover, Select, Skeleton,
  Space, Statistic, Switch, Table, Tabs, Tag, Typography, message
} from 'antd'
import type { ColumnsType, TableRowSelection } from 'antd/es/table/interface'
import { ArrowLeftOutlined, ReloadOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons'
import {
  api, type AssignmentUser, type BusinessTaskBucket, type LeadAppeal, type LeadFollowUp, type ManagedLead,
  type SubordinateBatchResult, type SubordinateSales, type SubordinateTask
} from '../services/api'
import { LEAD_STATUS_LABELS } from '../constants'
import { formatTimestamp } from '../services/time'
import { formatCurrency, receiveStatusLabel, summarizeBatchResult, todayStatusLabel } from '../services/subordinateSales'

const PAGE_SIZE = 20
type ReasonAction = { type: 'account'; sales: SubordinateSales; value: boolean } | { type: 'dispatch'; sales: SubordinateSales; value: boolean }
type BatchAction = 'transfer' | 'publicSea'

function Metrics({ sales }: { sales: SubordinateSales }) {
  const metrics = [
    ['今日待跟进', sales.todayPendingCount], ['首跟超时', sales.firstFollowTimeoutCount],
    ['挂起客资', sales.suspendedLeadCount], ['有效客资', sales.validLeadCount],
    ['成交客资', sales.convertedLeadCount], ['成交订单', sales.effectiveOrderCount]
  ] as const
  return <div className="subordinate-metrics">
    {metrics.map(([label, value]) => <Statistic key={label} title={label} value={value}/>) }
    <Statistic title="成交金额" value={sales.effectiveOrderAmount} formatter={() => formatCurrency(sales.effectiveOrderAmount)}/>
  </div>
}

function CategoryCounts({ sales }: { sales: SubordinateSales }) {
  return <Popover title="客资分类" content={<List size="small" dataSource={sales.categoryCounts}
    renderItem={item => <List.Item><span>{item.label}</span><Typography.Text strong>{item.count}</Typography.Text></List.Item>}/> }>
    <Button type="link" size="small">{sales.categoryCounts.reduce((sum, item) => sum + item.count, 0)} 条</Button>
  </Popover>
}

function BatchResultModal({ result, open, onClose }: { result?: SubordinateBatchResult; open: boolean; onClose: () => void }) {
  return <Modal open={open} title={result ? summarizeBatchResult(result) : '批量结果'} footer={<Button onClick={onClose}>关闭</Button>} onCancel={onClose}>
    <List size="small" dataSource={result?.items || []} renderItem={item => <List.Item>
      <Space><Tag color={item.success ? 'success' : 'error'}>{item.success ? '成功' : '失败'}</Tag>
        <span>客资 #{item.leadId}</span><Typography.Text type={item.success ? undefined : 'danger'}>{item.message}</Typography.Text></Space>
    </List.Item>}/>
  </Modal>
}

function ManagedLeadDetail({ leadId, onBack }: { leadId: number; onBack: () => void }) {
  const [lead, setLead] = useState<ManagedLead>()
  const [followUps, setFollowUps] = useState<LeadFollowUp[]>([])
  const [appeals, setAppeals] = useState<LeadAppeal[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [followUpError, setFollowUpError] = useState('')
  const [appealError, setAppealError] = useState('')
  useEffect(() => {
    if (!leadId) return
    setLoading(true); setError(''); setFollowUpError(''); setAppealError('')
    Promise.allSettled([api.managedLead(leadId), api.leadFollowUpPage(leadId, { pageNo: 1, pageSize: 100 }), api.leadAppeals(leadId)])
      .then(([detail, followUpPage, appealRows]) => {
        if (detail.status === 'fulfilled') setLead(detail.value)
        else setError(detail.reason instanceof Error ? detail.reason.message : '客资详情加载失败')
        if (followUpPage.status === 'fulfilled') setFollowUps(followUpPage.value.list)
        else setFollowUpError(followUpPage.reason instanceof Error ? followUpPage.reason.message : '跟进记录加载失败')
        if (appealRows.status === 'fulfilled') setAppeals(appealRows.value)
        else setAppealError(appealRows.reason instanceof Error ? appealRows.reason.message : '申诉记录加载失败')
      })
      .finally(() => setLoading(false))
  }, [leadId])
  return <div className="subordinate-lead-detail">
    <div className="subordinate-detail-heading">
      <Button icon={<ArrowLeftOutlined/>} onClick={onBack}>返回销售详情</Button>
      <div><Typography.Title level={4}>{lead?.submittedName || '客资详情'}</Typography.Title><Typography.Text type="secondary">客资 #{leadId}</Typography.Text></div>
    </div>
    {error && <Alert type="error" showIcon message={error}/>} 
    {loading ? <Skeleton active/> : lead && <Tabs items={[
      { key: 'overview', label: '概览', children: <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }} items={[
        { key: 'mobile', label: '手机号', children: lead.submittedMobile || '-' },
        { key: 'wechat', label: '微信号', children: lead.submittedWechatId || '-' },
        { key: 'status', label: '客资状态', children: LEAD_STATUS_LABELS[lead.status] || lead.status },
        { key: 'category', label: '客资分类', children: lead.leadCategory || '未配置' },
        { key: 'owner', label: '归属销售', children: lead.ownerUserName || `用户 #${lead.ownerUserId}` },
        { key: 'submitted', label: '提交时间', children: formatTimestamp(lead.submittedAt) },
        { key: 'region', label: '地区', children: [lead.provinceName, lead.cityName].filter(Boolean).join(' / ') || '-' },
        { key: 'remark', label: '备注', children: lead.remark || '-', span: 2 }
      ]}/> },
      { key: 'followUps', label: `跟进记录 ${followUps.length}`, children: <>{followUpError && <Alert type="error" showIcon message={followUpError}/>}<List dataSource={followUps} locale={{ emptyText: <Empty description="暂无跟进记录"/> }} renderItem={item => <List.Item>
        <List.Item.Meta title={<Space><span>{item.operatorName || `用户 #${item.operatorUserId}`}</span><Tag>{item.resultLabel || item.result}</Tag></Space>}
          description={<><div>{item.remark || '-'}</div><Typography.Text type="secondary">{formatTimestamp(item.occurredAt)}</Typography.Text></>}/>
      </List.Item>}/></> },
      { key: 'appeals', label: `申诉记录 ${appeals.length}`, children: <>{appealError && <Alert type="error" showIcon message={appealError}/>}<List dataSource={appeals} locale={{ emptyText: <Empty description="暂无申诉记录"/> }} renderItem={item => <List.Item>
        <List.Item.Meta title={<Space><span>第 {item.roundNo} 轮</span><Tag>{item.status}</Tag></Space>}
          description={<><div>{item.reason}</div><Typography.Text type="secondary">{formatTimestamp(item.submittedAt)}</Typography.Text></>}/>
      </List.Item>}/></> }
    ]}/>} 
  </div>
}

function SalesDetail({ sales, permissions, onBack, onChanged, onReasonAction }: {
  sales: SubordinateSales; permissions: string[]; onBack: () => void; onChanged: () => void; onReasonAction: (action: ReasonAction) => void
}) {
  const [tab, setTab] = useState('overview')
  const [leads, setLeads] = useState<ManagedLead[]>([])
  const [leadTotal, setLeadTotal] = useState(0)
  const [leadPage, setLeadPage] = useState(1)
  const [selected, setSelected] = useState<React.Key[]>([])
  const [tasks, setTasks] = useState<SubordinateTask[]>([])
  const [taskTotal, setTaskTotal] = useState(0)
  const [taskBucket, setTaskBucket] = useState<BusinessTaskBucket>('today')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [batchOpen, setBatchOpen] = useState(false)
  const [batchType, setBatchType] = useState<BatchAction>('transfer')
  const [candidates, setCandidates] = useState<AssignmentUser[]>([])
  const [batchSaving, setBatchSaving] = useState(false)
  const [batchResult, setBatchResult] = useState<SubordinateBatchResult>()
  const [resultOpen, setResultOpen] = useState(false)
  const [detailLeadId, setDetailLeadId] = useState<number>()
  const [form] = Form.useForm()

  const loadLeads = useCallback(async () => {
    setLoading(true); setError('')
    try { const page = await api.subordinateSalesLeads(sales.userId, { pageNo: leadPage, pageSize: PAGE_SIZE }); setLeads(page.list); setLeadTotal(page.total) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '客资加载失败') }
    finally { setLoading(false) }
  }, [leadPage, sales.userId])
  const loadTasks = useCallback(async () => {
    setLoading(true); setError('')
    try { const page = await api.subordinateSalesTasks(sales.userId, { pageNo: 1, pageSize: 100, bucket: taskBucket }); setTasks(page.list); setTaskTotal(page.total) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '待办加载失败') }
    finally { setLoading(false) }
  }, [sales.userId, taskBucket])
  useEffect(() => { setTab('overview'); setSelected([]); setLeadPage(1); setError('') }, [sales?.userId])
  useEffect(() => { if (tab === 'leads') void loadLeads() }, [loadLeads, tab])
  useEffect(() => { if (tab === 'tasks') void loadTasks() }, [loadTasks, tab])

  const openBatch = async (type: BatchAction) => {
    if (!selected.length) { message.warning('请先选择客资'); return }
    setBatchType(type); form.resetFields(); setBatchOpen(true)
    try { setCandidates(await api.subordinateTransferCandidates()) }
    catch (loadError) { message.error(loadError instanceof Error ? loadError.message : '销售候选加载失败') }
  }
  const submitBatch = async () => {
    const values = await form.validateFields()
    setBatchSaving(true)
    try {
      const ids = selected.map(Number)
      const result = batchType === 'transfer'
        ? await api.batchTransferSubordinateLeads(ids, values.targetUserId, values.reason.trim())
        : await api.batchReleaseSubordinateLeads(ids, values.collaboratorUserId, values.reason.trim())
      setBatchResult(result); setResultOpen(true); setBatchOpen(false); setSelected([]); await loadLeads(); onChanged()
    } catch (saveError) { message.error(saveError instanceof Error ? saveError.message : '批量操作失败') }
    finally { setBatchSaving(false) }
  }
  const leadColumns: ColumnsType<ManagedLead> = [
    { title: '姓名', dataIndex: 'submittedName', fixed: 'left', width: 120, render: (value, row) => <Button type="link" onClick={() => setDetailLeadId(row.id)}>{value}</Button> },
    { title: '手机号', dataIndex: 'submittedMobile', width: 130, render: value => value || '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag>{LEAD_STATUS_LABELS[value] || value}</Tag> },
    { title: '分类', dataIndex: 'leadCategory', width: 120, render: value => value || '未配置' },
    { title: '来源', dataIndex: 'sourceChannel', width: 120, render: value => value || '-' },
    { title: '提交时间', dataIndex: 'submittedAt', width: 170, render: value => formatTimestamp(value) }
  ]
  const taskColumns: ColumnsType<SubordinateTask> = [
    { title: '客资', dataIndex: 'leadName', render: (value, row) => value || `客资 #${row.leadId}` },
    { title: '任务类型', dataIndex: 'taskType', width: 180 },
    { title: '截止时间', dataIndex: 'dueAt', width: 180, render: value => formatTimestamp(value, '未排期') },
    { title: '状态', width: 90, render: (_, row) => row.overdue ? <Tag color="error">逾期</Tag> : <Tag color="processing">待处理</Tag> }
  ]
  const rowSelection: TableRowSelection<ManagedLead> = { selectedRowKeys: selected, onChange: setSelected, preserveSelectedRowKeys: true }
  if (detailLeadId) return <ManagedLeadDetail leadId={detailLeadId} onBack={() => setDetailLeadId(undefined)}/>
  return <div className="subordinate-sales-detail">
    <div className="subordinate-detail-heading">
      <Button className="subordinate-mobile-back" icon={<ArrowLeftOutlined/>} onClick={onBack}>返回列表</Button>
      <div className="subordinate-detail-title"><Typography.Title level={4}>{sales.name}</Typography.Title><Typography.Text type="secondary">{sales.username} · {sales.mobile || '未填写手机号'}</Typography.Text></div>
      <Space className="subordinate-detail-controls" wrap>
        <span>账号</span><Switch checkedChildren="启用" unCheckedChildren="停用" checked={sales.accountStatus === 0}
          disabled={!permissions.includes('zsjos:subordinate-sales:account-status')}
          onChange={value => onReasonAction({ type: 'account', sales, value })}/>
        <span>接单</span><Switch checkedChildren="开启" unCheckedChildren="关闭" checked={sales.accepting}
          disabled={!permissions.includes('zsjos:subordinate-sales:dispatch-mode')}
          onChange={value => onReasonAction({ type: 'dispatch', sales, value })}/>
      </Space>
    </div>
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'overview', label: '概览', children: <><Descriptions bordered size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={[
        { key: 'mobile', label: '手机号', children: sales.mobile || '-' },
        { key: 'account', label: '账号状态', children: <Tag color={sales.accountStatus === 0 ? 'success' : 'default'}>{sales.accountStatus === 0 ? '启用' : '停用'}</Tag> },
        { key: 'presence', label: '页面状态', children: sales.presence === 'online' ? '在线' : '离线' },
        { key: 'mode', label: '接单状态', children: sales.accepting ? '开启' : '关闭' },
        { key: 'receive', label: '新客资', children: receiveStatusLabel(sales) },
        { key: 'newcomer', label: '新手池', children: '暂未开放' },
        { key: 'today', label: '今日跟进状态', children: todayStatusLabel(sales.todayFollowUpStatus) },
        { key: 'categories', label: '客资分类', children: <CategoryCounts sales={sales}/> }
      ]}/><Metrics sales={sales}/></> },
      { key: 'leads', label: '名下客资', children: <>
        <div className="subordinate-batch-bar"><Typography.Text>已选 {selected.length} 条</Typography.Text><Space>
          <Button disabled={!selected.length || !permissions.includes('zsjos:subordinate-sales:batch-transfer')} onClick={() => void openBatch('transfer')}>转派</Button>
          <Button disabled={!selected.length || !permissions.includes('zsjos:subordinate-sales:batch-public-sea')} danger onClick={() => void openBatch('publicSea')}>释放到公海</Button>
        </Space></div>
        {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadLeads()}>重试</Button>}/>} 
        <Table rowKey="id" loading={loading} rowSelection={rowSelection} columns={leadColumns} dataSource={leads} scroll={{ x: 820 }} pagination={{ current: leadPage, pageSize: PAGE_SIZE, total: leadTotal, onChange: setLeadPage }}/>
      </> },
      { key: 'tasks', label: '待跟进任务', children: <>
        <Select value={taskBucket} onChange={setTaskBucket} style={{ width: 160, marginBottom: 12 }} options={[
          { value: 'overdue', label: '逾期' }, { value: 'today', label: '今日' }, { value: 'future', label: '未来' }, { value: 'unscheduled', label: '未排期' }
        ]}/>
        {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadTasks()}>重试</Button>}/>} 
        <Table rowKey="id" loading={loading} columns={taskColumns} dataSource={tasks} pagination={false} locale={{ emptyText: <Empty description="暂无待跟进任务"/> }}/>
        {taskTotal > tasks.length && <Typography.Text type="secondary">共 {taskTotal} 条，仅显示前 {tasks.length} 条</Typography.Text>}
      </> }
    ]}/>
    <Modal open={batchOpen} title={batchType === 'transfer' ? '批量转派客资' : '批量释放到公海'} confirmLoading={batchSaving} onOk={() => void submitBatch()} onCancel={() => setBatchOpen(false)}>
      <Form form={form} layout="vertical">
        {batchType === 'transfer' ? <Form.Item name="targetUserId" label="目标销售" rules={[{ required: true, message: '请选择目标销售' }]}><Select showSearch optionFilterProp="label" options={candidates.map(item => ({ value: item.id, label: item.nickname }))}/></Form.Item>
          : <Form.Item name="collaboratorUserId" label="公海跟进销售（可不填）"><Select allowClear showSearch optionFilterProp="label" options={candidates.map(item => ({ value: item.id, label: item.nickname }))}/></Form.Item>}
        <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }, { max: 500 }]}><Input.TextArea rows={4} maxLength={500} showCount/></Form.Item>
      </Form>
    </Modal>
    <BatchResultModal open={resultOpen} result={batchResult} onClose={() => setResultOpen(false)}/>
  </div>
}

export default function SubordinateSalesPage({ permissions }: { permissions: string[] }) {
  const [rows, setRows] = useState<SubordinateSales[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [presence, setPresence] = useState<string>()
  const [accountStatus, setAccountStatus] = useState<number>()
  const [accepting, setAccepting] = useState<boolean>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedSales, setSelectedSales] = useState<SubordinateSales>()
  const [reasonAction, setReasonAction] = useState<ReasonAction>()
  const [reasonSaving, setReasonSaving] = useState(false)
  const [reasonForm] = Form.useForm()
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { const result = await api.subordinateSalesPage({ pageNo: page, pageSize: PAGE_SIZE, keyword: keyword.trim() || undefined, presence, accountStatus, accepting }); setRows(result.list); setTotal(result.total) }
    catch (loadError) { setError(loadError instanceof Error ? loadError.message : '下属销售加载失败') }
    finally { setLoading(false) }
  }, [accepting, accountStatus, keyword, page, presence])
  useEffect(() => { void load() }, [load])
  const submitReasonAction = async () => {
    if (!reasonAction) return
    const values = await reasonForm.validateFields(); setReasonSaving(true)
    try {
      if (reasonAction.type === 'account') await api.updateSubordinateAccountStatus(reasonAction.sales.userId, reasonAction.value ? 0 : 1, values.reason.trim())
      else await api.updateSubordinateDispatchMode(reasonAction.sales.userId, reasonAction.value, values.reason.trim())
      message.success('操作成功'); setReasonAction(undefined); await load()
    } catch (saveError) { message.error(saveError instanceof Error ? saveError.message : '操作失败') }
    finally { setReasonSaving(false) }
  }
  useEffect(() => {
    if (!selectedSales) return
    const current = rows.find(row => row.userId === selectedSales.userId)
    if (current) setSelectedSales(current)
  }, [rows])
  const openReasonAction = (action: ReasonAction) => { reasonForm.resetFields(); setReasonAction(action) }
  return <section className="workspace-page subordinate-sales-page">
    <div className="subordinate-toolbar">
      <Space wrap><Input.Search allowClear placeholder="姓名 / 账号 / 手机号" onSearch={value => { setPage(1); setKeyword(value) }} style={{ width: 260 }}/>
        <Select allowClear placeholder="账号状态" value={accountStatus} onChange={value => { setPage(1); setAccountStatus(value) }} style={{ width: 130 }} options={[{ value: 0, label: '启用' }, { value: 1, label: '停用' }]}/>
        <Select allowClear placeholder="页面状态" value={presence} onChange={value => { setPage(1); setPresence(value) }} style={{ width: 130 }} options={[{ value: 'online', label: '在线' }, { value: 'offline', label: '离线' }]}/>
        <Select allowClear placeholder="接单状态" value={accepting} onChange={value => { setPage(1); setAccepting(value) }} style={{ width: 130 }} options={[{ value: true, label: '开启' }, { value: false, label: '关闭' }]}/></Space>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {error && <Alert className="subordinate-page-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    <div className={`subordinate-inbox-layout ${selectedSales ? 'show-detail' : 'show-list'}`}>
      <aside className="subordinate-sales-list-pane">
        <div className="subordinate-sales-list">
          {loading && !rows.length ? <Skeleton active/> : rows.length ? rows.map(row => <button type="button" key={row.userId}
            className={`subordinate-sales-item ${selectedSales?.userId === row.userId ? 'active' : ''}`} onClick={() => setSelectedSales(row)}>
            <div className="subordinate-sales-item-title"><span className="subordinate-sales-avatar"><UserOutlined/></span><strong>{row.name}</strong>
              <Tag color={row.accountStatus === 0 ? 'success' : 'default'}>{row.accountStatus === 0 ? '启用' : '停用'}</Tag></div>
            <div className="subordinate-sales-item-account">{row.username} · {row.mobile || '未填写手机号'}</div>
            <div className="subordinate-sales-item-status"><Tag color={row.presence === 'online' ? 'success' : 'default'}>{row.presence === 'online' ? '在线' : '离线'}</Tag>
              <Tag color={row.accepting ? 'processing' : 'default'}>{row.accepting ? '接单开启' : '接单关闭'}</Tag>
              <Tag color={row.canReceiveNewLeads ? 'success' : 'default'}>{receiveStatusLabel(row)}</Tag></div>
            <div className="subordinate-sales-item-summary"><span>今日待跟进 <b>{row.todayPendingCount}</b></span><span>{todayStatusLabel(row.todayFollowUpStatus)}</span></div>
            <div className="subordinate-sales-item-summary"><span>有效客资 {row.validLeadCount}</span><span>成交 {row.convertedLeadCount} / {formatCurrency(row.effectiveOrderAmount)}</span></div>
          </button>) : <Empty image={<TeamOutlined/>} description="暂无下属销售"/>}
        </div>
        <div className="subordinate-sales-pagination"><Pagination size="small" current={page} pageSize={PAGE_SIZE} total={total} showSizeChanger={false} onChange={setPage}/></div>
      </aside>
      <main className="subordinate-sales-detail-pane">
        {selectedSales ? <SalesDetail sales={selectedSales} permissions={permissions} onBack={() => setSelectedSales(undefined)} onChanged={() => void load()} onReasonAction={openReasonAction}/>
          : <Empty image={<TeamOutlined/>} description="从左侧选择销售查看详情"/>}
      </main>
    </div>
    <Modal open={Boolean(reasonAction)} title={reasonAction?.type === 'account' ? '修改账号状态' : '修改接单状态'} confirmLoading={reasonSaving} onOk={() => void submitReasonAction()} onCancel={() => setReasonAction(undefined)}>
      <Form form={reasonForm} layout="vertical"><Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }, { max: 500 }]}><Input.TextArea rows={4} maxLength={500} showCount/></Form.Item></Form>
    </Modal>
  </section>
}
