import { DeleteOutlined, FilterOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Badge, Button, DatePicker, Drawer, Empty, Input, InputNumber, Select, Space, Spin, Tag, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type AdvancedFilterCondition, type AdvancedFilterField, type AdvancedFilterGroup } from '../services/api'

const operatorLabels: Record<string, string> = { contains: '包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于', gt: '大于', lt: '小于', between: '区间', is_empty: '为空', is_not_empty: '不为空' }
const blank = (): AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
const hasValue = (value: unknown) => Array.isArray(value) ? value.length > 0 : value !== undefined && value !== null && value !== ''
const isComplete = (condition: AdvancedFilterCondition) => condition.operator === 'is_empty' || condition.operator === 'is_not_empty'
  || (condition.operator === 'between' ? hasValue(condition.valueFrom) && hasValue(condition.valueTo) : hasValue(condition.value))
const conditionCount = (group?: AdvancedFilterGroup): number => group ? group.conditions.length + group.groups.reduce((sum, item) => sum + conditionCount(item), 0) : 0
const effectiveGroup = (group: AdvancedFilterGroup): AdvancedFilterGroup => ({
  logic: group.logic,
  conditions: group.conditions.filter(isComplete),
  groups: group.groups.map(effectiveGroup).filter(item => item.conditions.length > 0 || item.groups.length > 0)
})
export const filterCount = (group?: AdvancedFilterGroup): number => group ? effectiveGroup(group).conditions.length + effectiveGroup(group).groups.reduce((sum, item) => sum + filterCount(item), 0) : 0
type Change = (value: AdvancedFilterGroup, immediate?: boolean) => void

function ConditionRow({ value, fields, onChange, onRemove }: { value: AdvancedFilterCondition; fields: AdvancedFilterField[]; onChange: (value: AdvancedFilterCondition, immediate?: boolean) => void; onRemove: () => void }) {
  const field = fields.find(item => item.fieldKey === value.fieldKey) || fields[0]
  const noValue = value.operator === 'is_empty' || value.operator === 'is_not_empty'
  const setField = (fieldKey: string) => { const next = fields.find(item => item.fieldKey === fieldKey)!; onChange({ fieldKey, operator: next.operators[0] }, true) }
  const dateValue = (key: 'value' | 'valueFrom' | 'valueTo', item: { valueOf(): number } | null) => onChange({ ...value, [key]: item?.valueOf() })
  const control = !field || noValue ? null : value.operator === 'between' ? <Space.Compact block>
    {field.valueType === 'date' ? <><DatePicker showTime onChange={item => dateValue('valueFrom', item)}/><DatePicker showTime onChange={item => dateValue('valueTo', item)}/></> : <><InputNumber value={value.valueFrom as number} onChange={item => onChange({ ...value, valueFrom: item })}/><InputNumber value={value.valueTo as number} onChange={item => onChange({ ...value, valueTo: item })}/></>}
  </Space.Compact> : field.valueType === 'select' ? <Select mode="multiple" showSearch optionFilterProp="label" value={(value.value as Array<string | number>) || []} options={field.options} loading={field.optionsLoading} notFoundContent={field.optionsError ? <Button type="link" size="small" onClick={field.retryOptions}>加载失败，重试</Button> : '暂无可选项'} onChange={item => onChange({ ...value, value: item }, true)}/>
    : field.valueType === 'number' ? <InputNumber value={value.value as number} onChange={item => onChange({ ...value, value: item })}/>
      : field.valueType === 'date' ? <DatePicker showTime onChange={item => dateValue('value', item)}/>
        : <Input value={(value.value as string) || ''} onChange={event => onChange({ ...value, value: event.target.value })}/>
  return <div className="advanced-filter-condition">
    <Select showSearch optionFilterProp="label" value={field?.fieldKey} onChange={setField} options={fields.map(item => ({ value: item.fieldKey, label: `${item.group} · ${item.label}` }))}/>
    <Select value={value.operator} onChange={operator => onChange({ fieldKey: value.fieldKey, operator }, true)} options={(field?.operators || []).map(item => ({ value: item, label: operatorLabels[item] || item }))}/>
    <div>{control}</div><Button aria-label="删除条件" type="text" danger icon={<DeleteOutlined/>} onClick={onRemove}/>
  </div>
}

