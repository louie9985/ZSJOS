import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Grid,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  TreeSelect,
  Typography
} from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined
} from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import DateTimeText from '../components/DateTimeText'
import { PHONE_PATTERN } from '../constants'
import { createIdempotencyKey } from '../services/idempotency'
import {
  api,
  AuthenticationError,
  type AssignmentUser,
  type PartnerOpenRequest,
  type PartnerOpenRequestCreate,
  type PartnerOpenRequestStatus,
  type SimpleDept
} from '../services/api'

const PAGE_SIZE = 10
const STATUS_OPTIONS: Array<{ value: PartnerOpenRequestStatus; label: string; color: string }> = [
  { value: 'pending', label: '审批中', color: 'processing' },
  { value: 'approved', label: '已通过', color: 'blue' },
  { value: 'opened', label: '已生成邀请码', color: 'success' },
  { value: 'rejected', label: '已驳回', color: 'error' },
  { value: 'cancelled', label: '已撤回', color: 'default' },
  { value: 'open_failed', label: '开通失败', color: 'warning' }
]
type DeptTreeNode = { title: string; value: number; children?: DeptTreeNode[] }

function hasPermission(permissions: string[], permission: string) {
  return permissions.includes(permission)
}

function statusMeta(status?: string) {
  return STATUS_OPTIONS.find(item => item.value === status) || { label: status || '未知', color: 'default' }
}

