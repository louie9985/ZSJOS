import LeadManagementPage from './LeadManagementPage'
import BusinessTable from '../components/BusinessTable'
import {
  EditOutlined, HistoryOutlined, KeyOutlined, MobileOutlined,
  PlusOutlined, ReloadOutlined, LeftOutlined, RightOutlined, StopOutlined
} from '@ant-design/icons'
import {
  Alert, App, Button, Descriptions, Empty, Form, Input, Modal, Pagination, Segmented, Select,
  Skeleton, Space, Statistic, Tabs, Tag, Tooltip, Typography
} from 'antd'
import { type ProColumns } from '@ant-design/pro-components'
import { useCallback, useEffect, useRef, useState } from 'react'
import SubjectAvatar from '../components/SubjectAvatar'
import DateTimeText from '../components/DateTimeText'
import { InboxAvatarControls, InboxAvatarError, useInboxAvatarRail } from '../components/InboxAvatarRail'
import { type SimpleUser } from '../services/api'
import { managementApi, type Partner, type PartnerCreate, type PartnerOwnershipLog } from '../services/managementApi'
import { formatTimestamp } from '../services/time'
import { hasPermission } from '../services/managementAccess'

const statusLabel = { enabled: '启用', disabled: '停用', converted: '历史已转员工' }
const passwordRule = /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/

