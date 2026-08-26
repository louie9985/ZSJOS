import { CopyOutlined, EyeOutlined, FileSearchOutlined, LinkOutlined, MessageOutlined, PlusOutlined, ReloadOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Cascader, Checkbox, DatePicker, Empty, Form, Input, InputNumber, Modal, Pagination, Radio, Select, Skeleton, Space, Steps, Switch, Tabs, Tag, Timeline, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import DetailFieldGrid from '../components/DetailFieldGrid'
import LeadDetailOverview, { NameAvatar } from '../components/LeadDetailOverview'
import StudentDetail from '../components/StudentDetail'
import OverflowToolbar, { type ToolbarAction } from '../components/OverflowToolbar'
import { ApiError, api, type AreaNode, type DictData, type DirectorTemplateSnapshot, type MediaAccountField, type MediaAccountFieldConfig, type MediaStudentDetail, type MyStudent, type PositioningCard, type StudentContactContext, type StudyPlanner } from '../services/api'
import { DICT_TYPE } from '../constants'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'
import { DirectorAutoSaveCoordinator, type DirectorAutoSaveState } from '../services/directorAutoSave'

const PAGE_SIZE = 20
const AUTO_SAVE_DELAY_MS = 1500
const MEDIA_STUDENT_TABS = new Set(['overview', 'accounts', 'content'])
export const normalizeMediaStudentTab = (value: string | null) => value === 'positioning'
  ? 'accounts' : value && MEDIA_STUDENT_TABS.has(value) ? value : 'overview'
const labels: Record<string, string> = { active: '服务中', completed: '已完成', cancelled: '已取消', precheck: '资料预审', interview: '学员采访', positioning_ready: '账号定位准备', co_creating: '草稿', ip_review: '专业审核中', operator_feasibility: '待运营确认', student_link_pending: '待生成学员链接', student_confirm: '待学员确认', student_agreed: '学员已同意', change_requested: '学员提出修改', operator_rejected: '运营已退回', ip_rejected: '专业审核已退回', trial_14d: '试运行', confirmed: '正式定位', archived: '已归档', topic: '选题', script: '脚本', in_production: '制作中', acceptance: '待验收', published: '已发布', rejected: '已退回', revising: '修改中' }
const statusLabel = (value?: string) => value ? labels[value] || value : '未记录'
const actionLabels: Record<string, string> = {
  COMPLETE_TOPIC: '完成选题', SUBMIT_PRODUCTION: '提交制作', SUBMIT_ACCEPTANCE: '提交验收',
  APPROVE_CONTENT: '通过验收', REJECT_CONTENT: '退回修改', START_CONTENT_REVISION: '开始修改',
  RESUBMIT_PRODUCTION: '重新提交', SUBMIT_POSITIONING_REVIEW: '提交审核',
  APPROVE_POSITIONING_FEASIBILITY: '复核通过', REJECT_POSITIONING_FEASIBILITY: '复核退回',
  GENERATE_POSITIONING_STUDENT_LINK: '生成学员确认链接', CONFIRM_POSITIONING_TRIAL: '确认试跑', ARCHIVE_POSITIONING: '归档'
}
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'
const positioningShareUrl = (sharePath: string) => {
  try {
    const url = new URL(sharePath)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error()
    return url.toString()
  } catch {
    throw new Error('学员确认页公网地址未配置或无效，请联系管理员')
  }
}
const positioningDisplayValue = (card: PositioningCard | undefined, key: string) => {
  const snapshot = card?.dictSnapshot?.[key]
  if (Array.isArray(snapshot)) {
    const labels = snapshot.map(item => item && typeof item === 'object'
      ? String((item as { labelSnapshot?: unknown }).labelSnapshot || '') : '').filter(Boolean)
    if (labels.length) return labels.join('、')
  } else if (snapshot && typeof snapshot === 'object') {
    const label = (snapshot as { labelSnapshot?: unknown }).labelSnapshot
    if (label != null && label !== '') return String(label)
  }
  const value = card?.valuesSnapshot?.[key]
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (value && typeof value === 'object') return JSON.stringify(value)
  return value == null || value === '' ? '未填写' : String(value)
}
const findAreaPath = (nodes: AreaNode[], targetId: number, parents: number[] = []): number[] | undefined => {
  for (const node of nodes) {
    const path = [...parents, node.id]
    if (node.id === targetId) return path
    const childPath = findAreaPath(node.children || [], targetId, path)
    if (childPath) return childPath
  }
  return undefined
}
export default function MediaStudentsPage({ permissions = [] }: { permissions?: string[] }) {
  const { message } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<MyStudent[]>([]), [detail, setDetail] = useState<MediaStudentDetail>()
  const [selectedServiceId, setSelectedServiceId] = useState<number>(), [selectedAccountId, setSelectedAccountId] = useState<number>()
  const [selectedId, setSelectedId] = useState<number>()
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState(''), [pageNo, setPageNo] = useState(1), [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false), [detailLoading, setDetailLoading] = useState(false), [error, setError] = useState(''), [detailError, setDetailError] = useState('')
  const [tab, setTab] = useState(normalizeMediaStudentTab(params.get('tab'))), [dialog, setDialog] = useState<'account' | 'content' | 'positioning' | 'reject-content' | 'reject-positioning' | 'precheck' | 'interview' | 'operator'>(), [saving, setSaving] = useState(false)
  const [directorContext, setDirectorContext] = useState<StudentContactContext>(), [operatorCandidates, setOperatorCandidates] = useState<StudyPlanner[]>([])
  const [platforms, setPlatforms] = useState<DictData[]>([]), [contentClasses, setContentClasses] = useState<DictData[]>([])
  const [accountFieldConfig, setAccountFieldConfig] = useState<MediaAccountFieldConfig>(), [fieldDicts, setFieldDicts] = useState<Record<string, DictData[]>>({})
  const [areas, setAreas] = useState<AreaNode[]>([]), [legacyRegionText, setLegacyRegionText] = useState<string>()
  const [positioningTemplate, setPositioningTemplate] = useState<DirectorTemplateSnapshot>()
  const [positioningDetail, setPositioningDetail] = useState<PositioningCard>()
  const [shareLink, setShareLink] = useState<string>()
  const [autoSave, setAutoSave] = useState<DirectorAutoSaveState>({ status: 'idle' })
  const [rejectingContent, setRejectingContent] = useState<MediaStudentDetail['contents'][number]>()
  const [rejectingPositioning, setRejectingPositioning] = useState<MediaStudentDetail['positioningCards'][number]>()
  const [form] = Form.useForm<Record<string, unknown>>(), listRun = useRef(0), detailRun = useRef(0)
  const stageDraftVersion = useRef<number | undefined>(undefined)
  const draftIdentity = useRef<{ serviceRelationId: number; stage: 'precheck' | 'interview'; session: number; templateVersionId?: number } | undefined>(undefined)
  const positioningDraft = useRef<{ id: number; version: number } | undefined>(undefined)
  const autoSaveCoordinator = useRef<DirectorAutoSaveCoordinator | undefined>(undefined)
  if (!autoSaveCoordinator.current) {
    autoSaveCoordinator.current = new DirectorAutoSaveCoordinator(AUTO_SAVE_DELAY_MS, setAutoSave, () => crypto.randomUUID(), cause => cause instanceof ApiError && [1900010024, 1900014003].includes(cause.code))
  }

  const loadDetail = useCallback(async (personId: number, preferredServiceId?: number, preferredAccountId?: number) => {
    const run = ++detailRun.current; setSelectedId(personId); setDetailLoading(true); setDetailError('')
    try {
      const value = await api.mediaStudents.get(personId)
      const service = value.student.services.find(item => item.serviceRelationId === preferredServiceId) || value.student.services[0]
      const context = service ? await api.studentContactContext(service.serviceRelationId) : undefined
      const accountId = value.accounts.some(item => item.id === preferredAccountId) ? preferredAccountId : value.accounts[0]?.id
      if (run === detailRun.current) { setDetail(value); setSelectedServiceId(service?.serviceRelationId); setSelectedAccountId(accountId); setDirectorContext(context) }
    }
    catch (cause) { if (run === detailRun.current) { setDetail(undefined); setDetailError(cause instanceof ApiError && cause.code === 403 ? '无权查看该学员' : errorText(cause)) } }
    finally { if (run === detailRun.current) setDetailLoading(false) }
  }, [])
  const loadPage = useCallback(async (targetPage: number, preferred?: number) => {
    const run = ++listRun.current; setLoading(true); setError('')
    try { const result = await api.mediaStudents.page({ pageNo: targetPage, pageSize: PAGE_SIZE, keyword: keyword || undefined }); if (run !== listRun.current) return
      setRows(result.list); setTotal(result.total); setPageNo(targetPage); const target = preferred || Number(params.get('personId')) || result.list[0]?.personId
      if (target) await loadDetail(target, undefined, Number(params.get('accountId')) || undefined); else setDetail(undefined)
    } catch (cause) { if (run === listRun.current) { setRows([]); setDetail(undefined); setError(errorText(cause)) } } finally { if (run === listRun.current) setLoading(false) }
  }, [keyword, loadDetail, params])
  useEffect(() => { void loadPage(1) }, [keyword])
  useEffect(() => () => autoSaveCoordinator.current?.dispose(), [])

  const accountName = (id: number) => detail?.accounts.find(item => item.id === id)?.nickname || '未匹配账号'
  const selectedService = detail?.student.services.find(item => item.serviceRelationId === selectedServiceId) || detail?.student.services[0]
  const resetAutoSave = () => {
    stageDraftVersion.current = undefined; draftIdentity.current = undefined; positioningDraft.current = undefined
    return autoSaveCoordinator.current!.begin()
  }
  const open = async (type: typeof dialog, accountId?: number) => { form.resetFields(); const session = resetAutoSave(); setLegacyRegionText(undefined); if (type === 'positioning') setPositioningTemplate(undefined); if (accountId) form.setFieldValue('accountId', accountId); if (type !== 'positioning' && type !== 'precheck' && type !== 'interview') setDialog(type)
    if (type === 'precheck' || type === 'interview') {
      let activeContext = directorContext
      if (detailLoading || !activeContext || activeContext.serviceRelationId !== selectedService?.serviceRelationId) {
        if (!selectedService) { message.error('当前课程服务上下文不可用，请刷新后再试'); return }
        activeContext = await api.studentContactContext(selectedService.serviceRelationId)
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setDirectorContext(activeContext)
      }
      const stageForm = activeContext.directorForms?.[type]
      stageDraftVersion.current = stageForm?.version ?? 0
      draftIdentity.current = { serviceRelationId: selectedService!.serviceRelationId, stage: type, session, templateVersionId: stageForm?.templateVersionId }
      const dictTypes = [...new Set((stageForm?.fields || []).filter(field => field.dictType).map(field => field.dictType!))]
      try {
        const [entries, areaRows] = await Promise.all([
          Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const)),
          type === 'interview' && (stageForm?.fields || []).some(field => field.type === 'region')
            ? (areas.length ? Promise.resolve(areas) : api.areaTree()) : Promise.resolve(areas)
        ])
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) })); setAreas(areaRows)
        const data = { ...(stageForm?.values || {}) }
        const existingRegion = data.region
        if (type === 'interview' && existingRegion && typeof existingRegion === 'object' && !Array.isArray(existingRegion)) {
          const code = Number((existingRegion as { code?: unknown }).code)
          data.region = Number.isFinite(code) ? findAreaPath(areaRows, code) : undefined
        } else if (type === 'interview' && typeof existingRegion === 'string') {
          setLegacyRegionText(existingRegion); delete data.region
        }
        form.setFieldsValue({ data, confirmed: false, interviewAt: stageForm?.interviewAt ? dayjs(stageForm.interviewAt) : activeContext?.defaultDirectorInterviewAt ? dayjs(activeContext.defaultDirectorInterviewAt) : undefined, submit: true })
        setDialog(type)
      }
      catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } return }
    }
    if (type === 'positioning') {
      positioningDraft.current = undefined
      try {
        const existing = detail?.positioningDrafts.find(card => card.accountId === accountId)
        const existingCard = existing ? await api.positioningCard.get(existing.id) : undefined
        const template = existingCard ? {
          templateId: existingCard.templateId || 0,
          templateVersionId: existingCard.templateVersionId || 0,
          templateVersionNo: existingCard.versionNo || 1,
          fields: existingCard.fieldsSnapshot || [],
          values: existingCard.valuesSnapshot || {},
          dictSnapshots: existingCard.dictSnapshot || {}
        } : await api.positioningCard.publishedTemplate()
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        if (existingCard) positioningDraft.current = { id: existingCard.id, version: existingCard.version }
        setPositioningTemplate(template)
        form.setFieldsValue({ accountId, data: template.values || {}, trialEndDate: existingCard?.trialEndDate ? dayjs(existingCard.trialEndDate) : dayjs().add(directorContext?.directorTrialDays || 14, 'day') })
        const dictTypes = [...new Set(template.fields.filter(field => field.dictType).map(field => field.dictType!))]
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) }))
        setDialog(type)
      } catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } return }
    }
    if (type === 'account') {
      try {
        const [platformRows, config] = await Promise.all([platforms.length ? platforms : api.dictDataByType('zsjos_account_platform'), api.mediaAccount.publishedFieldConfig()])
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setPlatforms(platformRows); setAccountFieldConfig(config)
        const dictTypes = [...new Set(config.fields.filter(field => field.enabled && field.dictType).map(field => field.dictType!))]
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setFieldDicts(Object.fromEntries(entries))
      } catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } return }
    }
    if (type === 'operator' && selectedServiceId) {
      try { const candidates = await api.studentCollaboratorCandidates(selectedServiceId, 'operator'); if (autoSaveCoordinator.current!.isCurrent(session)) setOperatorCandidates(candidates) }
      catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } return }
    }
    if (type === 'content' && !contentClasses.length) {
      try { const classes = await api.dictDataByType('zsjos_content_class'); if (autoSaveCoordinator.current!.isCurrent(session)) setContentClasses(classes) }
      catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } }
    } }
  const autoSaveDialog = dialog === 'precheck' || dialog === 'interview' || dialog === 'positioning'
  const draftSaveTask = () => {
    if (!autoSaveDialog || !detail || !selectedService) throw new Error('当前草稿上下文不可用，请重新打开后再试')
    const activeDialog = dialog
    const serviceRelationId = selectedService.serviceRelationId
    const personId = detail.student.personId
    const templateId = positioningTemplate?.templateId
    const values = form.getFieldsValue(true)
    const identity = activeDialog === 'precheck' || activeDialog === 'interview' ? draftIdentity.current : undefined
    const interviewAt = values.interviewAt && typeof (values.interviewAt as { format?: (value: string) => string }).format === 'function'
      ? (values.interviewAt as { format: (value: string) => string }).format('YYYY-MM-DDTHH:mm:ss') : undefined
    const data = activeDialog === 'precheck' ? {} : { ...((values.data || {}) as Record<string, unknown>) }
    if (activeDialog === 'interview') {
      const regionPath = data.region
      if (Array.isArray(regionPath) && regionPath.length) data.region = { code: Number(regionPath[regionPath.length - 1]) }
      else if (!regionPath && legacyRegionText) data.region = legacyRegionText
    }
    const accountId = Number(values.accountId)
    const trialEndDate = values.trialEndDate ? dayjs(values.trialEndDate as never).format('YYYY-MM-DD') : undefined
    return async (idempotencyKey: string, session: number) => {
        if (activeDialog === 'precheck' || activeDialog === 'interview') {
          if (!identity || identity.session !== session || identity.stage !== activeDialog || identity.serviceRelationId !== serviceRelationId || !autoSaveCoordinator.current!.isCurrent(session)) throw new Error('当前表单会话已失效，请重新打开后再试')
          const version = stageDraftVersion.current
          if (version == null) throw new Error('当前表单版本不可用，请重新打开后再试')
          const request = { interviewAt, data, version, idempotencyKey }
          const authoritativeVersion = activeDialog === 'precheck'
            ? await api.studentDirectorPrecheckDraft(serviceRelationId, request)
            : await api.studentDirectorInterviewDraft(serviceRelationId, request)
          if (!autoSaveCoordinator.current!.isCurrent(session)) return
          stageDraftVersion.current = authoritativeVersion
          return
        }
        if (!accountId || !templateId) throw new Error('请选择第三方账号后再保存草稿')
        const request = { accountId, studentPersonId: personId, serviceRelationId, templateId, trialEndDate, values: data }
        const result = positioningDraft.current
          ? await api.positioningCard.updateDraft(positioningDraft.current.id, { accountId, trialEndDate, values: data, version: positioningDraft.current.version })
          : await api.positioningCard.createDraft(request)
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        positioningDraft.current = result
    }
  }
  const scheduleAutoSave = () => {
    if (!autoSaveDialog) return
    autoSaveCoordinator.current!.schedule(draftSaveTask())
  }
  const closeDialog = async () => {
    try { await autoSaveCoordinator.current!.flush(); autoSaveCoordinator.current!.invalidate(); setDialog(undefined) }
    catch { /* Keep the form open so the user can retry without losing changes. */ }
  }
  const autoSaveNotice = autoSaveDialog ? <div className="director-autosave-status">
    <Typography.Text type={autoSave.status === 'error' || autoSave.status === 'conflict' ? 'danger' : 'secondary'}>
      {autoSave.status === 'dirty' ? '内容已修改，等待自动保存' : autoSave.status === 'saving' ? '正在保存草稿…' : autoSave.status === 'saved' ? `草稿已自动保存 ${dayjs(autoSave.savedAt).format('HH:mm:ss')}` : autoSave.status === 'error' || autoSave.status === 'conflict' ? autoSave.error : '草稿自动保存已开启'}
    </Typography.Text>
    {autoSave.status === 'error' && <Button type="link" size="small" onClick={() => void autoSaveCoordinator.current!.retry().catch(() => undefined)}>重试</Button>}
    {autoSave.status === 'conflict' && <Button type="link" size="small" onClick={() => { autoSaveCoordinator.current!.invalidate(); setDialog(undefined); if (detail) void loadDetail(detail.student.personId, selectedServiceId, selectedAccountId) }}>重新加载</Button>}
  </div> : null
  const submit = async () => { if (!detail || !dialog) return
    try {
      if (dialog === 'positioning') {
        setSaving(true); await autoSaveCoordinator.current!.saveNow(draftSaveTask()); autoSaveCoordinator.current!.invalidate(); setDialog(undefined); message.success('定位卡草稿已保存')
        await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId); return
      }
      const values = await form.validateFields(); setSaving(true)
      if (dialog === 'account') await api.mediaAccount.create({ studentPersonId: detail.student.personId, platformValue: String(values.platformValue), platformLabelSnapshot: platforms.find(x => x.value === values.platformValue)?.label || '', detailValues: (values.detailValues || {}) as Record<string, unknown> })
      if (dialog === 'content') await api.mediaContent.create({ ...values, contentClassLabelSnapshot: contentClasses.find(x => x.value === values.contentClassValue)?.label || '' } as never)
      if ((dialog === 'precheck' || dialog === 'interview') && selectedService && directorContext) {
        const stage = dialog
        const shouldSubmit = values.submit !== false
        await autoSaveCoordinator.current!.prepareSubmit()
        if (!shouldSubmit) {
          await autoSaveCoordinator.current!.saveNow(draftSaveTask())
          autoSaveCoordinator.current!.invalidate(); setDialog(undefined); message.success('草稿已保存')
          await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId); return
        }
        if (stage === 'precheck' && shouldSubmit && !values.interviewAt) throw new Error('提交资料预审前请填写访谈预约时间')
        const interviewAt = values.interviewAt && typeof (values.interviewAt as { format?: (value: string) => string }).format === 'function' ? (values.interviewAt as { format: (value: string) => string }).format('YYYY-MM-DDTHH:mm:ss') : undefined
        if (stage === 'precheck' && !values.confirmed) throw new Error('请先确认学员和服务资料无误')
        const data = stage === 'precheck' ? {} : { ...((values.data || {}) as Record<string, unknown>) }
        if (stage === 'interview') {
          const regionPath = data.region
          if (Array.isArray(regionPath) && regionPath.length) data.region = { code: Number(regionPath[regionPath.length - 1]) }
          else if (!regionPath && legacyRegionText) data.region = legacyRegionText
        }
        const request = { interviewAt, data, version: stageDraftVersion.current ?? directorContext.directorForms?.[stage]?.version ?? 0, idempotencyKey: crypto.randomUUID() }
        if (stage === 'precheck') await api.studentDirectorPrecheckSubmit(selectedService.serviceRelationId, request)
        else await api.studentDirectorInterviewSubmit(selectedService.serviceRelationId, request)
      }
      if (dialog === 'operator' && selectedService) await api.studentAssignCollaborator(selectedService.serviceRelationId, { collaboratorType: 'operator', userId: Number(values.userId), version: selectedService.version || 0, correctionReason: values.correctionReason ? String(values.correctionReason) : undefined, idempotencyKey: crypto.randomUUID() })
      if (dialog === 'reject-content' && rejectingContent) await api.mediaContent.rejectAcceptance(rejectingContent.id, rejectingContent.version, String(values.reason))
      if (dialog === 'reject-positioning' && rejectingPositioning) await api.positioningCard.operatorReject(rejectingPositioning.id, rejectingPositioning.version, String(values.reason))
      if (autoSaveDialog) autoSaveCoordinator.current!.invalidate()
      setDialog(undefined); message.success('已保存'); await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) {
        message.error(errorText(cause))
        if ((dialog === 'precheck' || dialog === 'interview') && !(cause instanceof ApiError && [1900010024, 1900014003].includes(cause.code))) {
          scheduleAutoSave()
        }
      }
    } finally { setSaving(false) } }
  const contentAction = async (row: MediaStudentDetail['contents'][number], action: string) => { try {
    if (action === 'REJECT_CONTENT') { form.resetFields(); setRejectingContent(row); setDialog('reject-content'); return }
    if (action === 'COMPLETE_TOPIC') await api.mediaContent.completeTopic(row.id, row.version); else if (action === 'SUBMIT_PRODUCTION') await api.mediaContent.submitProduction(row.id, row.version); else if (action === 'SUBMIT_ACCEPTANCE') await api.mediaContent.submitAcceptance(row.id, row.version); else if (action === 'APPROVE_CONTENT') await api.mediaContent.approveAcceptance(row.id, row.version); else if (action === 'START_CONTENT_REVISION') await api.mediaContent.startRevision(row.id, row.version); else if (action === 'RESUBMIT_PRODUCTION') await api.mediaContent.resubmitProduction(row.id, row.version); else return
    if (selectedId) await loadDetail(selectedId, selectedServiceId, selectedAccountId) } catch (cause) { message.error(errorText(cause)) } }
  const positioningAction = async (row: MediaStudentDetail['positioningCards'][number] | MediaStudentDetail['positioningDrafts'][number], action: string) => { try {
    if (action === 'REJECT_POSITIONING_FEASIBILITY') { form.resetFields(); setRejectingPositioning(row as MediaStudentDetail['positioningCards'][number]); setDialog('reject-positioning'); return }
    if (action === 'SUBMIT_POSITIONING_REVIEW') await api.positioningCard.submitReview(row.id, row.version)
    else if (action === 'APPROVE_POSITIONING_FEASIBILITY') await api.positioningCard.operatorApprove(row.id, row.version)
    else if (action === 'GENERATE_POSITIONING_STUDENT_LINK') {
      const result = await api.positioningCard.generateStudentLink(row.id, row.version)
      const url = positioningShareUrl(result.sharePath)
      setShareLink(url); await navigator.clipboard.writeText(url).catch(() => undefined)
      message.success('学员确认链接已生成并复制')
    } else if (action === 'CONFIRM_POSITIONING_TRIAL') await api.positioningCard.confirmTrial(row.id, row.version)
    else if (action === 'ARCHIVE_POSITIONING') await api.positioningCard.archive(row.id, row.version); else return
    if (selectedId) await loadDetail(selectedId, selectedServiceId, selectedAccountId) } catch (cause) { message.error(errorText(cause)) } }
  const viewPositioning = async (id: number) => { try { setPositioningDetail(await api.positioningCard.get(id)) } catch (cause) { message.error(errorText(cause)) } }
  const actions = (items: string[] | undefined, click: (action: string) => void) => items?.map(action => <Button size="small" key={action} onClick={() => click(action)}>{actionLabels[action] || action}</Button>)
  const accountField = (field: MediaAccountField) => {
    const name = ['detailValues', field.key]
    const rules = field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined
    if (field.type === 'textarea') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><Input.TextArea rows={3} /></Form.Item>
    if (field.type === 'number') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><InputNumber style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'date') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><DatePicker style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'select' || field.type === 'multi_select') return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><Select mode={field.type === 'multi_select' ? 'multiple' : undefined} options={(fieldDicts[field.dictType || ''] || []).map(item => ({ value: item.value, label: item.label }))} /></Form.Item>
    if (field.type === 'boolean') return <Form.Item key={field.key} name={name} label={field.label} valuePropName="checked"><Switch /></Form.Item>
    return <Form.Item key={field.key} name={name} label={field.label} rules={rules}><Input /></Form.Item>
  }
  const directorField = (field: NonNullable<StudentContactContext['formFields']>[number]) => {
    const name = ['data', field.key], rules = field.required ? [{ validator: (_: unknown, value: unknown) => {
      const empty = value == null || value === '' || Array.isArray(value) && value.length === 0
      return form.getFieldValue('submit') === false || !empty ? Promise.resolve() : Promise.reject(new Error(`请填写${field.title}`))
    } }] : undefined
    const label = <span>{field.title} {field.required ? <Typography.Text type="danger">（必填）</Typography.Text> : <Typography.Text type="secondary">（选填）</Typography.Text>}</span>
    if (field.type === 'textarea') return <Form.Item key={field.key} name={name} label={label} rules={rules}><Input.TextArea rows={4} /></Form.Item>
    if (field.type === 'number') return <Form.Item key={field.key} name={name} label={label} rules={rules}><InputNumber style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'date' || field.type === 'datetime') return <Form.Item key={field.key} name={name} label={label} rules={rules}><DatePicker showTime={field.type === 'datetime'} style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'region') return <div key={field.key}>{legacyRegionText && <Alert type="warning" showIcon message={`历史地区：${legacyRegionText}`} description="历史文本仅保留用于草稿兼容，正式提交前请从系统地区中重新选择。"/>}<Form.Item name={name} label={label} rules={rules}><Cascader options={areas} fieldNames={{ label: 'name', value: 'id', children: 'children' }} changeOnSelect showSearch placeholder="请选择地区" style={{ width: '100%' }} /></Form.Item></div>
    if (field.type === 'dict' || field.type === 'select' || field.type === 'multi_select' || field.type === 'radio' || field.type === 'checkbox_group') {
      const options = (fieldDicts[field.dictType || ''] || []).map(item => ({ value: item.value, label: item.label }))
      if (field.type === 'radio') return <Form.Item key={field.key} name={name} label={<span>{field.title} <Typography.Text type={field.required ? 'danger' : 'secondary'}>（单选·{field.required ? '必填' : '选填'}）</Typography.Text></span>} rules={rules}><Radio.Group options={options} /></Form.Item>
      if (field.type === 'checkbox_group') return <Form.Item key={field.key} name={name} label={<span>{field.title} <Typography.Text type={field.required ? 'danger' : 'secondary'}>（多选·{field.required ? '必填' : '选填'}）</Typography.Text></span>} rules={rules}><Checkbox.Group options={options} /></Form.Item>
      return <Form.Item key={field.key} name={name} label={label} rules={rules}><Select mode={field.multiple || field.type === 'multi_select' ? 'multiple' : undefined} options={options} /></Form.Item>
    }
    if (field.type === 'checkbox') return <Form.Item key={field.key} name={name} valuePropName="checked" label={label} rules={rules}><Checkbox>勾选</Checkbox></Form.Item>
    return <Form.Item key={field.key} name={name} label={label} rules={rules}><Input /></Form.Item>
  }
  const interviewFields = directorContext?.directorForms?.interview?.fields || []
  const interviewGroups = Array.from(new Set(interviewFields.map(field => field.group || '基本信息')))

  const selectedAccount = detail?.accounts.find(item => item.id === selectedAccountId)
  const selectedPositioningCards = detail?.positioningCards.filter(item => item.accountId === selectedAccountId) || []
  const selectedPositioningDrafts = detail?.positioningDrafts.filter(item => item.accountId === selectedAccountId) || []
  const selectedContents = detail?.contents.filter(item => item.accountId === selectedAccountId) || []
  const directorStage = directorContext?.directorStage || selectedService?.directorStage || 'precheck'
  const studentTaskLine = [
    { key: 'precheck', label: '资料预审', status: directorStage === 'precheck' ? 'current' as const : 'done' as const, detail: directorStage === 'precheck' ? '待编导审核资料并预约访谈' : '资料预审已提交' },
    { key: 'interview', label: '学员采访', status: directorStage === 'positioning_ready' ? 'done' as const : directorStage === 'interview' ? 'current' as const : 'pending' as const, detail: '采集学员级基础信息' }
  ]
  const taskLine = [...studentTaskLine, ...(selectedAccount?.taskLine || [])]
  const openDirectorAction = (action: string) => {
    if (action === 'DIRECTOR_PRECHECK') void open('precheck')
    if (action === 'DIRECTOR_INTERVIEW') void open('interview')
    if (action === 'ASSIGN_OPERATOR') void open('operator')
  }
  const selectService = async (serviceRelationId: number) => {
    const service = detail?.student.services.find(item => item.serviceRelationId === serviceRelationId)
    if (!service) return
    const run = ++detailRun.current
    setDetailLoading(true); setDetailError('')
    try {
      const context = await api.studentContactContext(serviceRelationId)
      if (run === detailRun.current) {
        setSelectedServiceId(serviceRelationId)
        setDirectorContext(context)
      }
    } catch (cause) {
      if (run === detailRun.current) {
        setDetailError(cause instanceof ApiError && cause.code === 403 ? '无权查看该课程服务' : errorText(cause))
      }
    } finally { if (run === detailRun.current) setDetailLoading(false) }
  }
  const selectAccount = (accountId: number) => {
    if (!detail) return
    setSelectedAccountId(accountId)
    setParams({ personId: String(detail.student.personId), tab: tab === 'overview' ? 'accounts' : tab, accountId: String(accountId) })
  }
  const latestContent = selectedContents[0]
  const mediaTimeline = detail?.operationTimeline.filter(item => item.type !== 'talk') || []
  const mediaOverview = detail && selectedService ? <LeadDetailOverview
    student={detail.student}
    categoryLabel={() => '-'}
    channelLabel={() => '-'}
    showFollowUp={false}
    studentService={selectedService}
    toolbar={<OverflowToolbar actions={directorContext?.availableActions.filter(action => ['DIRECTOR_PRECHECK', 'DIRECTOR_INTERVIEW', 'ASSIGN_OPERATOR'].includes(action)).map<ToolbarAction>(action => ({
      key: action,
      icon: action === 'DIRECTOR_PRECHECK' ? <FileSearchOutlined /> : action === 'DIRECTOR_INTERVIEW' ? <MessageOutlined /> : <UserSwitchOutlined />,
      label: action === 'DIRECTOR_PRECHECK' ? '资料预审' : action === 'DIRECTOR_INTERVIEW' ? '学员采访' : '指派运营',
      onClick: () => openDirectorAction(action)
    })) || []} />}
    slots={{
      latestActivity: <div className="lead-latest-followup"><div className="lead-section-header"><Typography.Text strong>最新内容</Typography.Text>{latestContent?.lastActivityAt && <Typography.Text type="secondary">{formatTimestamp(latestContent.lastActivityAt)}</Typography.Text>}</div>{latestContent ? <div className="lead-latest-followup-body"><div className="lead-latest-followup-tags"><Tag>{statusLabel(latestContent.status)}</Tag><Tag color="blue">{accountName(latestContent.accountId)}</Tag></div><Typography.Paragraph className="lead-latest-followup-remark">{latestContent.title || latestContent.contentNo}</Typography.Paragraph></div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内容" />}</div>,
      timeline: <div className="lead-flow-timeline"><div className="lead-section-header"><Typography.Text strong>操作时间线</Typography.Text></div>{mediaTimeline.length ? <Timeline items={mediaTimeline.slice(0, 8).map(item => ({ children: <div className="media-students-timeline-item"><strong>{item.title}</strong><span>{item.detail}</span><Typography.Text type="secondary">{item.operatorName || '系统记录'} · {formatTimestamp(item.occurredAt)}</Typography.Text></div> }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作记录" />}</div>,
      mainBeforeColumns: directorContext?.operatorAssignmentConflict ? <Alert type="warning" showIcon message="该学员存在多个运营归属，请通过“指派运营”统一修正" /> : undefined,
      taskStatus: <>
        <div className="lead-status-pipeline">
          {taskLine.map((item, index) => <div key={item.key} className={`lead-status-node ${item.status === 'done' ? 'done' : item.status === 'current' ? 'current' : 'future'}`}>
            <div className="lead-status-dot" />
            {index < taskLine.length - 1 && <div className="lead-status-connector" />}
            <span className="lead-status-step-label">{item.label}</span>
          </div>)}
        </div>
        <div className="lead-status-divider" />
        <div className="lead-status-labels">
          <div className={`lead-status-label-item color-${directorStage === 'positioning_ready' ? 'green' : 'blue'}`}>
            <span className="lead-status-label-name">学员级阶段</span>
            <span className="lead-status-label-value">{statusLabel(directorStage)}</span>
          </div>
          <div className={`lead-status-label-item color-${selectedAccount ? 'green' : 'gray'}`}>
            <span className="lead-status-label-name">账号级阶段</span>
            <span className="lead-status-label-value">{selectedAccount ? `${selectedAccount.nickname || selectedAccount.accountNo} · ${statusLabel(selectedPositioningCards[0]?.status)}` : '尚无账号'}</span>
          </div>
          <div className={`lead-status-label-item color-${directorContext?.directorInterviewAt ? 'blue' : 'gray'}`}>
            <span className="lead-status-label-name">访谈预约</span>
            <span className="lead-status-label-value">{directorContext?.directorInterviewAt ? formatTimestamp(directorContext.directorInterviewAt) : '未预约'}</span>
          </div>
        </div>
      </>,
      sidebarBeforeStatus: <><section className="lead-card"><div className="lead-card-header"><Typography.Text strong>课程服务信息</Typography.Text></div><DetailFieldGrid columns={1} items={[{ key: 'course', label: '课程', value: selectedService.courseName || selectedService.skuName }, { key: 'sku', label: '具体方案', value: selectedService.skuName }, { key: 'category', label: '分类', value: selectedService.categoryPath?.join(' / ') }, { key: 'attributes', label: '课程属性', value: selectedService.attributeValues?.join(' / ') }, { key: 'order', label: '订单号', value: selectedService.orderNo }, { key: 'status', label: '服务状态', value: statusLabel(selectedService.status) }, { key: 'planner', label: '学习规划师', value: selectedService.ownerUserName || '未分配' }, { key: 'director', label: '编导', value: selectedService.contentDirectorUserName || '未分配' }, { key: 'operator', label: '运营负责人', value: selectedService.operatorUserName || '未分配' }]} /></section><section className="lead-card"><div className="lead-card-header"><Typography.Text strong>待处理</Typography.Text></div><div className="media-students-stats"><div><strong>{detail.pendingStats.accountCount}</strong><span>第三方账号</span></div><div><strong>{detail.pendingStats.positioningCount}</strong><span>定位任务</span></div><div><strong>{detail.pendingStats.contentCount}</strong><span>内容任务</span></div><div><strong>{detail.pendingStats.productionCount}</strong><span>拍剪任务</span></div></div></section></>
    }}
  /> : undefined

  const tabs = detail ? [
    { key: 'accounts', label: '账号', children: <><section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>学员账号</Typography.Title>{hasPermission(permissions, 'zsjos:media-account:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('account')}>新增账号</Button>}</Space>{detail.accounts.length ? <div className="media-students-record-list">{detail.accounts.map(x => <div className={`media-students-record${selectedAccountId === x.id ? ' active' : ''}`} key={x.id} onClick={() => selectAccount(x.id)}><div><strong>{x.nickname || x.accountNo}</strong><span>{x.platformLabel || '平台未记录'} · {x.accountNo}</span>{x.detailSnapshots?.length ? <span>{x.detailSnapshots.map(field => `${field.label}：${field.displayValue || '未记录'}`).join(' · ')}</span> : <span>详情字段未记录</span>}</div><Space><Tag>{statusLabel(x.stage)}</Tag>{hasPermission(permissions, 'zsjos:positioning-card:create') && <Button onClick={event => { event.stopPropagation(); void open('positioning', x.id) }}>填写定位卡</Button>}</Space></div>)}</div> : <Empty description="暂无账号" />}</section><section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>账号定位卡</Typography.Title></Space>{selectedAccount ? <><div className="media-students-positioning-current">{selectedPositioningDrafts.map(x => <div className="media-students-record" key={`draft-${x.id}`}><div><strong>定位卡草稿</strong><span>最近保存：{formatTimestamp(x.lastActivityAt)}</span></div><Space><Tag>草稿</Tag><Button size="small" onClick={() => void open('positioning', x.accountId)}>继续编辑</Button>{actions(x.availableActions, action => void positioningAction(x, action))}</Space></div>)}{selectedPositioningCards.filter(x => x.current).map(x => <div className="media-students-record" key={`current-${x.submissionId}`}><div><strong>当前定位卡</strong><span>提交时间：{x.submittedAt ? formatTimestamp(x.submittedAt) : '历史提交时间未记录'}</span></div><Space wrap><Tag>{statusLabel(x.status)}</Tag><Tooltip title="查看定位卡"><Button size="small" icon={<EyeOutlined />} onClick={() => void viewPositioning(x.id)} /></Tooltip>{actions(x.availableActions, action => void positioningAction(x, action))}</Space></div>)}</div>{selectedPositioningCards.filter(x => !x.current).length ? <div className="media-students-positioning-history"><Typography.Text strong>历史提交</Typography.Text>{selectedPositioningCards.filter(x => !x.current).map(x => <div className="media-students-record" key={x.submissionId}><div><strong>定位卡历史提交</strong><span>{x.submittedAt ? formatTimestamp(x.submittedAt) : '历史提交时间未记录'}{x.studentDecisionComment ? ` · 修改意见：${x.studentDecisionComment}` : ''}</span></div><Tag>{statusLabel(x.status)}</Tag></div>)}</div> : null}{!selectedPositioningDrafts.length && !selectedPositioningCards.length && <Empty description="当前账号暂无定位卡" />}</> : <Empty description="请选择账号" />}</section></> },
    { key: 'content', label: '内容生产历史', children: <section className="media-students-card"><Space className="media-students-tab-heading"><Typography.Title level={5}>内容生产历史</Typography.Title>{hasPermission(permissions, 'zsjos:content:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('content')}>创建内容</Button>}</Space>{selectedAccount ? selectedContents.map(x => <div className="media-students-record" key={x.id}><div><strong>{x.title || x.contentNo}</strong><span>{accountName(x.accountId)} · {x.contentNo}</span></div><Space><Tag>{statusLabel(x.status)}</Tag>{actions(x.availableActions, action => void contentAction(x, action))}</Space></div>) : <Empty description="请选择账号" />}</section> }
  ] : []

  const serviceContextHeader = detail && selectedService ? <div className="media-students-service-context"><Typography.Text strong>课程上下文</Typography.Text><Select value={selectedService.serviceRelationId} onChange={value => void selectService(value)} options={detail.student.services.map(service => ({ value: service.serviceRelationId, label: `${service.courseName || service.skuName || '课程服务'} · ${service.orderNo || service.orderId || '订单未记录'}` }))} /></div> : undefined

  const body = detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError ? <Alert type="warning" showIcon message={detailError} action={selectedId ? <Button size="small" onClick={() => void loadDetail(selectedId, selectedServiceId)}>重试</Button> : undefined} /> : !detail ? <Empty description="从左侧选择一名学员" /> : selectedService ? <StudentDetail
    student={detail.student}
    service={selectedService}
    contactContext={directorContext}
    overviewContent={mediaOverview}
    contextHeader={serviceContextHeader}
    activeTab={tab}
    onTabChange={value => { setTab(value); setParams({ personId: String(detail.student.personId), tab: value }) }}
    extraTabs={tabs}
  /> : <Empty description="当前学员暂无课程服务" />

  return <section className="workspace-page media-students-page"><header className="media-students-filter-shell"><Typography.Title level={4}>我的学员</Typography.Title><Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void loadPage(pageNo, selectedId)} /></Tooltip></header><div className="media-students-inbox-layout"><aside className="media-students-list-pane"><div className="media-students-toolbar"><Input.Search allowClear value={search} onChange={e => setSearch(e.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索姓名或手机号" /></div>{error && <Alert type="error" showIcon message={error} />}<div className="media-students-scroll">{loading && !rows.length ? <Skeleton active /> : rows.map(x => <button type="button" className={`media-students-item${selectedId === x.personId ? ' active' : ''}`} key={x.personId} onClick={() => void loadDetail(x.personId)}><NameAvatar name={x.name || '学员'} size={36} /><span className="media-students-item-copy"><strong>{x.name || '未填写姓名'}</strong><span>{x.personNo || '暂无学员编号'}</span><span>{x.mobile || '无手机号'} · {x.services.length} 项服务</span></span></button>)}</div>{total > PAGE_SIZE && <Pagination simple current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={value => void loadPage(value)} />}</aside><main className="media-students-detail-pane">{body}</main></div>
    <Modal width={dialog === 'interview' || dialog === 'positioning' ? 'min(1180px, calc(100vw - 32px))' : undefined} styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} title={dialog === 'account' ? '新增第三方账号' : dialog === 'content' ? '创建内容' : dialog === 'positioning' ? '填写账号定位卡' : dialog === 'reject-content' ? '退回内容修改' : dialog === 'reject-positioning' ? '退回定位卡修改' : dialog === 'precheck' ? '资料预审' : dialog === 'interview' ? '学员采访' : '指派运营'} open={Boolean(dialog)} onCancel={() => void closeDialog()} onOk={() => void submit()} okText={dialog === 'positioning' ? '保存并关闭' : undefined} confirmLoading={saving}><Form form={form} layout="vertical" onValuesChange={scheduleAutoSave}>{autoSaveNotice}{dialog === 'account' && <><Form.Item name="platformValue" label="平台" rules={[{ required: true }]}><Select options={platforms.map(x => ({ value: x.value, label: x.label }))} /></Form.Item>{accountFieldConfig?.fields.filter(field => field.enabled).map(accountField)}</>}{dialog === 'content' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="title" label="内容标题" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="topic" label="选题说明"><Input.TextArea rows={3} /></Form.Item><Form.Item name="contentClassValue" label="内容分类" rules={[{ required: true }]}><Select options={contentClasses.map(x => ({ value: x.value, label: x.label }))} /></Form.Item></>}{dialog === 'positioning' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select disabled options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item>{positioningTemplate?.fields.map(directorField)}<Form.Item name="trialEndDate" label="试运行结束日期" rules={[{ required: true }]}><DatePicker style={{width:'100%'}} /></Form.Item></>}{dialog === 'precheck' && <><Alert type="info" showIcon message="请核对学员、课程、订单及服务归属信息。资料预审不填写业务表单。"/><Form.Item name="confirmed" valuePropName="checked" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请确认资料无误'))}]}><Checkbox>已确认资料无误</Checkbox></Form.Item><Form.Item name="interviewAt" label="访谈预约时间" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请选择访谈预约时间'))}]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item><Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>确认完成资料预审</Checkbox></Form.Item></>}{dialog === 'interview' && <><Alert type="info" showIcon message="带“必填”的项目提交时必须填写；单选请选择一项，多选可选择多项。"/>{interviewGroups.map(group => <section className="director-interview-group" key={group}><Typography.Title level={5}>{group === '自媒体能力' ? '自媒体运营基础能力' : group}</Typography.Title>{interviewFields.filter(field => (field.group || '基本信息') === group).map(directorField)}</section>)}<Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>提交并完成学员采访</Checkbox></Form.Item></>}{dialog === 'operator' && <><Form.Item name="userId" label="运营负责人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={operatorCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>{directorContext?.operatorAssignmentConflict && <Form.Item name="correctionReason" label="统一归属说明" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={3} /></Form.Item>}</>}{(dialog === 'reject-content' || dialog === 'reject-positioning') && <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={4} /></Form.Item>}</Form></Modal>
    <Modal title="定位卡内容" open={Boolean(positioningDetail)} footer={null} onCancel={() => setPositioningDetail(undefined)}><DetailFieldGrid columns={1} items={(positioningDetail?.fieldsSnapshot || []).filter(field => field.enabled).map(field => ({ key: field.key, label: field.title, value: positioningDisplayValue(positioningDetail, field.key) }))} /></Modal>
    <Modal title="学员确认链接" open={Boolean(shareLink)} onCancel={() => setShareLink(undefined)} footer={<Button type="primary" icon={<CopyOutlined />} onClick={() => shareLink && void navigator.clipboard.writeText(shareLink)}>复制链接</Button>}><Alert type="success" showIcon icon={<LinkOutlined />} message="链接已生成" description={shareLink} /></Modal></section>
}