function buildDeptTree(departments: SimpleDept[]) {
  const children = new Map<number, SimpleDept[]>()
  departments.forEach(dept => {
    const parentId = dept.parentId || 0
    children.set(parentId, [...(children.get(parentId) || []), dept])
  })
  const build = (parentId: number): DeptTreeNode[] =>
    (children.get(parentId) || []).map(dept => {
      const next = build(dept.id)
      return { title: dept.name, value: dept.id, children: next.length ? next : undefined }
    })
  return build(0)
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function Detail({ request }: { request?: PartnerOpenRequest }) {
  if (!request) return <Empty description="选择一条申请查看详情"/>
  const meta = statusMeta(request.status)
  return <section className="partner-open-detail">
    <header className="partner-open-detail-header">
      <div>
        <Typography.Title level={4}>{request.partnerName}</Typography.Title>
        <Typography.Text type="secondary">{request.requestNo}</Typography.Text>
      </div>
      <Tag color={meta.color}>{meta.label}</Tag>
    </header>
    <Descriptions bordered size="small" column={{ xs: 1, md: 2 }} items={[
      { key: 'mobile', label: '兼职手机号', children: request.partnerMobile || request.maskedPartnerMobile || '-' },
      { key: 'assigned', label: '归属员工', children: [request.assignedEmployeeName, request.assignedEmployeeDeptName].filter(Boolean).join(' / ') || '-' },
      { key: 'applicant', label: '发起人', children: [request.applicantName, request.applicantDeptName].filter(Boolean).join(' / ') || '-' },
      { key: 'submittedAt', label: '提交时间', children: <DateTimeText value={request.submittedAt || request.createTime}/> },
      { key: 'reviewedBy', label: '审批人', children: request.reviewedByName || '-' },
      { key: 'reviewedAt', label: '审批时间', children: <DateTimeText value={request.reviewedAt}/> },
      { key: 'inviteCode', label: '邀请码', children: request.inviteCode || '-' },
      { key: 'expiresAt', label: '邀请码过期', children: <DateTimeText value={request.inviteExpiresAt}/> },
      { key: 'reason', label: '审批意见', children: request.reviewReason || '-', span: 2 },
      { key: 'failure', label: '失败原因', children: request.failureReason || '-', span: 2 }
    ]}/>
  </section>
}

export default function PartnerOpenRequestPage({ permissions }: { permissions: string[] }) {
  const { message } = App.useApp()
  const screens = Grid.useBreakpoint()
  const [searchParams, setSearchParams] = useSearchParams()
  const [items, setItems] = useState<PartnerOpenRequest[]>([])
  const [selected, setSelected] = useState<PartnerOpenRequest>()
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<PartnerOpenRequestStatus>()
  const [loading, setLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [departments, setDepartments] = useState<SimpleDept[]>([])
  const [deptLoading, setDeptLoading] = useState(false)
  const [deptError, setDeptError] = useState('')
  const [selectedDeptId, setSelectedDeptId] = useState<number>()
  const [candidates, setCandidates] = useState<AssignmentUser[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [candidateError, setCandidateError] = useState('')
  const [candidateKeyword, setCandidateKeyword] = useState('')
  const [decision, setDecision] = useState<'approve' | 'reject'>()
  const [decisionLoading, setDecisionLoading] = useState(false)
  const [form] = Form.useForm<PartnerOpenRequestCreate>()
  const [decisionForm] = Form.useForm<{ reason: string }>()
  const requestSeq = useRef(0)

  const canQuery = hasPermission(permissions, 'zsjos:partner-open-request:query')
    || hasPermission(permissions, 'zsjos:partner-open-request:review')
  const canCreate = hasPermission(permissions, 'zsjos:partner-open-request:create')
  const canReview = hasPermission(permissions, 'zsjos:partner-open-request:review')
    && hasPermission(permissions, 'bpm:task:update')
  const taskId = searchParams.get('taskId') || undefined
  const handled = searchParams.get('handled') === 'true'

  const deptTree = useMemo(() => buildDeptTree(departments), [departments])
  const employeeOptions = useMemo(() => {
    const rows = candidates.map(user => ({
      value: user.id,
      label: [user.nickname, user.maskedMobile].filter(Boolean).join(' / ')
    }))
    const currentValue = form.getFieldValue('assignedEmployeeUserId')
    const selectedCandidate = selected?.assignedEmployeeUserId === currentValue ? selected : undefined
    if (currentValue && !rows.some(item => item.value === currentValue) && selectedCandidate?.assignedEmployeeName) {
      rows.unshift({ value: currentValue, label: selectedCandidate.assignedEmployeeName })
    }
    return rows
  }, [candidates, form, selected])

  const load = useCallback(async (nextPage = page) => {
    if (!canQuery) return
    const seq = ++requestSeq.current
    setLoading(true)
    setError('')
    setUnauthorized(false)
    try {
      const result = await api.partnerOpenRequestPage({
        pageNo: nextPage,
        pageSize: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        status
      })
      if (seq !== requestSeq.current) return
      setItems(result.list)
      setTotal(result.total)
      setPage(nextPage)
      setSelected(current => current && result.list.some(item => item.id === current.id) ? current : result.list[0])
    } catch (loadError) {
      if (seq !== requestSeq.current) return
      setItems([])
      setSelected(undefined)
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(errorText(loadError, '申请列表加载失败'))
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [canQuery, keyword, page, status])

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true)
    try {
      const detail = await api.partnerOpenRequest(id)
      setSelected(detail)
      setItems(current => current.map(item => item.id === detail.id ? detail : item))
    } catch (detailError) {
      message.error(errorText(detailError, '申请详情加载失败'))
    } finally {
      setDetailLoading(false)
    }
  }, [message])

  const loadDepartments = useCallback(async () => {
    setDeptLoading(true)
    setDeptError('')
    try {
      setDepartments(await api.simpleDepartments())
    } catch (deptLoadError) {
      setDepartments([])
      setDeptError(errorText(deptLoadError, '部门加载失败'))
    } finally {
      setDeptLoading(false)
    }
  }, [])

  const loadCandidates = useCallback(async () => {
    setCandidateLoading(true)
    setCandidateError('')
    try {
      const result = await api.partnerOpenAssigneeCandidates({
        pageNo: 1,
        pageSize: 100,
        deptId: selectedDeptId,
        keyword: candidateKeyword.trim() || undefined
      })
      setCandidates(result.list)
    } catch (candidateLoadError) {
      setCandidates([])
      setCandidateError(errorText(candidateLoadError, '归属员工加载失败'))
    } finally {
      setCandidateLoading(false)
    }
  }, [candidateKeyword, selectedDeptId])

  useEffect(() => { void load(1) }, []) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (createOpen) void loadCandidates()
  }, [createOpen, loadCandidates])
  useEffect(() => {
    const requestId = Number(searchParams.get('requestId'))
    if (requestId > 0) void loadDetail(requestId)
  }, [loadDetail, searchParams])

  const openCreate = async () => {
    form.resetFields()
    setSelectedDeptId(undefined)
    setCandidateKeyword('')
    setCreateOpen(true)
    void loadDepartments()
    void loadCandidates()
    try {
      const profile = await api.userProfile()
      form.setFieldsValue({ assignedEmployeeUserId: profile.id, idempotencyKey: createIdempotencyKey() })
      setCandidates(current => current.some(item => item.id === profile.id)
        ? current
        : [{ id: profile.id, nickname: profile.nickname, maskedMobile: profile.mobile, deptId: profile.dept?.id, status: 0 }, ...current])
    } catch {
      form.setFieldsValue({ idempotencyKey: createIdempotencyKey() })
    }
  }

  const submitCreate = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const id = await api.createPartnerOpenRequest(values)
      message.success('代开通申请已提交')
      setCreateOpen(false)
      await load(1)
      await loadDetail(id)
    } catch (submitError) {
      if (submitError instanceof Error) message.error(submitError.message)
    } finally {
      setSaving(false)
    }
  }

  const cancel = async (id: number) => {
    try {
      await api.cancelPartnerOpenRequest(id)
      message.success('申请已撤回')
      await load(page)
      await loadDetail(id)
    } catch (cancelError) {
      message.error(errorText(cancelError, '撤回失败'))
    }
  }

  const submitDecision = async () => {
    if (!taskId || !decision) return
    try {
      const values = await decisionForm.validateFields()
      setDecisionLoading(true)
      if (decision === 'approve') await api.approveBpmTask(taskId, values.reason || '同意')
      else await api.rejectBpmTask(taskId, values.reason)
      message.success(decision === 'approve' ? '审批已通过' : '审批已驳回')
      setDecision(undefined)
      const next = new URLSearchParams(searchParams)
      next.set('handled', 'true')
      setSearchParams(next, { replace: true })
      if (selected?.id) await loadDetail(selected.id)
      await load(page)
    } catch (decisionError) {
      if (decisionError instanceof Error) message.error(decisionError.message)
    } finally {
      setDecisionLoading(false)
    }
  }

  const selectRow = (row: PartnerOpenRequest) => {
    setSelected(row)
    void loadDetail(row.id)
    if (!screens.md) setDrawerOpen(true)
  }

  if (!canQuery) {
    return <section className="workspace-page partner-open-page">
      <Result403/>
    </section>
  }

  const detail = detailLoading ? <Spin/> : <Detail request={selected}/>

  return <section className="workspace-page partner-open-page">
    <header className="partner-open-toolbar">
      <div>
        <Typography.Title level={4}>代开通兼职账号</Typography.Title>
        <Typography.Text type="secondary">提交开通申请，审批通过后生成 H5 首次登录激活邀请码。</Typography.Text>
      </div>
      <Space wrap>
        <Input
          allowClear
          prefix={<SearchOutlined/>}
          value={keyword}
          onChange={event => setKeyword(event.target.value)}
          onPressEnter={() => void load(1)}
          placeholder="搜索姓名 / 手机 / 申请编号"
        />
        <Select
          allowClear
          className="partner-open-status-filter"
          value={status}
          onChange={setStatus}
          placeholder="状态"
          options={STATUS_OPTIONS.map(item => ({ value: item.value, label: item.label }))}
        />
        <Button icon={<ReloadOutlined/>} onClick={() => void load(page)}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined/>} disabled={!canCreate} onClick={() => void openCreate()}>发起申请</Button>
      </Space>
    </header>

    <div className="partner-open-layout">
      <section className="partner-open-list">
        {error && <Alert
          type={unauthorized ? 'warning' : 'error'}
          showIcon
          message={error}
          action={<Button size="small" onClick={() => void load(page)}>重试</Button>}
        />}
        <Table<PartnerOpenRequest>
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={items}
          pagination={{ current: page, total, pageSize: PAGE_SIZE, onChange: next => void load(next) }}
          onRow={row => ({ onClick: () => selectRow(row) })}
          rowClassName={row => row.id === selected?.id ? 'partner-open-row-active' : ''}
          locale={{ emptyText: <Empty description="暂无代开通申请"/> }}
          columns={[
            { title: '申请', dataIndex: 'requestNo', width: 170, render: (_, row) => <Space direction="vertical" size={0}><strong>{row.partnerName}</strong><Typography.Text type="secondary">{row.requestNo}</Typography.Text></Space> },
            { title: '手机号', dataIndex: 'maskedPartnerMobile', width: 130, render: (_, row) => row.maskedPartnerMobile || row.partnerMobile || '-' },
            { title: '归属员工', dataIndex: 'assignedEmployeeName', width: 140, render: (_, row) => row.assignedEmployeeName || '-' },
            { title: '状态', dataIndex: 'status', width: 130, render: value => { const meta = statusMeta(String(value)); return <Tag color={meta.color}>{meta.label}</Tag> } },
            { title: '提交时间', dataIndex: 'submittedAt', width: 170, render: value => <DateTimeText value={value as PartnerOpenRequest['submittedAt']}/> },
            { title: '操作', width: 96, fixed: 'right', render: (_, row) => row.availableActions?.includes('cancel') ? <Popconfirm title="确认撤回该申请？" onConfirm={() => void cancel(row.id)}><Button type="link" icon={<StopOutlined/>}>撤回</Button></Popconfirm> : <Button type="link" onClick={() => selectRow(row)}>详情</Button> }
          ]}
          scroll={{ x: 900 }}
        />
      </section>
      <aside className="partner-open-side">
        {detail}
        {canReview && taskId && selected && !handled && selected.status === 'pending' && (
          <Space className="partner-open-review-actions">
            <Button type="primary" icon={<CheckOutlined/>} onClick={() => { setDecision('approve'); decisionForm.resetFields() }}>通过</Button>
            <Button danger icon={<CloseOutlined/>} onClick={() => { setDecision('reject'); decisionForm.resetFields() }}>驳回</Button>
          </Space>
        )}
      </aside>
    </div>

    <Drawer className="partner-open-mobile-drawer" open={drawerOpen} onClose={() => setDrawerOpen(false)} title="申请详情" width="100%">
      {detail}
    </Drawer>

    <Modal
      width={640}
      title="发起代开通申请"
      open={createOpen}
      confirmLoading={saving}
      onCancel={() => setCreateOpen(false)}
      onOk={() => void submitCreate()}
    >
      <Form form={form} layout="vertical">
        <Form.Item name="partnerName" label="兼职姓名" rules={[{ required: true, message: '请输入兼职姓名' }, { max: 100 }]}>
          <Input/>
        </Form.Item>
        <Form.Item name="partnerMobile" label="兼职手机号" rules={[{ required: true, message: '请输入兼职手机号' }, { pattern: PHONE_PATTERN, message: '请输入正确的手机号' }]}>
          <Input maxLength={11}/>
        </Form.Item>
        <Form.Item label="部门">
          {deptError && <Alert className="partner-open-inline-alert" type="error" showIcon message={deptError} action={<Button size="small" onClick={() => void loadDepartments()}>重试</Button>}/>}
          <TreeSelect
            allowClear
            showSearch
            treeDefaultExpandAll
            loading={deptLoading}
            value={selectedDeptId}
            onChange={value => setSelectedDeptId(value)}
            treeData={deptTree}
            placeholder="按部门筛选归属员工"
          />
        </Form.Item>
        <Form.Item name="assignedEmployeeUserId" label="归属员工" rules={[{ required: true, message: '请选择归属员工' }]}>
          <Select
            showSearch
            filterOption={false}
            loading={candidateLoading}
            onSearch={setCandidateKeyword}
            onFocus={() => void loadCandidates()}
            notFoundContent={candidateLoading ? <Spin size="small"/> : '暂无可选运营'}
            options={employeeOptions}
            placeholder="选择新媒体运营"
          />
        </Form.Item>
        {candidateError && <Alert className="partner-open-inline-alert" type="error" showIcon message={candidateError} action={<Button size="small" onClick={() => void loadCandidates()}>重试</Button>}/>}
        <Form.Item name="idempotencyKey" hidden><Input/></Form.Item>
      </Form>
    </Modal>

    <Modal
      title={decision === 'approve' ? '通过申请' : '驳回申请'}
      open={Boolean(decision)}
      confirmLoading={decisionLoading}
      onCancel={() => setDecision(undefined)}
      onOk={() => void submitDecision()}
    >
      <Form form={decisionForm} layout="vertical">
        <Form.Item name="reason" label="审批意见" rules={decision === 'reject' ? [{ required: true, message: '请输入驳回原因' }] : undefined}>
          <Input.TextArea rows={4} maxLength={500} showCount/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}

function Result403() {
  return <div className="partner-open-result">
    <Alert type="warning" showIcon message="无权访问代开通兼职账号申请" description="当前账号缺少申请查询或审批权限。"/>
  </div>
}
