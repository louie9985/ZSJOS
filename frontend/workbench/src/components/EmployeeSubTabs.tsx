import { useCallback, useEffect, useState } from 'react'
import { Button, Descriptions, Empty, Form, Input, Modal, Skeleton, Tabs, Tag, message } from 'antd'
import { api, type HrmContract, type HrmCertificate, type HrmEducationExperience, type HrmWorkExperience, type HrmTrainingExperience, type HrmContact, type HrmSalaryCard, type HrmQuitInfo } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, CHANGE_REASON_LABELS, QUIT_TYPE_LABELS } from '../services/hrm'
import EmployeeSubTable from './EmployeeSubTable'
import HrmEmployeeMaterialFiles from './HrmEmployeeMaterialFiles'
import HrmEmployeeChangeRecordList from './HrmEmployeeChangeRecordList'
import dayjs from 'dayjs'

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

const CURRENT_YEAR = new Date().getFullYear()

/** 员工档案子表、材料附件与异动历史，内嵌于员工详情抽屉。 */
export default function EmployeeSubTabs({ employeeId, permissions }: { employeeId: number; permissions: string[] }) {
  const [activeTab, setActiveTab] = useState('contract')
  const canUpdate = permissions.includes('hrm:employee:update')
  const canDelete = permissions.includes('hrm:employee:delete')

  const contractLoading = useList<HrmContract>(employeeId, api.hrm.employee['contract']!.list)
  const certificateLoading = useList<HrmCertificate>(employeeId, api.hrm.employee['certificate']!.list)
  const educationLoading = useList<HrmEducationExperience>(employeeId, api.hrm.employee['education']!.list)
  const workLoading = useList<HrmWorkExperience>(employeeId, api.hrm.employee['workExperience']!.list)
  const trainingLoading = useList<HrmTrainingExperience>(employeeId, api.hrm.employee['training']!.list)
  const contactLoading = useList<HrmContact>(employeeId, api.hrm.employee['contact']!.list)

  return <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
    { key: 'contract', label: '合同', children: <ContractTab employeeId={employeeId} permissions={permissions} loading={contractLoading.loading} items={contractLoading.items} reload={contractLoading.reload}/> },
    { key: 'certificate', label: '证件', children: <CertificateTab employeeId={employeeId} permissions={permissions} loading={certificateLoading.loading} items={certificateLoading.items} reload={certificateLoading.reload}/> },
    { key: 'education', label: '教育经历', children: <EducationTab employeeId={employeeId} permissions={permissions} loading={educationLoading.loading} items={educationLoading.items} reload={educationLoading.reload}/> },
    { key: 'work', label: '工作经历', children: <WorkTab employeeId={employeeId} permissions={permissions} loading={workLoading.loading} items={workLoading.items} reload={workLoading.reload}/> },
    { key: 'training', label: '培训经历', children: <TrainingTab employeeId={employeeId} permissions={permissions} loading={trainingLoading.loading} items={trainingLoading.items} reload={trainingLoading.reload}/> },
    { key: 'contact', label: '联系人', children: <ContactTab employeeId={employeeId} permissions={permissions} loading={contactLoading.loading} items={contactLoading.items} reload={contactLoading.reload}/> },
    { key: 'salaryCard', label: '工资卡', children: <SalaryCardTab employeeId={employeeId} permissions={permissions}/> },
    { key: 'quit', label: '离职信息', children: <QuitInfoTab employeeId={employeeId}/> },
    { key: 'material', label: '材料附件', children: <HrmEmployeeMaterialFiles employeeId={employeeId} canUpdate={canUpdate}/> },
    { key: 'changeRecord', label: '异动记录', children: <HrmEmployeeChangeRecordList employeeId={employeeId}/> }
  ]}/>
}

