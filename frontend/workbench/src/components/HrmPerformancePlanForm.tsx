import { useEffect, useState } from 'react'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import {
  Alert, Button, DatePicker, Drawer, Form, Input, InputNumber, Select, Space, Switch, Tabs, message
} from 'antd'
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  api,
  type HrmAssessmentTemplate,
  type HrmPerformanceHandlerStage,
  type HrmPerformancePlan,
  type HrmPerformancePlanSave,
  type HrmPerformanceResultTemplate
} from '../services/api'
import { HRM_DICT } from '../services/hrm'
import { useDict } from '../services/useDict'
import DeptTreeSelect from './DeptTreeSelect'
import HrmEmployeePicker from './HrmEmployeePicker'
import PerformanceAssessmentConfigFields from './PerformanceAssessmentConfigFields'

type PlanFormValues = HrmPerformancePlanSave & {
  cycleMonth?: Dayjs
  cycleYear?: Dayjs
  customDateRange?: [Dayjs, Dayjs]
  paidForMonthValue?: Dayjs
}

const CYCLE_TYPES = [
  { value: 1, label: '月度' }, { value: 2, label: '季度' }, { value: 3, label: '上半年' },
  { value: 4, label: '下半年' }, { value: 5, label: '全年' }, { value: 6, label: '其他' }
]
const QUARTERS = [1, 2, 3, 4].map(value => ({ value, label: `第${['一', '二', '三', '四'][value - 1]}季度` }))
const RATER_TYPES = [
  { value: 1, label: '上级' }, { value: 2, label: '部门负责人' },
  { value: 3, label: '指定员工' }, { value: 4, label: '被考核人' }
]
const HANDLER_TYPES = RATER_TYPES.filter(item => item.value !== 4)

function defaultHandler(): HrmPerformanceHandlerStage {
  return { type: 2, level: 1 }
}

function defaultValues(): PlanFormValues {
  const now = dayjs()
  return {
    name: '', cycleType: 1, cycleMonth: now.startOf('month'), assessmentTemplateId: undefined as unknown as number,
    assessmentConfig: { name: '', scoreCalculation: 1, upperLimitType: 1, upperLimitScore: 100, dimensions: [] },
    resultTemplateId: undefined as unknown as number, resultConfig: { name: '', levels: [] },
    quotaSettingType: 1, targetConfirmation: false,
    reviewStages: [
      { name: '员工自评', rater: { type: 4 }, weight: 30, scoringType: 1, visibleContent: 2, requiredSetting: false, rejectAuthority: false },
      { name: '直属上级评分', rater: { type: 1, level: 1 }, weight: 70, scoringType: 1, visibleContent: 2, requiredSetting: true, rejectAuthority: true }
    ],
    resultAudit: false, resultAuditStages: [defaultHandler()], resultConfirmation: false,
    appealStages: [defaultHandler()], appealTimeoutDays: 2, appealTimeoutAction: 1,
    syncToSalary: false, scopes: [{ type: 1, employeeIds: [], deptIds: [] }]
  }
}

function toFormValues(plan: HrmPerformancePlan): PlanFormValues {
  const values = defaultValues()
  const cycleDate = plan.startTime ? dayjs(plan.startTime) : dayjs()
  return {
    ...values,
    ...plan,
    assessmentTemplateId: plan.assessmentTemplateId as number,
    assessmentConfig: plan.assessmentConfig || values.assessmentConfig,
    resultTemplateId: plan.resultTemplateId as number,
    resultConfig: plan.resultConfig || values.resultConfig,
    quotaSettingType: plan.quotaSettingType || 1,
    targetConfirmation: Boolean(plan.targetConfirmation),
    reviewStages: plan.reviewStages?.length ? plan.reviewStages : values.reviewStages,
    resultAudit: Boolean(plan.resultAudit),
    resultAuditStages: plan.resultAuditStages?.length ? plan.resultAuditStages : values.resultAuditStages,
    resultConfirmation: Boolean(plan.resultConfirmation),
    appealStages: plan.appealStages?.length ? plan.appealStages : values.appealStages,
    appealTimeoutDays: plan.appealTimeoutDays || 2,
    appealTimeoutAction: plan.appealTimeoutAction || 1,
    syncToSalary: Boolean(plan.syncToSalary),
    scopes: plan.scopes?.length ? plan.scopes : values.scopes,
    cycleMonth: plan.cycleType === 1 ? cycleDate : undefined,
    cycleYear: plan.cycleType !== 1 && plan.cycleType !== 6 ? cycleDate : undefined,
    customDateRange: plan.cycleType === 6 && plan.startTime && plan.endTime
      ? [dayjs(plan.startTime), dayjs(plan.endTime)] : undefined,
    paidForMonthValue: plan.paidForMonth ? dayjs(`${plan.paidForMonth}-01`) : undefined
  }
}

