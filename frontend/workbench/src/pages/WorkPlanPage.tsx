import { useEffect, useMemo, useState } from 'react'
import {
  Alert, Button, Descriptions, Empty, Form, Input, InputNumber, List, Modal, Pagination,
  Radio, Select, Segmented, Space, Spin, Tag, Upload, message
} from 'antd'
import { PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import {
  api, type SimpleDept, type SimpleUser, type WorkPlan, type WorkPlanInput,
  type WorkPlanTemplate, type WorkPlanTemplateField, type WorkTask, type WorkTaskInput
} from '../services/api'
import { loadWorkPlanPageResources } from '../services/workPlanLoading'

const STATUS: Record<string, string> = {
  draft: '草稿', active: '进行中', completed: '已完成', cancelled: '已取消',
  pending: '待完成', awaiting_confirmation: '待确认'
}
const PERIODS = [
  { label: '日计划', value: 'day' }, { label: '周计划', value: 'week' },
  { label: '月计划', value: 'month' }, { label: '季度计划', value: 'quarter' },
  { label: '年度计划', value: 'year' }, { label: '自定义周期', value: 'custom' }
]

function normalizeFieldType(fieldType: string) {
  return ({
    multi_text: 'textarea', select: 'single_select', department: 'dept', amount: 'money'
  } as Record<string, string>)[fieldType] || fieldType
}

function localDateTimeParts(value: number) {
  const date = new Date(value)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function dateInputValue(value?: string | number) {
  if (value == null) return undefined
  return (typeof value === 'number' ? localDateTimeParts(value) : value.replace(' ', 'T')).slice(0, 10)
}

function datetimeInputValue(value?: string | number) {
  if (value == null) return undefined
  return (typeof value === 'number' ? localDateTimeParts(value) : value.replace(' ', 'T').replace(/Z$/, '')).slice(0, 16)
}

function normalizeFieldValues(fields: WorkPlanTemplateField[], values?: Record<string, unknown>) {
  if (!values) return values
  const normalized = { ...values }
  fields.forEach(field => {
    const key = field.fieldKey || field.label
    if (field.fieldType === 'date' && typeof normalized[key] === 'string') normalized[key] = dateInputValue(normalized[key] as string)
    if (field.fieldType === 'datetime' && typeof normalized[key] === 'string') normalized[key] = datetimeInputValue(normalized[key] as string)
  })
  return normalized
}

function validatePlanDates(value: WorkPlanInput) {
  if (value.endDate < value.startDate) throw new Error('计划结束日期不能早于开始日期')
}

function taskDateWarning(dueAt?: string, plan?: WorkPlan, parent?: WorkTask) {
  if (!dueAt) return undefined
  const warnings: string[] = []
  const dueDate = dueAt.slice(0, 10)
  if (plan && (dueDate < dateInputValue(plan.startDate)! || dueDate > dateInputValue(plan.endDate)!)) warnings.push('截止时间超出计划周期')
  if (parent?.dueAt && dueAt > datetimeInputValue(parent.dueAt)!) warnings.push('截止时间晚于上级任务')
  if (new Date(dueAt).getTime() < Date.now()) warnings.push('截止时间已经逾期')
  return warnings.length ? `${warnings.join('；')}。系统仍允许保存，并会按截止时间提醒处理。` : undefined
}

function draftTaskDateWarning(startDate?: string, endDate?: string, tasks?: WorkTaskInput[]) {
  if (!startDate || !endDate) return undefined
  const count = (tasks || []).filter(task => task.dueAt && (task.dueAt.slice(0, 10) < startDate || task.dueAt.slice(0, 10) > endDate)).length
  return count ? `${count} 项任务的截止时间超出计划周期。系统仍允许保存，并会按截止时间提醒处理。` : undefined
}

const SUPPLEMENTAL_FIELD_TYPES = [
  { label: '单行文本', value: 'text' }, { label: '多行文本', value: 'textarea' },
  { label: '数字', value: 'number' }, { label: '日期', value: 'date' }, { label: '日期时间', value: 'datetime' }
]
const SUPPLEMENTAL_SECTIONS = [
  { label: '计划目标', value: 'plan' }, { label: '任务要求', value: 'task' },
  { label: '完成汇报', value: 'report' }, { label: '计划总结', value: 'summary' }
]

function AttachmentPicker({ value = [], onChange }: { value?: number[]; onChange?: (value: number[]) => void }) {
  const [uploading, setUploading] = useState(false)
  return <Upload
    fileList={value.map(id => ({ uid: String(id), name: `附件 #${id}`, status: 'done' as const }))}
    customRequest={async options => {
      setUploading(true)
      try {
        const result = await api.uploadWorkPlanAttachment(options.file as File)
        onChange?.([...new Set([...value, result.infraFileId])])
        options.onSuccess?.(result)
      } catch (error) {
        options.onError?.(error as Error)
        message.error(error instanceof Error ? error.message : '附件上传失败')
      } finally {
        setUploading(false)
      }
    }}
    onRemove={file => { onChange?.(value.filter(id => String(id) !== file.uid)); return true }}
  ><Button loading={uploading} icon={<UploadOutlined />}>上传附件</Button></Upload>
}

function parseOptions(field: WorkPlanTemplateField) {
  try {
    const parsed = JSON.parse(field.optionsJson || '[]')
    return Array.isArray(parsed) ? parsed as Array<{ label: string; value: string }> : []
  }
  catch { return [] }
}

function DynamicFields({ fields, section, users, departments, name = ['planFields'] }: {
  fields: WorkPlanTemplateField[]; section: WorkPlanTemplateField['section']; users: SimpleUser[];
  departments: SimpleDept[]; name?: (string | number)[]
}) {
  return <>{fields.filter(field => field.section === section).map(field => {
    const fieldName = [...name, field.fieldKey || field.label]
    const fieldType = normalizeFieldType(field.fieldType)
    const rules = [{ required: field.required, message: `请填写${field.label}` }]
    if (fieldType === 'user') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><Select showSearch optionFilterProp="label" options={users.map(user => ({ label: user.nickname, value: user.id }))} /></Form.Item>
    if (fieldType === 'dept') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><Select showSearch optionFilterProp="label" options={departments.map(dept => ({ label: dept.name, value: dept.id }))} /></Form.Item>
    if (fieldType === 'textarea') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><Input.TextArea placeholder={field.placeholder} rows={3} /></Form.Item>
    if (fieldType === 'single_select' || fieldType === 'multi_select' || fieldType === 'dict') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><Select mode={fieldType === 'multi_select' ? 'multiple' : undefined} options={parseOptions(field)} /></Form.Item>
    if (fieldType === 'integer' || fieldType === 'decimal' || fieldType === 'money') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.unit ? `${field.label}（${field.unit}）` : field.label} required={field.required} rules={rules}><InputNumber style={{ width: '100%' }} precision={fieldType === 'integer' ? 0 : 2} placeholder={field.placeholder} /></Form.Item>
    if (fieldType === 'date' || fieldType === 'datetime') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><Input type={fieldType === 'date' ? 'date' : 'datetime-local'} /></Form.Item>
    if (fieldType === 'attachment') return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><AttachmentPicker /></Form.Item>
    return <Form.Item key={fieldName.join('.')} name={fieldName} label={field.label} required={field.required} rules={rules}><Input placeholder={field.placeholder} /></Form.Item>
  })}</>
}

function TaskFields({ users, departments, fields }: { users: SimpleUser[]; departments: SimpleDept[]; fields: WorkPlanTemplateField[] }) {
  return <>
    <Form.Item name="parentTaskId" hidden><Input /></Form.Item>
    <Form.Item name="title" label="任务名称" rules={[{ required: true }]}><Input /></Form.Item>
    <Form.Item name="description" label="任务说明"><Input.TextArea rows={2} /></Form.Item>
    <Form.Item name="deliverableRequirement" label="交付要求"><Input.TextArea rows={2} /></Form.Item>
    <Form.Item name="assigneeUserId" label="责任人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={users.map(user => ({ label: user.nickname, value: user.id }))} /></Form.Item>
    <Form.Item name="dueAt" label="截止时间"><Input type="datetime-local" /></Form.Item>
    <Form.Item name="confirmationRequired" label="完成后需要确认" initialValue={false}><Radio.Group options={[{ label: '不需要', value: false }, { label: '需要', value: true }]} /></Form.Item>
    <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue('confirmationRequired') ? <Form.Item name="confirmerUserId" label="确认人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={users.map(user => ({ label: user.nickname, value: user.id }))} /></Form.Item> : null}</Form.Item>
    <DynamicFields fields={fields} section="task" users={users} departments={departments} name={['taskFields']} />
  </>
}

function TaskEditor({ open, task, parentTaskId, plan, users, departments, fields, onCancel, onSaved }: {
  open: boolean; task?: WorkTask; parentTaskId?: number; plan?: WorkPlan; users: SimpleUser[]; departments: SimpleDept[]; fields: WorkPlanTemplateField[];
  onCancel: () => void; onSaved: (value: WorkTaskInput) => Promise<void>
}) {
  const [form] = Form.useForm<WorkTaskInput>()
  const dueAt = Form.useWatch('dueAt', form)
  const effectiveParentTaskId = parentTaskId ?? task?.parentTaskId
  const warning = taskDateWarning(dueAt, plan, plan?.tasks.find(item => item.id === effectiveParentTaskId))
  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(task ? {
      parentTaskId: task.parentTaskId, title: task.title, description: task.description,
      deliverableRequirement: task.deliverableRequirement, assigneeUserId: task.assigneeUserId,
      dueAt: datetimeInputValue(task.dueAt), confirmationRequired: task.confirmationRequired,
      confirmerUserId: task.confirmerUserId, taskFields: task.taskFields, version: task.version
    } : { parentTaskId, confirmationRequired: false })
  }, [open, task, parentTaskId, form])
  return <Modal open={open} title={task ? '调整工作任务' : parentTaskId ? '分派给下属' : '新增工作任务'} okText="保存" onCancel={onCancel}
    onOk={async () => onSaved(await form.validateFields())} destroyOnHidden>
    <Form form={form} layout="vertical"><TaskFields users={users} departments={departments} fields={fields} />{warning && <Alert type="warning" showIcon message={warning} style={{ marginBottom: 16 }} />}<Form.Item name="reason" label="分派或调整原因" rules={[{ required: true }]}><Input.TextArea rows={2} /></Form.Item></Form>
  </Modal>
}