/** 通用的加载一列子表的 hook，轮询切换 tab 时不重复加载。 */
function useList<T>(employeeId: number, listFn: (id: number) => Promise<T[]>) {
  const [items, setItems] = useState<T[]>([])
  const [loading, setLoading] = useState(false)
  const reload = useCallback(async () => {
    setLoading(true)
    try { setItems(await listFn(employeeId)) }
    catch { setItems([]) }
    finally { setLoading(false) }
  }, [employeeId, listFn])
  useEffect(() => { void reload() }, [reload])
  return { items, loading, reload }
}

function ContractTab({ employeeId, items, loading, reload, permissions }: {
  employeeId: number; items: HrmContract[]; loading: boolean; reload: () => void; permissions: string[]
}) {
  return <EmployeeSubTable<HrmContract>
    title="合同" items={items} loading={loading} onReload={reload}
    canCreate={permissions.includes('hrm:employee:update')} canUpdate={permissions.includes('hrm:employee:update')} canDelete={permissions.includes('hrm:employee:delete')}
    dateFields={['startTime', 'endTime', 'signTime']}
    fields={[
      { name: 'no', label: '合同编号' },
      { name: 'type', label: '合同类型', type: 'select', options: [{ value: 1, label: '固定期限' }, { value: 2, label: '无固定期限' }, { value: 3, label: '任务期限' }, { value: 4, label: '实习' }, { value: 5, label: '劳务' }, { value: 6, label: '返聘' }, { value: 7, label: '派遣' }, { value: 8, label: '借调' }, { value: 9, label: '其他' }] },
      { name: 'startTime', label: '合同开始', type: 'date' },
      { name: 'endTime', label: '合同结束', type: 'date' },
      { name: 'term', label: '合同期限(年)', type: 'number' },
      { name: 'status', label: '合同状态', type: 'select', options: [{ value: 0, label: '未执行' }, { value: 1, label: '执行中' }, { value: 2, label: '已到期' }] },
      { name: 'signCompany', label: '签署公司' },
      { name: 'signTime', label: '签署时间', type: 'date' },
      { name: 'expireRemind', label: '到期提醒', type: 'switch' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ]}
    columns={[
      { title: '合同编号', dataIndex: 'no', width: 130, render: (v?: string) => v || '-' },
      { title: '类型', dataIndex: 'type', width: 100, render: (v?: number) => v === 1 ? '固定' : v === 2 ? '无固定' : v === 3 ? '任务' : v === 4 ? '实习' : v === 5 ? '劳务' : v === 6 ? '返聘' : v === 7 ? '派遣' : v === 8 ? '借调' : v === 9 ? '其他' : '-' },
      { title: '起止', width: 180, render: (_, row) => `${fmtTime(row.startTime)} ~ ${fmtTime(row.endTime)}` },
      { title: '期限', dataIndex: 'term', width: 80, align: 'right', render: (v?: number) => v != null ? `${v} 年` : '-' },
      { title: '状态', dataIndex: 'status', width: 90, render: (v?: number) => v === 1 ? <Tag color="success">执行中</Tag> : v === 2 ? <Tag>已到期</Tag> : v === 0 ? <Tag>未执行</Tag> : '-' }
    ]}
    onCreate={async data => { await api.hrm.employee.contract.create({ ...data, employeeId } as HrmContract) }}
    onUpdate={async data => { await api.hrm.employee.contract.update({ ...data, employeeId } as HrmContract) }}
    onDelete={async id => { await api.hrm.employee.contract.delete(id) }}
  />
}

function CertificateTab({ employeeId, items, loading, reload, permissions }: {
  employeeId: number; items: HrmCertificate[]; loading: boolean; reload: () => void; permissions: string[]
}) {
  return <EmployeeSubTable<HrmCertificate>
    title="证件" items={items} loading={loading} onReload={reload}
    canCreate={permissions.includes('hrm:employee:update')} canUpdate={permissions.includes('hrm:employee:update')} canDelete={permissions.includes('hrm:employee:delete')}
    dateFields={['startTime', 'endTime', 'issuingTime']}
    fields={[
      { name: 'name', label: '证件名称', required: true },
      { name: 'level', label: '等级' },
      { name: 'no', label: '证书编号' },
      { name: 'startTime', label: '有效期开始', type: 'date' },
      { name: 'endTime', label: '有效期结束', type: 'date' },
      { name: 'issuingAuthority', label: '发证机构' },
      { name: 'issuingTime', label: '发证时间', type: 'date' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ]}
    columns={[
      { title: '证件名称', dataIndex: 'name', width: 140, render: (v?: string) => v || '-' },
      { title: '证书编号', dataIndex: 'no', width: 140, render: (v?: string) => v || '-' },
      { title: '有效期', width: 170, render: (_, row) => `${fmtTime(row.startTime)} ~ ${fmtTime(row.endTime)}` },
      { title: '发证机构', dataIndex: 'issuingAuthority', width: 140, render: (v?: string) => v || '-' }
    ]}
    onCreate={async data => { await api.hrm.employee.certificate.create({ ...data, employeeId } as HrmCertificate) }}
    onUpdate={async data => { await api.hrm.employee.certificate.update({ ...data, employeeId } as HrmCertificate) }}
    onDelete={async id => { await api.hrm.employee.certificate.delete(id) }}
  />
}

function EducationTab({ employeeId, items, loading, reload, permissions }: {
  employeeId: number; items: HrmEducationExperience[]; loading: boolean; reload: () => void; permissions: string[]
}) {
  const education = useDict(HRM_DICT.EMPLOYEE_EDUCATION)
  return <EmployeeSubTable<HrmEducationExperience>
    title="教育经历" items={items} loading={loading} onReload={reload}
    canCreate={permissions.includes('hrm:employee:update')} canUpdate={permissions.includes('hrm:employee:update')} canDelete={permissions.includes('hrm:employee:delete')}
    dateFields={['admissionTime', 'graduationTime']}
    fields={[
      { name: 'education', label: '学历', type: 'select', required: true, options: education.options },
      { name: 'graduateSchool', label: '毕业院校' },
      { name: 'major', label: '专业' },
      { name: 'admissionTime', label: '入学时间', type: 'date' },
      { name: 'graduationTime', label: '毕业时间', type: 'date' },
      { name: 'teachingMethods', label: '培养方式', type: 'select', options: [{ value: 1, label: '全日制' }, { value: 2, label: '成人' }, { value: 3, label: '远程' }, { value: 4, label: '自学' }, { value: 5, label: '其他' }] },
      { name: 'firstDegree', label: '第一学历', type: 'switch' }
    ]}
    columns={[
      { title: '学历', dataIndex: 'education', width: 100, render: (v?: number) => v != null ? (education.labels[String(v)] || v) : '-' },
      { title: '毕业院校', dataIndex: 'graduateSchool', width: 160, render: (v?: string) => v || '-' },
      { title: '专业', dataIndex: 'major', width: 130, render: (v?: string) => v || '-' },
      { title: '入学-毕业', width: 180, render: (_, row) => `${fmtTime(row.admissionTime)} ~ ${fmtTime(row.graduationTime)}` },
      { title: '第一学历', dataIndex: 'firstDegree', width: 90, align: 'center', render: (v?: boolean) => v ? <Tag color="success">是</Tag> : <Tag>否</Tag> }
    ]}
    onCreate={async data => { await api.hrm.employee.education.create({ ...data, employeeId } as HrmEducationExperience) }}
    onUpdate={async data => { await api.hrm.employee.education.update({ ...data, employeeId } as HrmEducationExperience) }}
    onDelete={async id => { await api.hrm.employee.education.delete(id) }}
  />
}

function WorkTab({ employeeId, items, loading, reload, permissions }: {
  employeeId: number; items: HrmWorkExperience[]; loading: boolean; reload: () => void; permissions: string[]
}) {
  return <EmployeeSubTable<HrmWorkExperience>
    title="工作经历" items={items} loading={loading} onReload={reload}
    canCreate={permissions.includes('hrm:employee:update')} canUpdate={permissions.includes('hrm:employee:update')} canDelete={permissions.includes('hrm:employee:delete')}
    dateFields={['startTime', 'endTime']}
    fields={[
      { name: 'workUnit', label: '工作单位', required: true },
      { name: 'postName', label: '职位', required: true },
      { name: 'startTime', label: '开始时间', type: 'date' },
      { name: 'endTime', label: '结束时间', type: 'date' },
      { name: 'reason', label: '离职原因' },
      { name: 'witnessName', label: '证明人' },
      { name: 'witnessPhone', label: '证明人电话' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ]}
    columns={[
      { title: '工作单位', dataIndex: 'workUnit', width: 160, render: (v?: string) => v || '-' },
      { title: '职位', dataIndex: 'postName', width: 130, render: (v?: string) => v || '-' },
      { title: '起止', width: 180, render: (_, row) => `${fmtTime(row.startTime)} ~ ${fmtTime(row.endTime)}` },
      { title: '证明人', dataIndex: 'witnessName', width: 100, render: (v?: string) => v || '-' }
    ]}
    onCreate={async data => { await api.hrm.employee.workExperience.create({ ...data, employeeId } as HrmWorkExperience) }}
    onUpdate={async data => { await api.hrm.employee.workExperience.update({ ...data, employeeId } as HrmWorkExperience) }}
    onDelete={async id => { await api.hrm.employee.workExperience.delete(id) }}
  />
}

function TrainingTab({ employeeId, items, loading, reload, permissions }: {
  employeeId: number; items: HrmTrainingExperience[]; loading: boolean; reload: () => void; permissions: string[]
}) {
  return <EmployeeSubTable<HrmTrainingExperience>
    title="培训经历" items={items} loading={loading} onReload={reload}
    canCreate={permissions.includes('hrm:employee:update')} canUpdate={permissions.includes('hrm:employee:update')} canDelete={permissions.includes('hrm:employee:delete')}
    dateFields={['startTime', 'endTime']}
    fields={[
      { name: 'course', label: '培训课程', required: true },
      { name: 'organizationName', label: '培训机构' },
      { name: 'startTime', label: '开始时间', type: 'date' },
      { name: 'endTime', label: '结束时间', type: 'date' },
      { name: 'duration', label: '时长' },
      { name: 'result', label: '结果' },
      { name: 'certificateName', label: '证书名称' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ]}
    columns={[
      { title: '课程', dataIndex: 'course', width: 160, render: (v?: string) => v || '-' },
      { title: '机构', dataIndex: 'organizationName', width: 150, render: (v?: string) => v || '-' },
      { title: '起止', width: 180, render: (_, row) => `${fmtTime(row.startTime)} ~ ${fmtTime(row.endTime)}` },
      { title: '证书', dataIndex: 'certificateName', width: 120, render: (v?: string) => v || '-' }
    ]}
    onCreate={async data => { await api.hrm.employee.training.create({ ...data, employeeId } as HrmTrainingExperience) }}
    onUpdate={async data => { await api.hrm.employee.training.update({ ...data, employeeId } as HrmTrainingExperience) }}
    onDelete={async id => { await api.hrm.employee.training.delete(id) }}
  />
}

function ContactTab({ employeeId, items, loading, reload, permissions }: {
  employeeId: number; items: HrmContact[]; loading: boolean; reload: () => void; permissions: string[]
}) {
  return <EmployeeSubTable<HrmContact>
    title="联系人" items={items} loading={loading} onReload={reload}
    canCreate={permissions.includes('hrm:employee:update')} canUpdate={permissions.includes('hrm:employee:update')} canDelete={permissions.includes('hrm:employee:delete')}
    fields={[
      { name: 'name', label: '姓名', required: true },
      { name: 'relation', label: '关系' },
      { name: 'phone', label: '电话' },
      { name: 'workUnit', label: '工作单位' },
      { name: 'postName', label: '职位' },
      { name: 'address', label: '地址' }
    ]}
    columns={[
      { title: '姓名', dataIndex: 'name', width: 120, render: (v?: string) => v || '-' },
      { title: '关系', dataIndex: 'relation', width: 100, render: (v?: string) => v || '-' },
      { title: '电话', dataIndex: 'phone', width: 140, render: (v?: string) => v || '-' },
      { title: '工作单位', dataIndex: 'workUnit', width: 160, render: (v?: string) => v || '-' }
    ]}
    onCreate={async data => { await api.hrm.employee.contact.create({ ...data, employeeId } as HrmContact) }}
    onUpdate={async data => { await api.hrm.employee.contact.update({ ...data, employeeId } as HrmContact) }}
    onDelete={async id => { await api.hrm.employee.contact.delete(id) }}
  />
}

function SalaryCardTab({ employeeId, permissions }: { employeeId: number; permissions: string[] }) {
  const [card, setCard] = useState<HrmSalaryCard>()
  const [loading, setLoading] = useState(false)
  const [editable, setEditable] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<HrmSalaryCard>()
  const canUpdate = permissions.includes('hrm:employee:update')

  const load = useCallback(async () => {
    setLoading(true)
    try { setCard(await api.hrm.employee.salaryCard.get(employeeId)) }
    catch { setCard(undefined) }
    finally { setLoading(false) }
  }, [employeeId])

  useEffect(() => { void load() }, [load])

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await api.hrm.employee.salaryCard.save(employeeId, values)
      message.success('已保存')
      setEditable(false); void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  if (loading && !card) return <Skeleton active paragraph={{ rows: 2 }}/>
  if (!card) return <>
    <Empty description="暂无工资卡信息"/>
    {canUpdate && <Button type="primary" onClick={() => { form.resetFields(); setEditable(true) }}>添加</Button>}
  </>

  return <>
    <Descriptions className="hrm-summary" size="small" column={1} bordered items={[
      { key: 'card', label: '银行卡号', children: card.bankCardNumber || '-' },
      { key: 'bank', label: '开户银行', children: card.bankName || '-' },
      { key: 'branch', label: '开户支行', children: card.bankBranchName || '-' },
      { key: 'area', label: '开户地区', children: card.bankAreaName || '-' }
    ]}/>
    {canUpdate && <Button style={{ marginTop: 8 }} onClick={() => { form.setFieldsValue(card); setEditable(true) }}>编辑</Button>}
    {editable && <Modal title="编辑工资卡" open onCancel={() => setEditable(false)} onOk={() => void handleSave()} confirmLoading={saving} width="min(760px, 96vw)">
      <Form form={form} layout="vertical">
        <Form.Item name="bankCardNumber" label="银行卡号" rules={[{ required: true, message: '请输入银行卡号' }]}><Input/></Form.Item>
        <Form.Item name="bankName" label="开户银行"><Input/></Form.Item>
        <Form.Item name="bankBranchName" label="开户支行"><Input/></Form.Item>
      </Form>
    </Modal>}
  </>
}

function QuitInfoTab({ employeeId }: { employeeId: number }) {
  const [info, setInfo] = useState<HrmQuitInfo>()
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    setLoading(true)
    api.hrm.employee.quitInfo.get(employeeId).then(setInfo).catch(() => setInfo(undefined)).finally(() => setLoading(false))
  }, [employeeId])
  if (loading && !info) return <Skeleton active paragraph={{ rows: 2 }}/>
  if (!info) return <Empty description="暂无离职信息"/>
  return <Descriptions column={2} size="small" bordered items={[
    { key: 'type', label: '离职类型', children: info.type != null ? (QUIT_TYPE_LABELS[info.type] || info.type) : '-' },
    { key: 'reason', label: '离职原因', children: info.reason != null ? (CHANGE_REASON_LABELS[info.reason] || info.reason) : '-' },
    { key: 'plan', label: '计划离职时间', children: fmtTime(info.planQuitTime) },
    { key: 'salary', label: '薪资结算时间', children: fmtTime(info.salarySettlementTime) },
    { key: 'remark', label: '备注', children: info.remark || '-', span: 2 }
  ]}/>
}

/** 通用权限判断 hook，供无 permissions 参数的子 tab 复用。 */
function usePerm(reload: () => void) {
  return { canUpdate: true, canDelete: true, reload }
}