function cloneAssessmentTemplate(template: HrmAssessmentTemplate) {
  return {
    name: template.name,
    scoreCalculation: template.scoreCalculation || 1,
    upperLimitType: template.upperLimitType || 1,
    upperLimitScore: template.upperLimitScore ?? 100,
    dimensions: (template.dimensions || []).map(dimension => ({
      ...dimension,
      quotaType: dimension.quotaType || 1,
      allowEdit: Boolean(dimension.allowEdit),
      quotas: (dimension.quotas || []).map(quota => ({
        ...quota,
        illustrate: quota.illustrate ?? quota.description,
        scoreType: quota.scoreType || 1
      }))
    }))
  }
}

function HandlerFields({ namePrefix, absolutePrefix, allowSelf = false }: {
  namePrefix: Array<string | number>
  absolutePrefix: Array<string | number>
  allowSelf?: boolean
}) {
  const form = Form.useFormInstance<PlanFormValues>()
  return <Space align="start" wrap>
    <Form.Item name={[...namePrefix, 'type']} label="处理人" rules={[{ required: true, message: '请选择处理人' }]}>
      <Select
        style={{ width: 160 }}
        options={allowSelf ? RATER_TYPES : HANDLER_TYPES}
        onChange={(type: number) => {
          const levelPath = [...absolutePrefix, 'level'] as Parameters<typeof form.setFieldValue>[0]
          const employeePath = [...absolutePrefix, 'employeeId'] as Parameters<typeof form.setFieldValue>[0]
          form.setFieldValue(levelPath, type === 1 || type === 2 ? 1 : undefined)
          form.setFieldValue(employeePath, undefined)
        }}
      />
    </Form.Item>
    <Form.Item noStyle shouldUpdate={(before, after) =>
      before && after && JSON.stringify(before) !== JSON.stringify(after)
    }>
      {({ getFieldValue }) => {
        const type = getFieldValue([...absolutePrefix, 'type']) as number | undefined
        if (type === 1 || type === 2) {
          return <Form.Item name={[...namePrefix, 'level']} label="层级" rules={[{ required: true, message: '请输入层级' }]}>
            <InputNumber min={1} max={10} precision={0} style={{ width: 130 }}/>
          </Form.Item>
        }
        if (type === 3) {
          return <Form.Item name={[...namePrefix, 'employeeId']} label="指定员工" rules={[{ required: true, message: '请选择员工' }]}>
            <HrmEmployeePicker style={{ width: 260 }}/>
          </Form.Item>
        }
        return type === 4 ? <span className="hrm-form-inline-note">当前被考核员工</span> : null
      }}
    </Form.Item>
  </Space>
}

function HandlerStageList({ name }: { name: 'resultAuditStages' | 'appealStages' }) {
  return <Form.List name={name}>
    {(fields, { add, remove }) => <Space direction="vertical" size="small" style={{ width: '100%' }}>
      {fields.map(({ key, name: fieldName }) => <div key={key} className="hrm-quota-row">
        <Space align="start">
          <HandlerFields namePrefix={[fieldName]} absolutePrefix={[name, fieldName]}/>
          <Button type="text" danger icon={<DeleteOutlined/>} title="删除节点" disabled={fields.length <= 1} onClick={() => remove(fieldName)}/>
        </Space>
      </div>)}
      <Button type="dashed" icon={<PlusOutlined/>} disabled={fields.length >= 3} onClick={() => add({ type: 1, level: 1 })}>添加处理节点</Button>
    </Space>}
  </Form.List>
}

