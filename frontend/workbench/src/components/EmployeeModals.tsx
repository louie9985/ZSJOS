import { useEffect, useMemo, useState } from 'react'
import { DatePicker, Form, Input, InputNumber, Modal, Select, Space, message } from 'antd'
import { api, type HrmEmployee, type HrmEmployeeChangeReq, type HrmEmployeeQuitReq, type HrmEmployeeSave } from '../services/api'
import { useDict } from '../services/useDict'
import {
  CHANGE_REASON_OPTIONS, CHANGE_TYPE_LABELS, HRM_DICT, employeeStatusOptionsOf, quitReasonOptionsOf
} from '../services/hrm'
import DeptTreeSelect from './DeptTreeSelect'
import HrmEmployeePicker from './HrmEmployeePicker'
import dayjs from 'dayjs'

type DateFieldName = 'birthday' | 'entryTime' | 'regularTime' | 'leaveTime' | 'effectTime' | 'planQuitTime' | 'applyQuitTime' | 'salarySettlementTime'

/** 后端时间字段是毫秒时间戳；antd DatePicker 用 dayjs。这里做双向转换。 */
function tsToString(value?: number) {
  return value ? dayjs(value).toISOString() : undefined
}

/** 弹窗回填时把毫秒时间戳转成 dayjs 放入表单。 */
function backfillDates(values: Record<string, unknown>, fields: DateFieldName[]) {
  const out: Record<string, unknown> = { ...values }
  for (const field of fields) {
    const raw = values[field] as number | undefined
    if (raw != null) out[field] = dayjs(raw)
  }
  return out
}

/** 提交前把 dayjs 转回毫秒时间戳。 */
function encodeDates(values: Record<string, unknown>, fields: DateFieldName[]) {
  const out: Record<string, unknown> = { ...values }
  for (const field of fields) {
    const raw = values[field] as dayjs.Dayjs | undefined
    out[field] = raw ? raw.valueOf() : undefined
  }
  return out
}

const PERSONAL_FIELDS = [
  { name: 'name', label: '姓名', required: true },
  { name: 'jobNumber', label: '工号', required: true },
  { name: 'mobile', label: '手机号' },
  { name: 'email', label: '邮箱' },
  { name: 'sex', label: '性别', type: 'sex' },
  { name: 'idType', label: '证件类型', type: 'idType' },
  { name: 'idNumber', label: '证件号码' },
  { name: 'nation', label: '民族' },
  { name: 'nativePlace', label: '籍贯' },
  { name: 'birthday', label: '出生日期', type: 'date' },
  { name: 'address', label: '户籍地址', type: 'textarea' },
  { name: 'highestEducation', label: '最高学历', type: 'education' }
]

const POSITION_FIELDS = [
  { name: 'deptId', label: '部门', type: 'dept', required: true },
  { name: 'postName', label: '职位' },
  { name: 'postLevel', label: '职级' },
  { name: 'leaderEmployeeId', label: '直属上级', type: 'leader' },
  { name: 'workCity', label: '工作城市' },
  { name: 'workAddress', label: '工作地点' },
  { name: 'workDetailAddress', label: '详细地址' }
]

