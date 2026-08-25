import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Descriptions, Form, Input, Modal, Select, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmEmployee } from '../services/api'
import { ENTRY_STATUS_LABELS, ENTRY_STATUS_COLORS, EMPLOYEE_TYPE_LABELS, EDUCATION_OPTIONS, ID_TYPE_OPTIONS, SEX_OPTIONS } from '../services/hrm'
import dayjs from 'dayjs'

function fmtDate(value?: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DD') : '-'
}

type ProfileFormValues = Partial<Pick<HrmEmployee, 'name' | 'mobile' | 'email' | 'idType' | 'idNumber' | 'sex' | 'nation' | 'nativePlace' | 'address' | 'highestEducation'>> & { birthday?: dayjs.Dayjs }

const EDITABLE: Array<{ name: keyof ProfileFormValues; label: string; control: 'input' | 'select' | 'date' | 'textarea' }> = [
  { name: 'name', label: '姓名', control: 'input' },
  { name: 'mobile', label: '手机号', control: 'input' },
  { name: 'email', label: '邮箱', control: 'input' },
  { name: 'idType', label: '证件类型', control: 'select' },
  { name: 'idNumber', label: '证件号码', control: 'input' },
  { name: 'sex', label: '性别', control: 'select' },
  { name: 'nation', label: '民族', control: 'input' },
  { name: 'nativePlace', label: '籍贯', control: 'input' },
  { name: 'birthday', label: '出生日期', control: 'date' },
  { name: 'address', label: '户籍地址', control: 'textarea' },
  { name: 'highestEducation', label: '最高学历', control: 'select' }
]

function renderControl(field: (typeof EDITABLE)[number], form: ProfileFormValues, setForm: (value: ProfileFormValues) => void) {
  const value = form[field.name]
  if (field.control === 'select') {
    const options = field.name === 'idType' ? ID_TYPE_OPTIONS
      : field.name === 'sex' ? SEX_OPTIONS
        : field.name === 'highestEducation' ? EDUCATION_OPTIONS
          : []
    return <Select value={value} onChange={val => setForm({ ...form, [field.name]: val })} options={options} style={{ width: '100%' }}/>
  }
  if (field.control === 'date') {
    return <DatePicker value={value as dayjs.Dayjs | undefined} onChange={val => setForm({ ...form, birthday: val ?? undefined })} style={{ width: '100%' }}/>
  }
  if (field.control === 'textarea') {
    return <Input.TextArea rows={2} value={value as string} onChange={e => setForm({ ...form, address: e.target.value })}/>
  }
  return <Input value={value as string} onChange={e => setForm({ ...form, [field.name]: e.target.value })}/>
}

