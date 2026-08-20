import { DeleteOutlined, FilterOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Badge, Button, DatePicker, Drawer, Empty, Input, InputNumber, Select, Space, Spin, Tag } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, type AdvancedFilterCondition, type AdvancedFilterField, type AdvancedFilterGroup, type AdvancedFilterScene } from '../services/api'

const operatorLabels: Record<string, string> = {
  contains: '包含', not_contains: '不包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于',
  gt: '晚于/大于', gte: '不早于/大于等于', lt: '早于/小于', lte: '不晚于/小于等于', between: '区间',
  relative: '相对时间', is_empty: '为空', is_not_empty: '不为空'
}
const blank = (): AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
export const cloneFilterGroup = (group?: AdvancedFilterGroup): AdvancedFilterGroup => group ? {
  logic: group.logic,
  conditions: group.conditions.map(condition => ({ ...condition })),
  groups: group.groups.map(cloneFilterGroup)
} : blank()
const hasValue = (value: unknown) => Array.isArray(value) ? value.length > 0 : value !== undefined && value !== null && value !== ''
const isComplete = (condition: AdvancedFilterCondition) => condition.operator === 'is_empty' || condition.operator === 'is_not_empty'
  || (condition.operator === 'between' ? hasValue(condition.valueFrom) && hasValue(condition.valueTo) : hasValue(condition.value))
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
  const setField = (fieldKey: string) => {
    const next = fields.find(item => item.fieldKey === fieldKey)
    if (next) onChange({ fieldKey, operator: next.operators[0] })
  }
  const dateValue = (key: 'value' | 'valueFrom' | 'valueTo', item: { valueOf(): number } | null) => onChange({ ...value, [key]: item?.valueOf() })
  const date = (raw: unknown) => hasValue(raw) ? dayjs(Number(raw)) : null
  let control = null
  if (field && !noValue) {
    if (value.operator === 'relative') {
      control = <Select value={value.value as string | undefined} options={relativeDateOptions} onChange={item => onChange({ ...value, value: item })}/>
    } else if (value.operator === 'between') {
      control = <Space.Compact block>{field.valueType === 'date'
        ? <><DatePicker showTime value={date(value.valueFrom)} onChange={item => dateValue('valueFrom', item)}/><DatePicker showTime value={date(value.valueTo)} onChange={item => dateValue('valueTo', item)}/></>
        : <><InputNumber value={value.valueFrom as number | undefined} onChange={item => onChange({ ...value, valueFrom: item })}/><InputNumber value={value.valueTo as number | undefined} onChange={item => onChange({ ...value, valueTo: item })}/></>}</Space.Compact>
    } else if (field.valueType === 'select') {
      control = <Select mode="multiple" showSearch optionFilterProp="label" value={(value.value as Array<string | number>) || []} options={field.options} loading={field.optionsLoading} notFoundContent={field.optionsError ? <Button type="link" size="small" onClick={field.retryOptions}>加载失败，重试</Button> : '暂无可选项'} onChange={item => onChange({ ...value, value: item })}/>
    } else if (field.valueType === 'number') {
      control = <InputNumber value={value.value as number | undefined} onChange={item => onChange({ ...value, value: item })}/>
    } else if (field.valueType === 'date') {
      control = <DatePicker showTime value={date(value.value)} onChange={item => dateValue('value', item)}/>
    } else control = <Input value={(value.value as string) || ''} onChange={event => onChange({ ...value, value: event.target.value })}/>
  }
  return <div className="advanced-filter-condition">
    <Select showSearch optionFilterProp="label" value={field?.fieldKey} onChange={setField} options={fieldOptions}/>
    <Select value={value.operator} onChange={operator => onChange({ fieldKey: value.fieldKey, operator })} options={(field?.operators || []).map(item => ({ value: item, label: operatorLabels[item] || item }))}/>
    <div>{control}</div>
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
      <Select value={value.logic} onChange={logic => onChange({ ...value, logic })} options={[{ value: 'AND', label: '满足全部' }, { value: 'OR', label: '满足任一' }]}/>
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

export function AdvancedFilterToolbar({ scene, placeholder, value, keyword, onKeyword, onChange }: {
  scene: AdvancedFilterScene
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
  useEffect(() => setSearchText(keyword), [keyword])
  const loadOptions = useCallback(async (source?: string) => {
    if (!source) return []
    if (source.startsWith('dict:')) return (await api.dictDataByType(source.slice(5))).map(item => ({ value: item.value, label: item.label }))
    return []
  }, [])
  const loadCatalog = useCallback(async () => {
    setCatalogState('loading')
    try {
      const catalog = await api.advancedFilterCatalog(scene)
      setRelativeDateOptions(catalog.relativeDateOptions || [])
      setFields(catalog.fields.map(field => field.optionSource && !field.options.length ? { ...field, optionsLoading: true } : field))
      setCatalogState('ready')
      await Promise.all(catalog.fields.map(async field => {
        if (!field.optionSource || field.options.length) return
        try {
          const options = await loadOptions(field.optionSource)
          setFields(current => current.map(item => item.fieldKey === field.fieldKey ? { ...item, options, optionsLoading: false } : item))
        } catch {
          setFields(current => current.map(item => item.fieldKey === field.fieldKey ? { ...item, optionsLoading: false, optionsError: true, retryOptions: () => void loadCatalog() } : item))
        }
      }))
    } catch { setCatalogState('error'); setFields([]) }
  }, [loadOptions, scene])
  useEffect(() => { void loadCatalog() }, [loadCatalog])
  const show = () => { setDraft(cloneFilterGroup(value)); setOpen(true) }
  const cancel = () => { setDraft(cloneFilterGroup(value)); setOpen(false) }
  const apply = () => {
    const effective = effectiveGroup(draft)
    onChange(filterCount(effective) ? effective : undefined)
    setOpen(false)
  }
  const clearApplied = () => { setDraft(blank()); onChange(undefined) }
  const active = flattened(value)
  const summarize = (condition: AdvancedFilterCondition) => {
    const field = fields.find(item => item.fieldKey === condition.fieldKey)
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
    {active.length > 0 && <div className="advanced-filter-tags">{active.map(({ condition, path }, index) => { const field = fields.find(item => item.fieldKey === condition.fieldKey); return <Tag closable key={`${condition.fieldKey}-${index}`} onClose={event => { event.preventDefault(); const next = removeFilterAtPath(cloneFilterGroup(value), path); onChange(filterCount(next) ? next : undefined) }}>{field?.label || '筛选字段'} {operatorLabels[condition.operator]} {summarize(condition)}</Tag> })}<Button size="small" type="link" onClick={clearApplied}>清空全部</Button></div>}
    <Drawer className="advanced-filter-drawer" open={open} placement="right" size={560} title="高级筛选" onClose={cancel} footer={<div className="advanced-filter-footer"><Button onClick={() => setDraft(blank())}>重置</Button><Space><Button onClick={cancel}>取消</Button><Button type="primary" onClick={apply}>应用筛选</Button></Space></div>}>
      {catalogState === 'loading' ? <div className="advanced-filter-state"><Spin/><span>正在加载可筛选字段</span></div> : catalogState === 'error' ? <Alert type="error" showIcon message="筛选字段加载失败" action={<Button icon={<ReloadOutlined/>} onClick={() => void loadCatalog()}>重试</Button>}/> : fields.length ? <GroupEditor value={draft} fields={fields} relativeDateOptions={relativeDateOptions} depth={0} total={conditionCount(draft)} onChange={setDraft}/> : <Empty description="当前场景没有可用筛选字段"/>}
    </Drawer>
  </>
}