function validateConfiguration(values: PlanFormValues) {
  for (const scope of values.scopes) {
    if (scope.type === 1 && !(scope.employeeIds?.length || scope.deptIds?.length)) throw new Error('员工部门范围至少选择员工或部门')
    if (scope.type === 2 && (!scope.employeeType || !scope.employeeStatuses?.length)) throw new Error('聘用形式范围必须选择聘用形式和员工状态')
  }
  const dimensions = values.assessmentConfig.dimensions || []
  if (!dimensions.length) throw new Error('考核配置至少需要一个维度')
  const dimensionWeight = dimensions.reduce((sum, dimension) => sum + Number(dimension.weight || 0), 0)
  if (Math.abs(dimensionWeight - 100) > 0.001) throw new Error('考核维度权重合计必须等于 100%')
  for (const dimension of dimensions) {
    if (!dimension.quotas?.length) throw new Error(`维度「${dimension.name || '-'}」至少需要一个指标`)
    const quotaWeight = dimension.quotas.reduce((sum, quota) => sum + Number(quota.weight || 0), 0)
    if (!dimension.allowEdit && Math.abs(quotaWeight - 100) > 0.001) throw new Error(`维度「${dimension.name || '-'}」的指标权重合计必须等于 100%`)
    if (dimension.allowEdit && quotaWeight > 100.001) throw new Error(`维度「${dimension.name || '-'}」的指标权重合计不能超过 100%`)
  }
  const stageWeight = values.reviewStages.reduce((sum, stage) => sum + Number(stage.weight || 0), 0)
  if (Math.abs(stageWeight - 100) > 0.001) throw new Error('评分阶段权重合计必须等于 100%')
  const stageKeys = values.reviewStages.map(stage => `${stage.rater.type}:${stage.rater.employeeId ?? stage.rater.level ?? ''}`)
  if (new Set(stageKeys).size !== stageKeys.length) throw new Error('评分人不能重复')
  const levels = [...(values.resultConfig.levels || [])].sort((a, b) => a.minScore - b.minScore)
  if (!levels.length) throw new Error('结果配置至少需要一个等级')
  if (levels[0].minScore !== 0 || levels.at(-1)?.maxScore !== 100 || levels.some((level, index) => index > 0 && Math.abs(level.minScore - levels[index - 1].maxScore - 0.01) > 0.0001)) {
    throw new Error('结果等级区间必须连续覆盖 0 到 100 分，相邻区间间隔 0.01')
  }
}

function buildPayload(values: PlanFormValues): HrmPerformancePlanSave {
  validateConfiguration(values)
  const { cycleMonth, cycleYear, customDateRange, paidForMonthValue, ...rest } = values
  const payload: HrmPerformancePlanSave = { ...rest }
  if (values.cycleType === 1 && cycleMonth) {
    payload.cycle = cycleMonth.format('YYYY-MM')
    payload.startTime = cycleMonth.startOf('month').valueOf()
    payload.endTime = cycleMonth.endOf('month').valueOf()
    payload.quarter = undefined
  } else if (values.cycleType === 6 && customDateRange) {
    payload.cycle = `${customDateRange[0].format('YYYY-MM-DD')} ~ ${customDateRange[1].format('YYYY-MM-DD')}`
    payload.startTime = customDateRange[0].startOf('day').valueOf()
    payload.endTime = customDateRange[1].endOf('day').valueOf()
    payload.quarter = undefined
  } else if (cycleYear) {
    const year = cycleYear.year()
    let startMonth = 0
    let endMonth = 11
    if (values.cycleType === 2) {
      startMonth = ((values.quarter || 1) - 1) * 3
      endMonth = startMonth + 2
    } else if (values.cycleType === 3) endMonth = 5
    else if (values.cycleType === 4) startMonth = 6
    payload.cycle = String(year)
    payload.startTime = dayjs().year(year).month(startMonth).startOf('month').valueOf()
    payload.endTime = dayjs().year(year).month(endMonth).endOf('month').valueOf()
    if (values.cycleType !== 2) payload.quarter = undefined
  }
  if (values.quotaSettingType === 1) {
    payload.targetConfirmation = false
    payload.targetConfirmationStage = undefined
  } else if (!values.targetConfirmation) payload.targetConfirmationStage = undefined
  if (!values.resultAudit) payload.resultAuditStages = undefined
  if (!values.resultConfirmation) payload.appealStages = undefined
  payload.paidForMonth = values.syncToSalary ? paidForMonthValue?.format('YYYY-MM') : undefined
  return payload
}

