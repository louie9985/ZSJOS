import { DeleteOutlined, FilterOutlined, PlusOutlined, ReloadOutlined, SaveOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Badge, Button, DatePicker, Empty, Input, InputNumber, Modal, Select, Space, Spin, Switch, Tag, message } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, type AdvancedFilterCondition, type AdvancedFilterField, type AdvancedFilterGroup, type AdvancedFilterScene, type AdvancedFilterTemplate } from '../services/api'
import ResizableDrawer from './ResizableDrawer'
import { ADVANCED_FILTER_DRAWER_WIDTH_STORAGE_KEY } from '../constants'

const operatorLabels: Record<string, string> = {
  contains: '包含', not_contains: '不包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于',
  gt: '晚于/大于', gte: '不早于/大于等于', lt: '早于/小于', lte: '不晚于/小于等于', between: '区间',
  relative: '相对时间', is_empty: '为空', is_not_empty: '不为空'
}
const summaryOperatorLabels: Record<string, string> = { gt: '大于', gte: '大于等于', lt: '小于', lte: '小于等于', between: '介于' }
const durationUnitOptions = [{ value: 'minute', label: '分钟' }, { value: 'hour', label: '小时' }, { value: 'day', label: '天' }]
const durationUnitLabels = Object.fromEntries(durationUnitOptions.map(item => [item.value, item.label]))
const FILTER_SELECT_POPUP_WIDTH = 240
const FILTER_COMPACT_POPUP_WIDTH = 180
const FILTER_TEMPLATE_POPUP_WIDTH = 280
const blank = (): AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
export const cloneFilterGroup = (group?: AdvancedFilterGroup): AdvancedFilterGroup => group ? {
  logic: group.logic,
  conditions: group.conditions.map(condition => ({ ...condition })),
  groups: group.groups.map(cloneFilterGroup)
} : blank()
const hasValue = (value: unknown) => Array.isArray(value) ? value.length > 0 : value !== undefined && value !== null && value !== ''
const isComplete = (condition: AdvancedFilterCondition) => {
  if (condition.operator === 'is_empty' || condition.operator === 'is_not_empty') return true
  if (condition.fieldKey === 'duration.diff') return hasValue(condition.startFieldKey) && hasValue(condition.endFieldKey) && hasValue(condition.unit)
    && (condition.operator === 'between' ? hasValue(condition.valueFrom) && hasValue(condition.valueTo) : hasValue(condition.value))
  return condition.operator === 'between' ? hasValue(condition.valueFrom) && hasValue(condition.valueTo) : hasValue(condition.value)
}
const effectiveGroup = (group: AdvancedFilterGroup): AdvancedFilterGroup => ({
  logic: group.logic,
  conditions: group.conditions.filter(isComplete),
  groups: group.groups.map(effectiveGroup).filter(item => item.conditions.length > 0 || item.groups.length > 0)
})
export const conditionCount = (group?: AdvancedFilterGroup): number => group
  ? group.conditions.length + group.groups.reduce((sum, item) => sum + conditionCount(item), 0) : 0
export const filterCount = (group?: AdvancedFilterGroup): number => group
  ? group.conditions.filter(isComplete).length + group.groups.reduce((sum, item) => sum + filterCount(item), 0) : 0
type Change = (value: AdvancedFilterGroup) => void