/** 员工端我的档案：查看完整个人档案，编辑个人联系与证件信息。 */
export default function HrmMyProfilePage() {
  const [bindStatus, setBindStatus] = useState(true)
  const [employee, setEmployee] = useState<HrmEmployee>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const [editOpen, setEditOpen] = useState(false)
  const [form, setForm] = useState<ProfileFormValues>({})
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const [status, data] = await Promise.all([
        api.hrm.portal.employee.getBindStatus(),
        api.hrm.portal.employee.get()
      ])
      if (current !== version.current) return
      setBindStatus(status); setEmployee(data)
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '档案加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const openEdit = () => {
    if (!employee) return
    setForm({
      name: employee.name, mobile: employee.mobile, email: employee.email,
      idType: employee.idType, idNumber: employee.idNumber, sex: employee.sex,
      nation: employee.nation, nativePlace: employee.nativePlace,
      birthday: employee.birthday ? dayjs(employee.birthday) : undefined,
      address: employee.address, highestEducation: employee.highestEducation
    })
    setEditOpen(true)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      await api.hrm.portal.employee.update({
        ...form,
        birthday: form.birthday ? form.birthday.valueOf() : undefined
      })
      message.success('档案已更新')
      setEditOpen(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  if (loading && !employee) return <section className="workspace-page hrm-page"><Skeleton active paragraph={{ rows: 10 }}/></section>

  return <section className="workspace-page hrm-page hrm-my-profile-page">
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {employee && !bindStatus && <Alert className="hrm-inline-alert" type="warning" showIcon message="当前账号尚未绑定员工档案，请联系 HR 管理员绑定后再查看"/>}
    {employee && bindStatus && <>
      <div className="page-heading">
        <Space size="middle">
          <h2 className="hrm-profile-name">{employee.name}</h2>
          {employee.jobNumber && <Tag>{employee.jobNumber}</Tag>}
          {employee.entryStatus != null && <Tag color={ENTRY_STATUS_COLORS[employee.entryStatus]}>{ENTRY_STATUS_LABELS[employee.entryStatus]}</Tag>}
        </Space>
        <Button type="primary" onClick={openEdit}>编辑信息</Button>
      </div>
      <div className="hrm-table-area">
        <Descriptions column={3} size="middle" bordered items={[
          { key: 'mobile', label: '手机号', children: employee.mobile || '-' },
          { key: 'email', label: '邮箱', children: employee.email || '-' },
          { key: 'sex', label: '性别', children: employee.sex === 1 ? '男' : employee.sex === 2 ? '女' : '-' },
          { key: 'idType', label: '证件类型', children: employee.idType != null ? ID_TYPE_OPTIONS.find(x => x.value === employee.idType)?.label : '-' },
          { key: 'idNumber', label: '证件号码', children: employee.idNumber || '-' },
          { key: 'nation', label: '民族', children: employee.nation || '-' },
          { key: 'nativePlace', label: '籍贯', children: employee.nativePlace || '-' },
          { key: 'birthday', label: '出生日期', children: fmtDate(employee.birthday) },
          { key: 'age', label: '年龄', children: employee.age != null ? `${employee.age} 岁` : '-' },
          { key: 'education', label: '最高学历', children: employee.highestEducation != null ? EDUCATION_OPTIONS.find(x => x.value === employee.highestEducation)?.label : '-' },
          { key: 'address', label: '户籍地址', children: employee.address || '-', span: 2 }
        ]}/>
        <h4 className="hrm-drawer-subtitle">任职信息</h4>
        <Descriptions column={3} size="small" bordered items={[
          { key: 'dept', label: '部门', children: employee.deptName || '-' },
          { key: 'post', label: '职位', children: employee.postName || '-' },
          { key: 'postLevel', label: '职级', children: employee.postLevel || '-' },
          { key: 'type', label: '聘用形式', children: employee.type != null ? EMPLOYEE_TYPE_LABELS[employee.type] : '-' },
          { key: 'leader', label: '直属上级', children: employee.leaderEmployeeName || '-' },
          { key: 'probation', label: '试用期', children: employee.probation != null ? `${employee.probation} 个月` : '-' },
          { key: 'entryTime', label: '入职时间', children: fmtDate(employee.entryTime) },
          { key: 'regularTime', label: '转正时间', children: fmtDate(employee.regularTime) },
          { key: 'companyAge', label: '司龄', children: employee.companyAge != null ? `${employee.companyAge} 年` : '-' },
          { key: 'workCity', label: '工作城市', children: employee.workCity || '-' },
          { key: 'workAddress', label: '工作地点', children: employee.workAddress || '-' },
          { key: 'workDetail', label: '详细地址', children: employee.workDetailAddress || '-' }
        ]}/>
      </div>
    </>}

    <Modal title="编辑档案信息" open={editOpen} onCancel={() => setEditOpen(false)}
      onOk={handleSave} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
      <Form layout="vertical" className="hrm-edit-form">
        <div className="hrm-edit-grid">
          {EDITABLE.map(field => <Form.Item key={field.name} label={field.label}>
            {renderControl(field, form, setForm)}
          </Form.Item>)}
        </div>
        <Alert message="员工编号、部门、职位、入职信息等由 HR 管理员维护，如需修改请联系 HR" type="info" showIcon/>
      </Form>
    </Modal>
  </section>
}
