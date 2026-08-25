import HrmProTable from './HrmProTable'
import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Upload, message } from 'antd'
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { api, type HrmEmployeeCreateFromUser, type HrmEmployeeImportResult, type SimpleUser } from '../services/api'
import { downloadBlob } from '../services/download'
import { EMPLOYEE_TYPE, HRM_DICT } from '../services/hrm'
import { useDict } from '../services/useDict'
import DeptTreeSelect from './DeptTreeSelect'
import HrmEmployeePicker from './HrmEmployeePicker'

type CreateRow = Omit<HrmEmployeeCreateFromUser, 'entryTime'> & {
  entryTime: Dayjs
  nickname: string
  username?: string
}

export function HrmEmployeeImportModal({ open, onClose, onImported }: {
  open: boolean
  onClose: () => void
  onImported: () => void
}) {
  const [file, setFile] = useState<File>()
  const [strategy, setStrategy] = useState(3)
  const [result, setResult] = useState<HrmEmployeeImportResult>()
  const [loading, setLoading] = useState(false)
  const [templateLoading, setTemplateLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setFile(undefined); setStrategy(3); setResult(undefined)
  }, [open])

  const submit = async () => {
    if (!file) { message.warning('请选择导入文件'); return }
    setLoading(true)
    try {
      const imported = await api.hrm.employee.import(file, strategy)
      setResult(imported)
      message.success('员工档案导入完成')
      onImported()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入失败')
    } finally { setLoading(false) }
  }

  const downloadTemplate = async () => {
    setTemplateLoading(true)
    try { await downloadBlob('/hrm/employee/get-import-template', '员工档案导入模板.xlsx') }
    catch (error) { message.error(error instanceof Error ? error.message : '模板下载失败') }
    finally { setTemplateLoading(false) }
  }

  const failures = Object.entries(result?.failureJobNumbers || {}).map(([jobNumber, reason]) => ({ jobNumber, reason }))
  return <Modal title="导入员工档案" open={open} onCancel={onClose} onOk={() => void submit()}
    okText="开始导入" confirmLoading={loading} width="min(960px, 96vw)" destroyOnHidden>
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Select value={strategy} onChange={setStrategy} style={{ width: 190 }} options={[
          { value: 1, label: '重复员工：跳过' },
          { value: 2, label: '重复员工：覆盖' },
          { value: 3, label: '重复员工：记为失败' }
        ]}/>
        <Button icon={<DownloadOutlined/>} loading={templateLoading} onClick={() => void downloadTemplate()}>下载导入模板</Button>
      </Space>
      <Upload.Dragger accept=".xls,.xlsx" maxCount={1} beforeUpload={selected => { setFile(selected); setResult(undefined); return false }}
        onRemove={() => { setFile(undefined); setResult(undefined) }} fileList={file ? [{ uid: 'employee-import', name: file.name }] : []}>
        <p className="ant-upload-drag-icon"><InboxOutlined/></p>
        <p className="ant-upload-text">选择员工档案 Excel 文件</p>
      </Upload.Dragger>
      {result && <>
        <Descriptions size="small" bordered column={4} items={[
          { key: 'create', label: '新增', children: result.createJobNumbers.length },
          { key: 'update', label: '更新', children: result.updateJobNumbers.length },
          { key: 'skip', label: '跳过', children: result.skipJobNumbers.length },
          { key: 'failure', label: '失败', children: failures.length }
        ]}/>
        {failures.length > 0 && <HrmProTable size="small" rowKey="jobNumber" pagination={false} dataSource={failures}
          columns={[{ title: '行号/员工标识', dataIndex: 'jobNumber', width: 180 }, { title: '失败原因', dataIndex: 'reason' }]}/>}
      </>}
    </Space>
  </Modal>
}