function ConditionRow({ value, fields, relativeDateOptions, onChange, onRemove }: {
  value: AdvancedFilterCondition
  fields: AdvancedFilterField[]
  relativeDateOptions: Array<{ value: string; label: string }>
  onChange: (value: AdvancedFilterCondition) => void
  onRemove: () => void
}) {
  const field = fields.find(item => item.fieldKey === value.fieldKey) || fields[0]
  const noValue = value.operator === 'is_empty' || value.operator === 'is_not_empty'
  const fieldOptions = useMemo(() => Array.from(new Set(fields.map(item => item.group))).map(group => ({
    label: group,
    options: fields.filter(item => item.group === group).map(item => ({ value: item.fieldKey, label: item.label }))
  })), [fields])
  const durationDateOptions = useMemo(() => field?.valueType === 'duration' && field.options.length
    ? field.options : fields.filter(item => item.valueType === 'date').map(item => ({ value: item.fieldKey, label: item.label })), [field, fields])
  const setField = (fieldKey: string) => {
    const next = fields.find(item => item.fieldKey === fieldKey)
    if (next) onChange(next.valueType === 'duration' ? { fieldKey, operator: next.operators[0], unit: 'hour' } : { fieldKey, operator: next.operators[0] })
  }
  const setOperator = (operator: string) => {
    onChange(field?.valueType === 'duration'
      ? { fieldKey: value.fieldKey, operator, startFieldKey: value.startFieldKey, endFieldKey: value.endFieldKey, unit: value.unit || 'hour' }
      : { fieldKey: value.fieldKey, operator })
  }
  const dateValue = (key: 'value' | 'valueFrom' | 'valueTo', item: { valueOf(): number } | null) => onChange({ ...value, [key]: item?.valueOf() })
  const date = (raw: unknown) => hasValue(raw) ? dayjs(Number(raw)) : null
  const number = (raw: unknown) => typeof raw === 'number' ? raw : typeof raw === 'string' && raw !== '' ? Number(raw) : undefined
  let control = null
  if (field && !noValue) {
    if (field.valueType === 'duration') {
      control = <div className="advanced-filter-duration-control">
        <Select showSearch optionFilterProp="label" popupMatchSelectWidth={FILTER_SELECT_POPUP_WIDTH} placeholder="开始时间" value={value.startFieldKey} options={durationDateOptions} onChange={item => onChange({ ...value, startFieldKey: item })}/>
        <Select showSearch optionFilterProp="label" popupMatchSelectWidth={FILTER_SELECT_POPUP_WIDTH} placeholder="结束时间" value={value.endFieldKey} options={durationDateOptions} onChange={item => onChange({ ...value, endFieldKey: item })}/>
        <Select popupMatchSelectWidth={FILTER_COMPACT_POPUP_WIDTH} value={value.unit || 'hour'} options={durationUnitOptions} onChange={unit => onChange({ ...value, unit })}/>
        {value.operator === 'between'
          ? <Space.Compact block><InputNumber placeholder="最小值" value={number(value.valueFrom)} onChange={item => onChange({ ...value, valueFrom: item })}/><InputNumber placeholder="最大值" value={number(value.valueTo)} onChange={item => onChange({ ...value, valueTo: item })}/></Space.Compact>
          : <InputNumber placeholder="比较值" value={number(value.value)} onChange={item => onChange({ ...value, value: item })}/>}
      </div>
    } else if (value.operator === 'relative') {
      control = <Select popupMatchSelectWidth={FILTER_SELECT_POPUP_WIDTH} value={value.value as string | undefined} options={relativeDateOptions} onChange={item => onChange({ ...value, value: item })}/>
    } else if (value.operator === 'between') {
      control = <Space.Compact block>{field.valueType === 'date'
        ? <><DatePicker showTime value={date(value.valueFrom)} onChange={item => dateValue('valueFrom', item)}/><DatePicker showTime value={date(value.valueTo)} onChange={item => dateValue('valueTo', item)}/></>
        : <><InputNumber value={value.valueFrom as number | undefined} onChange={item => onChange({ ...value, valueFrom: item })}/><InputNumber value={value.valueTo as number | undefined} onChange={item => onChange({ ...value, valueTo: item })}/></>}</Space.Compact>
    } else if (field.valueType === 'select') {
      control = <Select mode="multiple" showSearch optionFilterProp="label" popupMatchSelectWidth={FILTER_SELECT_POPUP_WIDTH} value={(value.value as Array<string | number>) || []} options={field.options} loading={field.optionsLoading} notFoundContent={field.optionsError ? <Button type="link" size="small" onClick={field.retryOptions}>加载失败，重试</Button> : '暂无可选项'} onChange={item => onChange({ ...value, value: item })}/>
    } else if (field.valueType === 'number') {
      control = <InputNumber value={value.value as number | undefined} onChange={item => onChange({ ...value, value: item })}/>
    } else if (field.valueType === 'date') {
      control = <DatePicker showTime value={date(value.value)} onChange={item => dateValue('value', item)}/>
    } else control = <Input value={(value.value as string) || ''} onChange={event => onChange({ ...value, value: event.target.value })}/>
  }
  return <div className="advanced-filter-condition">
    <Select showSearch optionFilterProp="label" popupMatchSelectWidth={FILTER_SELECT_POPUP_WIDTH} value={field?.fieldKey} onChange={setField} options={fieldOptions}/>
    <Select popupMatchSelectWidth={FILTER_COMPACT_POPUP_WIDTH} value={value.operator} onChange={setOperator} options={(field?.operators || []).map(item => ({ value: item, label: operatorLabels[item] || item }))}/>
    <div className="advanced-filter-value-control">{control}</div>
    <Button aria-label="删除条件" type="text" danger icon={<DeleteOutlined/>} onClick={onRemove}/>
  </div>
}