export default function HrmPerformancePlanForm({ open, plan, onClose, onSaved }: {
  open: boolean
  plan?: HrmPerformancePlan
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<PlanFormValues>()
  const [saving, setSaving] = useState(false)
  const [templateLoading, setTemplateLoading] = useState(false)
  const [templateError, setTemplateError] = useState('')
  const [assessmentTemplates, setAssessmentTemplates] = useState<HrmAssessmentTemplate[]>([])
  const [resultTemplates, setResultTemplates] = useState<HrmPerformanceResultTemplate[]>([])
  const employeeTypes = useDict(HRM_DICT.EMPLOYEE_TYPE)
  const employeeStatuses = useDict(HRM_DICT.EMPLOYEE_STATUS)
  const cycleType = Form.useWatch('cycleType', form)
  const quotaSettingType = Form.useWatch('quotaSettingType', form)
  const targetConfirmation = Form.useWatch('targetConfirmation', form)
  const resultAudit = Form.useWatch('resultAudit', form)
  const resultConfirmation = Form.useWatch('resultConfirmation', form)
  const syncToSalary = Form.useWatch('syncToSalary', form)

  const loadTemplates = async () => {
    setTemplateLoading(true); setTemplateError('')
    try {
      const [assessmentList, resultList] = await Promise.all([
        api.hrm.perfCfg.assessmentTemplate.simpleList(), api.hrm.perfCfg.resultTemplate.simpleList()
      ])
      setAssessmentTemplates(assessmentList)
      setResultTemplates(resultList)
    } catch (e) {
      setTemplateError(e instanceof Error ? e.message : '绩效模板加载失败')
    } finally {
      setTemplateLoading(false)
    }
  }

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(plan ? toFormValues(plan) : defaultValues())
    void loadTemplates()
  }, [open, plan, form])

  const changeAssessmentTemplate = async (id: number) => {
    setTemplateLoading(true)
    try {
      const template = await api.hrm.perfCfg.assessmentTemplate.get(id)
      if (form.getFieldValue('assessmentTemplateId') === id) form.setFieldValue('assessmentConfig', cloneAssessmentTemplate(template))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '考核模板加载失败')
    } finally {
      setTemplateLoading(false)
    }
  }

  const changeResultTemplate = async (id: number) => {
    setTemplateLoading(true)
    try {
      const template = await api.hrm.perfCfg.resultTemplate.get(id)
      if (form.getFieldValue('resultTemplateId') === id) {
        form.setFieldValue('resultConfig', { name: template.name, levels: template.levels.map(level => ({ ...level })) })
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '结果模板加载失败')
    } finally {
      setTemplateLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      const payload = buildPayload(await form.validateFields())
      setSaving(true)
      if (plan) await api.hrm.performance.plan.update(payload)
      else await api.hrm.performance.plan.create(payload)
      message.success(plan ? '已保存' : '已创建')
      onClose(); onSaved()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  const basicTab = <Space direction="vertical" size="small" style={{ width: '100%' }}>
    <Form.Item name="name" label="计划名称" rules={[{ required: true, message: '请输入计划名称' }, { max: 50 }]}>
      <Input placeholder="如 2026 年第三季度绩效计划"/>
    </Form.Item>
    <Space size="large" align="start" wrap>
      <Form.Item name="cycleType" label="周期类型" rules={[{ required: true, message: '请选择周期类型' }]}>
        <Select style={{ width: 180 }} options={CYCLE_TYPES} onChange={() => form.setFieldValue('quarter', undefined)}/>
      </Form.Item>
      {cycleType === 1 && <Form.Item name="cycleMonth" label="考核月份" rules={[{ required: true, message: '请选择月份' }]}><DatePicker picker="month"/></Form.Item>}
      {cycleType !== 1 && cycleType !== 6 && <Form.Item name="cycleYear" label="考核年份" rules={[{ required: true, message: '请选择年份' }]}><DatePicker picker="year"/></Form.Item>}
      {cycleType === 2 && <Form.Item name="quarter" label="季度" rules={[{ required: true, message: '请选择季度' }]}><Select style={{ width: 160 }} options={QUARTERS}/></Form.Item>}
      {cycleType === 6 && <Form.Item name="customDateRange" label="考核日期" rules={[{ required: true, message: '请选择起止日期' }]}><DatePicker.RangePicker/></Form.Item>}
    </Space>
    <Form.Item name="description" label="考核说明"><Input.TextArea rows={3} maxLength={200} showCount/></Form.Item>
    {(employeeTypes.error || employeeStatuses.error) && <Alert type="error" showIcon message="员工字典加载失败" action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => { void employeeTypes.reload(); void employeeStatuses.reload() }}>重试</Button>}/>}
    <Form.Item label="考评范围" required>
      <Form.List name="scopes">
        {(fields, { add, remove }) => <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {fields.map(({ key, name }) => <div key={key} className="hrm-quota-row">
            <Form.Item name={[name, 'type']} label="范围类型" rules={[{ required: true, message: '请选择范围类型' }]}>
              <Select style={{ width: 180 }} options={[{ value: 1, label: '员工部门' }, { value: 2, label: '聘用形式' }]}/>
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) => getFieldValue(['scopes', name, 'type']) === 1 ? <Space align="start" wrap>
                <Form.Item name={[name, 'employeeIds']} label="员工"><HrmEmployeePicker mode="multiple" style={{ width: 300 }}/></Form.Item>
                <Form.Item name={[name, 'deptIds']} label="部门"><DeptTreeSelect multiple treeCheckable style={{ width: 300 }}/></Form.Item>
              </Space> : <Space align="start" wrap>
                <Form.Item name={[name, 'employeeType']} label="聘用形式" rules={[{ required: true, message: '请选择聘用形式' }]}>
                  <Select style={{ width: 180 }} loading={employeeTypes.loading} options={employeeTypes.options}/>
                </Form.Item>
                <Form.Item name={[name, 'employeeStatuses']} label="员工状态" rules={[{ required: true, message: '请选择员工状态' }]}>
                  <Select mode="multiple" style={{ width: 300 }} loading={employeeStatuses.loading} options={employeeStatuses.options}/>
                </Form.Item>
              </Space>}
            </Form.Item>
            <Button type="text" danger icon={<DeleteOutlined/>} title="删除范围" disabled={fields.length <= 1} onClick={() => remove(name)}/>
          </div>)}
          <Button type="dashed" icon={<PlusOutlined/>} disabled={fields.length >= 3} onClick={() => add({ type: 2, employeeStatuses: [] })}>添加考评范围</Button>
        </Space>}
      </Form.List>
    </Form.Item>
  </Space>

  const assessmentTab = <>
    {templateError && <Alert className="hrm-inline-alert" type="error" showIcon message={templateError} action={<Button size="small" onClick={() => void loadTemplates()}>重试</Button>}/>}
    <Form.Item name="assessmentTemplateId" label="考核模板" rules={[{ required: true, message: '请选择考核模板' }]}>
      <Select showSearch optionFilterProp="label" loading={templateLoading} options={assessmentTemplates.filter(item => item.id != null).map(item => ({ value: item.id!, label: item.name }))} onChange={id => void changeAssessmentTemplate(id)}/>
    </Form.Item>
    <PerformanceAssessmentConfigFields namePrefix="assessmentConfig"/>
  </>

  const processTab = <>
    <Form.Item name="quotaSettingType" label="指标制定" rules={[{ required: true }]}>
      <Select options={[{ value: 1, label: '系统制定' }, { value: 2, label: '员工制定' }]}/>
    </Form.Item>
    {quotaSettingType === 2 && <>
      <Form.Item name="targetConfirmation" label="目标确认" valuePropName="checked"><Switch/></Form.Item>
      {targetConfirmation && <HandlerFields namePrefix={['targetConfirmationStage']} absolutePrefix={['targetConfirmationStage']} allowSelf/>}
    </>}
    <Form.Item label="评分阶段" required>
      <Form.List name="reviewStages">
        {(fields, { add, remove }) => <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {fields.map(({ key, name }) => <div key={key} className="hrm-dimension-block">
            <Space align="start" wrap>
              <Form.Item name={[name, 'name']} label="阶段名称" rules={[{ required: true, message: '请输入阶段名称' }]}><Input maxLength={50} style={{ width: 180 }}/></Form.Item>
              <HandlerFields namePrefix={[name, 'rater']} absolutePrefix={['reviewStages', name, 'rater']} allowSelf/>
              <Form.Item name={[name, 'weight']} label="权重" rules={[{ required: true, message: '请输入权重' }]}><InputNumber min={0.01} max={100} precision={2} addonAfter="%"/></Form.Item>
              <Button type="text" danger icon={<DeleteOutlined/>} title="删除阶段" onClick={() => remove(name)}/>
            </Space>
            <Space align="start" wrap>
              <Form.Item name={[name, 'scoringType']} label="评分方式" rules={[{ required: true }]}><Select style={{ width: 150 }} options={[{ value: 1, label: '按指标评分' }, { value: 2, label: '按总分评分' }]}/></Form.Item>
              <Form.Item name={[name, 'visibleContent']} label="可见内容" rules={[{ required: true }]}><Select style={{ width: 150 }} options={[{ value: 1, label: '仅自己' }, { value: 2, label: '全部评分' }]}/></Form.Item>
              <Form.Item name={[name, 'requiredSetting']} label="评语必填" valuePropName="checked"><Switch/></Form.Item>
              <Form.Item noStyle shouldUpdate>
                {({ getFieldValue }) => <Form.Item name={[name, 'rejectAuthority']} label="允许驳回" valuePropName="checked">
                  <Switch disabled={getFieldValue(['reviewStages', name, 'rater', 'type']) === 4}/>
                </Form.Item>}
              </Form.Item>
            </Space>
          </div>)}
          <Button type="dashed" icon={<PlusOutlined/>} onClick={() => add({ name: '', rater: { type: 1, level: 1 }, weight: 0, scoringType: 1, visibleContent: 2, requiredSetting: false, rejectAuthority: true })}>添加评分阶段</Button>
        </Space>}
      </Form.List>
    </Form.Item>
    <Form.Item name="resultAudit" label="结果审核" valuePropName="checked"><Switch/></Form.Item>
    {resultAudit && <Form.Item label="审核节点" required><HandlerStageList name="resultAuditStages"/></Form.Item>}
    <Form.Item name="resultConfirmation" label="结果确认" valuePropName="checked"><Switch/></Form.Item>
    {resultConfirmation && <>
      <Space align="start" wrap>
        <Form.Item name="appealTimeoutDays" label="申诉超期天数" rules={[{ required: true }]}><InputNumber min={1} max={100} precision={0}/></Form.Item>
        <Form.Item name="appealTimeoutAction" label="超期处理" rules={[{ required: true }]}><Select style={{ width: 190 }} options={[{ value: 1, label: '未审批自动拒绝' }, { value: 2, label: '未审批自动通过' }]}/></Form.Item>
      </Space>
      <Form.Item label="申诉节点" required><HandlerStageList name="appealStages"/></Form.Item>
    </>}
  </>

  const resultTab = <>
    <Form.Item name="resultTemplateId" label="结果模板" rules={[{ required: true, message: '请选择结果模板' }]}>
      <Select showSearch optionFilterProp="label" loading={templateLoading} options={resultTemplates.filter(item => item.id != null).map(item => ({ value: item.id!, label: item.name }))} onChange={id => void changeResultTemplate(id)}/>
    </Form.Item>
    <Form.Item label="结果等级" required>
      <Form.List name={['resultConfig', 'levels']}>
        {(fields, { add, remove }) => <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {fields.map(({ key, name }) => <Space key={key} align="start" wrap>
            <Form.Item name={[name, 'name']} label="等级" rules={[{ required: true }]}><Input style={{ width: 110 }}/></Form.Item>
            <Form.Item name={[name, 'minScore']} label="最低分" rules={[{ required: true }]}><InputNumber min={0} max={100} precision={2}/></Form.Item>
            <Form.Item name={[name, 'maxScore']} label="最高分" rules={[{ required: true }]}><InputNumber min={0} max={100} precision={2}/></Form.Item>
            <Form.Item name={[name, 'coefficient']} label="绩效系数" rules={[{ required: true }]}><InputNumber min={0} precision={2}/></Form.Item>
            <Button type="text" danger icon={<DeleteOutlined/>} title="删除等级" onClick={() => remove(name)}/>
          </Space>)}
          <Button type="dashed" icon={<PlusOutlined/>} onClick={() => add({ name: '', minScore: 0, maxScore: 0, coefficient: 1 })}>添加等级</Button>
        </Space>}
      </Form.List>
    </Form.Item>
    <Form.Item name="syncToSalary" label="同步薪资" valuePropName="checked"><Switch/></Form.Item>
    {syncToSalary && <Form.Item name="paidForMonthValue" label="参与计薪月份" rules={[{ required: true, message: '请选择计薪月份' }]}><DatePicker picker="month"/></Form.Item>}
  </>

  return <Drawer
    title={plan ? '编辑绩效计划' : '新建绩效计划'} width="min(1040px, 96vw)" open={open} onClose={onClose}
    destroyOnClose extra={<Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={saving} onClick={() => void handleSave()}>保存</Button></Space>}
  >
    <Form form={form} layout="vertical" preserve>
      <Tabs items={[
        { key: 'basic', label: '基本信息与范围', children: basicTab, forceRender: true },
        { key: 'assessment', label: '考核指标', children: assessmentTab, forceRender: true },
        { key: 'process', label: '考核流程', children: processTab, forceRender: true },
        { key: 'result', label: '结果与薪资', children: resultTab, forceRender: true }
      ]}/>
    </Form>
  </Drawer>
}