function GroupEditor({ value, fields, depth, total, onChange, onRemove }: { value: AdvancedFilterGroup; fields: AdvancedFilterField[]; depth: number; total: number; onChange: Change; onRemove?: () => void }) {
  const addCondition = () => fields[0] && total < 20 && onChange({ ...value, conditions: [...value.conditions, { fieldKey: fields[0].fieldKey, operator: fields[0].operators[0] }] }, true)
  return <div className={`advanced-filter-group depth-${depth}`}>
    <div className="advanced-filter-group-head"><Select value={value.logic} onChange={logic => onChange({ ...value, logic }, true)} options={[{ value: 'AND', label: '满足全部' }, { value: 'OR', label: '满足任一' }]}/><Space><Button size="small" disabled={!fields.length || total >= 20} icon={<PlusOutlined/>} onClick={addCondition}>条件</Button>{depth === 0 && value.groups.length < 5 && <Button size="small" onClick={() => onChange({ ...value, groups: [...value.groups, blank()] }, true)}>条件组</Button>}{onRemove && <Button size="small" danger onClick={onRemove}>删除组</Button>}</Space></div>
    {value.conditions.map((condition, index) => <ConditionRow key={`${condition.fieldKey}-${index}`} value={condition} fields={fields} onChange={(next, immediate) => onChange({ ...value, conditions: value.conditions.map((item, i) => i === index ? next : item) }, immediate)} onRemove={() => onChange({ ...value, conditions: value.conditions.filter((_, i) => i !== index) }, true)}/>) }
    {value.groups.map((group, index) => <GroupEditor key={`group-${index}`} value={group} fields={fields} depth={1} total={total} onChange={(next, immediate) => onChange({ ...value, groups: value.groups.map((item, i) => i === index ? next : item) }, immediate)} onRemove={() => onChange({ ...value, groups: value.groups.filter((_, i) => i !== index) }, true)}/>) }
  </div>
}