function GroupEditor({ value, fields, relativeDateOptions, depth, total, onChange, onRemove }: {
  value: AdvancedFilterGroup
  fields: AdvancedFilterField[]
  relativeDateOptions: Array<{ value: string; label: string }>
  depth: number
  total: number
  onChange: Change
  onRemove?: () => void
}) {
  const addCondition = () => fields[0] && total < 20 && onChange({ ...value, conditions: [...value.conditions, { fieldKey: fields[0].fieldKey, operator: fields[0].operators[0] }] })
  return <div className={`advanced-filter-group depth-${depth}`}>
    <div className="advanced-filter-group-head">
      <Select popupMatchSelectWidth={FILTER_COMPACT_POPUP_WIDTH} value={value.logic} onChange={logic => onChange({ ...value, logic })} options={[{ value: 'AND', label: '满足全部' }, { value: 'OR', label: '满足任一' }]}/>
      <Space><Button size="small" disabled={!fields.length || total >= 20} icon={<PlusOutlined/>} onClick={addCondition}>条件</Button>{depth === 0 && value.groups.length < 5 && <Button size="small" onClick={() => onChange({ ...value, groups: [...value.groups, blank()] })}>条件组</Button>}{onRemove && <Button size="small" danger onClick={onRemove}>删除组</Button>}</Space>
    </div>
    {value.conditions.map((condition, index) => <ConditionRow key={`${condition.fieldKey}-${index}`} value={condition} fields={fields} relativeDateOptions={relativeDateOptions} onChange={next => onChange({ ...value, conditions: value.conditions.map((item, itemIndex) => itemIndex === index ? next : item) })} onRemove={() => onChange({ ...value, conditions: value.conditions.filter((_, itemIndex) => itemIndex !== index) })}/>) }
    {value.groups.map((group, index) => <GroupEditor key={`group-${index}`} value={group} fields={fields} relativeDateOptions={relativeDateOptions} depth={1} total={total} onChange={next => onChange({ ...value, groups: value.groups.map((item, itemIndex) => itemIndex === index ? next : item) })} onRemove={() => onChange({ ...value, groups: value.groups.filter((_, itemIndex) => itemIndex !== index) })}/>) }
  </div>
}

function flattened(group?: AdvancedFilterGroup, prefix: number[] = []): Array<{ condition: AdvancedFilterCondition; path: number[] }> {
  if (!group) return []
  return [...group.conditions.map((condition, index) => ({ condition, path: [...prefix, index] })),
    ...group.groups.flatMap((child, index) => flattened(child, [...prefix, -(index + 1)]))].filter(item => isComplete(item.condition))
}

export function removeFilterAtPath(group: AdvancedFilterGroup, path: number[]): AdvancedFilterGroup {
  const [head, ...rest] = path
  if (head >= 0) return { ...group, conditions: group.conditions.filter((_, index) => index !== head) }
  const groupIndex = -head - 1
  return { ...group, groups: group.groups.map((child, index) => index === groupIndex ? removeFilterAtPath(child, rest) : child) }
}

