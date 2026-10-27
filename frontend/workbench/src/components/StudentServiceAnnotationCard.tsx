import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Alert, Button, Input, Select, Skeleton, Typography } from 'antd'
import { ApiError } from '../services/api'
import { annotationChanged, annotationOptions, annotationValues, studentServiceAnnotationApi, STUDENT_ANNOTATION_CONFLICT, type AnnotationChoice, type AnnotationOption, type StudentAnnotationValues, type StudentServiceAnnotation } from '../services/studentServiceAnnotation'
import { useStudentArchiveEditor } from './StudentArchiveEditGuard'
import './StudentServiceAnnotationCard.css'

export default function StudentServiceAnnotationCard({ relationId, canUpdate }: { relationId: number; canUpdate: boolean }) {
  const id = useId()
  const [saved, setSaved] = useState<StudentServiceAnnotation>()
  const [draft, setDraft] = useState<StudentAnnotationValues>()
  const [options, setOptions] = useState<AnnotationOption[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [optionError, setOptionError] = useState('')
  const [optionsReady, setOptionsReady] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [conflict, setConflict] = useState(false)
  const [savedOnce, setSavedOnce] = useState(false)
  const alive = useRef(false)
  const generation = useRef(0)
  const busy = useRef(false)
  const dirty = Boolean(saved && draft && annotationChanged(saved, draft))
  useStudentArchiveEditor(dirty, saving)
  const editable = Boolean(canUpdate && saved?.canEdit)
  const invalid = draft?.proxyStudent === 'true' && !draft.agentName?.trim()

  const load = useCallback(async () => {
    const request = ++generation.current
    setLoading(true); setLoadError(''); setOptionError(''); setOptionsReady(false)
    try {
      const row = await studentServiceAnnotationApi.get(relationId)
      if (!alive.current || request !== generation.current) return
      setSaved(row); setDraft(annotationValues(row)); setSaveError(''); setConflict(false); setSavedOnce(false)
      if (canUpdate && row.canEdit) {
        try {
          const choices = await studentServiceAnnotationApi.options(relationId)
          if (!alive.current || request !== generation.current) return
          setOptions(choices); setOptionsReady(true)
          if (!choices.length) setOptionError('是否选项尚未配置，请联系管理员维护字典')
        } catch (error) {
          if (alive.current && request === generation.current) setOptionError(error instanceof Error ? error.message : '选项读取失败')
        }
      }
    } catch (error) {
      if (alive.current && request === generation.current) setLoadError(error instanceof Error ? error.message : '批注读取失败')
    } finally {
      if (alive.current && request === generation.current) setLoading(false)
    }
  }, [relationId, canUpdate])
  useEffect(() => { alive.current = true; void load(); return () => { alive.current = false; generation.current++ } }, [load])

  const save = useCallback(async () => {
    if (!saved || !draft || !editable || invalid || busy.current || conflict || !optionsReady || optionError) return
    busy.current = true; setSaving(true); setSaveError('')
    const request = generation.current
    try {
      const row = await studentServiceAnnotationApi.save(relationId, annotationValues(draft), saved.version)
      if (!alive.current || request !== generation.current) return
      setSaved(row); setDraft(annotationValues(row)); setSavedOnce(true)
    } catch (error) {
      if (!alive.current || request !== generation.current) return
      setConflict(error instanceof ApiError && error.code === STUDENT_ANNOTATION_CONFLICT)
      setSaveError(error instanceof Error ? error.message : '保存失败，请重试')
    } finally {
      busy.current = false
      if (alive.current && request === generation.current) setSaving(false)
    }
  }, [saved, draft, editable, invalid, conflict, optionsReady, optionError, relationId])
  useEffect(() => {
    if (!dirty || loading || saving || saveError || invalid) return
    const timer = setTimeout(() => { void save() }, 800)
    return () => clearTimeout(timer)
  }, [dirty, loading, saving, saveError, invalid, save])
  const reload = () => {
    if (!dirty || window.confirm('重新读取会放弃当前未保存的批注，是否继续？')) void load()
  }
  const change = (values: Partial<StudentAnnotationValues>) => {
    setDraft(current => current ? { ...current, ...values } : current)
    if (!conflict) setSaveError('')
    setSavedOnce(false)
  }
  const choice = (field: 'proxyStudent' | 'serviceNeeded', label: string) => {
    const value = draft?.[field] ?? null
    const snapshot = field === 'proxyStudent' ? saved?.proxyStudentLabel : saved?.serviceNeededLabel
    const choices = annotationOptions(options, saved?.[field] ?? null, snapshot ?? null)
    return <div className="student-annotation-field"><label htmlFor={`${id}-${field}`}>{label}</label>{editable
      ? <Select id={`${id}-${field}`} aria-label={label} size="small" allowClear placeholder="未标注" value={value ?? undefined} options={choices} disabled={saving || !optionsReady || Boolean(optionError) || conflict}
        onChange={(next: AnnotationChoice | undefined) => change({ [field]: next ?? null, ...(field === 'proxyStudent' && next !== 'true' ? { agentName: null } : {}) })} />
      : <span>{saved?.[field] ? snapshot || '历史标签未记录' : '未标注'}</span>}</div>
  }
  return <section className="lead-card student-annotation-card" aria-label="学员批注">
    <div className="lead-card-header"><Typography.Text strong>学员批注</Typography.Text></div>
    {loading ? <Skeleton active title={false} paragraph={{ rows: 3 }} /> : loadError ? <Alert type="error" title={loadError} action={<Button size="small" onClick={() => void load()}>重试</Button>} /> : saved && draft && <>
      {choice('proxyStudent', '是否为代理学员')}
      <div className="student-annotation-field"><label htmlFor={`${id}-agent`}>代理人</label>{editable
        ? <Input id={`${id}-agent`} aria-label="代理人" size="small" value={draft.agentName ?? ''} maxLength={100} placeholder="填写代理人" disabled={saving || conflict || draft.proxyStudent !== 'true'} onChange={event => change({ agentName: event.target.value })} />
        : <span>{saved.agentName || '未填写'}</span>}</div>
      {choice('serviceNeeded', '是否需要服务')}
      {invalid && editable && <Typography.Text type="warning">请填写代理人后保存</Typography.Text>}
      {optionError && <Alert type="error" title={optionError} action={<Button size="small" onClick={reload}>重试</Button>} />}
      {saveError && <Alert type="error" title={saveError} action={<Button size="small" onClick={conflict ? reload : () => void save()}>{conflict ? '读取最新' : '重试保存'}</Button>} />}
      <div className="student-annotation-status" role="status">{saving ? '正在保存…' : saveError ? '未保存，内容已保留' : !editable ? '只读' : dirty ? '待保存' : savedOnce ? '已保存' : '修改后自动保存'}</div>
    </>}
  </section>
}