export default function SubordinatePartnerPage({ permissions }: { permissions: string[] }) {
  const { message } = App.useApp()
  const canManageAll = hasPermission(permissions, 'zsjos:partner:manage-all')
  const canReadAll = canManageAll || hasPermission(permissions, 'zsjos:partner:query-all')
  const hasExpandedQuery = hasPermission(permissions, 'zsjos:partner:query')
  const rail = useInboxAvatarRail('zsjos.partner.inbox.collapsed')
  const [view, setView] = useState<'inbox' | 'table'>('inbox')
  const [activeTab, setActiveTab] = useState('overview')
  const [partners, setPartners] = useState<Partner[]>([])
  const [selected, setSelected] = useState<Partner>()
  const [keyword, setKeyword] = useState(''), [appliedKeyword, setAppliedKeyword] = useState('')
  const [partnerPage, setPartnerPage] = useState(1), [partnerTotal, setPartnerTotal] = useState(0)
  const [leadTotal, setLeadTotal] = useState(0)
  const [loading, setLoading] = useState(false), [leadLoading, setLeadLoading] = useState(false)
  const [error, setError] = useState(''), [leadError, setLeadError] = useState('')
  const [createOpen, setCreateOpen] = useState(false), [stateChange, setStateChange] = useState<{ row: Partner; enabled: boolean }>()
  const [mobilePartner, setMobilePartner] = useState<Partner>(), [passwordPartner, setPasswordPartner] = useState<Partner>()
  const [assignment, setAssignment] = useState<Partner>(), [assignmentUserId, setAssignmentUserId] = useState<number>()
  const [assignmentReason, setAssignmentReason] = useState(''), [candidates, setCandidates] = useState<SimpleUser[]>([])
  const [assignmentLoading, setAssignmentLoading] = useState(false)
  const [logPartner, setLogPartner] = useState<Partner>(), [assignmentLogs, setAssignmentLogs] = useState<PartnerOwnershipLog[]>([])
  const [logLoading, setLogLoading] = useState(false)
  const [createForm] = Form.useForm<PartnerCreate>(), [stateForm] = Form.useForm<{ reason: string }>()
  const [mobileForm] = Form.useForm<{ mobile: string }>(), [passwordForm] = Form.useForm<{ password: string }>()
  const partnerRequestRef = useRef(0), leadRequestRef = useRef(0)

  const loadPartners = useCallback(async () => {
    const requestId = ++partnerRequestRef.current
    setLoading(true); setError('')
    try {
      const result = await managementApi.partnerPage({ pageNo: partnerPage, pageSize: 20, keyword: appliedKeyword || undefined })
      if (requestId !== partnerRequestRef.current) return
      setPartners(result.list); setPartnerTotal(result.total)
      setSelected(current => current && result.list.some(item => item.id === current.id)
        ? result.list.find(item => item.id === current.id) : undefined)
    } catch (e) {
      if (requestId !== partnerRequestRef.current) return
      setPartners([]); setPartnerTotal(0); setSelected(undefined); setError(e instanceof Error ? e.message : '兼职列表加载失败')
    } finally {
      if (requestId === partnerRequestRef.current) setLoading(false)
    }
  }, [appliedKeyword, partnerPage])

  const loadLeads = useCallback(async () => {
    if (!selected) return
    const requestId = ++leadRequestRef.current
    setLeadLoading(true); setLeadError('')
    try {
      const result = await managementApi.partnerLeads(selected.id, { pageNo: 1, pageSize: 20 })
      if (requestId !== leadRequestRef.current) return
      setLeadTotal(result.total)
    } catch (e) {
      if (requestId !== leadRequestRef.current) return
      setLeadTotal(0); setLeadError(e instanceof Error ? e.message : '兼职客资加载失败')
    } finally {
      if (requestId === leadRequestRef.current) setLeadLoading(false)
    }
  }, [selected])

  useEffect(() => { void loadPartners() }, [loadPartners])
  useEffect(() => {
    ++leadRequestRef.current
    setLeadTotal(0); setLeadError(''); setLeadLoading(false)
  }, [selected?.id])
  useEffect(() => { void loadLeads() }, [loadLeads])
  useEffect(() => () => {
    ++partnerRequestRef.current; ++leadRequestRef.current
  }, [])

  const mutate = async (action: () => Promise<unknown>, success: string) => {
    try { await action(); message.success(success); await loadPartners(); return true }
    catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); return false }
  }
  const submitCreate = async () => {
    const values = await createForm.validateFields()
    if (!await mutate(() => managementApi.createPartner(values), '兼职账号已创建')) return
    setCreateOpen(false); createForm.resetFields()
  }
  const submitState = async () => {
    if (!stateChange) return
    const { reason } = await stateForm.validateFields()
    if (!await mutate(() => managementApi.setPartnerEnabled(stateChange.row.id, stateChange.enabled, reason.trim()), '兼职状态已更新')) return
    setStateChange(undefined); stateForm.resetFields()
  }
  const submitMobile = async () => {
    if (!mobilePartner) return
    const { mobile } = await mobileForm.validateFields()
    if (!await mutate(() => managementApi.updatePartnerMobile(mobilePartner.id, mobile), '登录手机号已更新')) return
    setMobilePartner(undefined); mobileForm.resetFields()
  }
  const submitPassword = async () => {
    if (!passwordPartner) return
    const { password } = await passwordForm.validateFields()
    if (!await mutate(() => managementApi.resetPartnerPassword(passwordPartner.id, password), '登录密码已重置')) return
    setPasswordPartner(undefined); passwordForm.resetFields()
  }
  const openAssignment = async (row: Partner) => {
    setAssignment(row); setAssignmentUserId(row.assignedEmployeeUserId); setAssignmentReason(''); setAssignmentLoading(true)
    try { setCandidates(await managementApi.partnerAssignmentCandidates()) }
    catch (e) { setCandidates([]); message.error(e instanceof Error ? e.message : '候选员工加载失败') }
    finally { setAssignmentLoading(false) }
  }
  const submitAssignment = async () => {
    if (!assignment || !assignmentReason.trim()) return
    setAssignmentLoading(true)
    try {
      await managementApi.updatePartnerAssignment(assignment.id, assignmentUserId, assignmentReason.trim(), assignment.assignmentVersion)
      message.success(assignmentUserId ? '兼职归属已更新' : '兼职归属已解除')
      setAssignment(undefined); await loadPartners()
    } catch (e) { message.error(e instanceof Error ? e.message : '兼职归属更新失败') }
    finally { setAssignmentLoading(false) }
  }
  const openLogs = async (row: Partner) => {
    setLogPartner(row); setLogLoading(true)
    try { setAssignmentLogs((await managementApi.partnerAssignmentLogs(row.id)).list) }
    catch (e) { setAssignmentLogs([]); message.error(e instanceof Error ? e.message : '归属历史加载失败') }
    finally { setLogLoading(false) }
  }

  const selectPartner = (partner: Partner) => {
    setSelected(partner); setActiveTab('overview'); setView('inbox')
  }
  const search = (value: string) => { setPartnerPage(1); setAppliedKeyword(value.trim()) }
  const searchBox = <Input.Search value={keyword} allowClear placeholder="搜索姓名、编号或手机号"
    onChange={e => { setKeyword(e.target.value); if (!e.target.value) search('') }}
    onSearch={() => { setPartnerPage(1); setAppliedKeyword(keyword.trim()) }} />
  const partnerColumns: ProColumns<Partner>[] = [
    { title: '兼职信息', key: 'identity', width: 220, render: (_: unknown, row: Partner) => <Button type="link" className="subordinate-partner-identity" onClick={() => selectPartner(row)}><SubjectAvatar seed={row.partnerNo} label={row.name} size={36}/><span>{row.name}</span></Button> },
    { title: '兼职编号', dataIndex: 'partnerNo', width: 160 },
    { title: '手机号', dataIndex: 'mobile', width: 150 },
    { title: '账号状态', dataIndex: 'status', width: 130, render: (_, row) => <Tag>{statusLabel[row.status]}</Tag> },
    { title: '当前归属', dataIndex: 'assignedEmployeeName', width: 140, render: (_, row) => row.assignedEmployeeName || '未分配' },
    { title: '归属时间', dataIndex: 'assignedAt', width: 160, render: (_, row) => <DateTimeText value={row.assignedAt}/> },
    { title: '操作', key: 'action', width: 110, render: (_: unknown, row: Partner) => <Button type="link" onClick={() => selectPartner(row)}>查看概览</Button> }
  ]
  const leadFailure = leadError && <Alert type="error" showIcon message={leadError} action={<Button size="small" onClick={() => void loadLeads()}>重试</Button>}/>

  return <section className="workspace-page subordinate-partner-page">
    <div className="page-heading">
      <div><Typography.Title level={4}>兼职管理</Typography.Title><Typography.Text type="secondary">{canManageAll ? '管理当前租户全部兼职账号及其提交客资' : canReadAll ? '查看全部兼职及其提交客资' : hasExpandedQuery ? '查看本人及关联运营名下的兼职及其提交客资' : '查看直接归属给我的兼职及其提交客资'}</Typography.Text></div>
      <Space wrap><Segmented value={view} onChange={setView} options={[{ label: '收件箱', value: 'inbox' }, { label: '表格', value: 'table' }]}/>
        <Button aria-label="刷新兼职列表" icon={<ReloadOutlined/>} onClick={() => void loadPartners()}/>
        {canManageAll && <Button type="primary" icon={<PlusOutlined/>} onClick={() => setCreateOpen(true)}>新增兼职</Button>}</Space>
    </div>
    {view === 'table' ? <div className="subordinate-partner-table-area">
      <div className="subordinate-partner-table-toolbar">{searchBox}<Typography.Text type="secondary">共 {partnerTotal} 位兼职</Typography.Text></div>
      {error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void loadPartners()}>重试</Button>}/> : <BusinessTable<Partner> tableKey="subordinate-partner-page-2"
        headerTitle="兼职列表" onReload={() => void loadPartners()}
        columnsState={{ persistenceKey: 'crm-partner-table-columns', persistenceType: 'localStorage' }} rowKey="id" loading={loading} columns={partnerColumns} dataSource={partners} scroll={{ x: 1100 }} locale={{ emptyText: <Empty description="暂无授权范围内的兼职"/> }} pagination={{ current: partnerPage, pageSize: 20, total: partnerTotal, showSizeChanger: false, onChange: setPartnerPage }}/>}
    </div> : <div className={`subordinate-partner-layout${rail.collapsed ? ' is-list-collapsed' : ''}`}>
      <aside className="subordinate-partner-list" aria-label="兼职列表">
        <div className="subordinate-partner-toolbar" ref={rail.filterRef}>
          <InboxAvatarControls label="兼职管理" listId="subordinate-partner-list" collapsed={rail.collapsed} filtered={Boolean(appliedKeyword)} onChange={rail.change}/>
          <div className="advanced-filter-toolbar subordinate-partner-search" hidden={rail.collapsed}>{searchBox}</div>
        </div>
        {error && (rail.collapsed ? <InboxAvatarError message={error} retry={() => void loadPartners()} expand={() => rail.change(false)}/> : <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadPartners()}>重试</Button>}/>)}
        <div id="subordinate-partner-list" className="subordinate-partner-scroll" ref={rail.scrollRef} aria-busy={loading}>
          {loading ? (rail.collapsed ? <Skeleton.Avatar active size={36}/> : <Skeleton active/>) : partners.length ? partners.map(item => <Tooltip key={item.id} title={rail.collapsed ? `${item.name} · ${item.partnerNo}` : undefined} trigger={['hover', 'focus']}>
            <button type="button" className={`subordinate-partner-item${selected?.id === item.id ? ' active' : ''}`} aria-label={`${item.name} · ${item.partnerNo}`} aria-current={selected?.id === item.id ? 'true' : undefined} onClick={() => selectPartner(item)}>
              <SubjectAvatar seed={item.partnerNo} label={item.name} size={36}/>
              <span className="subordinate-partner-item-copy"><span className="subordinate-partner-item-title"><strong>{item.name}</strong><Tag>{statusLabel[item.status]}</Tag></span><span>{item.partnerNo}</span><span>{item.mobile || '无手机号'} · {item.assignedEmployeeName || '未分配'}</span></span>
            </button>
          </Tooltip>) : !error && (rail.collapsed ? <span role="status" className="subordinate-partner-empty">暂无兼职</span> : <Empty description="暂无授权范围内的兼职"/>)}
        </div>
        {partnerTotal > 20 && (rail.collapsed ? <nav className="subordinate-partner-pagination" aria-label="兼职分页">
          <Button aria-label="上一页兼职" icon={<LeftOutlined/>} disabled={loading || partnerPage <= 1} onClick={() => setPartnerPage(partnerPage - 1)}/>
          <span>{partnerPage}/{Math.ceil(partnerTotal / 20)}</span>
          <Button aria-label="下一页兼职" icon={<RightOutlined/>} disabled={loading || partnerPage >= Math.ceil(partnerTotal / 20)} onClick={() => setPartnerPage(partnerPage + 1)}/>
        </nav> : <Pagination simple current={partnerPage} pageSize={20} total={partnerTotal} disabled={loading} showSizeChanger={false} onChange={setPartnerPage}/>)}
      </aside>
      <main className="subordinate-partner-leads">
        {!selected ? <Empty description="请选择兼职查看概览与客资"/> : <>
          <div className="subordinate-partner-detail-heading">
            <div className="subordinate-partner-identity"><SubjectAvatar seed={selected.partnerNo} label={selected.name} size={48}/><div><Typography.Title level={4}>{selected.name} <Tag>{statusLabel[selected.status]}</Tag></Typography.Title><Typography.Text type="secondary">{selected.partnerNo} · {selected.mobile || '无手机号'}</Typography.Text></div></div>
            <Space wrap>
              <Button aria-label="刷新客资" icon={<ReloadOutlined/>} onClick={() => void loadLeads()}/>
              {canManageAll && <><Button icon={<MobileOutlined/>} onClick={() => { setMobilePartner(selected); mobileForm.setFieldsValue({ mobile: selected.mobile }) }}>手机号</Button>
                <Button icon={<KeyOutlined/>} onClick={() => setPasswordPartner(selected)}>密码</Button>
                <Button icon={<EditOutlined/>} onClick={() => void openAssignment(selected)}>归属</Button>
                <Button icon={<HistoryOutlined/>} onClick={() => void openLogs(selected)}>归属历史</Button>
                {selected.status !== 'converted' && <Button danger={selected.status === 'enabled'} icon={<StopOutlined/>} onClick={() => { setStateChange({ row: selected, enabled: selected.status === 'disabled' }); stateForm.resetFields() }}>{selected.status === 'disabled' ? '启用' : '停用'}</Button>}</>}
            </Space>
          </div>
          <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
            { key: 'overview', label: '概览', children: <div className="subordinate-partner-overview-grid">
              <section className="subordinate-partner-overview-main"><Typography.Title level={5}>客资统计</Typography.Title>
                {leadFailure || (leadLoading ? <Skeleton active/> : <><div className="subordinate-partner-stats">
                  <Statistic title="累计提交客资" value={leadTotal} suffix="条"/>
                </div><Typography.Paragraph type="secondary">统计该兼职提交的全部客资，不受明细分页影响。</Typography.Paragraph><Button onClick={() => setActiveTab('leads')}>查看客资明细</Button></>)}
              </section>
              <aside className="subordinate-partner-overview-aside"><Typography.Title level={5}>兼职信息</Typography.Title><Descriptions column={1} size="small" layout="vertical" items={[
                { key: 'owner', label: '当前归属', children: selected.assignedEmployeeName || '未分配' },
                { key: 'assigned', label: '归属时间', children: <DateTimeText value={selected.assignedAt}/> },
                { key: 'enabled', label: '启用时间', children: <DateTimeText value={selected.enabledAt}/> },
                { key: 'disabled', label: '停用时间', children: <DateTimeText value={selected.disabledAt}/> }
              ]}/></aside>
            </div> },
            { key: 'leads', label: '客资明细', children: activeTab === 'leads' ? <LeadManagementPage key={selected.id} permissions={permissions} partnerId={selected.id}/> : null }
          ]}/>
        </>}
      </main>
    </div>}

    <Modal title="新增兼职账号" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => void submitCreate()}><Form form={createForm} layout="vertical"><Form.Item name="partnerNo" label="兼职编号" rules={[{ required: true }]}><Input/></Form.Item><Form.Item name="name" label="姓名" rules={[{ required: true }]}><Input/></Form.Item><Form.Item name="mobile" label="手机号" rules={[{ required: true }, { pattern: /^1\d{10}$/, message: '请输入正确的手机号' }]}><Input maxLength={11}/></Form.Item><Form.Item name="password" label="初始密码" rules={[{ required: true }, { pattern: passwordRule, message: '8-20 位且包含字母和数字' }]}><Input.Password/></Form.Item><Form.Item name="channelId" label="渠道编号"><Input/></Form.Item></Form></Modal>
    <Modal title={stateChange?.enabled ? '启用兼职' : '停用兼职'} open={Boolean(stateChange)} onCancel={() => setStateChange(undefined)} onOk={() => void submitState()}><Form form={stateForm} layout="vertical"><Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={4} maxLength={500} showCount/></Form.Item></Form></Modal>
    <Modal title={`修改登录手机号${mobilePartner ? ` · ${mobilePartner.name}` : ''}`} open={Boolean(mobilePartner)} onCancel={() => setMobilePartner(undefined)} onOk={() => void submitMobile()}><Form form={mobileForm} layout="vertical"><Form.Item name="mobile" label="新手机号" rules={[{ required: true }, { pattern: /^1\d{10}$/, message: '请输入正确的手机号' }]}><Input maxLength={11}/></Form.Item></Form></Modal>
    <Modal title={`重置登录密码${passwordPartner ? ` · ${passwordPartner.name}` : ''}`} open={Boolean(passwordPartner)} onCancel={() => setPasswordPartner(undefined)} onOk={() => void submitPassword()}><Form form={passwordForm} layout="vertical"><Form.Item name="password" label="新密码" rules={[{ required: true }, { pattern: passwordRule, message: '8-20 位且包含字母和数字' }]}><Input.Password/></Form.Item></Form></Modal>
    <Modal title={`设置兼职归属${assignment ? ` · ${assignment.name}` : ''}`} open={Boolean(assignment)} confirmLoading={assignmentLoading} onCancel={() => setAssignment(undefined)} onOk={() => void submitAssignment()} okButtonProps={{ disabled: !assignmentReason.trim() }}><Form layout="vertical"><Form.Item label="归属员工"><Select allowClear loading={assignmentLoading} value={assignmentUserId} onChange={setAssignmentUserId} placeholder="留空表示解除归属" options={candidates.map(user => ({ value: user.id, label: user.nickname }))}/></Form.Item><Form.Item label="调整原因" required><Input.TextArea rows={4} maxLength={500} showCount value={assignmentReason} onChange={e => setAssignmentReason(e.target.value)}/></Form.Item></Form></Modal>
    <Modal title={`兼职归属历史${logPartner ? ` · ${logPartner.name}` : ''}`} open={Boolean(logPartner)} footer={null} width={760} onCancel={() => setLogPartner(undefined)}><BusinessTable<PartnerOwnershipLog> tableKey="subordinate-partner-page-3" mode="compact"     rowKey="id" size="small" loading={logLoading} pagination={false} dataSource={assignmentLogs} columns={[{ title: '变更前', dataIndex: 'previousEmployeeName', render: (_, row) => row.previousEmployeeName || '未分配' }, { title: '变更后', dataIndex: 'employeeName', render: (_, row) => row.employeeName || '未分配' }, { title: '原因', dataIndex: 'reason' }, { title: '操作人', dataIndex: 'operatorName' }, { title: '时间', dataIndex: 'occurredAt', render: (_, row) => formatTimestamp(row.occurredAt) }]}/></Modal>
  </section>
}
