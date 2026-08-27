import {
  ArrowLeftOutlined, EditOutlined, HistoryOutlined, KeyOutlined, MobileOutlined,
  PlusOutlined, ReloadOutlined, SearchOutlined, StopOutlined
} from '@ant-design/icons'
import {
  Alert, App, Button, Empty, Form, Input, List, Modal, Pagination, Select,
  Skeleton, Space, Table, Tag, Typography
} from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import LeadDetail from '../components/LeadDetail'
import { DICT_TYPE } from '../constants'
import { api, type DictData, type ManagedLead, type SimpleUser } from '../services/api'
import { managementApi, type Partner, type PartnerCreate, type PartnerOwnershipLog } from '../services/managementApi'
import { dictionaryDisplayLabel } from '../services/leadManagement'
import { formatTimestamp } from '../services/time'
import { hasPermission } from '../services/managementAccess'

const statusLabel = { enabled: '启用', disabled: '停用', converted: '历史已转员工' }
const passwordRule = /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/

export default function SubordinatePartnerPage({ permissions }: { permissions: string[] }) {
  const { message } = App.useApp()
  const canManage = hasPermission(permissions, 'zsjos:partner:manage')
  const [partners, setPartners] = useState<Partner[]>([])
  const [selected, setSelected] = useState<Partner>()
  const [leads, setLeads] = useState<ManagedLead[]>([])
  const [detail, setDetail] = useState<ManagedLead>()
  const [keyword, setKeyword] = useState(''), [appliedKeyword, setAppliedKeyword] = useState('')
  const [partnerPage, setPartnerPage] = useState(1), [partnerTotal, setPartnerTotal] = useState(0)
  const [leadPage, setLeadPage] = useState(1), [leadTotal, setLeadTotal] = useState(0)
  const [loading, setLoading] = useState(false), [leadLoading, setLeadLoading] = useState(false)
  const [error, setError] = useState(''), [leadError, setLeadError] = useState('')
  const [detailId, setDetailId] = useState<number>()
  const [detailLoading, setDetailLoading] = useState(false), [detailError, setDetailError] = useState('')
  const [categories, setCategories] = useState<DictData[]>([]), [channels, setChannels] = useState<DictData[]>([])
  const [createOpen, setCreateOpen] = useState(false), [stateChange, setStateChange] = useState<{ row: Partner; enabled: boolean }>()
  const [mobilePartner, setMobilePartner] = useState<Partner>(), [passwordPartner, setPasswordPartner] = useState<Partner>()
  const [assignment, setAssignment] = useState<Partner>(), [assignmentUserId, setAssignmentUserId] = useState<number>()
  const [assignmentReason, setAssignmentReason] = useState(''), [candidates, setCandidates] = useState<SimpleUser[]>([])
  const [assignmentLoading, setAssignmentLoading] = useState(false)
  const [logPartner, setLogPartner] = useState<Partner>(), [assignmentLogs, setAssignmentLogs] = useState<PartnerOwnershipLog[]>([])
  const [logLoading, setLogLoading] = useState(false)
  const [createForm] = Form.useForm<PartnerCreate>(), [stateForm] = Form.useForm<{ reason: string }>()
  const [mobileForm] = Form.useForm<{ mobile: string }>(), [passwordForm] = Form.useForm<{ password: string }>()
  const partnerRequestRef = useRef(0), leadRequestRef = useRef(0), detailRequestRef = useRef(0)

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
      setPartners([]); setPartnerTotal(0); setError(e instanceof Error ? e.message : '兼职列表加载失败')
    } finally {
      if (requestId === partnerRequestRef.current) setLoading(false)
    }
  }, [appliedKeyword, partnerPage])

  const loadLeads = useCallback(async () => {
    if (!selected) return
    const requestId = ++leadRequestRef.current
    setLeadLoading(true); setLeadError('')
    try {
      const result = await managementApi.partnerLeads(selected.id, { pageNo: leadPage, pageSize: 20 })
      if (requestId !== leadRequestRef.current) return
      setLeads(result.list); setLeadTotal(result.total)
    } catch (e) {
      if (requestId !== leadRequestRef.current) return
      setLeads([]); setLeadTotal(0); setLeadError(e instanceof Error ? e.message : '兼职客资加载失败')
    } finally {
      if (requestId === leadRequestRef.current) setLeadLoading(false)
    }
  }, [leadPage, selected])

  useEffect(() => { void loadPartners() }, [loadPartners])
  useEffect(() => {
    ++leadRequestRef.current; ++detailRequestRef.current
    setLeads([]); setLeadTotal(0); setLeadError(''); setLeadLoading(false)
    setDetail(undefined); setDetailId(undefined); setDetailError(''); setDetailLoading(false)
  }, [selected?.id])
  useEffect(() => { void loadLeads() }, [loadLeads])
  useEffect(() => { void Promise.allSettled([
    api.dictDataByType(DICT_TYPE.LEAD_CATEGORY).then(setCategories),
    api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL).then(setChannels)
  ]) }, [])
  useEffect(() => () => {
    ++partnerRequestRef.current; ++leadRequestRef.current; ++detailRequestRef.current
  }, [])

  const openLead = async (id: number) => {
    const requestId = ++detailRequestRef.current
    setDetailId(id); setDetail(undefined); setDetailLoading(true); setDetailError('')
    try {
      const result = await managementApi.partnerLead(id)
      if (requestId === detailRequestRef.current) setDetail(result)
    } catch (e) {
      if (requestId === detailRequestRef.current) setDetailError(e instanceof Error ? e.message : '客资详情加载失败')
    } finally {
      if (requestId === detailRequestRef.current) setDetailLoading(false)
    }
  }

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

  if (detailId !== undefined) return <section className="workspace-page subordinate-partner-page">
    <Space align="start"><Button icon={<ArrowLeftOutlined/>} onClick={() => setDetailId(undefined)}>返回兼职客资</Button>{detail && <div><Typography.Title level={4}>{detail.submittedName}</Typography.Title><Typography.Text type="secondary">{detail.leadNo}</Typography.Text></div>}</Space>
    {detailError ? <Alert type="error" showIcon message={detailError} action={<Button size="small" onClick={() => void openLead(detailId)}>重试</Button>}/>
      : detailLoading || !detail ? <Skeleton active/>
      : <LeadDetail lead={detail} categories={categories}
        categoryLabel={value => dictionaryDisplayLabel(categories, value, false)}
        channelLabel={value => dictionaryDisplayLabel(channels, value, false)}
        mode="manager-readonly" autoExpandFollowUp={false} onDirtyChange={() => undefined}
        onChanged={() => void openLead(detail.id)}/>}
  </section>

  return <section className="workspace-page subordinate-partner-page">
    <div className="page-heading">
      <div><Typography.Title level={4}>兼职管理</Typography.Title><Typography.Text type="secondary">{canManage ? '管理全部兼职账号及其提交客资' : '查看归属给我的兼职及其全部提交客资'}</Typography.Text></div>
      {canManage && <Button type="primary" icon={<PlusOutlined/>} onClick={() => setCreateOpen(true)}>新增兼职</Button>}
    </div>
    <div className="subordinate-partner-layout">
      <aside className="subordinate-partner-list">
        <Space.Compact block><Input value={keyword} allowClear placeholder="搜索姓名、编号或手机号" onChange={e => setKeyword(e.target.value)} onPressEnter={() => { setPartnerPage(1); setAppliedKeyword(keyword.trim()) }}/><Button title="搜索" icon={<SearchOutlined/>} onClick={() => { setPartnerPage(1); setAppliedKeyword(keyword.trim()) }}/></Space.Compact>
        {error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadPartners()}>重试</Button>}/>
          : loading ? <Skeleton active/> : partners.length ? <><List dataSource={partners} renderItem={item => <List.Item className={selected?.id === item.id ? 'is-selected' : ''} onClick={() => { setSelected(item); setLeadPage(1) }}>
            <List.Item.Meta title={<Space>{item.name}<Tag>{statusLabel[item.status]}</Tag></Space>} description={`${item.partnerNo}${item.mobile ? ` · ${item.mobile}` : ''}`}/>
          </List.Item>}/><Pagination simple current={partnerPage} pageSize={20} total={partnerTotal} onChange={setPartnerPage}/></> : <Empty description="暂无可查看兼职"/>}
      </aside>
      <main className="subordinate-partner-leads">
        {!selected ? <Empty description="请选择兼职查看账号与客资"/> : <>
          <div className="subordinate-partner-detail-heading">
            <div><Typography.Title level={4}>{selected.name}</Typography.Title><Typography.Text type="secondary">{selected.partnerNo} · {selected.mobile} · 当前归属：{selected.assignedEmployeeName || '未分配'}</Typography.Text></div>
            <Space wrap>
              <Button title="刷新客资" icon={<ReloadOutlined/>} onClick={() => void loadLeads()}/>
              {canManage && <><Button icon={<MobileOutlined/>} onClick={() => { setMobilePartner(selected); mobileForm.setFieldsValue({ mobile: selected.mobile }) }}>手机号</Button>
                <Button icon={<KeyOutlined/>} onClick={() => setPasswordPartner(selected)}>密码</Button>
                <Button icon={<EditOutlined/>} onClick={() => void openAssignment(selected)}>归属</Button>
                <Button icon={<HistoryOutlined/>} onClick={() => void openLogs(selected)}>归属历史</Button>
                {selected.status !== 'converted' && <Button danger={selected.status === 'enabled'} icon={<StopOutlined/>} onClick={() => { setStateChange({ row: selected, enabled: selected.status === 'disabled' }); stateForm.resetFields() }}>{selected.status === 'disabled' ? '启用' : '停用'}</Button>}</>}
            </Space>
          </div>
          {leadError ? <Alert type="error" showIcon message={leadError} action={<Button size="small" onClick={() => void loadLeads()}>重试</Button>}/>
            : leadLoading ? <Skeleton active/> : leads.length ? <><List dataSource={leads} renderItem={lead => <List.Item actions={[<Button key="detail" type="link" onClick={() => void openLead(lead.id)}>查看</Button>]}>
              <List.Item.Meta title={<Space><span>{lead.submittedName}</span><Typography.Text code>{lead.leadNo}</Typography.Text></Space>} description={`提交时归属：${lead.partnerOwnerNameSnapshot || '未记录'} · ${lead.ownerUserName || '待分配销售'}`}/>
            </List.Item>}/><Pagination current={leadPage} pageSize={20} total={leadTotal} showSizeChanger={false} onChange={setLeadPage}/></> : <Empty description="该兼职暂无客资"/>}
        </>}
      </main>
    </div>

    <Modal title="新增兼职账号" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => void submitCreate()}><Form form={createForm} layout="vertical"><Form.Item name="partnerNo" label="兼职编号" rules={[{ required: true }]}><Input/></Form.Item><Form.Item name="name" label="姓名" rules={[{ required: true }]}><Input/></Form.Item><Form.Item name="mobile" label="手机号" rules={[{ required: true }, { pattern: /^1\d{10}$/, message: '请输入正确的手机号' }]}><Input maxLength={11}/></Form.Item><Form.Item name="password" label="初始密码" rules={[{ required: true }, { pattern: passwordRule, message: '8-20 位且包含字母和数字' }]}><Input.Password/></Form.Item><Form.Item name="channelId" label="渠道编号"><Input/></Form.Item></Form></Modal>
    <Modal title={stateChange?.enabled ? '启用兼职' : '停用兼职'} open={Boolean(stateChange)} onCancel={() => setStateChange(undefined)} onOk={() => void submitState()}><Form form={stateForm} layout="vertical"><Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={4} maxLength={500} showCount/></Form.Item></Form></Modal>
    <Modal title={`修改登录手机号${mobilePartner ? ` · ${mobilePartner.name}` : ''}`} open={Boolean(mobilePartner)} onCancel={() => setMobilePartner(undefined)} onOk={() => void submitMobile()}><Form form={mobileForm} layout="vertical"><Form.Item name="mobile" label="新手机号" rules={[{ required: true }, { pattern: /^1\d{10}$/, message: '请输入正确的手机号' }]}><Input maxLength={11}/></Form.Item></Form></Modal>
    <Modal title={`重置登录密码${passwordPartner ? ` · ${passwordPartner.name}` : ''}`} open={Boolean(passwordPartner)} onCancel={() => setPasswordPartner(undefined)} onOk={() => void submitPassword()}><Form form={passwordForm} layout="vertical"><Form.Item name="password" label="新密码" rules={[{ required: true }, { pattern: passwordRule, message: '8-20 位且包含字母和数字' }]}><Input.Password/></Form.Item></Form></Modal>
    <Modal title={`设置兼职归属${assignment ? ` · ${assignment.name}` : ''}`} open={Boolean(assignment)} confirmLoading={assignmentLoading} onCancel={() => setAssignment(undefined)} onOk={() => void submitAssignment()} okButtonProps={{ disabled: !assignmentReason.trim() }}><Form layout="vertical"><Form.Item label="归属员工"><Select allowClear loading={assignmentLoading} value={assignmentUserId} onChange={setAssignmentUserId} placeholder="留空表示解除归属" options={candidates.map(user => ({ value: user.id, label: user.nickname }))}/></Form.Item><Form.Item label="调整原因" required><Input.TextArea rows={4} maxLength={500} showCount value={assignmentReason} onChange={e => setAssignmentReason(e.target.value)}/></Form.Item></Form></Modal>
    <Modal title={`兼职归属历史${logPartner ? ` · ${logPartner.name}` : ''}`} open={Boolean(logPartner)} footer={null} width={760} onCancel={() => setLogPartner(undefined)}><Table rowKey="id" size="small" loading={logLoading} pagination={false} dataSource={assignmentLogs} columns={[{ title: '变更前', dataIndex: 'previousEmployeeName', render: value => value || '未分配' }, { title: '变更后', dataIndex: 'employeeName', render: value => value || '未分配' }, { title: '原因', dataIndex: 'reason' }, { title: '操作人', dataIndex: 'operatorName' }, { title: '时间', dataIndex: 'occurredAt', render: value => formatTimestamp(value) }]}/></Modal>
  </section>
}