export function HrmEmployeeCreateFromUserModal({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: () => void
}) {
  const [form] = Form.useForm<{ employees: CreateRow[] }>()
  const rows = Form.useWatch('employees', form) || []
  const [users, setUsers] = useState<SimpleUser[]>([])
  const [boundIds, setBoundIds] = useState<number[]>([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const employeeType = useDict(HRM_DICT.EMPLOYEE_TYPE)
  const employeeStatus = useDict(HRM_DICT.EMPLOYEE_STATUS)

  useEffect(() => {
    if (!open) return
    let mounted = true
    form.resetFields(); setLoading(true)
    Promise.all([api.simpleUsers(), api.hrm.employee.boundUserIdList()])
      .then(([userList, ids]) => { if (mounted) { setUsers(userList); setBoundIds(ids) } })
      .catch(error => { if (mounted) message.error(error instanceof Error ? error.message : '后台用户加载失败') })
      .finally(() => { if (mounted) setLoading(false) })
    return () => { mounted = false }
  }, [form, open])

  const availableUsers = useMemo(() => users.filter(user => !boundIds.includes(user.id)), [boundIds, users])
  const selectUsers = (ids: number[]) => {
    const current = new Map(rows.map(row => [row.userId, row]))
    form.setFieldValue('employees', ids.map(id => {
      const existing = current.get(id)
      if (existing) return existing
      const user = users.find(item => item.id === id)!
      return {
        userId: user.id, nickname: user.nickname, username: user.username, deptId: user.deptId,
        jobNumber: '', mobile: '', entryTime: dayjs(), type: EMPLOYEE_TYPE.FORMAL, probation: 0
      } satisfies CreateRow
    }))
  }

  const submit = async () => {
    let values: { employees: CreateRow[] }
    try { values = await form.validateFields() } catch { return }
    if (!values.employees?.length) { message.warning('请选择未建档的后台用户'); return }
    setSubmitting(true)
    try {
      const payload = values.employees.map(({ nickname: _nickname, username: _username, entryTime, ...row }) => ({
        ...row, entryTime: entryTime.valueOf(),
        probation: row.type === EMPLOYEE_TYPE.FORMAL ? row.probation : undefined,
        status: row.type === EMPLOYEE_TYPE.INFORMAL ? row.status : undefined
      }))
      const ids = await api.hrm.employee.createList(payload)
      message.success(`已创建 ${ids.length} 份员工档案`)
      onCreated(); onClose()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批量建档失败')
    } finally { setSubmitting(false) }
  }

  const columns: ColumnsType<CreateRow> = [
    { title: '后台用户', dataIndex: 'nickname', width: 130, fixed: 'left', render: (_, row) => <><div>{row.nickname}</div><span className="hrm-muted">{row.username || '-'}</span></> },
    { title: '手机号', width: 160, render: (_, __, index) => <Form.Item name={['employees', index, 'mobile']} rules={[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]} noStyle><Input placeholder="11 位手机号"/></Form.Item> },
    { title: '工号', width: 140, render: (_, __, index) => <Form.Item name={['employees', index, 'jobNumber']} rules={[{ required: true, message: '请输入工号' }]} noStyle><Input maxLength={64}/></Form.Item> },
    { title: '部门', width: 180, render: (_, __, index) => <Form.Item name={['employees', index, 'deptId']} noStyle><DeptTreeSelect/></Form.Item> },
    { title: '直属上级', width: 190, render: (_, __, index) => <Form.Item name={['employees', index, 'leaderEmployeeId']} noStyle><HrmEmployeePicker allowClear/></Form.Item> },
    { title: '入职时间', width: 180, render: (_, __, index) => <Form.Item name={['employees', index, 'entryTime']} rules={[{ required: true, message: '请选择入职时间' }]} noStyle><DatePicker showTime style={{ width: '100%' }}/></Form.Item> },
    { title: '聘用形式', width: 130, render: (_, __, index) => <Form.Item name={['employees', index, 'type']} rules={[{ required: true }]} noStyle><Select options={employeeType.options} onChange={value => {
      form.setFieldValue(['employees', index, 'probation'], value === EMPLOYEE_TYPE.FORMAL ? 0 : undefined)
      form.setFieldValue(['employees', index, 'status'], value === EMPLOYEE_TYPE.INFORMAL ? 3 : undefined)
    }}/></Form.Item> },
    { title: '试用期/状态', width: 150, render: (_, row, index) => row.type === EMPLOYEE_TYPE.FORMAL
      ? <Form.Item name={['employees', index, 'probation']} rules={[{ required: true, message: '请输入试用期' }]} noStyle><InputNumber min={0} max={6} addonAfter="月" style={{ width: '100%' }}/></Form.Item>
      : <Form.Item name={['employees', index, 'status']} rules={[{ required: true, message: '请选择状态' }]} noStyle><Select options={employeeStatus.options.filter(option => Number(option.value) >= 3)}/></Form.Item> },
    { title: '职位', width: 150, render: (_, __, index) => <Form.Item name={['employees', index, 'postName']} noStyle><Input maxLength={255}/></Form.Item> }
  ]

  return <Modal title="从后台用户批量建档" open={open} onCancel={onClose} onOk={() => void submit()}
    okText="确认建档" confirmLoading={submitting} width="96%" destroyOnHidden>
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {(employeeType.error || employeeStatus.error) && <Alert type="warning" showIcon message="员工类型或状态字典加载失败"/>}
      <Select mode="multiple" loading={loading} value={rows.map(row => row.userId)} onChange={selectUsers}
        placeholder="选择未建档后台用户" style={{ width: '100%' }} maxCount={100} optionFilterProp="label"
        options={availableUsers.map(user => ({ value: user.id, label: `${user.nickname}${user.username ? `（${user.username}）` : ''}` }))}/>
      <Form form={form} component={false} initialValues={{ employees: [] }}>
        <HrmProTable<CreateRow> size="small" rowKey="userId" columns={columns} dataSource={rows} pagination={false}
          scroll={{ x: 1450, y: 420 }} locale={{ emptyText: '请选择未建档的后台用户' }}/>
      </Form>
    </Space>
  </Modal>
}
