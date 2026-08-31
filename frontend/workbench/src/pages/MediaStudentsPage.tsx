import { CopyOutlined, FileSearchOutlined, ImportOutlined, LinkOutlined, MessageOutlined, PlusOutlined, ReloadOutlined, UploadOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Cascader, Checkbox, DatePicker, Empty, Form, Input, InputNumber, Modal, Pagination, Radio, Select, Skeleton, Space, Steps, Switch, Tabs, Tag, Timeline, Tooltip, Typography, Upload } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import DetailFieldGrid from '../components/DetailFieldGrid'
import LeadDetailOverview, { NameAvatar } from '../components/LeadDetailOverview'
import StudentDetail from '../components/StudentDetail'
import OverflowToolbar, { type ToolbarAction } from '../components/OverflowToolbar'
import AccountMaintenancePanel from '../components/AccountMaintenancePanel'
import ProductionTicketPositioningCard from '../components/ProductionTicketPositioningCard'
import WorkOrderAttachmentPicker from '../components/WorkOrderAttachmentPicker'
import { ApiError, api, type AreaNode, type DictData, type DirectorTemplateSnapshot, type MediaAccountField, type MediaAccountFieldConfig, type MediaStudentDetail, type MyStudent, type PositioningCard, type PositioningCardImportSource, type ProductionTicketCreateContext, type StudentContactContext, type StudyPlanner } from '../services/api'
import { DICT_TYPE } from '../constants'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'
import { DirectorAutoSaveCoordinator, type DirectorAutoSaveState } from '../services/directorAutoSave'
import { mergePositioningJsonValues, parsePositioningJson, serializePositioningFormValues, type PositioningJsonImportPreview } from '../services/positioningJsonImport'
import { workOrderApi, type WorkOrderDepartment, type WorkOrderFile, type WorkOrderTemplate } from '../services/workOrderApi'

const PAGE_SIZE = 20
const AUTO_SAVE_DELAY_MS = 1500
const MEDIA_STUDENT_TABS = new Set(['overview', 'accounts', 'content'])
export const normalizeMediaStudentTab = (value: string | null) => value === 'positioning' || value === 'maintenance'
  ? 'accounts' : value && MEDIA_STUDENT_TABS.has(value) ? value : 'overview'