function PlanEditor({ open, plan, templates, users, departments, onCancel, onSaved }: {
  open: boolean; plan?: WorkPlan; templates: WorkPlanTemplate[]; users: SimpleUser[]; departments: SimpleDept[];
  onCancel: () => void; onSaved: (value: WorkPlanInput) => Promise<void>
}) {
  const [form] = Form.useForm<WorkPlanInput>()
  const [selected, setSelected] = useState<WorkPlanTemplate>()
  const fields = plan?.fieldDefinitions || selected?.fields || []
  useEffect(() => {
    if (!open) return
    if (plan) {
      setSelected(templates.find(item => item.versionId === plan.templateVersionId))
      form.setFieldsValue({
        title: plan.title, periodType: plan.periodType, startDate: dateInputValue(plan.startDate), endDate: dateInputValue(plan.endDate),
        templateVersionId: plan.templateVersionId, ownerUserId: plan.ownerUserId, objective: plan.objective,
        keyRequirements: plan.keyRequirements, planFields: normalizeFieldValues(plan.fieldDefinitions || [], plan.planFields), version: plan.version,
        tasks: (plan.tasks || []).filter(task => !task.parentTaskId).map(task => ({
          title: task.title, description: task.description, deliverableRequirement: task.deliverableRequirement,
          assigneeUserId: task.assigneeUserId, dueAt: datetimeInputValue(task.dueAt), confirmationRequired: task.confirmationRequired,
          confirmerUserId: task.confirmerUserId, taskFields: task.taskFields
        }))
      })
    } else { setSelected(undefined); form.resetFields() }
  }, [open, plan, templates, form])
  const chooseTemplate = (versionId: number) => {
    const template = templates.find(item => item.versionId === versionId)
    setSelected(template)
    form.setFieldsValue({
      periodType: template?.periodMode || 'month',
      tasks: (template?.presetItems || []).map(item => ({
        title: item.title, description: item.description, deliverableRequirement: item.deliverableRequirement,
        confirmationRequired: Boolean(item.confirmationRequired)
      }))
    })
  }
  return <Modal open={open} title={plan ? '编辑工作计划' : '新建工作计划'} okText="保存草稿" width={820}
    onCancel={onCancel} onOk={async () => {
      try {
        const value = await form.validateFields()
        validatePlanDates(value)
        await onSaved(value)
      } catch (cause) {
        if (cause instanceof Error) message.error(cause.message)
      }
    }} destroyOnHidden>
    <Form form={form} layout="vertical">
      <div className="work-plan-form-grid">
        <Form.Item name="templateVersionId" label="计划模板" rules={[{ required: true }]}><Select disabled={Boolean(plan)} options={templates.map(template => ({ label: template.name, value: template.versionId }))} onChange={chooseTemplate} /></Form.Item>
        <Form.Item name="title" label="计划名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="periodType" label="周期" rules={[{ required: true }]}><Select disabled options={PERIODS} /></Form.Item>
        <Form.Item name="ownerUserId" label="计划负责人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={users.map(user => ({ label: user.nickname, value: user.id }))} /></Form.Item>
        <Form.Item name="startDate" label="开始日期" rules={[{ required: true }]}><Input type="date" /></Form.Item>
        <Form.Item name="endDate" label="结束日期" rules={[{ required: true }]}><Input type="date" /></Form.Item>
      </div>
      <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => {
        const warning = draftTaskDateWarning(getFieldValue('startDate'), getFieldValue('endDate'), getFieldValue('tasks'))
        return warning ? <Alert type="warning" showIcon message={warning} style={{ marginBottom: 16 }} /> : null
      }}</Form.Item>
      <Form.Item name="objective" label="目标说明"><Input.TextArea rows={3} /></Form.Item>
      <Form.Item name="keyRequirements" label="重点要求"><Input.TextArea rows={2} /></Form.Item>
      <DynamicFields fields={fields} section="plan" users={users} departments={departments} />
      <Form.List name="tasks">
        {(taskRows, { add, remove }, { errors }) => <>
          <Space style={{ marginBottom: 12 }}><strong>工作任务</strong><Button size="small" icon={<PlusOutlined />} onClick={() => add({ confirmationRequired: false })}>添加任务</Button></Space>
          {taskRows.map((row, index) => <div className="work-plan-form-item" key={row.key}>
            <Space className="work-plan-form-item-heading"><strong>任务 {index + 1}</strong><Button type="link" danger onClick={() => remove(row.name)}>删除</Button></Space>
            <Form.Item noStyle name={row.name}><Form.Item noStyle><div /></Form.Item></Form.Item>
            <Form.Item name={[row.name, 'title']} label="任务名称" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name={[row.name, 'description']} label="任务说明"><Input.TextArea rows={2} /></Form.Item>
            <Form.Item name={[row.name, 'deliverableRequirement']} label="交付要求"><Input.TextArea rows={2} /></Form.Item>
            <div className="work-plan-form-grid">
              <Form.Item name={[row.name, 'assigneeUserId']} label="责任人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={users.map(user => ({ label: user.nickname, value: user.id }))} /></Form.Item>
              <Form.Item name={[row.name, 'dueAt']} label="截止时间"><Input type="datetime-local" /></Form.Item>
              <Form.Item name={[row.name, 'confirmationRequired']} label="需要确认"><Radio.Group options={[{ label: '不需要', value: false }, { label: '需要', value: true }]} /></Form.Item>
              <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => getFieldValue(['tasks', row.name, 'confirmationRequired']) ? <Form.Item name={[row.name, 'confirmerUserId']} label="确认人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={users.map(user => ({ label: user.nickname, value: user.id }))} /></Form.Item> : null}</Form.Item>
            </div>
            <DynamicFields fields={fields} section="task" users={users} departments={departments} name={['tasks', row.name, 'taskFields']} />
          </div>)}
          <Form.ErrorList errors={errors} />
        </>}
      </Form.List>
      <Form.List name="supplementalFields">{(rows, { add, remove }) => <>
        <Space style={{ marginBottom: 12 }}><strong>补充字段</strong><Button size="small" onClick={() => add({ section: 'plan', fieldType: 'text' })}>添加字段</Button></Space>
        {rows.map(row => <div className="work-plan-form-grid" key={row.key}>
          <Form.Item name={[row.name, 'label']} label="字段名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name={[row.name, 'section']} label="使用位置" rules={[{ required: true }]}><Select options={SUPPLEMENTAL_SECTIONS} /></Form.Item>
          <Form.Item name={[row.name, 'fieldType']} label="字段类型" rules={[{ required: true }]}><Select options={SUPPLEMENTAL_FIELD_TYPES} /></Form.Item>
          <Button type="link" danger onClick={() => remove(row.name)}>删除</Button>
        </div>)}
      </>}</Form.List>
      {plan?.status === 'active' && <Form.Item name="reason" label="调整说明（选填）"><Input.TextArea rows={2} /></Form.Item>}
    </Form>
  </Modal>
}