export function AdvancedFilterToolbar({ scene, placeholder, value, keyword, onKeyword, onChange }: { scene: 'lead' | 'order'; placeholder: string; value?: AdvancedFilterGroup; keyword: string; onKeyword: (value: string) => void; onChange: (value?: AdvancedFilterGroup) => void }) {
  const [open, setOpen] = useState(false), [fields, setFields] = useState<AdvancedFilterField[]>([]), [draft, setDraft] = useState(value || blank()), [searchText, setSearchText] = useState(keyword)
  const [catalogState, setCatalogState] = useState<'loading' | 'ready' | 'error'>('loading')
  const timer = useRef<number | undefined>(undefined), onChangeRef = useRef(onChange)
  useEffect(() => { onChangeRef.current = onChange }, [onChange])
  useEffect(() => setSearchText(keyword), [keyword])
  const loadOptions = useCallback(async (source?: string) => {
    if (!source) return []
    if (source.startsWith('dict:')) return (await api.dictDataByType(source.slice(5))).map(item => ({ value: item.value, label: item.label }))
    if (source === 'visible-users') return (await api.simpleUsers()).map(item => ({ value: item.id, label: item.nickname }))
    return []
  }, [])
  const loadCatalog = useCallback(async () => {
    setCatalogState('loading')
    try {
      const catalog = await api.advancedFilterCatalog(scene)
      setFields(catalog.fields.map(field => field.optionSource && !field.options.length ? { ...field, optionsLoading: true } : field)); setCatalogState('ready')
      await Promise.all(catalog.fields.map(async field => {
        if (!field.optionSource || field.options.length) return
        try { const options = await loadOptions(field.optionSource); setFields(current => current.map(item => item.fieldKey === field.fieldKey ? { ...item, options, optionsLoading: false } : item)) }
        catch { setFields(current => current.map(item => item.fieldKey === field.fieldKey ? { ...item, optionsLoading: false, optionsError: true, retryOptions: () => void loadCatalog() } : item)) }
      }))
    } catch { setCatalogState('error'); setFields([]) }
  }, [loadOptions, scene])
  useEffect(() => { void loadCatalog() }, [loadCatalog])
  const emit = useCallback((next: AdvancedFilterGroup, immediate = false) => {
    setDraft(next); window.clearTimeout(timer.current)
    const deliver = () => { const effective = effectiveGroup(next); onChangeRef.current(filterCount(effective) ? effective : undefined) }
    if (immediate) deliver(); else timer.current = window.setTimeout(deliver, 500)
  }, [])
  useEffect(() => () => window.clearTimeout(timer.current), [])
  const count = filterCount(value), flat = useMemo(() => [...draft.conditions.map((condition, index) => ({ condition, path: ['conditions', index] as const })), ...draft.groups.flatMap((group, groupIndex) => group.conditions.map((condition, index) => ({ condition, path: [groupIndex, index] as const })))].filter(item => isComplete(item.condition)), [draft])
  const removeTag = (path: readonly unknown[]) => typeof path[0] === 'string' ? emit({ ...draft, conditions: draft.conditions.filter((_, index) => index !== path[1]) }, true) : emit({ ...draft, groups: draft.groups.map((group, groupIndex) => groupIndex === path[0] ? { ...group, conditions: group.conditions.filter((_, index) => index !== path[1]) } : group) }, true)
  const submitKeyword = () => onKeyword(searchText.trim())
  return <><div className="advanced-filter-toolbar"><Input allowClear value={searchText} placeholder={placeholder} suffix={<SearchOutlined role="button" aria-label="搜索" tabIndex={0} onClick={submitKeyword} onKeyDown={event => event.key === 'Enter' && submitKeyword()}/>} onChange={event => { setSearchText(event.target.value); if (!event.target.value) onKeyword('') }} onPressEnter={submitKeyword}/><Badge count={count}><Button icon={<FilterOutlined/>} onClick={() => setOpen(true)}>筛选</Button></Badge></div>
    {count > 0 && <div className="advanced-filter-tags">{flat.map(({ condition, path }, index) => { const field = fields.find(item => item.fieldKey === condition.fieldKey); return <Tag closable key={`${condition.fieldKey}-${index}`} onClose={event => { event.preventDefault(); removeTag(path) }}>{field?.group} · {field?.label} {operatorLabels[condition.operator]}</Tag> })}<Button size="small" type="link" onClick={() => emit(blank(), true)}>清空全部</Button></div>}
    <Drawer className="advanced-filter-drawer" open={open} placement="right" width={560} title={<div><Typography.Text strong>高级筛选</Typography.Text><Typography.Text type="secondary"> · 修改后自动生效</Typography.Text></div>} onClose={() => setOpen(false)} footer={<Space><Button onClick={() => emit(blank(), true)}>清空全部</Button><Button type="primary" onClick={() => setOpen(false)}>关闭</Button></Space>}>
      {catalogState === 'loading' ? <div className="advanced-filter-state"><Spin/><span>正在加载可筛选字段</span></div> : catalogState === 'error' ? <Alert type="error" showIcon message="筛选字段加载失败" action={<Button icon={<ReloadOutlined/>} onClick={() => void loadCatalog()}>重试</Button>}/> : fields.length ? <GroupEditor value={draft} fields={fields} depth={0} total={conditionCount(draft)} onChange={emit}/> : <Empty description="当前场景没有可用筛选字段"/>}
    </Drawer></>
}