const labels: Record<string, string> = { active: '服务中', completed: '已完成', cancelled: '已取消', precheck: '资料预审', interview: '学员采访', positioning_ready: '账号定位准备', co_creating: '草稿', ip_review: '历史专业审核中', operator_feasibility: '待运营审核', student_link_pending: '待生成学员链接', student_confirm: '待学员确认', student_agreed: '历史学员已同意', change_requested: '学员提出修改', operator_rejected: '运营已退回', ip_rejected: '历史专业审核已退回', trial_14d: '历史试运行', confirmed: '已确认', superseded: '已被新版本替换', archived: '历史已归档', topic: '选题', script: '脚本', in_production: '制作中', acceptance: '待验收', published: '已发布', rejected: '已退回', revising: '修改中' }
const statusLabel = (value?: string) => value ? labels[value] || value : '未记录'
const actionLabels: Record<string, string> = {
  COMPLETE_TOPIC: '完成选题', SUBMIT_PRODUCTION: '提交制作', SUBMIT_ACCEPTANCE: '提交验收',
  APPROVE_CONTENT: '通过验收', REJECT_CONTENT: '退回修改', START_CONTENT_REVISION: '开始修改',
  RESUBMIT_PRODUCTION: '重新提交', SUBMIT_POSITIONING_REVIEW: '提交审核',
  APPROVE_POSITIONING_FEASIBILITY: '审核通过', REJECT_POSITIONING_FEASIBILITY: '审核退回',
  GENERATE_POSITIONING_STUDENT_LINK: '生成学员确认链接', START_POSITIONING_REVISION: '修改定位卡'
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
const collectAreaCodes = (nodes: AreaNode[]): number[] => nodes.flatMap(node => [node.id, ...collectAreaCodes(node.children || [])])
const positioningFormValues = (values: Record<string, unknown>, fields: DirectorTemplateSnapshot['fields'], areaRows: AreaNode[]) => Object.fromEntries(
  Object.entries(values).map(([key, value]) => {
    const field = fields.find(item => item.key === key)
    if ((field?.type === 'date' || field?.type === 'datetime') && typeof value === 'string') return [key, dayjs(value)]
    if (field?.type === 'region' && value && typeof value === 'object' && !Array.isArray(value)) {
      const code = Number((value as { code?: unknown }).code)
      return [key, Number.isFinite(code) ? findAreaPath(areaRows, code) : undefined]
    }
    return [key, value]
  }),
)
export default function MediaStudentsPage({ permissions = [] }: { permissions?: string[] }) {
  const location = useLocation()
  const initialLocationKey = useRef(location.key)
  const { message, modal } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<MyStudent[]>([]), [detail, setDetail] = useState<MediaStudentDetail>()
  const [selectedServiceId, setSelectedServiceId] = useState<number>(), [selectedAccountId, setSelectedAccountId] = useState<number>()
  const [maintenanceEditorAccountId, setMaintenanceEditorAccountId] = useState<number>()
  const [selectedId, setSelectedId] = useState<number>()
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState(''), [pageNo, setPageNo] = useState(1), [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false), [detailLoading, setDetailLoading] = useState(false), [error, setError] = useState(''), [detailError, setDetailError] = useState('')
  const [tab, setTab] = useState(normalizeMediaStudentTab(params.get('tab'))), [dialog, setDialog] = useState<'account' | 'content' | 'positioning' | 'reject-content' | 'reject-positioning' | 'precheck' | 'interview' | 'operator'>(), [saving, setSaving] = useState(false)
  const [directorContext, setDirectorContext] = useState<StudentContactContext>(), [operatorCandidates, setOperatorCandidates] = useState<StudyPlanner[]>([])
  const [platforms, setPlatforms] = useState<DictData[]>([]), [contentClasses, setContentClasses] = useState<DictData[]>([])
  const [accountFieldConfig, setAccountFieldConfig] = useState<MediaAccountFieldConfig>(), [fieldDicts, setFieldDicts] = useState<Record<string, DictData[]>>({})
  const [areas, setAreas] = useState<AreaNode[]>([]), [legacyRegionText, setLegacyRegionText] = useState<string>()
  const [positioningTemplate, setPositioningTemplate] = useState<DirectorTemplateSnapshot>()
  const [positioningImportSources, setPositioningImportSources] = useState<PositioningCardImportSource[]>([])
  const [positioningImportOpen, setPositioningImportOpen] = useState(false)
  const [positioningImportLoading, setPositioningImportLoading] = useState(false)
  const [positioningImportError, setPositioningImportError] = useState('')
  const [positioningImportSourceId, setPositioningImportSourceId] = useState<number>()
  const [positioningImportSaving, setPositioningImportSaving] = useState(false)
  const [positioningJsonOpen, setPositioningJsonOpen] = useState(false)
  const [positioningJsonText, setPositioningJsonText] = useState('')
  const [positioningJsonFileName, setPositioningJsonFileName] = useState('')
  const [positioningJsonPreview, setPositioningJsonPreview] = useState<PositioningJsonImportPreview>()
  const [positioningJsonError, setPositioningJsonError] = useState('')
  const [positioningJsonSaving, setPositioningJsonSaving] = useState(false)
  const [positioningDetail, setPositioningDetail] = useState<PositioningCard>()
  const [ticketContext, setTicketContext] = useState<ProductionTicketCreateContext>(), [ticketContextLoading, setTicketContextLoading] = useState(false), [ticketContextError, setTicketContextError] = useState(''), [ticketOpen, setTicketOpen] = useState(false), [ticketSaving, setTicketSaving] = useState(false), [ticketTemplates, setTicketTemplates] = useState<WorkOrderTemplate[]>([]), [ticketDepartments, setTicketDepartments] = useState<WorkOrderDepartment[]>([]), [ticketTargetDepartments, setTicketTargetDepartments] = useState<WorkOrderDepartment[]>([]), [ticketUsers, setTicketUsers] = useState<Array<{ id: number; nickname: string }>>([]), [ticketDictionaries, setTicketDictionaries] = useState<Array<{ dictType: string; value: string; label: string }>>([]), [ticketFiles, setTicketFiles] = useState<WorkOrderFile[]>([]), [ticketAccountId, setTicketAccountId] = useState<number>()
  const [shareLink, setShareLink] = useState<string>()
  const [autoSave, setAutoSave] = useState<DirectorAutoSaveState>({ status: 'idle' })
  const [rejectingContent, setRejectingContent] = useState<MediaStudentDetail['contents'][number]>()
  const [rejectingPositioning, setRejectingPositioning] = useState<MediaStudentDetail['positioningCards'][number]>()
  const [form] = Form.useForm<Record<string, unknown>>(), [ticketForm] = Form.useForm<Record<string, unknown>>(), listRun = useRef(0), detailRun = useRef(0)
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
  useEffect(() => {
    if (location.key !== initialLocationKey.current) void loadPage(1, Number(params.get('personId')) || undefined)
  }, [location.key, loadPage, params])
  useEffect(() => () => autoSaveCoordinator.current?.dispose(), [])

  const accountName = (id: number) => detail?.accounts.find(item => item.id === id)?.nickname || '未匹配账号'
  const loadTicketContext = async (accountId: number, sceneCode: string) => { setTicketContext(undefined); setTicketContextError(''); setTicketContextLoading(true); try { const [context, targetDepartments] = await Promise.all([api.productionTicket.createContext(accountId, sceneCode), workOrderApi.candidateDepartments(sceneCode)]); setTicketContext(context); setTicketTargetDepartments(targetDepartments.list.map(dept => ({ id: dept.id, name: dept.name }))); const assignmentType = context.allowedAssignmentTypes.includes('PERSON') ? 'PERSON' : 'DEPARTMENT'; ticketForm.setFieldsValue({ sceneCode, assignmentType, assigneeUserId: undefined, targetDeptId: undefined }) } catch (cause) { setTicketContextError(errorText(cause)) } finally { setTicketContextLoading(false) } }
  const openTicket = async (accountId: number) => {
    setTicketOpen(true); setTicketAccountId(accountId); setTicketContext(undefined); setTicketContextError(''); setTicketContextLoading(true); setTicketFiles([]); ticketForm.resetFields()
    try { const [catalog, departments, users, dictionaries] = await Promise.all([workOrderApi.templates(), workOrderApi.departments(), workOrderApi.users(), workOrderApi.dictionaries()]); const templates = (catalog.list || []).filter(item => item.processorType === 'PRODUCTION_TICKET'); setTicketTemplates(templates); setTicketDepartments(departments); setTicketUsers(users); setTicketDictionaries(dictionaries); if (!templates.length) setTicketContextError('当前没有已发布且你可发起的拍剪工单模板'); else await loadTicketContext(accountId, templates[0].code) } catch (cause) { setTicketContextError(errorText(cause)); setTicketContextLoading(false) }
  }
  const createTicket = async () => {
    if (!ticketContext?.canCreate) return
    try { const values = await ticketForm.validateFields(); const dynamicValues = Object.fromEntries((ticketContext.fields || []).map(field => { const value = values[field.key]; return [field.key, dayjs.isDayjs(value) ? (field.type === 'date' ? value.format('YYYY-MM-DD') : value.format('YYYY-MM-DDTHH:mm:ss')) : value] })); setTicketSaving(true); await api.productionTicket.create({ sceneCode: String(values.sceneCode), accountId: ticketContext.accountId, assigneeUserId: values.assignmentType === 'PERSON' ? Number(values.assigneeUserId) : undefined, targetDeptId: values.assignmentType === 'DEPARTMENT' ? Number(values.targetDeptId) : undefined, operatorRemark: String(values.operatorRemark || ''), values: dynamicValues, attachmentIds: ticketFiles.map(file => file.id) }); message.success(values.assignmentType === 'PERSON' ? '工单已派发，等待剪拍专员接单' : '工单已进入指定部门候选池'); setTicketOpen(false); setTicketFiles([]); if (detail) await loadDetail(detail.student.personId, selectedServiceId, ticketContext.accountId) } catch (cause) { if (!(cause as { errorFields?: unknown }).errorFields) message.error(errorText(cause)) } finally { setTicketSaving(false) }
  }
  const selectedService = detail?.student.services.find(item => item.serviceRelationId === selectedServiceId) || detail?.student.services[0]
  const resetAutoSave = () => {
    stageDraftVersion.current = undefined; draftIdentity.current = undefined; positioningDraft.current = undefined
    return autoSaveCoordinator.current!.begin()
  }
  const loadPositioningImportSources = async (accountId: number) => {
    if (!detail || !selectedService || !hasPermission(permissions, 'zsjos:positioning-card:query')) return
    setPositioningImportLoading(true); setPositioningImportError('')
    try {
      const sources = await api.positioningCard.importSources({ studentPersonId: detail.student.personId, accountId, serviceRelationId: selectedService.serviceRelationId })
      setPositioningImportSources(sources)
    } catch (cause) {
      setPositioningImportSources([])
      setPositioningImportError(cause instanceof ApiError && cause.code === 403 ? '无权读取可导入的定位卡' : errorText(cause))
    } finally { setPositioningImportLoading(false) }
  }
  const open = async (type: typeof dialog, accountId?: number, positioningDraftId?: number) => { form.resetFields(); const session = resetAutoSave(); setLegacyRegionText(undefined); if (type === 'positioning') { setPositioningTemplate(undefined); setPositioningImportSources([]); setPositioningImportSourceId(undefined); setPositioningImportError(''); setPositioningJsonOpen(false); setPositioningJsonPreview(undefined); setPositioningJsonError('') } if (accountId) form.setFieldValue('accountId', accountId); if (type !== 'positioning' && type !== 'precheck' && type !== 'interview') setDialog(type)
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
        const existing = positioningDraftId ? { id: positioningDraftId } : detail?.positioningDrafts.find(card => card.accountId === accountId)
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
        const dictTypes = [...new Set(template.fields.filter(field => field.dictType).map(field => field.dictType!))]
        const [entries, areaRows] = await Promise.all([
          Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const)),
          template.fields.some(field => field.type === 'region') ? (areas.length ? Promise.resolve(areas) : api.areaTree()) : Promise.resolve(areas),
        ])
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) })); setAreas(areaRows)
        form.setFieldsValue({ accountId, data: positioningFormValues(template.values || {}, template.fields, areaRows) })
        setDialog(type)
        if (accountId) void loadPositioningImportSources(accountId)
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
  const importPositioningSubmission = async () => {
    if (!detail || !selectedService || !positioningImportSourceId) return
    const accountId = Number(form.getFieldValue('accountId'))
    if (!accountId) { message.error('请先选择账号'); return }
    if (positioningDraft.current || form.isFieldsTouched()) {
      const confirmed = await new Promise<boolean>(resolve => modal.confirm({
        title: '覆盖当前定位卡草稿？',
        content: '导入会用所选提交版本覆盖当前草稿中的定位内容，已提交的历史定位卡不会改变。',
        okText: '确认导入', cancelText: '取消', onOk: () => resolve(true), onCancel: () => resolve(false)
      }))
      if (!confirmed) return
    }
    setPositioningImportSaving(true)
    try {
      await autoSaveCoordinator.current!.flush()
      const currentDraft = positioningDraft.current
      const result = await api.positioningCard.importSubmission({
        sourceSubmissionId: positioningImportSourceId, accountId, studentPersonId: detail.student.personId,
        serviceRelationId: selectedService.serviceRelationId, targetDraftId: currentDraft?.id,
        version: currentDraft?.version
      })
      positioningDraft.current = { id: result.id, version: result.version }
      setPositioningTemplate(result)
      form.setFieldsValue({ data: positioningFormValues(result.values || {}, result.fields, areas) })
      setPositioningImportOpen(false); setPositioningImportSourceId(undefined)
      message.success(result.skippedFieldKeys.length ? `定位卡已导入，${result.skippedFieldKeys.length} 个不兼容字段未复制` : '定位卡已导入')
      const dictTypes = [...new Set(result.fields.filter(field => field.dictType).map(field => field.dictType!))]
      try {
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) }))
      } catch { message.warning('定位卡已导入，但表单选项加载失败，请关闭后重新打开定位卡') }
    } catch (cause) { message.error(errorText(cause)) }
    finally { setPositioningImportSaving(false) }
  }
  const positioningJsonContext = () => ({
    fields: positioningTemplate?.fields || [],
    dictionaryValues: Object.fromEntries(Object.entries(fieldDicts).map(([dictType, items]) => [dictType, items.map(item => item.value)])),
    areaCodes: collectAreaCodes(areas),
  })
  const previewPositioningJson = (text = positioningJsonText) => {
    setPositioningJsonError('')
    try {
      setPositioningJsonPreview(parsePositioningJson(text, positioningJsonContext()))
    } catch (cause) {
      setPositioningJsonPreview(undefined)
      setPositioningJsonError(errorText(cause))
    }
  }
  const readPositioningJsonFile = async (file: File) => {
    setPositioningJsonError(''); setPositioningJsonPreview(undefined)
    if (!file.name.toLowerCase().endsWith('.json')) {
      setPositioningJsonError('请选择 .json 文件')
      return Upload.LIST_IGNORE
    }
    try {
      const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
      setPositioningJsonFileName(file.name); setPositioningJsonText(text)
      previewPositioningJson(text)
    } catch {
      setPositioningJsonError('文件必须是有效的 UTF-8 文本')
    }
    return Upload.LIST_IGNORE
  }
  const confirmPositioningJsonImport = async () => {
    if (!positioningJsonPreview || !positioningTemplate) return
    if (!positioningJsonPreview.importable.length && !positioningJsonPreview.cleared.length) return
    setPositioningJsonSaving(true); setPositioningJsonError('')
    try {
      await autoSaveCoordinator.current!.flush()
      const current = (form.getFieldValue('data') || {}) as Record<string, unknown>
      const formPreview = { ...positioningJsonPreview, values: positioningFormValues(positioningJsonPreview.values, positioningTemplate.fields, areas) }
      form.setFieldValue('data', mergePositioningJsonValues(current, formPreview))
      await autoSaveCoordinator.current!.saveNow(draftSaveTask())
      setPositioningJsonOpen(false)
      message.success(`已导入 ${positioningJsonPreview.importable.length} 个字段，清空 ${positioningJsonPreview.cleared.length} 个，跳过 ${positioningJsonPreview.skipped.length} 个`)
    } catch (cause) {
      setPositioningJsonError(errorText(cause))
    } finally { setPositioningJsonSaving(false) }
  }
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
    if (activeDialog === 'positioning') {
      Object.assign(data, serializePositioningFormValues(data, positioningTemplate?.fields || []))
    }
    const accountId = Number(values.accountId)
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
        const request = { accountId, studentPersonId: personId, serviceRelationId, templateId, values: data }
        const result = positioningDraft.current
          ? await api.positioningCard.updateDraft(positioningDraft.current.id, { accountId, values: data, version: positioningDraft.current.version })
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
    try { await autoSaveCoordinator.current!.flush(); autoSaveCoordinator.current!.invalidate(); setPositioningJsonOpen(false); setDialog(undefined) }
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
    } else if (action === 'START_POSITIONING_REVISION') {
      const draft = await api.positioningCard.startRevision(row.id, row.version)
      if (selectedId) await loadDetail(selectedId, selectedServiceId, row.accountId)
      await open('positioning', row.accountId, draft.id)
      return
    } else return
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
  const selectedEffectivePositioning = selectedPositioningCards.filter(item => item.effective)
  const selectedLatestPositioning = selectedPositioningCards.filter(item => item.latestRound && !item.effective)
  const selectedPositioningHistory = selectedPositioningCards.filter(item => !item.latestRound && !item.effective)
  const selectedAccountDraft = selectedPositioningDrafts[0]
  const selectedAccountEffective = selectedEffectivePositioning[0]
  const selectedAccountLatest = selectedLatestPositioning[0]
  const selectedPositioningView = selectedAccountLatest || selectedAccountEffective
  const selectedCanStartRevision = selectedAccountEffective?.availableActions.includes('START_POSITIONING_REVISION')
  const selectedPositioningBusy = !selectedAccountDraft && Boolean(selectedAccountLatest)
  const selectedPositioningButtonLabel = selectedAccountDraft ? '继续编辑定位卡' : selectedPositioningBusy ? '定位审核中'
    : selectedAccountEffective ? '修改定位卡' : '填写定位卡'
  const selectedPositioningButtonVisible = hasPermission(permissions, 'zsjos:positioning-card:create') || selectedCanStartRevision
  const positioningRowActions = (row: MediaStudentDetail['positioningCards'][number] | MediaStudentDetail['positioningDrafts'][number]) => actions(
    row.availableActions?.filter(action => action !== 'START_POSITIONING_REVISION'),
    action => void positioningAction(row, action)
  )
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
  const selectAccount = (accountId: number, editMaintenance = false) => {
    if (!detail) return
    setSelectedAccountId(accountId)
    setMaintenanceEditorAccountId(editMaintenance ? accountId : undefined)
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
    { key: 'accounts', label: '账号', children: <>
      <section className="media-accounts-section">
        <div className="media-students-tab-heading">
          <div className="media-students-section-heading">
            <Typography.Title level={5}>学员账号</Typography.Title>
            <Typography.Text type="secondary">{detail.accounts.length} 个第三方账号</Typography.Text>
          </div>
          {hasPermission(permissions, 'zsjos:media-account:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void open('account')}>新增账号</Button>}
        </div>
        {detail.accounts.length ? <div className="media-account-grid">{detail.accounts.map(x => {
          const selected = selectedAccountId === x.id
          return <article className={`media-account-card${selected ? ' active' : ''}`} key={x.id}>
            <button type="button" className="media-account-card-select" aria-pressed={selected} onClick={() => selectAccount(x.id)}>
              <span className="media-account-card-eyebrow"><Tag>{x.platformLabel || '平台未记录'}</Tag></span>
              <strong>{x.nickname || x.accountNo}</strong>
              <span className="media-account-number">{x.accountNo}</span>
              <dl className="media-account-meta">
                <div><dt>当下状态</dt><dd>{x.currentStatusLabelSnapshot || '未填写'}</dd></div>
                <div><dt>阶段</dt><dd>{x.stageLabelSnapshot || statusLabel(x.stage)}</dd></div>
                <div><dt>维护排期</dt><dd>{x.maintenanceStartDate && x.maintenanceEndDate ? `${x.maintenanceStartDate} 至 ${x.maintenanceEndDate}` : '未排期'}</dd></div>
              </dl>
              <span className="media-account-detail-line">{x.detailSnapshots?.length ? x.detailSnapshots.map(field => `${field.label}：${field.displayValue || '未记录'}`).join(' · ') : '详情字段未记录'}</span>
            </button>
          </article>
        })}</div> : <Empty description="暂无账号" />}
      </section>
      {selectedAccount ? <>
        <section className="media-account-context-bar">
          <div className="media-students-section-heading"><Typography.Text type="secondary">当前账号</Typography.Text><Typography.Title level={5}>{selectedAccount.nickname || selectedAccount.accountNo}</Typography.Title></div>
          <Space wrap>
            {hasPermission(permissions, 'zsjos:production-ticket:create') && <Tooltip title={selectedAccountEffective ? '发起拍剪工单' : '当前账号尚无已确认定位卡'}><span><Button disabled={!selectedAccountEffective} onClick={() => void openTicket(selectedAccount.id)}>发起拍剪工单</Button></span></Tooltip>}
          </Space>
        </section>
        <div className="media-account-workspace-shell"><div className="media-account-workspace">
        <AccountMaintenancePanel key={`${selectedAccount.id}-${maintenanceEditorAccountId === selectedAccount.id ? 'edit' : 'view'}`} account={selectedAccount} canMaintain={selectedAccount.availableActions.includes('MAINTAIN_ACCOUNT')} initiallyEditing={maintenanceEditorAccountId === selectedAccount.id} onEditingFinished={() => setMaintenanceEditorAccountId(undefined)} onSaved={async () => { if (selectedId) await loadDetail(selectedId, selectedServiceId, selectedAccountId) }} />
        <section className="media-students-card media-account-positioning">
          <div className="media-students-tab-heading"><div className="media-students-section-heading"><Typography.Title level={5}>账号定位卡</Typography.Title><Typography.Text type="secondary">{selectedAccount.nickname || selectedAccount.accountNo}</Typography.Text></div><Space wrap>{selectedPositioningView && <Button size="small" onClick={() => void viewPositioning(selectedPositioningView.id)}>查看定位卡</Button>}{selectedPositioningButtonVisible && <Tooltip title={selectedPositioningBusy ? '当前定位版本正在运营或学员审核中' : undefined}><span><Button size="small" disabled={selectedPositioningBusy || Boolean(selectedAccountEffective && !selectedCanStartRevision && !selectedAccountDraft)} onClick={() => { if (selectedAccountDraft) void open('positioning', selectedAccount.id); else if (selectedCanStartRevision && selectedAccountEffective) void positioningAction(selectedAccountEffective, 'START_POSITIONING_REVISION'); else void open('positioning', selectedAccount.id) }}>{selectedPositioningButtonLabel}</Button></span></Tooltip>}</Space></div>
          {selectedPositioningDrafts.map(x => <div className="media-students-record" key={`draft-${x.id}`}><div><strong>定位卡草稿</strong><span>最近保存：{formatTimestamp(x.lastActivityAt)}</span></div><Space wrap><Tag>草稿</Tag>{positioningRowActions(x)}</Space></div>)}
          {selectedEffectivePositioning.length > 0 && <div className="media-students-positioning-group"><Typography.Text strong>当前生效定位</Typography.Text>{selectedEffectivePositioning.map(x => <div className="media-students-record" key={`effective-${x.submissionId}`}><div><strong>学员已确认</strong><span>确认版本提交于：{x.submittedAt ? formatTimestamp(x.submittedAt) : '历史提交时间未记录'}</span></div><Space wrap><Tag color="success">当前生效</Tag>{positioningRowActions(x)}</Space></div>)}</div>}
          {selectedLatestPositioning.length > 0 && <div className="media-students-positioning-group"><Typography.Text strong>当前审核轮次</Typography.Text>{selectedLatestPositioning.map(x => <div className="media-students-record" key={`latest-${x.submissionId}`}><div><strong>第 {x.submissionNo || '-'} 次提交</strong><span>{x.submittedAt ? formatTimestamp(x.submittedAt) : '历史提交时间未记录'}{x.studentDecisionComment ? ` · 修改意见：${x.studentDecisionComment}` : ''}</span></div><Space wrap><Tag>{statusLabel(x.status)}</Tag>{positioningRowActions(x)}</Space></div>)}</div>}
          {selectedPositioningHistory.length > 0 && <div className="media-students-positioning-group"><Typography.Text strong>历史提交</Typography.Text>{selectedPositioningHistory.map(x => <div className="media-students-record" key={`history-${x.submissionId}`}><div><strong>第 {x.submissionNo || '-'} 次提交</strong><span>{x.submittedAt ? formatTimestamp(x.submittedAt) : '历史提交时间未记录'}{x.studentDecisionComment ? ` · 修改意见：${x.studentDecisionComment}` : ''}</span></div><Tag>{statusLabel(x.status)}</Tag></div>)}</div>}
          {!selectedPositioningDrafts.length && !selectedPositioningCards.length && <Empty description="当前账号暂无定位卡" />}
        </section>
      </div></div></> : detail.accounts.length > 0 && <section className="media-students-card media-account-empty"><Empty description="请选择账号查看维护状态和定位卡" /></section>}
    </> },
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
    <Modal width={dialog === 'interview' || dialog === 'positioning' ? 'min(1180px, calc(100vw - 32px))' : undefined} styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} title={dialog === 'account' ? '新增第三方账号' : dialog === 'content' ? '创建内容' : dialog === 'positioning' ? '填写账号定位卡' : dialog === 'reject-content' ? '退回内容修改' : dialog === 'reject-positioning' ? '退回定位卡修改' : dialog === 'precheck' ? '资料预审' : dialog === 'interview' ? '学员采访' : '指派运营'} open={Boolean(dialog)} onCancel={() => void closeDialog()} onOk={() => void submit()} okText={dialog === 'positioning' ? '保存并关闭' : undefined} confirmLoading={saving}>
      <Form form={form} layout="vertical" onValuesChange={scheduleAutoSave}>
        {autoSaveNotice}
        {dialog === 'account' && <><Form.Item name="platformValue" label="平台" rules={[{ required: true }]}><Select options={platforms.map(x => ({ value: x.value, label: x.label }))} /></Form.Item>{accountFieldConfig?.fields.filter(field => field.enabled).map(accountField)}</>}
        {dialog === 'content' && <><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><Form.Item name="title" label="内容标题" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="topic" label="选题说明"><Input.TextArea rows={3} /></Form.Item><Form.Item name="contentClassValue" label="内容分类" rules={[{ required: true }]}><Select options={contentClasses.map(x => ({ value: x.value, label: x.label }))} /></Form.Item></>}
        {dialog === 'positioning' && <><div className="media-students-positioning-toolbar"><Space wrap><Button icon={<UploadOutlined />} onClick={() => { setPositioningJsonText(''); setPositioningJsonFileName(''); setPositioningJsonPreview(undefined); setPositioningJsonError(''); setPositioningJsonOpen(true) }}>导入 JSON</Button>{hasPermission(permissions, 'zsjos:positioning-card:query') && <Button icon={<ImportOutlined />} onClick={() => { setPositioningImportOpen(true); if (!positioningImportSources.length && !positioningImportLoading) void loadPositioningImportSources(Number(form.getFieldValue('accountId'))) }}>导入现有定位卡</Button>}</Space></div><Form.Item name="accountId" label="第三方账号" rules={[{ required: true }]}><Select disabled options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item>{positioningTemplate?.fields.map(directorField)}</>}
        {dialog === 'precheck' && <><Alert type="info" showIcon message="请核对学员、课程、订单及服务归属信息。资料预审不填写业务表单。"/><Form.Item name="confirmed" valuePropName="checked" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请确认资料无误'))}]}><Checkbox>已确认资料无误</Checkbox></Form.Item><Form.Item name="interviewAt" label="访谈预约时间" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请选择访谈预约时间'))}]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item><Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>确认完成资料预审</Checkbox></Form.Item></>}
        {dialog === 'interview' && <><Alert type="info" showIcon message="带“必填”的项目提交时必须填写；单选请选择一项，多选可选择多项。"/>{interviewGroups.map(group => <section className="director-interview-group" key={group}><Typography.Title level={5}>{group === '自媒体能力' ? '自媒体运营基础能力' : group}</Typography.Title>{interviewFields.filter(field => (field.group || '基本信息') === group).map(directorField)}</section>)}<Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>提交并完成学员采访</Checkbox></Form.Item></>}
        {dialog === 'operator' && <><Form.Item name="userId" label="运营负责人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={operatorCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>{directorContext?.operatorAssignmentConflict && <Form.Item name="correctionReason" label="统一归属说明" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={3} /></Form.Item>}</>}
        {(dialog === 'reject-content' || dialog === 'reject-positioning') && <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={4} /></Form.Item>}
      </Form>
    </Modal>
    <Modal title="导入现有定位卡" open={positioningImportOpen} onCancel={() => setPositioningImportOpen(false)} onOk={() => void importPositioningSubmission()} okText="导入" okButtonProps={{ disabled: !positioningImportSourceId }} confirmLoading={positioningImportSaving}>
      {positioningImportLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : positioningImportError ? <Alert type="error" showIcon message={positioningImportError} action={<Button size="small" onClick={() => void loadPositioningImportSources(Number(form.getFieldValue('accountId')))}>重试</Button>} /> : positioningImportSources.length ? <Radio.Group value={positioningImportSourceId} onChange={event => setPositioningImportSourceId(event.target.value)} className="media-students-positioning-import-list">{positioningImportSources.map(source => <Radio value={source.submissionId} key={source.submissionId}><span className="media-students-positioning-import-option"><strong>{source.accountLabel}</strong><span><Tag color={source.sameAccount ? 'blue' : undefined}>{source.sameAccount ? '当前账号' : '其他账号'}</Tag>第 {source.submissionNo} 次提交 · {formatTimestamp(source.submittedAt)} · {statusLabel(source.status)}</span></span></Radio>)}</Radio.Group> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可导入的已提交定位卡" />}
    </Modal>
    <Modal width="min(760px, calc(100vw - 32px))" title="导入定位卡 JSON" open={positioningJsonOpen} onCancel={() => { if (!positioningJsonSaving) setPositioningJsonOpen(false) }} onOk={() => void confirmPositioningJsonImport()} okText="确认导入并保存" okButtonProps={{ disabled: !positioningJsonPreview || !positioningJsonPreview.importable.length && !positioningJsonPreview.cleared.length }} confirmLoading={positioningJsonSaving} maskClosable={!positioningJsonSaving}>
      <div className="media-students-json-import">
        <Alert type="info" showIcon message="仅按当前模板字段 key 匹配" description="字典字段请填写服务端稳定 value；null 表示清空该字段，未提供或校验失败的字段会保留原值。" />
        <Upload.Dragger accept=".json,application/json" maxCount={1} showUploadList={false} beforeUpload={readPositioningJsonFile} disabled={positioningJsonSaving}>
          <p className="ant-upload-drag-icon"><UploadOutlined /></p>
          <p>点击或拖入 UTF-8 .json 文件</p>
          {positioningJsonFileName && <Tag>{positioningJsonFileName}</Tag>}
        </Upload.Dragger>
        <Input.TextArea rows={8} value={positioningJsonText} disabled={positioningJsonSaving} placeholder={'也可以直接粘贴 JSON，例如：\n{\n  "strongStoryHook": "十年一线实战经验",\n  "recommendedMatchRate": 85\n}'} onChange={event => { setPositioningJsonText(event.target.value); setPositioningJsonFileName(''); setPositioningJsonPreview(undefined); setPositioningJsonError('') }} />
        <Button onClick={() => previewPositioningJson()} disabled={!positioningJsonText.trim() || positioningJsonSaving}>解析并预览</Button>
        {positioningJsonError && <Alert type="error" showIcon message={positioningJsonError} />}
        {positioningJsonPreview && <div className="media-students-json-preview">
          {positioningJsonPreview.importable.length > 0 && <section><Typography.Text strong>可导入 <Tag color="success">{positioningJsonPreview.importable.length}</Tag></Typography.Text>{positioningJsonPreview.importable.map(item => <div key={item.key}><span>{item.title} <code>{item.key}</code></span><Typography.Text type="secondary">{JSON.stringify(item.value)}</Typography.Text></div>)}</section>}
          {positioningJsonPreview.cleared.length > 0 && <section><Typography.Text strong>将清空 <Tag color="warning">{positioningJsonPreview.cleared.length}</Tag></Typography.Text>{positioningJsonPreview.cleared.map(item => <div key={item.key}><span>{item.title} <code>{item.key}</code></span><Typography.Text type="secondary">null</Typography.Text></div>)}</section>}
          {positioningJsonPreview.skipped.length > 0 && <section><Typography.Text strong>已跳过 <Tag>{positioningJsonPreview.skipped.length}</Tag></Typography.Text>{positioningJsonPreview.skipped.map(item => <div key={item.key}><span>{item.title} <code>{item.key}</code></span><Typography.Text type="danger">{item.reason}</Typography.Text></div>)}</section>}
          {!positioningJsonPreview.importable.length && !positioningJsonPreview.cleared.length && !positioningJsonPreview.skipped.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="JSON 中没有可处理的字段" />}
        </div>}
      </div>
    </Modal>
    <Modal width="min(1040px, calc(100vw - 32px))" title="发起拍剪工单" open={ticketOpen} onCancel={() => setTicketOpen(false)} onOk={() => void createTicket()} okText="确认发起" okButtonProps={{ disabled: !ticketContext?.canCreate || Boolean(ticketContextError) }} confirmLoading={ticketSaving}>
      {ticketContextLoading ? <Skeleton active paragraph={{ rows: 8 }} /> : ticketContextError ? <Alert type="error" showIcon message={ticketContextError} action={<Button size="small" onClick={() => ticketAccountId && void openTicket(ticketAccountId)}>重试</Button>} /> : ticketContext ? <Form form={ticketForm} layout="vertical"><Form.Item name="sceneCode" label="工单模板" rules={[{ required: true }]}><Select options={ticketTemplates.map(item => ({ value: item.code, label: item.name }))} onChange={code => ticketAccountId && void loadTicketContext(ticketAccountId, code)} /></Form.Item><DetailFieldGrid columns={2} items={[{ key: 'student', label: '学员姓名', value: ticketContext.studentName || '未记录' }, { key: 'account', label: '第三方账号', value: `${ticketContext.platformLabel || '平台未记录'} · ${ticketContext.accountName || ticketContext.accountNo || '账号未记录'}` }, ...(ticketContext.accountFields || []).map(field => ({ key: field.key, label: field.label, value: field.displayValue || String(field.value ?? '未记录') }))]} />{ticketContext.canCreate ? <><ProductionTicketPositioningCard snapshot={ticketContext.positioning} /><Form.Item name="assignmentType" label="指派方式" rules={[{ required: true }]}><Radio.Group optionType="button" options={ticketContext.allowedAssignmentTypes.map(value => ({ value, label: value === 'PERSON' ? '指定人' : '指定部门' }))} /></Form.Item><Form.Item noStyle shouldUpdate={(prev, next) => prev.assignmentType !== next.assignmentType}>{({ getFieldValue }) => getFieldValue('assignmentType') === 'DEPARTMENT' ? <Form.Item name="targetDeptId" label="接收部门" rules={[{ required: true, message: '请选择接收部门' }]}><Select showSearch optionFilterProp="label" options={ticketTargetDepartments.map(dept => ({ value: dept.id, label: dept.name }))} /></Form.Item> : <Form.Item name="assigneeUserId" label="剪拍专员" rules={[{ required: true, message: '请选择剪拍专员' }]}><Select showSearch optionFilterProp="label" options={ticketContext.assigneeCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>}</Form.Item>{(ticketContext.fields || []).map(field => <Form.Item key={field.key} name={field.key} label={field.label} rules={field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined}>{field.type === 'textarea' ? <Input.TextArea rows={3} /> : field.type === 'number' ? <InputNumber style={{ width: '100%' }} /> : field.type === 'date' || field.type === 'datetime' ? <DatePicker showTime={field.type === 'datetime'} style={{ width: '100%' }} /> : field.type === 'user' ? <Select showSearch optionFilterProp="label" options={ticketUsers.map(item => ({ value: item.id, label: item.nickname }))} /> : field.type === 'department' ? <Select showSearch optionFilterProp="label" options={ticketDepartments.map(item => ({ value: item.id, label: item.name }))} /> : field.type === 'dictionary' ? <Select options={ticketDictionaries.filter(item => item.dictType === field.dictionaryType).map(item => ({ value: item.value, label: item.label }))} /> : <Input />}</Form.Item>)}<Form.Item name="operatorRemark" label="运营备注" rules={[{ required: true, message: '请填写运营备注' }, { max: 500, message: '运营备注不能超过 500 字' }]}><Input.TextArea rows={4} maxLength={500} showCount placeholder="补充拍摄重点、剪辑要求或其他交接事项" /></Form.Item><Form.Item label="附件"><WorkOrderAttachmentPicker value={ticketFiles} onChange={setTicketFiles} /></Form.Item></> : <Alert type="warning" showIcon message={ticketContext.unavailableReason || '当前账号不可发起拍剪工单'} />}</Form> : null}
    </Modal>
    <Modal title="定位卡内容" open={Boolean(positioningDetail)} footer={null} onCancel={() => setPositioningDetail(undefined)}><DetailFieldGrid columns={1} items={(positioningDetail?.fieldsSnapshot || []).filter(field => field.enabled).map(field => ({ key: field.key, label: field.title, value: positioningDisplayValue(positioningDetail, field.key) }))} /></Modal>
    <Modal title="学员确认链接" open={Boolean(shareLink)} onCancel={() => setShareLink(undefined)} footer={<Button type="primary" icon={<CopyOutlined />} onClick={() => shareLink && void navigator.clipboard.writeText(shareLink)}>复制链接</Button>}><Alert type="success" showIcon icon={<LinkOutlined />} message="链接已生成" description={shareLink} /></Modal></section>
}