export default function WorkPlanPage({ permissions }: { permissions: string[] }) {
  const [summaryForm] = Form.useForm<{ summary: string; infraFileIds?: number[]; summaryFields?: Record<string, unknown> }>()
  const [reviewForm] = Form.useForm<{ decision: 'confirmed' | 'returned'; comment?: string }>()
  const [reportForm] = Form.useForm<{ completionSummary: string; infraFileIds?: number[]; reportFields?: Record<string, unknown> }>()
  const [view, setView] = useState<'plans' | 'tasks' | 'team'>('plans')
  const [plans, setPlans] = useState<WorkPlan[]>([]); const [total, setTotal] = useState(0)
  const [detail, setDetail] = useState<WorkPlan>(); const [templates, setTemplates] = useState<WorkPlanTemplate[]>([])
  const [users, setUsers] = useState<SimpleUser[]>([]); const [departments, setDepartments] = useState<SimpleDept[]>([])
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [auxiliaryErrors, setAuxiliaryErrors] = useState<string[]>([]); const [page, setPage] = useState(1)
  const [planModal, setPlanModal] = useState<{ plan?: WorkPlan }>(); const [taskModal, setTaskModal] = useState<{ task?: WorkTask; parentTaskId?: number }>()
  const [reportTask, setReportTask] = useState<WorkTask>(); const [reviewTask, setReviewTask] = useState<WorkTask>(); const [summaryModal, setSummaryModal] = useState(false)
  const canCreate = permissions.includes('zsjos:work-plan:create')
  const load = async () => {
    setLoading(true); setError(''); setAuxiliaryErrors([])
    try {
      const resources = await loadWorkPlanPageResources(api, page, permissions)
      setPlans(resources.page.list); setTotal(resources.page.total); setTemplates(resources.templates)
      setUsers(resources.users); setDepartments(resources.departments); setAuxiliaryErrors(resources.auxiliaryErrors)
      if (detail) setDetail(await api.workPlan(detail.id))
    } catch (cause) { setError(cause instanceof Error ? cause.message : '加载失败') }
    finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [page])
  const openPlan = async (id: number) => setDetail(await api.workPlan(id))
  const savePlan = async (value: WorkPlanInput) => {
    if (planModal?.plan) await api.updateWorkPlan(planModal.plan.id, value); else await api.createWorkPlan(value)
    setPlanModal(undefined); await load(); message.success('计划已保存')
  }
  const taskList = useMemo(() => plans.flatMap(plan => (plan.tasks || []).map(task => ({ ...task, planTitle: plan.title }))), [plans])
  const renderTaskActions = (task: WorkTask) => <Space wrap>
    {task.availableActions.includes('assign') && <Button size="small" onClick={() => setTaskModal({ task })}>调整任务</Button>}
    {task.availableActions.includes('decompose') && <Button size="small" onClick={() => setTaskModal({ parentTaskId: task.id })}>分派给下属</Button>}
    {task.availableActions.includes('complete') && <Button size="small" type="primary" onClick={() => setReportTask(task)}>完成汇报</Button>}
    {task.availableActions.includes('review') && <Button size="small" onClick={() => { reviewForm.setFieldsValue({ decision: 'confirmed' }); setReviewTask(task) }}>确认完成</Button>}
  </Space>
  return <div className="work-plan-page"><Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Space wrap><Segmented value={view} onChange={value => setView(value as typeof view)} options={[{ label: '我的计划', value: 'plans' }, { label: '我的任务', value: 'tasks' }, { label: '团队任务', value: 'team' }]} />{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setPlanModal({})}>新建计划</Button>}<Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button></Space>
    {error && <Alert type="error" message={error} showIcon action={<Button onClick={() => void load()}>重试</Button>} />}
    {auxiliaryErrors.length > 0 && <Alert type="warning" message={auxiliaryErrors.join('；')} showIcon action={<Button onClick={() => void load()}>重试</Button>} />}
    {loading ? <Spin /> : view === 'plans' ? <div className="work-plan-layout"><List className="work-plan-list-pane" bordered dataSource={plans} locale={{ emptyText: <Empty description="暂无计划" /> }} renderItem={plan => <List.Item onClick={() => void openPlan(plan.id)} style={{ cursor: 'pointer' }}><List.Item.Meta title={plan.title} description={<Space><Tag>{STATUS[plan.status]}</Tag><span>{plan.startDate} 至 {plan.endDate}</span></Space>} /></List.Item>} /><div className="work-plan-detail-pane">{detail ? <><Descriptions title={detail.title} bordered column={2}><Descriptions.Item label="状态">{detail.status === 'active' && detail.summaryReady ? '待总结' : STATUS[detail.status]}</Descriptions.Item><Descriptions.Item label="周期">{detail.startDate} 至 {detail.endDate}</Descriptions.Item><Descriptions.Item label="目标" span={2}>{detail.objective || '-'}</Descriptions.Item></Descriptions><Space wrap style={{ margin: '16px 0' }}>{detail.availableActions.includes('update') && <Button onClick={() => setPlanModal({ plan: detail })}>编辑计划</Button>}{detail.availableActions.includes('publish') && <Button type="primary" onClick={() => Modal.confirm({ title: '发布计划', content: detail.tasks?.length ? '发布后任务将进入待完成状态，确认继续？' : '当前计划暂无任务，发布后仍可继续拆解任务。确认发布？', onOk: async () => { await api.publishWorkPlan(detail.id, detail.version); await openPlan(detail.id); message.success('计划已发布') } })}>发布计划</Button>}{detail.availableActions.includes('assign') && <Button onClick={() => setTaskModal({})}>新增工作任务</Button>}{detail.availableActions.includes('close') && <Button type="primary" onClick={() => setSummaryModal(true)}>计划总结</Button>}</Space><List bordered dataSource={detail.tasks || []} locale={{ emptyText: '暂无工作任务，可先发布计划再逐步拆解' }} renderItem={task => <List.Item actions={[renderTaskActions(task)]}><List.Item.Meta title={<>{task.parentTaskId ? '└ ' : ''}{task.title} <Tag>{STATUS[task.status]}</Tag></>} description={`${task.description || ''} ${task.dueAt ? `截止 ${task.dueAt}` : ''}${task.blockedByChildren ? ` · 仍有 ${(task.totalChildCount || 0) - (task.completedChildCount || 0)} 个下级任务未完成，可先提交整体汇报` : ''}`} /></List.Item>} /></> : <Empty description="选择一个计划查看详情" />}</div></div> : <List bordered dataSource={taskList} locale={{ emptyText: <Empty description="暂无任务" /> }} renderItem={task => <List.Item actions={[renderTaskActions(task)]}><List.Item.Meta title={<>{task.title} <Tag>{STATUS[task.status]}</Tag></>} description={`${task.planTitle || ''} ${task.dueAt ? `截止 ${task.dueAt}` : ''}`} /></List.Item>} />}
    <Pagination current={page} pageSize={12} total={total} onChange={setPage} />
  </Space>
  <PlanEditor open={Boolean(planModal)} plan={planModal?.plan} templates={templates} users={users} departments={departments} onCancel={() => setPlanModal(undefined)} onSaved={savePlan} />
  <TaskEditor open={Boolean(taskModal)} task={taskModal?.task} parentTaskId={taskModal?.parentTaskId} plan={detail} users={users} departments={departments} fields={detail?.fieldDefinitions || []} onCancel={() => setTaskModal(undefined)} onSaved={async value => { if (!detail) return; if (taskModal?.task) await api.adjustWorkTask(taskModal.task.id, value); else await api.addWorkTask(detail.id, value); setTaskModal(undefined); await openPlan(detail.id); message.success('工作任务已保存') }} />
  <Modal open={Boolean(reportTask)} title="完成汇报" okText="提交汇报" onCancel={() => setReportTask(undefined)} onOk={async () => { const value = await reportForm.validateFields(); if (!reportTask) return; await api.submitWorkReport(reportTask.id, { ...value, infraFileIds: value.infraFileIds || [], version: reportTask.version }); reportForm.resetFields(); setReportTask(undefined); await load(); message.success('完成汇报已提交') }} destroyOnHidden><Form form={reportForm} layout="vertical"><Form.Item name="completionSummary" label="完成说明" rules={[{ required: true }]}><Input.TextArea rows={4} /></Form.Item><DynamicFields fields={detail?.fieldDefinitions || []} section="report" users={users} departments={departments} name={['reportFields']} /><Form.Item name="infraFileIds" label="附件"><AttachmentPicker /></Form.Item></Form></Modal>
  <Modal open={Boolean(reviewTask)} title="确认任务完成" okText="提交" onCancel={() => setReviewTask(undefined)} onOk={async () => { const value = await reviewForm.validateFields(); if (!reviewTask) return; await api.confirmWorkReport(reviewTask.id, { ...value, version: reviewTask.version }); reviewForm.resetFields(); setReviewTask(undefined); await load(); message.success(value.decision === 'confirmed' ? '已确认完成' : '已退回修改') }} destroyOnHidden><Form form={reviewForm} layout="vertical"><Form.Item name="decision" label="确认结果" rules={[{ required: true }]}><Radio.Group options={[{ label: '确认完成', value: 'confirmed' }, { label: '退回修改', value: 'returned' }]} /></Form.Item><Form.Item noStyle shouldUpdate>{({ getFieldValue }) => <Form.Item name="comment" label={getFieldValue('decision') === 'returned' ? '退回原因' : '确认意见'} rules={[{ required: getFieldValue('decision') === 'returned' }]}><Input.TextArea rows={3} /></Form.Item>}</Form.Item></Form></Modal>
  <Modal open={summaryModal} title="计划总结" okText="完成计划" onCancel={() => setSummaryModal(false)} onOk={async () => { const value = await summaryForm.validateFields(); if (!detail) return; await api.submitWorkPlanSummary(detail.id, { ...value, infraFileIds: value.infraFileIds || [], version: detail.version }); summaryForm.resetFields(); setSummaryModal(false); await load(); message.success('计划已完成') }}><Form form={summaryForm} layout="vertical">{detail && !detail.summaryReady && <Alert type="warning" showIcon message="当前仍有未完成任务。系统允许总结并结束计划，请在总结中说明未完成或延期情况。" style={{ marginBottom: 16 }} />}<Form.Item name="summary" label="整体总结" rules={[{ required: true }]}><Input.TextArea rows={5} /></Form.Item><DynamicFields fields={detail?.fieldDefinitions || []} section="summary" users={users} departments={departments} name={['summaryFields']} /><Form.Item name="infraFileIds" label="附件"><AttachmentPicker /></Form.Item></Form></Modal>
  </div>
}