export function AdvancedFilterToolbar({ scene, pageKey, placeholder, value, keyword, onKeyword, onChange }: {
  scene: AdvancedFilterScene
  pageKey?: string
  placeholder: string
  value?: AdvancedFilterGroup
  keyword: string
  onKeyword: (value: string) => void
  onChange: (value?: AdvancedFilterGroup) => void
}) {
  const [open, setOpen] = useState(false)
  const [fields, setFields] = useState<AdvancedFilterField[]>([])
  const [relativeDateOptions, setRelativeDateOptions] = useState<Array<{ value: string; label: string }>>([])
  const [draft, setDraft] = useState<AdvancedFilterGroup>(() => cloneFilterGroup(value))
  const [searchText, setSearchText] = useState(keyword)
  const [catalogState, setCatalogState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [templates, setTemplates] = useState<AdvancedFilterTemplate[]>([])
  const [templateState, setTemplateState] = useState<'idle' | 'loading' | 'error'>('idle')
  const [saveOpen, setSaveOpen] = useState(false)
  const [templateName, setTemplateName] = useState('')
  const [templateDefault, setTemplateDefault] = useState(false)
  const [savingTemplate, setSavingTemplate] = useState(false)
  useEffect(() => setSearchText(keyword), [keyword])
  const loadOptions = useCallback(async (source?: string) => {
    if (!source) return []
    if (source.startsWith('dict:')) return (await api.dictDataByType(source.slice(5))).map(item => ({ value: item.value, label: item.label }))
    throw new Error(`不支持的筛选选项来源: ${source}`)
  }, [])
  const loadCatalog = useCallback(async () => {
    setCatalogState('loading')
    try {
      const catalog = await api.advancedFilterCatalog(scene)
      setRelativeDateOptions(catalog.relativeDateOptions || [])
      setFields(catalog.fields.map(field => field.optionSource && !field.options.length ? { ...field, optionsLoading: true, optionsState: 'loading' } : { ...field, optionsState: field.options.length ? 'ready' : 'empty' }))
      setCatalogState('ready')
      await Promise.all(catalog.fields.map(async field => {
        if (!field.optionSource || field.options.length) return
        try {
          const options = await loadOptions(field.optionSource)
          setFields(current => current.map(item => item.fieldKey === field.fieldKey ? { ...item, options, optionsLoading: false, optionsState: options.length ? 'ready' : 'empty' } : item))
        } catch {
          setFields(current => current.map(item => item.fieldKey === field.fieldKey ? { ...item, optionsLoading: false, optionsState: 'error', optionsError: true, retryOptions: () => void loadCatalog() } : item))
        }
      }))
    } catch { setCatalogState('error'); setFields([]) }
  }, [loadOptions, scene])
  useEffect(() => { void loadCatalog() }, [loadCatalog])
  const loadTemplates = useCallback(async () => {
    if (!pageKey) return
    setTemplateState('loading')
    try {
      setTemplates(await api.advancedFilterTemplates(scene, pageKey))
      setTemplateState('idle')
    } catch {
      setTemplates([])
      setTemplateState('error')
    }
  }, [pageKey, scene])
  useEffect(() => { void loadTemplates() }, [loadTemplates])
  const show = () => { setDraft(cloneFilterGroup(value)); setOpen(true) }
  const cancel = () => { setDraft(cloneFilterGroup(value)); setOpen(false) }
  const apply = () => {
    const effective = effectiveGroup(draft)
    onChange(filterCount(effective) ? effective : undefined)
    setOpen(false)
  }
  const clearApplied = () => { setDraft(blank()); onChange(undefined) }
  const applyTemplate = (id?: number) => {
    const template = templates.find(item => item.id === id)
    if (!template) return
    setDraft(cloneFilterGroup(template.filter))
  }
  const openSave = () => {
    if (!pageKey || !filterCount(draft)) {
      message.warning('请先配置筛选条件后再保存模板')
      return
    }
    setTemplateName('')
    setTemplateDefault(false)
    setSaveOpen(true)
  }
  const saveTemplate = async () => {
    if (!pageKey) return
    const name = templateName.trim()
    if (!name) {
      message.warning('请输入模板名称')
      return
    }
    const effective = effectiveGroup(draft)
    if (!filterCount(effective)) {
      message.warning('请先配置筛选条件后再保存模板')
      return
    }
    setSavingTemplate(true)
    try {
      await api.createPersonalAdvancedFilterTemplate({
        scene,
        pageKey,
        name,
        filter: effective,
        sort: templates.filter(item => item.scope === 'personal').length * 10 + 10,
        enabled: true,
        defaultTemplate: templateDefault
      })
      message.success('个人筛选模板已保存')
      setSaveOpen(false)
      await loadTemplates()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存筛选模板失败')
    } finally {
      setSavingTemplate(false)
    }
  }
  const templateOptions = useMemo(() => templates.map(template => ({
    value: template.id,
    label: `${template.scope === 'system' ? '系统' : '个人'} · ${template.name}${template.defaultTemplate ? ' · 默认' : ''}`
  })), [templates])
  const active = flattened(value)
  const summarize = (condition: AdvancedFilterCondition) => {
    const field = fields.find(item => item.fieldKey === condition.fieldKey)
    if (condition.fieldKey === 'duration.diff') {
      const options = field?.options?.length ? field.options : fields.filter(item => item.valueType === 'date').map(item => ({ value: item.fieldKey, label: item.label }))
      const start = options.find(option => option.value === condition.startFieldKey)?.label || '开始时间'
      const end = options.find(option => option.value === condition.endFieldKey)?.label || '结束时间'
      const unit = durationUnitLabels[condition.unit || 'hour'] || condition.unit || ''
      if (condition.operator === 'between') return `${end} - ${start} ${summaryOperatorLabels.between} ${condition.valueFrom ?? ''} - ${condition.valueTo ?? ''} ${unit}`
      return `${end} - ${start} ${summaryOperatorLabels[condition.operator] || condition.operator} ${condition.value ?? ''} ${unit}`
    }
    if (condition.operator === 'is_empty' || condition.operator === 'is_not_empty') return ''
    if (condition.operator === 'between') return field?.valueType === 'date'
      ? `${dayjs(Number(condition.valueFrom)).format('YYYY-MM-DD HH:mm')} - ${dayjs(Number(condition.valueTo)).format('YYYY-MM-DD HH:mm')}`
      : `${condition.valueFrom ?? ''} - ${condition.valueTo ?? ''}`
    const raw = Array.isArray(condition.value) ? condition.value : [condition.value]
    return raw.map(item => field?.options.find(option => String(option.value) === String(item))?.label
      || relativeDateOptions.find(option => option.value === item)?.label
      || (field?.valueType === 'date' ? dayjs(Number(item)).format('YYYY-MM-DD HH:mm') : String(item ?? ''))).join('、')
  }
  const submitKeyword = () => onKeyword(searchText.trim())
  return <>
    <div className="advanced-filter-toolbar"><Input allowClear value={searchText} placeholder={placeholder} suffix={<SearchOutlined role="button" aria-label="搜索" tabIndex={0} onClick={submitKeyword} onKeyDown={event => event.key === 'Enter' && submitKeyword()}/>} onChange={event => { setSearchText(event.target.value); if (!event.target.value) onKeyword('') }} onPressEnter={submitKeyword}/><Badge count={filterCount(value)}><Button icon={<FilterOutlined/>} onClick={show}>筛选</Button></Badge></div>
    {active.length > 0 && <div className="advanced-filter-tags">{active.map(({ condition, path }, index) => { const field = fields.find(item => item.fieldKey === condition.fieldKey); const label = condition.fieldKey === 'duration.diff' ? summarize(condition) : `${field?.label || '筛选字段'} ${operatorLabels[condition.operator]} ${summarize(condition)}`; return <Tag closable key={`${condition.fieldKey}-${index}`} onClose={event => { event.preventDefault(); const next = removeFilterAtPath(cloneFilterGroup(value), path); onChange(filterCount(next) ? next : undefined) }}>{label}</Tag> })}<Button size="small" type="link" onClick={clearApplied}>清空全部</Button></div>}
    <ResizableDrawer className="advanced-filter-drawer" open={open} placement="right" width="min(560px, 100vw)" defaultSize={560} minSize={420} storageKey={ADVANCED_FILTER_DRAWER_WIDTH_STORAGE_KEY} title="高级筛选" onClose={cancel} footer={<div className="advanced-filter-footer"><Button onClick={() => setDraft(blank())}>重置</Button><Space><Button onClick={cancel}>取消</Button><Button type="primary" onClick={apply}>应用筛选</Button></Space></div>}>
      {pageKey && <div className="advanced-filter-template-panel">
        <div className="advanced-filter-template-actions">
          <Select allowClear className="advanced-filter-template-select" loading={templateState === 'loading'} status={templateState === 'error' ? 'error' : undefined} placeholder="选择筛选模板" optionFilterProp="label" popupMatchSelectWidth={FILTER_TEMPLATE_POPUP_WIDTH} showSearch options={templateOptions} onChange={applyTemplate}/>
          <Button icon={<SaveOutlined/>} disabled={!filterCount(draft)} onClick={openSave}>保存模板</Button>
        </div>
        {templateState === 'error' && <Alert type="warning" showIcon message="筛选模板加载失败" action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => void loadTemplates()}>重试</Button>}/>}
      </div>}
      {catalogState === 'loading' ? <div className="advanced-filter-state"><Spin/><span>正在加载可筛选字段</span></div> : catalogState === 'error' ? <Alert type="error" showIcon message="筛选字段加载失败" action={<Button icon={<ReloadOutlined/>} onClick={() => void loadCatalog()}>重试</Button>}/> : fields.length ? <GroupEditor value={draft} fields={fields} relativeDateOptions={relativeDateOptions} depth={0} total={conditionCount(draft)} onChange={setDraft}/> : <Empty description="当前场景没有可用筛选字段"/>}
    </ResizableDrawer>
    <Modal title="保存个人筛选模板" open={saveOpen} confirmLoading={savingTemplate} onCancel={() => setSaveOpen(false)} onOk={() => void saveTemplate()}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Input maxLength={30} showCount placeholder="模板名称" value={templateName} onChange={event => setTemplateName(event.target.value)}/>
        <Space><Switch checked={templateDefault} onChange={setTemplateDefault}/><span>设为本页面默认个人模板</span></Space>
      </Space>
    </Modal>
  </>
}
