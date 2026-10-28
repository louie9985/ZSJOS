import { filterSelectDisplayProps } from "./filterSelectDisplay"
import { useEffect, useState } from 'react'
import { Alert, Button, Select } from 'antd'
import { api, type AdvancedFilterField } from '../services/api'

export type StudentQuickFilters = { ownerUserId?: number; acceptanceStatus?: string; deliveryStage?: string; taskState?: string; proxyStudent?: string; serviceNeeded?: string }
export default function StudentServiceFilters({ value, onChange, canReadAnnotation = false }: { canReadAnnotation?: boolean; value: StudentQuickFilters; onChange: (value: StudentQuickFilters) => void }) {
  const [fields, setFields] = useState<AdvancedFilterField[]>([])
  const [annotationChoices, setAnnotationChoices] = useState<{value: string; label: string}[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)
  const load = async () => {
    setLoading(true); setError(false)
    try { const [catalog, choices] = await Promise.all([api.advancedFilterCatalog('student_service'), canReadAnnotation ? api.dictDataByType('infra_boolean_string') : Promise.resolve([])]); setFields(catalog.fields); setAnnotationChoices(choices) }
    catch { setFields([]); setError(true) }
    finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [canReadAnnotation])
  if (error) return <Alert type="error" showIcon title="学员筛选选项加载失败" action={<Button size="small" onClick={() => void load()}>重试</Button>} />
  return <>{([
    ['ownerUserId', 'service.ownerUserId', '全部学习规划师'],
    ['acceptanceStatus', 'service.acceptanceStatus', '全部接收状态'],
    ['deliveryStage', 'service.deliveryStage', '全部交付阶段'],
    ['taskState', 'service.taskState', '全部任务状态'],
  ] as const).map(([key, field, placeholder]) => <Select key={key} aria-label={placeholder} allowClear showSearch optionFilterProp="label" loading={loading} disabled={loading} placeholder={placeholder} value={value[key]} options={(fields.find(f => f.fieldKey === field)?.options || []).map(option => ({ ...option, value: key === 'ownerUserId' ? Number(option.value) : option.value }))} onChange={selected => onChange({ ...value, [key]: selected })}  {...filterSelectDisplayProps}/>) }{canReadAnnotation && ([ ['proxyStudent', '是否为代理'], ['serviceNeeded', '是否需要督学'] ] as const).map(([key, label]) => <Select key={key} aria-label={label} placeholder={label} allowClear loading={loading} disabled={loading} value={value[key]} options={annotationChoices} onChange={selected => onChange({ ...value, [key]: selected })} {...filterSelectDisplayProps} />)}</>
}