/** 员工档案新增/编辑弹窗。 */
export function EmployeeFormModal({ open, employee, onClose, onSaved }: {
  open: boolean
  employee?: HrmEmployee
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<HrmEmployeeSave>()
  const [saving, setSaving] = useState(false)
  const isEdit = !!employee
  const employeeType = useDict(HRM_DICT.EMPLOYEE_TYPE)
  const employeeStatus = useDict(HRM_DICT.EMPLOYEE_STATUS)

  const watchType = Form.useWatch('type', form)
  const watchStatus = Form.useWatch('status', form)

  const statusOptions = useMemo(() => employeeStatusOptionsOf(watchType), [watchType])

  useEffect(() => {
    if (!open) return
    if (isEdit && employee) {
      form.setFieldsValue(backfillDates({ ...employee } as Record<string, unknown>, ['birthday', 'entryTime', 'regularTime', 'leaveTime']) as Partial<HrmEmployeeSave>)
    } else form.resetFields()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isEdit])

  // 聘用形式改变时重置不匹配的员工状态
  useEffect(() => {
    if (!watchType) return
    const matched = statusOptions.some((item) => item.value === watchStatus)
    if (watchStatus && !matched) form.setFieldValue('status', undefined)
  }, [statusOptions])

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const payload = encodeDates(values, ['birthday', 'entryTime', 'regularTime', 'leaveTime']) as HrmEmployeeSave
      if (isEdit) await api.hrm.employee.update(payload)
      else await api.hrm.employee.create(payload)
      message.success(isEdit ? '已保存' : '已创建')
      onClose(); onSaved()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const renderPersonal = () => PERSONAL_FIELDS.map(field => {
    if (field.type === 'sex') return <Form.Item key={field.name} name={field.name} label={field.label}>
      <Select options={[{ value: 1, label: '男' }, { value: 2, label: '女' }]}/>
    </Form.Item>
    if (field.type === 'idType') return <Form.Item key={field.name} name={field.name} label={field.label}>
      <Select options={[{ value: 1, label: '身份证' }, { value: 2, label: '港澳通行证' }, { value: 3, label: '台湾通行证' }, { value: 4, label: '护照' }, { value: 5, label: '其他' }]}/>
    </Form.Item>
    if (field.type === 'education') return <Form.Item key={field.name} name={field.name} label={field.label}>
      <Select options={[{ value: 1, label: '小学' }, { value: 2, label: '初中' }, { value: 3, label: '中专' }, { value: 4, label: '中职' }, { value: 5, label: '技校' }, { value: 6, label: '高中' }, { value: 7, label: '大专' }, { value: 8, label: '本科' }, { value: 9, label: '硕士' }, { value: 10, label: '博士' }, { value: 11, label: '博士后' }, { value: 12, label: '其他' }]}/>
    </Form.Item>
    if (field.type === 'date') return <Form.Item key={field.name} name={field.name} label={field.label}>
      <DatePicker style={{ width: '100%' }}/>
    </Form.Item>
    if (field.type === 'textarea') return <Form.Item key={field.name} name={field.name} label={field.label}>
      <Input.TextArea rows={2}/>
    </Form.Item>
    return <Form.Item key={field.name} name={field.name} label={field.label} rules={field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined}>
      <Input/>
    </Form.Item>
  })

  return <Modal title={isEdit ? '编辑员工档案' : '新增员工档案'} open={open} onCancel={onClose}
    onOk={() => void handleSave()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
    <Form form={form} layout="vertical" className="hrm-edit-form">
      <div className="hrm-edit-grid">{renderPersonal()}</div>
      <h4 className="hrm-drawer-subtitle">任职信息</h4>
      <div className="hrm-edit-grid">
        <Form.Item name="deptId" label="部门" rules={[{ required: true, message: '请选择部门' }]}>
          <DeptTreeSelect/>
        </Form.Item>
        <Form.Item name="postName" label="职位"><Input/></Form.Item>
        <Form.Item name="postLevel" label="职级"><Input/></Form.Item>
        <Form.Item name="leaderEmployeeId" label="直属上级"><HrmEmployeePicker/></Form.Item>
        <Form.Item name="entryTime" label="入职时间"><DatePicker style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="probation" label="试用期(月)"><InputNumber min={0} max={36} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="type" label="聘用形式" rules={[{ required: true, message: '请选择聘用形式' }]}>
          <Select options={[{ value: 1, label: '正式' }, { value: 2, label: '非正式' }]} loading={employeeType.loading}/>
        </Form.Item>
        <Form.Item name="status" label="员工状态" rules={[{ required: true, message: '请选择员工状态' }]}>
          <Select options={statusOptions} loading={employeeStatus.loading}/>
        </Form.Item>
        <Form.Item name="workCity" label="工作城市"><Input/></Form.Item>
        <Form.Item name="workAddress" label="工作地点"><Input/></Form.Item>
      </div>
      <Form.Item name="workDetailAddress" label="详细地址"><Input/></Form.Item>
    </Form>
  </Modal>
}

/** 五类异动共用的职级/岗位变更弹窗。 */
export function PositionChangeModal({ open, employee, changeType, onClose, onSaved }: {
  open: boolean
  employee?: HrmEmployee
  changeType: number
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<HrmEmployeeChangeReq & { effectTime?: dayjs.Dayjs }>()
  const [saving, setSaving] = useState(false)
  const [reason, setReason] = useState(CHANGE_REASON_OPTIONS[0].value)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue({ employeeId: employee?.id, reason, effectTime: dayjs() })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const submit = ({
    4: api.hrm.employee.regular,
    5: api.hrm.employee.transfer,
    6: api.hrm.employee.promote,
    7: api.hrm.employee.demote,
    8: api.hrm.employee.convertToFullTime
  } as Record<number, (data: HrmEmployeeChangeReq) => Promise<boolean>>)[changeType]

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await submit(encodeDates(values, ['effectTime']) as HrmEmployeeChangeReq)
      message.success('操作成功')
      onClose(); onSaved()
    } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') }
    finally { setSaving(false) }
  }

  return <Modal title={CHANGE_TYPE_LABELS[changeType]} open={open} onCancel={onClose}
    onOk={() => void handleSave()} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
    {employee && <div className="hrm-modal-employee">
      <span className="hrm-muted">{employee.name}（{employee.postName || '未设岗位'}）</span>
    </div>}
    <Form form={form} layout="vertical">
      <Form.Item name="reason" label="异动原因" rules={[{ required: true, message: '请选择异动原因' }]}>
        <Select options={CHANGE_REASON_OPTIONS} onChange={setReason}/>
      </Form.Item>
      <Form.Item name="effectTime" label="生效日期" rules={[{ required: true, message: '请选择生效日期' }]}>
        <DatePicker style={{ width: '100%' }}/>
      </Form.Item>
      {changeType === 8 && <Form.Item name="probation" label="试用期(月)"><InputNumber min={0} max={36} style={{ width: '100%' }}/></Form.Item>}
      <Form.Item name="newDeptId" label="新部门">
        <DeptTreeSelect allowClear/>
      </Form.Item>
      <Form.Item name="newPostName" label="新岗位"><Input placeholder="留空表示不变"/></Form.Item>
      <Form.Item name="newPostLevel" label="新职级"><Input placeholder="留空表示不变"/></Form.Item>
      <Form.Item name="newLeaderEmployeeId" label="新直属上级"><HrmEmployeePicker allowClear/></Form.Item>
      <Form.Item name="newWorkAddress" label="新工作地点"><Input placeholder="留空表示不变"/></Form.Item>
      <Form.Item name="remark" label="备注"><Input.TextArea rows={2}/></Form.Item>
    </Form>
  </Modal>
}

/** 员工离职弹窗。 */
export function EmployeeQuitModal({ open, employee, onClose, onSaved }: {
  open: boolean
  employee?: HrmEmployee
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<HrmEmployeeQuitReq>()
  const [saving, setSaving] = useState(false)

  const watchType = Form.useWatch('type', form)
  const reasonOptions = quitReasonOptionsOf(watchType)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue({ employeeId: employee?.id, type: 1 })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await api.hrm.employee.quit(encodeDates(values, ['planQuitTime', 'applyQuitTime', 'salarySettlementTime']) as HrmEmployeeQuitReq)
      message.success('离职已提交')
      onClose(); onSaved()
    } catch (e) { message.error(e instanceof Error ? e.message : '提交失败') }
    finally { setSaving(false) }
  }

  return <Modal title="办理离职" open={open} onCancel={onClose}
    onOk={() => void handleSave()} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
    {employee && <div className="hrm-modal-employee">
      <span className="hrm-muted">{employee.name}（{employee.postName || '未设岗位'}）</span>
    </div>}
    <Form form={form} layout="vertical">
      <Form.Item name="type" label="离职类型" rules={[{ required: true, message: '请选择离职类型' }]}>
        <Select options={[{ value: 1, label: '主动离职' }, { value: 2, label: '被动离职' }, { value: 3, label: '退休' }]}/>
      </Form.Item>
      {reasonOptions.length > 0 && <Form.Item name="reason" label="离职原因" rules={[{ required: true, message: '请选择离职原因' }]}>
        <Select options={reasonOptions}/>
      </Form.Item>}
      <Form.Item name="planQuitTime" label="计划离职时间" rules={[{ required: true, message: '请选择计划离职时间' }]}>
        <DatePicker style={{ width: '100%' }}/>
      </Form.Item>
      <Form.Item name="applyQuitTime" label="申请离职时间">
        <DatePicker showTime style={{ width: '100%' }}/>
      </Form.Item>
      <Form.Item name="salarySettlementTime" label="薪资结算时间">
        <DatePicker style={{ width: '100%' }}/>
      </Form.Item>
      <Form.Item name="remark" label="备注"><Input.TextArea rows={2}/></Form.Item>
    </Form>
  </Modal>
}
