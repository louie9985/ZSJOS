import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal, Pagination, Select, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmRecruitPost } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, RECRUIT_POST_STATUS_COLORS, RECRUIT_POST_STATUS_LABELS, SALARY_UNIT_LABELS } from '../services/hrm'
import DeptTreeSelect from '../components/DeptTreeSelect'
import HrmEmployeePicker from '../components/HrmEmployeePicker'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }
function fmtAmount(value?: number, unit?: number) {
  if (value == null || value === -1) return '面议'
  return `¥${value}${SALARY_UNIT_LABELS[unit ?? -1] ?? ''}`
}

/** 招聘职位：列表 + 状态流转 + 新建/编辑。 */
export default function HrmRecruitPostPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmRecruitPost[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [filterStatus, setFilterStatus] = useState<number>()
  const [detail, setDetail] = useState<HrmRecruitPost>()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HrmRecruitPost>()
  const [saving, setSaving] = useState(false)
  const [stopOpen, setStopOpen] = useState(false)
  const [stopTarget, setStopTarget] = useState<HrmRecruitPost>()
  const [stopReason, setStopReason] = useState('')
  const [acting, setActing] = useState(false)
  const [form] = Form.useForm<Omit<HrmRecruitPost, 'latestEntryTime'> & { latestEntryTime?: dayjs.Dayjs }>()

  const postStatus = useDict(HRM_DICT.RECRUIT_POST_STATUS)
  const jobNature = useDict(HRM_DICT.RECRUIT_JOB_NATURE)
  const workTime = useDict(HRM_DICT.RECRUIT_WORK_TIME)
  const education = useDict(HRM_DICT.RECRUIT_POST_EDUCATION)
  const salaryUnit = useDict(HRM_DICT.RECRUIT_SALARY_UNIT)
  const emergency = useDict(HRM_DICT.RECRUIT_EMERGENCY_LEVEL)

  const canCreate = permissions.includes('hrm:recruit:post:create')
  const canUpdate = permissions.includes('hrm:recruit:post:update')

  const loadPage = useCallback(async (page: number, status?: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.recruit.post.page({ pageNo: page, pageSize: PAGE_SIZE, status })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) { if (version === listVersion.current) setError(e instanceof Error ? e.message : '职位加载失败') }
    finally { if (version === listVersion.current) setLoading(false) }
  }, [])

  useEffect(() => { void loadPage(pageNo, filterStatus) }, [loadPage, pageNo, filterStatus])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, filterStatus) }, [loadPage, filterStatus])

  const openForm = async (row?: HrmRecruitPost) => {
    if (row) {
      const detail = await api.hrm.recruit.post.get(row.id!).catch(() => row)
      setEditing(detail)
      form.setFieldsValue({ ...detail, latestEntryTime: detail.latestEntryTime ? dayjs(detail.latestEntryTime) : undefined })
    } else {
      setEditing(undefined)
      form.resetFields()
    }
    setFormOpen(true)
  }

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const payload: HrmRecruitPost = { ...values, latestEntryTime: values.latestEntryTime?.valueOf() }
      if (editing) await api.hrm.recruit.post.update(payload)
      else await api.hrm.recruit.post.create(payload)
      message.success(editing ? '已保存' : '已创建')
      setFormOpen(false); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const toggleStatus = (row: HrmRecruitPost) => {
    if (row.status === 1) {
      setStopTarget(row); setStopReason(''); setStopOpen(true)
    } else {
      Modal.confirm({ title: '开启招聘', content: `确定重新开始招聘「${row.postName}」吗？`, okText: '确认',
        onOk: async () => {
          setActing(true)
          try { await api.hrm.recruit.post.updateStatus({ id: row.id!, status: 1 }); message.success('已开启'); reload() }
          catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e }
          finally { setActing(false) }
        } })
    }
  }

  const handleStop = async () => {
    if (!stopTarget) return
    setActing(true)
    try {
      await api.hrm.recruit.post.updateStatus({ id: stopTarget.id!, status: 0, stopReason: stopReason })
      message.success('已停止招聘')
      setStopOpen(false); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') }
    finally { setActing(false) }
  }

  const openDetail = async (row: HrmRecruitPost) => {
    setDetail(row)
    try { setDetail(await api.hrm.recruit.post.get(row.id!)) }
    catch (e) { message.error(e instanceof Error ? e.message : '详情加载失败') }
  }

  const columns: ColumnsType<HrmRecruitPost> = [
    { title: '职位名称', dataIndex: 'postName', width: 160, fixed: 'left', ellipsis: true, render: (value: string) => value },
    { title: '用人部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '招聘人数', dataIndex: 'recruitNum', width: 90, align: 'right', render: (value?: number) => value != null ? `${value} 人` : '-' },
    { title: '已入职', dataIndex: 'hasEntryNum', width: 90, align: 'right', render: (value?: number) => value != null ? `${value} 人` : '-' },
    { title: '薪资', width: 150, align: 'right', render: (_, row) => {
      if (row.minSalary === -1 || row.maxSalary === -1) return '面议'
      if (row.minSalary != null && row.maxSalary != null) return `${fmtAmount(row.minSalary, row.salaryUnit)} ~ ${fmtAmount(row.maxSalary, row.salaryUnit)}`
      return '-'
    } },
    { title: '学历要求', dataIndex: 'educationRequire', width: 100, render: (value?: number) => value != null ? (education.labels[String(value)] || value) : '-' },
    { title: '招聘进度', dataIndex: 'recruitSchedule', width: 90, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
    { title: '状态', dataIndex: 'status', width: 100, align: 'center', render: (value?: number) => value != null
      ? <Tag color={RECRUIT_POST_STATUS_COLORS[value]}>{RECRUIT_POST_STATUS_LABELS[value]}</Tag> : '-' },
    { title: '操作', width: 180, align: 'center', fixed: 'right', render: (_, row) => <Space size="small">
      <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
      {canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}
      {canUpdate && <Button type="link" size="small" onClick={() => toggleStatus(row)}>{row.status === 1 ? '停止' : '开启'}</Button>}
    </Space> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无招聘职位"/>
        : <>
          <HrmProTable<HrmRecruitPost> advanced persistenceKey="recruit-post" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading} scroll={{ x: 1300 }}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-recruit-post-page">
    <div className="page-heading">
      <Select allowClear placeholder="状态" value={filterStatus} onChange={value => { setFilterStatus(value); setPageNo(1) }}
        style={{ width: 120 }} loading={postStatus.loading} options={postStatus.options}/>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm()}>发布职位</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.postName || '职位详情'} width="min(840px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detail && <>
        <Descriptions column={2} size="small" bordered items={[
          { key: 'postName', label: '职位名称', children: detail.postName },
          { key: 'dept', label: '用人部门', children: detail.deptName || '-' },
          { key: 'jobNature', label: '工作性质', children: detail.jobNature != null ? (jobNature.labels[String(detail.jobNature)] || detail.jobNature) : '-' },
          { key: 'area', label: '工作城市', children: detail.areaName || '-' },
          { key: 'recruitNum', label: '招聘人数', children: detail.recruitNum != null ? `${detail.recruitNum} 人` : '-' },
          { key: 'hasEntry', label: '已入职', children: detail.hasEntryNum != null ? `${detail.hasEntryNum} 人` : '-' },
          { key: 'salary', label: '薪资', children: detail.minSalary != null ? `${fmtAmount(detail.minSalary, detail.salaryUnit)} ~ ${fmtAmount(detail.maxSalary, detail.salaryUnit)}` : '-' },
          { key: 'education', label: '学历要求', children: detail.educationRequire != null ? (education.labels[String(detail.educationRequire)] || detail.educationRequire) : '-' },
          { key: 'owner', label: '招聘负责人', children: detail.ownerEmployeeName || '-' },
          { key: 'emergency', label: '紧急程度', children: detail.emergencyLevel != null ? (emergency.labels[String(detail.emergencyLevel)] || detail.emergencyLevel) : '-' },
          { key: 'status', label: '状态', children: detail.status != null ? <Tag color={RECRUIT_POST_STATUS_COLORS[detail.status]}>{RECRUIT_POST_STATUS_LABELS[detail.status]}</Tag> : '-' },
          { key: 'latestEntry', label: '最迟到岗', children: fmtDate(detail.latestEntryTime) },
          { key: 'description', label: '职位描述', children: detail.description || '-', span: 2 }
        ]}/>
      </>}
    </Drawer>

    <Modal title={editing ? '编辑职位' : '发布职位'} open={formOpen} onCancel={() => setFormOpen(false)}
      onOk={() => void handleSave()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
      <Form form={form} layout="vertical" className="hrm-edit-grid">
        <Form.Item name="postName" label="职位名称" rules={[{ required: true, message: '请输入职位名称' }]}><Input/></Form.Item>
        <Form.Item name="deptId" label="用人部门"><DeptTreeSelect/></Form.Item>
        <Form.Item name="jobNature" label="工作性质"><Select loading={jobNature.loading} options={jobNature.options} allowClear/></Form.Item>
        <Form.Item name="recruitNum" label="招聘人数" rules={[{ required: true, message: '请输入招聘人数' }]}><InputNumber min={1} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="workTime" label="工作经验"><Select loading={workTime.loading} options={workTime.options} allowClear/></Form.Item>
        <Form.Item name="educationRequire" label="学历要求"><Select loading={education.loading} options={education.options} allowClear/></Form.Item>
        <Form.Item name="minSalary" label="最低薪资(-1=面议)"><InputNumber min={-1} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="maxSalary" label="最高薪资(-1=面议)"><InputNumber min={-1} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="salaryUnit" label="薪资单位"><Select loading={salaryUnit.loading} options={salaryUnit.options} allowClear/></Form.Item>
        <Form.Item name="emergencyLevel" label="紧急程度"><Select loading={emergency.loading} options={emergency.options} allowClear/></Form.Item>
        <Form.Item name="ownerEmployeeId" label="招聘负责人"><HrmEmployeePicker allowClear/></Form.Item>
        <Form.Item name="latestEntryTime" label="最迟到岗时间"><DatePicker style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="description" label="职位描述" ><Input.TextArea rows={4}/></Form.Item>
      </Form>
    </Modal>

    <Modal title="停止招聘" open={stopOpen} onCancel={() => setStopOpen(false)} onOk={() => void handleStop()}
      confirmLoading={acting} width="min(720px, 96vw)" destroyOnClose>
      <p className="hrm-muted">确定停止招聘「{stopTarget?.postName}」吗？请填写停止原因。</p>
      <Input.TextArea rows={3} value={stopReason} onChange={e => setStopReason(e.target.value)} placeholder="停止原因"/>
    </Modal>
  </section>
}
