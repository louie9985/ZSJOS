import { CopyOutlined, EditOutlined, EyeOutlined, FileSearchOutlined, ImportOutlined, LinkOutlined, PlusOutlined, PlayCircleOutlined, ReloadOutlined,
  SendOutlined, UploadOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Cascader, Checkbox, DatePicker, Empty, Form, Input, InputNumber, Modal, Pagination, Radio, Select, Skeleton, Space, Switch, Tag, Tooltip, Typography, Upload } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import DetailFieldGrid from '../components/DetailFieldGrid'
import ContentApprovalDraft from '../components/ContentApprovalDraft'
import { NameAvatar } from '../components/LeadDetailOverview'
import StudentDetail from '../components/StudentDetail'
import OverflowToolbar, { type ToolbarAction } from '../components/OverflowToolbar'
import AccountMaintenancePanel from '../components/AccountMaintenancePanel'
import ProductionTicketPositioningCard from '../components/ProductionTicketPositioningCard'
import WorkOrderAttachmentPicker from '../components/WorkOrderAttachmentPicker'
import { ApiError, api, type AreaNode, type DictData, type DirectorTemplateSnapshot, type MediaStudentDetail, type MyStudent, type PositioningCard, type PositioningCardImportSource, type ProductionTicketCreateContext, type StudentContactContext, type StudyPlanner } from '../services/api'
import { DICT_TYPE } from '../constants'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'
import { DirectorAutoSaveCoordinator, type DirectorAutoSaveState } from '../services/directorAutoSave'
import { mergePositioningJsonValues, parsePositioningJson, serializePositioningFormValues, type PositioningJsonImportPreview } from '../services/positioningJsonImport'
import { workOrderApi, type WorkOrderDepartment, type WorkOrderFile, type WorkOrderTemplate } from '../services/workOrderApi'
import AccountPublishedWorks from '../components/AccountPublishedWorks'
import { StudentDeliveryPanel } from '../components/StudentDeliveryPanel'
import { contentReviewApi, partnerStudentInvitationApi, type PartnerStudentInvitation } from '../services/materialApi'
import PositioningCardMaterialPicker from '../components/PositioningCardMaterialPicker'
import PositioningCardAttachments from '../components/PositioningCardAttachments'
import PositioningCardFields from '../components/PositioningCardFields'
import PositioningInterviewDialog from '../components/PositioningInterviewDialog'
import { StudentPlannerOperations } from './RegistrationPages'
import { positioningInterviewApi, type InterviewContext } from '../services/positioningInterviewApi'

const PAGE_SIZE = 20
const AUTO_SAVE_DELAY_MS = 1500
export const normalizeMediaStudentTab = (value: string | null) => value && /^account-\d+$/.test(value) ? value : 'overview'
export const accountIdFromTab = (value: string | null) => {
  const normalized = normalizeMediaStudentTab(value)
  return normalized === 'overview' ? undefined : Number(normalized.slice('account-'.length))
}
export const resolveMediaStudentAccountId = (
  accounts: Array<{ id: number }>,
  contents: Array<{ id: number; accountId: number }>,
  positioningRows: Array<{ id: number; accountId: number }>,
  links: { preferredAccountId?: number; accountId?: number; tab?: string | null; contentId?: number; positioningCardId?: number },
) => {
  const linkedContentAccountId = contents.find(item => item.id === links.contentId)?.accountId
  const linkedPositioningAccountId = positioningRows.find(item => item.id === links.positioningCardId)?.accountId
  const requested = links.preferredAccountId || links.accountId || accountIdFromTab(links.tab || null)
    || linkedContentAccountId || linkedPositioningAccountId
  return accounts.some(item => item.id === requested) ? requested : undefined
}
const labels: Record<string, string> = { active: '服务中', completed: '已完成', cancelled: '已取消', precheck: '资料预审', positioning_interview: '定位访谈', positioning_interview_completed: '定位访谈已完成', interview: '历史采访阶段', positioning_ready: '历史定位准备', co_creating: '草稿', ip_review: '历史专业审核中', operator_feasibility: '待运营审核', student_link_pending: '待生成学员链接', student_confirm: '待学员确认', student_agreed: '历史学员已同意', change_requested: '学员提出修改', operator_rejected: '运营已退回', ip_rejected: '历史专业审核已退回', trial_14d: '历史试运行', confirmed: '已确认', superseded: '已被新版本替换', archived: '历史已归档', topic: '选题', script: '脚本', in_production: '制作中', acceptance: '待验收', published: '已发布', rejected: '已退回', revising: '修改中' }
const statusLabel = (value?: string) => value ? labels[value] || value : '未记录'
const actionLabels: Record<string, string> = {
  DIRECTOR_PRECHECK: '资料预审', START_POSITIONING_INTERVIEW: '开始定位访谈', CONTINUE_POSITIONING_INTERVIEW: '继续定位访谈', VIEW_POSITIONING_INTERVIEW: '查看定位访谈', CREATE_POSITIONING_CARD: '填写定位卡', ASSIGN_OPERATOR: '指派运营', DIRECTOR_OPERATOR_ASSIGN: '指派运营',
  COMPLETE_TOPIC: '完成选题', SUBMIT_PRODUCTION: '提交制作', SUBMIT_ACCEPTANCE: '提交验收',
  APPROVE_CONTENT: '通过验收', REJECT_CONTENT: '退回修改', START_CONTENT_REVISION: '开始修改',
  RESUBMIT_PRODUCTION: '重新提交', SUBMIT_POSITIONING_REVIEW: '提交审核',
  APPROVE_POSITIONING_FEASIBILITY: '审核通过', REJECT_POSITIONING_FEASIBILITY: '审核退回',
  GENERATE_POSITIONING_STUDENT_LINK: '生成学员确认链接', START_POSITIONING_REVISION: '修改定位卡'
}
const interviewActionSet = new Set(['DIRECTOR_PRECHECK', 'START_POSITIONING_INTERVIEW', 'CONTINUE_POSITIONING_INTERVIEW', 'VIEW_POSITIONING_INTERVIEW', 'CREATE_POSITIONING_CARD', 'ASSIGN_OPERATOR', 'DIRECTOR_OPERATOR_ASSIGN'])
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
      ? String((item as { labelSnapshot?: unknown; titleSnapshot?: unknown }).labelSnapshot || (item as { titleSnapshot?: unknown }).titleSnapshot || '') : '').filter(Boolean)
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
// 草稿沿用业务值，但表单结构始终跟随最新发布模板；旧字段仅保留在服务端快照中。
export const mergePositioningDraftTemplate = (latest: DirectorTemplateSnapshot, draft: PositioningCard) => ({
  templateId: latest.templateId,
  templateVersionId: latest.templateVersionId,
  templateVersionNo: latest.templateVersionNo,
  fields: latest.fields,
  values: Object.fromEntries(latest.fields.map(field => [field.key, draft.valuesSnapshot?.[field.key]]).filter(([, value]) => value !== undefined)),
  dictSnapshots: Object.fromEntries(latest.fields.map(field => [field.key, draft.dictSnapshot?.[field.key]]).filter(([, value]) => value !== undefined)),
})
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
export const positioningJsonPrompt = `你是一名专业的新媒体账号定位编导助手。

任务：阅读我提供的学员访谈稿、沟通记录或编导文稿，提取明确、可验证的账号定位信息，并输出符合“中世健教育编导定位卡”导入要求的 JSON。

严格规则：只输出合法 JSON，不要 Markdown、解释或分析；顶层必须是对象；键必须使用下方字段 key；只填写文稿明确出现或可直接归纳的信息，无法判断就省略；不要编造；不要输出空字符串或空数组；普通字段用字符串，多选字段用字符串数组；字典字段只能用规定 value，不能用中文名称；null 表示清空。

不要输出以下系统字段：pc_history、pc_interview_files、pc_homepage_douyin_refs、pc_homepage_xiaohongshu_refs、pc_homepage_channels_refs、pc_homepage_other_refs、pc_delivery_s1_refs、pc_delivery_s2_refs、pc_delivery_s3_refs、pc_delivery_s4_refs、pc_delivery_s5_refs、pc_delivery_s6_refs。

普通字段：pc_account_name（账号名称建议）、pc_target_user（目标用户）、pc_lead_capture（客资提取方式）、pc_product_goal（承接产品目标）、pc_join_goal（学员加入目标）、pc_learning_stage（当前学习/资格阶段）、pc_primary_track（主赛道）、pc_secondary_track（辅赛道）、pc_cooperation（学员配合等级）、pc_shoot_time（连续可拍摄时间）、pc_appearance（出镜意愿）、pc_expression（表达能力等级）、pc_assets（专业优势和案例资产）、pc_trust（信任证据）、pc_risk（执行主要风险）、pc_days7（7天账号数据）、pc_days14（14天验证指标）、pc_days28（28天调整触发条件）、pc_student_duties（学员承担事项）、pc_company_duties（公司承担事项）、pc_homepage_douyin（抖音平台主页搭建）、pc_homepage_xiaohongshu（小红书平台主页搭建）、pc_homepage_channels（视频号平台主页搭建）、pc_homepage_other（其他平台主页搭建）、pc_delivery_s0 至 pc_delivery_s6（各阶段交付约定）、pc_internal_goal（内部交付目标建议）。

多选字典：pc_account_position 可用 beginner_growth、exam_companion、mother_sidejob、skill_growth、career_upgrade、light_lead_gen、light_practice、local_service、professional_ip、business_operation、information_guide；pc_profession 可用 t1_occupational_nutrition、t2_health_management、t3_psychology_social_work、t4_pharmacy、t5_traditional_chinese_pharmacy、t6_tcm_appropriate_technology、t7_tcm_apprenticeship_specialty、t8_healthcare_academic_upgrade、t9_healthcare_career_monetization；pc_content_form 可用 health_koc_image_text、study_koc_image_text、study_koc_video、exam_info_image_text、senior_koc_image_text、health_koc_video、side_hustle_koc_video、entrepreneur_koc_video、career_enablement_koc_video、senior_koc_video、teacher_kol_video、career_planner_staff_image_text、study_planner_staff_image_text、career_planner_staff_video、study_planner_staff_video、course_consultant_staff_video、course_consultant_staff_image_text、course_assistant_staff_video、course_assistant_staff_image_text、light_entrepreneur_operation_video、light_entrepreneur_operation_image_text、exam_info_video。

主页字段只输出文字建议；不要输出素材引用。数字指标必须来自文稿，未提供时不要虚构。

请处理以下文稿：
【在此粘贴编导文稿】`
export const mediaAccountTabKey = (accountId: number) => `account-${accountId}`
export const buildMediaAccountTabLabels = (accounts: MediaStudentDetail['accounts']) => {
  return new Map(accounts.map(account => [
    account.id,
    [account.nickname?.trim() || '未命名账号', account.platformLabel?.trim() || '平台待填写'].join(' · '),
  ]))
}
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
  // The student inbox deliberately remains master-detail for every global inbox layout setting.
  const location = useLocation()
  const initialLocationKey = useRef(location.key)
  const { message, modal } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<MyStudent[]>([]), [detail, setDetail] = useState<MediaStudentDetail>()
  const [selectedServiceId, setSelectedServiceId] = useState<number>(), [selectedAccountId, setSelectedAccountId] = useState<number>()
  const [accountMissing, setAccountMissing] = useState<Record<number, number>>({})
  const updateMissing = useCallback((id: number, count: number) => setAccountMissing(current => current[id] === count ? current : { ...current, [id]: count }), [])
  const [maintenanceEditorAccountId, setMaintenanceEditorAccountId] = useState<number>()
  const [selectedId, setSelectedId] = useState<number>()
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState(''), [pageNo, setPageNo] = useState(1), [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false), [detailLoading, setDetailLoading] = useState(false), [error, setError] = useState(''), [detailError, setDetailError] = useState('')
  const [interviewSummary, setInterviewSummary] = useState<InterviewContext>(), [summaryError, setSummaryError] = useState('')
  const [interviewId, setInterviewId] = useState<number>()
  const [tab, setTab] = useState(normalizeMediaStudentTab(params.get('tab'))), [dialog, setDialog] = useState<'account' | 'content' | 'positioning' | 'reject-content' | 'reject-positioning' | 'precheck' | 'operator' | 'student-partner'>(), [saving, setSaving] = useState(false), [contentSubmitAfterSave, setContentSubmitAfterSave] = useState(false)
  const [directorContext, setDirectorContext] = useState<StudentContactContext>(), [operatorCandidates, setOperatorCandidates] = useState<StudyPlanner[]>([])
  const [contentClasses, setContentClasses] = useState<DictData[]>([])
  const [contentPurposes, setContentPurposes] = useState<DictData[]>([])
  const [contentFormats, setContentFormats] = useState<DictData[]>([])
  const [fieldDicts, setFieldDicts] = useState<Record<string, DictData[]>>({})
  const [areas, setAreas] = useState<AreaNode[]>([]), [legacyRegionText, setLegacyRegionText] = useState<string>()
  const [positioningTemplate, setPositioningTemplate] = useState<DirectorTemplateSnapshot>()
  const [positioningInterviews, setPositioningInterviews] = useState<unknown[]>([])
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
  const [studentInvitation, setStudentInvitation] = useState<PartnerStudentInvitation>()
  const [autoSave, setAutoSave] = useState<DirectorAutoSaveState>({ status: 'idle' })
  const [rejectingContent, setRejectingContent] = useState<MediaStudentDetail['contents'][number]>()
  const [rejectingPositioning, setRejectingPositioning] = useState<MediaStudentDetail['positioningCards'][number]>()
  const [form] = Form.useForm<Record<string, unknown>>(), [ticketForm] = Form.useForm<Record<string, unknown>>(), listRun = useRef(0), detailRun = useRef(0)
  const stageDraftVersion = useRef<number | undefined>(undefined)
  const draftIdentity = useRef<{ serviceRelationId: number; stage: 'precheck'; session: number; templateVersionId?: number } | undefined>(undefined)
  const precheckPending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const operatorPending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const accountCreatePending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const positioningDraft = useRef<{ id: number; version: number } | undefined>(undefined)
  const autoSaveCoordinator = useRef<DirectorAutoSaveCoordinator | undefined>(undefined)
  if (!autoSaveCoordinator.current) {
    autoSaveCoordinator.current = new DirectorAutoSaveCoordinator(AUTO_SAVE_DELAY_MS, setAutoSave, () => crypto.randomUUID(), cause => cause instanceof ApiError && [1900010024, 1900014003].includes(cause.code))
  }

  const loadDetail = useCallback(async (personId: number, preferredServiceId?: number, preferredAccountId?: number, background = false) => {
    const run = ++detailRun.current; setSelectedId(personId); if (!background) setDetailLoading(true); setDetailError(''); setInterviewSummary(undefined); setSummaryError('')
    try {
      const value = await api.mediaStudents.get(personId)
      const service = value.student.services.find(item => item.serviceRelationId === preferredServiceId) || value.student.services[0]
      const context = service ? await api.studentContactContext(service.serviceRelationId) : undefined
      const accountId = resolveMediaStudentAccountId(value.accounts, value.contents,
        [...value.positioningCards, ...value.positioningDrafts], {
          preferredAccountId,
          accountId: Number(params.get('accountId')) || undefined,
          tab: params.get('tab'),
          contentId: Number(params.get('contentId')) || undefined,
          positioningCardId: Number(params.get('positioningCardId')) || undefined,
        })
      if (run === detailRun.current) {
        setDetail(value); setSelectedServiceId(service?.serviceRelationId); setSelectedAccountId(accountId); setDirectorContext(context)
        setTab(accountId ? mediaAccountTabKey(accountId) : 'overview')
      }
      if (service && context?.availableActions.some(action => action.includes('POSITIONING_INTERVIEW'))) {
        try { const summary = await positioningInterviewApi.context(service.serviceRelationId); if (run === detailRun.current) setInterviewSummary(summary) }
        catch (cause) { if (run === detailRun.current) setSummaryError(errorText(cause)) }
      }
    }
    catch (cause) { if (run === detailRun.current) { setDetail(undefined); setDetailError(cause instanceof ApiError && cause.code === 403 ? '无权查看该学员' : errorText(cause)) } }
    finally { if (run === detailRun.current) setDetailLoading(false) }
  }, [params])
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
  const open = async (type: typeof dialog, accountId?: number, positioningDraftId?: number) => { form.resetFields(); const session = resetAutoSave(); setLegacyRegionText(undefined); if (type === 'account') accountCreatePending.current = undefined; if (type === 'positioning') { setPositioningTemplate(undefined); setPositioningImportSources([]); setPositioningImportSourceId(undefined); setPositioningImportError(''); setPositioningJsonOpen(false); setPositioningJsonPreview(undefined); setPositioningJsonError('') } if (accountId) form.setFieldValue('accountId', accountId); if (type !== 'positioning' && type !== 'precheck') setDialog(type)
    if (type === 'precheck') {
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
          Promise.resolve(areas)
        ])
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) })); setAreas(areaRows)
        const data = { ...(stageForm?.values || {}) }
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
        const latestTemplate = await api.positioningCard.publishedTemplate()
        const template = existingCard ? mergePositioningDraftTemplate(latestTemplate, existingCard) : latestTemplate
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        if (existingCard) positioningDraft.current = { id: existingCard.id, version: existingCard.version }
        setPositioningTemplate(template)
        setPositioningInterviews(accountId ? await api.positioningCard.interviews(accountId) : [])
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
    if (type === 'operator' && selectedServiceId) {
      try { const candidates = await api.studentCollaboratorCandidates(selectedServiceId, 'operator'); if (autoSaveCoordinator.current!.isCurrent(session)) setOperatorCandidates(candidates) }
      catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } return }
    }
    if (type === 'content' && (!contentClasses.length || !contentPurposes.length || !contentFormats.length)) {
      try { const [classes, purposes, formats] = await Promise.all([api.dictDataByType('zsjos_content_class'), api.dictDataByType('zsjos_content_purpose'), api.dictDataByType('zsjos_content_format')]); if (autoSaveCoordinator.current!.isCurrent(session)) { setContentClasses(classes); setContentPurposes(purposes); setContentFormats(formats) } }
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
  const copyPositioningJsonPrompt = async () => {
    try { await navigator.clipboard.writeText(positioningJsonPrompt); message.success('提示词已复制') }
    catch { message.error('复制失败，请手动选择提示词复制') }
  }
  const autoSaveDialog = dialog === 'precheck' || dialog === 'positioning'
  const draftSaveTask = () => {
    if (!autoSaveDialog || !detail || !selectedService) throw new Error('当前草稿上下文不可用，请重新打开后再试')
    const activeDialog = dialog
    const serviceRelationId = selectedService.serviceRelationId
    const personId = detail.student.personId
    const templateId = positioningTemplate?.templateId
    const values = form.getFieldsValue(true)
    const identity = activeDialog === 'precheck' ? draftIdentity.current : undefined
    const interviewAt = values.interviewAt && typeof (values.interviewAt as { format?: (value: string) => string }).format === 'function'
      ? (values.interviewAt as { format: (value: string) => string }).format('YYYY-MM-DDTHH:mm:ss') : undefined
    const data = activeDialog === 'precheck' ? {} : { ...((values.data || {}) as Record<string, unknown>) }
    if (activeDialog === 'positioning') {
      Object.assign(data, serializePositioningFormValues(data, positioningTemplate?.fields || []))
    }
    const accountId = Number(values.accountId)
    return async (idempotencyKey: string, session: number) => {
        if (activeDialog === 'precheck') {
          if (!identity || identity.session !== session || identity.stage !== activeDialog || identity.serviceRelationId !== serviceRelationId || !autoSaveCoordinator.current!.isCurrent(session)) throw new Error('当前表单会话已失效，请重新打开后再试')
          const version = stageDraftVersion.current
          if (version == null) throw new Error('当前表单版本不可用，请重新打开后再试')
          const request = { interviewAt, data, version, idempotencyKey }
          const authoritativeVersion = await api.studentDirectorPrecheckDraft(serviceRelationId, request)
          if (!autoSaveCoordinator.current!.isCurrent(session)) return
          stageDraftVersion.current = authoritativeVersion
          return
        }
        if (!templateId) throw new Error('定位卡模板不可用，请刷新后再试')
        const request = { accountId: accountId || undefined, studentPersonId: personId, serviceRelationId, templateId, values: data }
        const result = positioningDraft.current
          ? await api.positioningCard.updateDraft(positioningDraft.current.id, { accountId: accountId || undefined, values: data, version: positioningDraft.current.version })
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
  const createEmptyAccount = async () => {
    if (!detail || saving) return
    setSaving(true)
    try {
        if (!selectedService || !directorContext) throw new Error('当前学员服务上下文不可用，请刷新后再试')
        const requestValues = { studentPersonId: detail.student.personId, serviceRelationId: selectedService.serviceRelationId, version: directorContext.version, detailValues: {} }
        const fingerprint = JSON.stringify(requestValues)
        if (!accountCreatePending.current || accountCreatePending.current.fingerprint !== fingerprint) accountCreatePending.current = { fingerprint, key: crypto.randomUUID() }
        const accountId = await api.mediaAccount.create({ ...requestValues, idempotencyKey: accountCreatePending.current.key })
        accountCreatePending.current = undefined
        setDialog(undefined); message.success('账号已创建')
        await loadDetail(detail.student.personId, selectedServiceId, accountId)
        setMaintenanceEditorAccountId(accountId)
        setParams({ personId: String(detail.student.personId), accountId: String(accountId) }, { replace: true })
        return
    } catch (cause) { message.error(errorText(cause)) } finally { setSaving(false) }
  }
  const submit = async (submitContent = contentSubmitAfterSave) => { if (!detail || !dialog) return
    try {
      if (dialog === 'positioning') {
        setSaving(true); await autoSaveCoordinator.current!.saveNow(draftSaveTask()); autoSaveCoordinator.current!.invalidate(); setDialog(undefined); message.success('定位卡草稿已保存')
        await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId); return
      }
      const values = await form.validateFields(); setSaving(true)
      if (dialog === 'content') {
        const works = Array.isArray(values.works) ? (values.works as Array<Record<string, unknown>>).map(work => ({
          ...work,
          plannedPublishAt: work.plannedPublishAt ? dayjs(work.plannedPublishAt as never).format('YYYY-MM-DDTHH:mm:ss') : undefined,
          purposeLabelSnapshot: contentPurposes.find(item => item.value === work.purposeValue)?.label || '',
          formatLabelSnapshot: contentFormats.find(item => item.value === work.formatValue)?.label || '',
        })) : []
        const batchId = await contentReviewApi.createFromStudent({
          studentPersonId: detail.student.personId,
          accountIds: Array.isArray(values.accountIds) ? (values.accountIds as unknown[]).map(Number).filter(Number.isFinite) : [],
          accountSnapshots: values.accountSnapshots as Record<string, Record<string, unknown>> | undefined,
          works: works as never,
        })
        if (submitContent) await contentReviewApi.submit(batchId, 0)
        setContentSubmitAfterSave(false)
      }
      if (dialog === 'student-partner') {
        const invitation = await partnerStudentInvitationApi.create({
          studentPersonId: detail.student.personId,
          name: String(values.studentName || '').trim(),
          mobile: String(values.studentMobile || '').trim(),
        })
        setStudentInvitation(invitation)
        setDialog(undefined)
        message.success('兼职账号邀请码已生成')
        await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)
        return
      }
      if (dialog === 'precheck' && selectedService && directorContext) {
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
        const data = {}
        const requestBody = { interviewAt, data, version: stageDraftVersion.current ?? directorContext.directorForms?.[stage]?.version ?? 0 }
        const fingerprint = JSON.stringify(requestBody)
        if (precheckPending.current?.fingerprint !== fingerprint) precheckPending.current = { fingerprint, key: crypto.randomUUID() }
        const request = { ...requestBody, idempotencyKey: precheckPending.current.key }
        await api.studentDirectorPrecheckSubmit(selectedService.serviceRelationId, request)
        precheckPending.current = undefined
      }
      if (dialog === 'operator' && selectedService && directorContext) {
        const requestBody = { collaboratorType: 'operator', userId: Number(values.userId), version: directorContext.version, correctionReason: values.correctionReason ? String(values.correctionReason) : undefined } as const
        const fingerprint = JSON.stringify(requestBody)
        if (operatorPending.current?.fingerprint !== fingerprint) operatorPending.current = { fingerprint, key: crypto.randomUUID() }
        await api.studentAssignCollaborator(selectedService.serviceRelationId, { ...requestBody, idempotencyKey: operatorPending.current.key })
        operatorPending.current = undefined
      }
      if (dialog === 'reject-content' && rejectingContent) await api.mediaContent.rejectAcceptance(rejectingContent.id, rejectingContent.version, String(values.reason))
      if (dialog === 'reject-positioning' && rejectingPositioning) await api.positioningCard.operatorReject(rejectingPositioning.id, rejectingPositioning.version, String(values.reason))
      if (autoSaveDialog) autoSaveCoordinator.current!.invalidate()
      setDialog(undefined); message.success('已保存'); await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) {
        message.error(errorText(cause))
        if (dialog === 'precheck' && !(cause instanceof ApiError && [1900010024, 1900014003].includes(cause.code))) {
          scheduleAutoSave()
        }
      }
    } finally { setSaving(false) } }
  const openStudentPartnerInvitation = () => {
    if (!detail) return
    form.resetFields()
    form.setFieldsValue({ studentName: detail.student.name || '', studentMobile: detail.student.mobile || '' })
    setDialog('student-partner')
  }
  const copyStudentInvitationCode = async () => {
    if (!studentInvitation) return
    try {
      await navigator.clipboard.writeText(studentInvitation.inviteCode)
      message.success('邀请码已复制')
    } catch {
      message.error('复制失败，请手动选择邀请码')
    }
  }
  const directorField = (field: NonNullable<StudentContactContext['formFields']>[number]) => {
    const name = ['data', field.key], rules = field.required ? [{ validator: (_: unknown, value: unknown) => {
      const empty = value == null || value === '' || Array.isArray(value) && value.length === 0
      return form.getFieldValue('submit') === false || !empty ? Promise.resolve() : Promise.reject(new Error(`请填写${field.title}`))
    } }] : undefined
    const label = <span>{field.title} {field.required ? <Typography.Text type="danger">（必填）</Typography.Text> : <Typography.Text type="secondary">（选填）</Typography.Text>}</span>
    const extra = field.description?.trim() ? <Typography.Text type="secondary" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{field.description}</Typography.Text> : undefined
    if (field.type === 'textarea') return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><Input.TextArea rows={4} /></Form.Item>
    if (field.type === 'number') return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><InputNumber style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'date' || field.type === 'datetime') return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><DatePicker showTime={field.type === 'datetime'} style={{ width: '100%' }} /></Form.Item>
    if (field.type === 'region') return <div key={field.key}>{legacyRegionText && <Alert type="warning" showIcon message={`历史地区：${legacyRegionText}`} description="历史文本仅保留用于草稿兼容，正式提交前请从系统地区中重新选择。"/>}<Form.Item name={name} label={label} rules={rules} extra={extra}><Cascader options={areas} fieldNames={{ label: 'name', value: 'id', children: 'children' }} changeOnSelect showSearch placeholder="请选择地区" style={{ width: '100%' }} /></Form.Item></div>
    if (field.type === 'dict' || field.type === 'select' || field.type === 'multi_select' || field.type === 'radio' || field.type === 'checkbox_group') {
      const options = (fieldDicts[field.dictType || ''] || []).map(item => ({ value: item.value, label: item.label }))
      if (field.type === 'radio') return <Form.Item key={field.key} name={name} label={<span>{field.title} <Typography.Text type={field.required ? 'danger' : 'secondary'}>（单选·{field.required ? '必填' : '选填'}）</Typography.Text></span>} rules={rules} extra={extra}><Radio.Group options={options} /></Form.Item>
      if (field.type === 'checkbox_group') return <Form.Item key={field.key} name={name} label={<span>{field.title} <Typography.Text type={field.required ? 'danger' : 'secondary'}>（多选·{field.required ? '必填' : '选填'}）</Typography.Text></span>} rules={rules} extra={extra}><Checkbox.Group options={options} /></Form.Item>
      return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><Select mode={field.multiple || field.type === 'multi_select' ? 'multiple' : undefined} options={options} /></Form.Item>
    }
    if (field.type === 'checkbox') return <Form.Item key={field.key} name={name} valuePropName="checked" label={label} rules={rules} extra={extra}><Checkbox>勾选</Checkbox></Form.Item>
    if (field.type === 'attachment') return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><PositioningCardAttachments fieldKey={field.key} ensureDraft={async () => { await autoSaveCoordinator.current!.saveNow(draftSaveTask()); if (!positioningDraft.current) throw new Error('草稿保存失败'); return positioningDraft.current.id }} onChange={() => scheduleAutoSave()} /></Form.Item>
    if (field.type === 'material_picker') return <Form.Item key={field.key} name={name} label={label} extra={extra}><PositioningCardMaterialPicker field={field} canQuery={hasPermission(permissions, 'zsjos:material:query')} /></Form.Item>
    if (field.type === 'system_history') return <Form.Item key={field.key} name={name} label={label} extra={extra}><Input.TextArea rows={3} disabled value={positioningInterviews.length ? positioningInterviews.map(item => JSON.stringify(item)).join('\n') : '暂无历史定位或采访记录'} /></Form.Item>
    return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><Input /></Form.Item>
  }
  const openDirectorAction = (action: string) => {
    if (action === 'DIRECTOR_PRECHECK') void open('precheck')
    if (['START_POSITIONING_INTERVIEW', 'CONTINUE_POSITIONING_INTERVIEW', 'VIEW_POSITIONING_INTERVIEW'].includes(action) && selectedService) setInterviewId(selectedService.serviceRelationId)
    if (action === 'ASSIGN_OPERATOR' || action === 'DIRECTOR_OPERATOR_ASSIGN') void open('operator')
    if (action === 'CREATE_POSITIONING_CARD') void open('positioning', selectedAccountId)
  }
  const directorStage = directorContext?.directorStage || selectedService?.directorStage || 'precheck'
  const directorActions: ToolbarAction[] = (directorContext?.availableActions || [])
    .filter(action => interviewActionSet.has(action))
    .map(action => ({
      key: action,
      icon: action === 'DIRECTOR_PRECHECK' ? <FileSearchOutlined /> : action === 'VIEW_POSITIONING_INTERVIEW' ? <EyeOutlined /> : String(action) === 'CREATE_POSITIONING_CARD' ? <EditOutlined /> : action === 'ASSIGN_OPERATOR' || action === 'DIRECTOR_OPERATOR_ASSIGN' ? <UserSwitchOutlined /> : <PlayCircleOutlined />,
      label: actionLabels[action] || action,
      onClick: () => openDirectorAction(action),
    }))
  // 内容审批由运营发起；编导只负责后续逐条审核。
  const contentApprovalAction: ToolbarAction[] = hasPermission(permissions, 'zsjos:content-review:submit') ? [{ key: 'CREATE_CONTENT_REVIEW', icon: <SendOutlined />, label: '发起内容审批', onClick: () => void open('content') }] : []
  const overviewAccountActions: ToolbarAction[] = [
    ...(directorContext?.availableActions.includes('CREATE_MEDIA_ACCOUNT') ? [{
      key: 'CREATE_MEDIA_ACCOUNT',
      icon: <PlusOutlined />,
      label: '新增账号',
      onClick: () => Modal.confirm({
        title: '确认新增账号？',
        content: '账号新增后无法直接删除，请确认已核对当前学员和服务信息。',
        okText: '确认新增',
        cancelText: '取消',
        onOk: createEmptyAccount,
      }),
      disabled: saving,
    }] : []),
    ...(hasPermission(permissions, 'zsjos:partner-invitation:create-student') ? [{
      key: 'CREATE_STUDENT_PARTNER',
      icon: <PlusOutlined />,
      label: '开通兼职账号',
      onClick: openStudentPartnerInvitation,
    }] : []),
  ]
  const overviewContent = (plannerActions: ToolbarAction[]) => {
    const overviewToolbarActions = [...plannerActions, ...directorActions, ...contentApprovalAction, ...overviewAccountActions]
    return <div className="media-students-overview-content">
      {overviewToolbarActions.length > 0 && <OverflowToolbar actions={overviewToolbarActions} />}
    <section className="lead-card media-students-profile-card">
      <div className="lead-card-header"><Typography.Text strong>学员档案</Typography.Text></div>
      <div className="lead-profile-fields">
        {[
          ['姓名', detail?.student.name || '未填写'], ['学员编号', detail?.student.personNo || '历史未记录'],
          ['手机号', detail?.student.mobile || '未填写'], ['微信号', detail?.student.wechatId || '未填写'],
        ].map(([label, value]) => <div className="lead-profile-row" key={label}><span className="lead-field-label">{label}</span><span className="lead-field-value">{value}</span></div>)}
      </div>
      <div className="lead-profile-meta">
        {[
          ['学习规划师', directorContext?.ownerUserName || selectedService?.ownerUserName || '未指派'],
          ['责任编导', directorContext?.contentDirectorUserName || selectedService?.contentDirectorUserName || '未指派'],
          ['职业规划师', directorContext?.careerPlannerUserName || selectedService?.careerPlannerUserName || '未指派'],
          ['运营负责人', directorContext?.operatorUserName || selectedService?.operatorUserName || '未指派'],
          ['当前阶段', statusLabel(directorStage)],
          ['定位访谈预约时间', formatTimestamp(directorContext?.directorInterviewAt)],
          ['访谈进度', directorStage === 'positioning_interview_completed' ? '已完成定位访谈' : directorActions.some(action => action.key === 'CONTINUE_POSITIONING_INTERVIEW') ? '草稿已保存，可继续填写' : '尚未完成定位访谈'],
          ['确认完成度', interviewSummary ? `${interviewSummary.items.filter(item => item.status).length} / ${interviewSummary.fields.filter(field => field.enabled && !field.systemField).length} 项` : '暂无访谈记录'],
          ['访谈稿', interviewSummary ? `${interviewSummary.attachments.length} 份` : '暂无访谈稿'],
          ['服务状态', statusLabel(selectedService?.status)],
        ].map(([label, value]) => <div className="lead-profile-row" key={label}><span className="lead-field-label">{label}</span><span className="lead-field-value">{value}</span></div>)}
      </div>
      {summaryError && <Alert type="error" message={summaryError} action={selectedId ? <Button onClick={() => void loadDetail(selectedId, selectedServiceId, selectedAccountId)}>重试</Button> : undefined} />}
    </section>
    </div>
  }

  const accountTabLabels = buildMediaAccountTabLabels(detail?.accounts || [])
  const tabs = (detail?.accounts || []).map(account => {
    return {
      key: mediaAccountTabKey(account.id),
      label: `${accountTabLabels.get(account.id) || '未命名账号 · 平台待填写'}${accountMissing[account.id] > 0 ? ` · 待补 ${accountMissing[account.id]}` : ''}`,
      children: <div className="media-account-workspace-shell">
        <div className="media-account-workspace">
          <AccountMaintenancePanel key={`${account.id}-${maintenanceEditorAccountId === account.id ? 'edit' : 'view'}`} account={account} deliveryActions={<StudentDeliveryPanel accountId={account.id} submittedBy={directorContext?.contentDirectorUserId || 0} canQuery={hasPermission(permissions, 'zsjos:student-delivery:query')} canSubmit={hasPermission(permissions, 'zsjos:student-delivery:submit')} canDefer={hasPermission(permissions, 'zsjos:student-delivery:defer')} onChanged={() => { if (selectedId) void loadDetail(selectedId, selectedServiceId, account.id, true) }} />} canQuery={hasPermission(permissions, 'zsjos:media-account:query')} onMissingChange={updateMissing} canMaintain={account.availableActions.includes('MAINTAIN_ACCOUNT')} initiallyEditing={maintenanceEditorAccountId === account.id} onEditingFinished={() => setMaintenanceEditorAccountId(undefined)} onSaved={async () => { if (selectedId) await loadDetail(selectedId, selectedServiceId, account.id, true) }} />
          <AccountPublishedWorks key={account.id} accountId={account.id} contents={detail?.contents || []} canQuery={hasPermission(permissions, 'zsjos:content:query')} />
        </div>
      </div>,
    }
  })

  const body = detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError ? <Alert type="warning" showIcon message={detailError} action={selectedId ? <Button size="small" onClick={() => void loadDetail(selectedId, selectedServiceId)}>重试</Button> : undefined} /> : !detail ? <Empty description="从左侧选择一名学员" /> : selectedService && directorContext ? <StudentPlannerOperations student={detail.student} service={selectedService} context={directorContext} permissions={permissions} onRefresh={() => loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)}>
    {plannerActions => <StudentDetail
      student={detail.student}
      service={selectedService}
      contactContext={directorContext}
      overviewOnly
      overviewContent={overviewContent(plannerActions)}
      activeTab={tab}
      onTabChange={value => {
        setTab(value)
        const accountId = value.startsWith('account-') ? Number(value.slice('account-'.length)) : undefined
        setSelectedAccountId(accountId)
        setParams(accountId ? { personId: String(detail.student.personId), accountId: String(accountId) } : { personId: String(detail.student.personId) }, { replace: true })
      }}
      extraTabs={tabs}
    />}
  </StudentPlannerOperations> : <Empty description="当前学员暂无可用服务关系" />

  return <section className="workspace-page media-students-page"><header className="media-students-filter-shell"><Typography.Title level={4}>我的学员</Typography.Title><Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void loadPage(pageNo, selectedId)} /></Tooltip></header><div className="media-students-inbox-layout"><aside className="media-students-list-pane"><div className="media-students-toolbar"><Input.Search allowClear value={search} onChange={e => setSearch(e.target.value)} onSearch={value => setKeyword(value.trim())} placeholder="搜索姓名或手机号" /></div>{error && <Alert type="error" showIcon message={error} />}<div className="media-students-scroll">{loading && !rows.length ? <Skeleton active /> : rows.length ? rows.map(x => <button type="button" className={`media-students-item${selectedId === x.personId ? ' active' : ''}`} key={x.personId} onClick={() => { setParams({ personId: String(x.personId) }, { replace: true }); void loadDetail(x.personId) }}><NameAvatar name={x.name || '学员'} seed={x.personNo} size={36} subjectType="student" /><span className="media-students-item-copy"><strong>{x.name || '未填写姓名'}</strong><span>{x.personNo || '暂无学员编号'}</span><span>{x.mobile || '无手机号'} · {x.services.length} 项服务</span></span></button>) : <Empty description="暂无可见学员" />}</div>{total > PAGE_SIZE && <Pagination simple current={pageNo} pageSize={PAGE_SIZE} total={total} onChange={value => void loadPage(value)} />}</aside><main className="media-students-detail-pane">{body}</main></div>
    {interviewId && <PositioningInterviewDialog relationId={interviewId} onClose={() => setInterviewId(undefined)} onChanged={() => selectedId ? loadDetail(selectedId, selectedServiceId, selectedAccountId) : Promise.resolve()} />}
    <Modal width={dialog === 'content' ? 'min(1100px, calc(100vw - 32px))' : dialog === 'positioning' ? 'min(1180px, calc(100vw - 32px))' : undefined} maskClosable={false} styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} title={dialog === 'account' ? '新增第三方账号' : dialog === 'content' ? '发起内容审批' : dialog === 'positioning' ? (form.getFieldValue('accountId') ? '填写账号定位卡' : '填写定位卡草稿') : dialog === 'reject-content' ? '退回内容修改' : dialog === 'reject-positioning' ? '退回定位卡修改' : dialog === 'precheck' ? '资料预审' : dialog === 'student-partner' ? '开通学员兼职账号' : '指派运营'} open={Boolean(dialog)} onCancel={() => void closeDialog()} footer={dialog === 'content' ? [<Button key="cancel" onClick={() => void closeDialog()}>取消</Button>, <Button key="draft" loading={saving} onClick={() => void submit(false)}>保存草稿</Button>, <Button key="submit" type="primary" loading={saving} onClick={() => void submit(true)}>保存并提交审批</Button>] : undefined} onOk={() => void submit()} okText={dialog === 'positioning' ? '保存并关闭' : undefined} confirmLoading={saving}>
      <Form form={form} layout="vertical" onValuesChange={scheduleAutoSave}>
        {autoSaveNotice}
        {dialog === 'account' && <Alert type="info" showIcon message="创建空白账号" description="账号归属当前学员，业务资料全部留空。创建后自动打开账号表，由编导与运营分别补充负责字段。" />}
        {dialog === 'content' && <ContentApprovalDraft accounts={detail?.accounts || []} purposeOptions={contentPurposes.map(x => ({ value: x.value, label: x.label }))} formatOptions={contentFormats.map(x => ({ value: x.value, label: x.label }))} />}
        {dialog === 'positioning' && <><div className="media-students-positioning-toolbar"><Space wrap><Button icon={<UploadOutlined />} onClick={() => { setPositioningJsonText(''); setPositioningJsonFileName(''); setPositioningJsonPreview(undefined); setPositioningJsonError(''); setPositioningJsonOpen(true) }}>导入 JSON</Button>{hasPermission(permissions, 'zsjos:positioning-card:query') && <Button icon={<ImportOutlined />} onClick={() => { setPositioningImportOpen(true); if (!positioningImportSources.length && !positioningImportLoading && form.getFieldValue('accountId')) void loadPositioningImportSources(Number(form.getFieldValue('accountId'))) }}>导入现有定位卡</Button>}</Space></div><Form.Item name="accountId" label="第三方账号"><Select allowClear disabled={Boolean(selectedAccountId)} options={detail?.accounts.map(x => ({ value: x.id, label: x.nickname || x.accountNo }))} /></Form.Item><PositioningCardFields fields={positioningTemplate?.fields || []} render={directorField} /></>}
        {dialog === 'precheck' && <><Alert type="info" showIcon message="核对学员、订单和服务归属后，预约定位访谈。"/><Form.Item name="confirmed" valuePropName="checked" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请确认资料无误'))}]}><Checkbox>已确认资料无误</Checkbox></Form.Item><Form.Item name="interviewAt" label="定位访谈预约时间（北京时间）" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false?Promise.resolve():!v?Promise.reject(new Error('请选择定位访谈预约时间')):dayjs(v).isAfter(dayjs())?Promise.resolve():Promise.reject(new Error('定位访谈预约时间必须晚于当前北京时间'))}]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item><Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>确认完成资料预审</Checkbox></Form.Item></>}
        {dialog === 'operator' && <><Form.Item name="userId" label="运营负责人" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={operatorCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>{directorContext?.operatorAssignmentConflict && <Form.Item name="correctionReason" label="统一归属说明" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={3} /></Form.Item>}</>}
        {dialog === 'student-partner' && <><Alert type="info" showIcon message="姓名和手机号仅用于本次兼职账号注册，不会修改学员主体资料。" /><Form.Item name="studentName" label="注册姓名" rules={[{ required: true, whitespace: true, max: 100, message: '请输入注册姓名' }]}><Input maxLength={100} /></Form.Item><Form.Item name="studentMobile" label="注册手机号" rules={[{ required: true, whitespace: true, message: '请输入注册手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号' }]}><Input maxLength={11} /></Form.Item></>}
        {(dialog === 'reject-content' || dialog === 'reject-positioning') && <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 500 }]}><Input.TextArea rows={4} /></Form.Item>}
      </Form>
    </Modal>
    <Modal title="导入现有定位卡" open={positioningImportOpen} onCancel={() => setPositioningImportOpen(false)} onOk={() => void importPositioningSubmission()} okText="导入" okButtonProps={{ disabled: !positioningImportSourceId }} confirmLoading={positioningImportSaving}>
      {positioningImportLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : positioningImportError ? <Alert type="error" showIcon message={positioningImportError} action={<Button size="small" onClick={() => void loadPositioningImportSources(Number(form.getFieldValue('accountId')))}>重试</Button>} /> : positioningImportSources.length ? <Radio.Group value={positioningImportSourceId} onChange={event => setPositioningImportSourceId(event.target.value)} className="media-students-positioning-import-list">{positioningImportSources.map(source => <Radio value={source.submissionId} key={source.submissionId}><span className="media-students-positioning-import-option"><strong>{source.accountLabel}</strong><span><Tag color={source.sameAccount ? 'blue' : undefined}>{source.sameAccount ? '当前账号' : '其他账号'}</Tag>第 {source.submissionNo} 次提交 · {formatTimestamp(source.submittedAt)} · {statusLabel(source.status)}</span></span></Radio>)}</Radio.Group> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可导入的已提交定位卡" />}
    </Modal>
    <Modal width="min(760px, calc(100vw - 32px))" title="导入定位卡 JSON" open={positioningJsonOpen} onCancel={() => { if (!positioningJsonSaving) setPositioningJsonOpen(false) }} onOk={() => void confirmPositioningJsonImport()} okText="确认导入并保存" okButtonProps={{ disabled: !positioningJsonPreview || !positioningJsonPreview.importable.length && !positioningJsonPreview.cleared.length }} confirmLoading={positioningJsonSaving} maskClosable={!positioningJsonSaving}>
      <div className="media-students-json-import">
        <Alert type="info" showIcon message="仅按当前模板字段 key 匹配" description="字典字段请填写服务端稳定 value；null 表示清空该字段，未提供或校验失败的字段会保留原值。" action={<Button size="small" icon={<CopyOutlined />} onClick={() => void copyPositioningJsonPrompt()}>复制提示词</Button>} />
        <Upload.Dragger accept=".json,application/json" maxCount={1} showUploadList={false} beforeUpload={readPositioningJsonFile} disabled={positioningJsonSaving}>
          <p className="ant-upload-drag-icon"><UploadOutlined /></p>
          <p>点击或拖入 UTF-8 .json 文件</p>
          {positioningJsonFileName && <Tag>{positioningJsonFileName}</Tag>}
        </Upload.Dragger>
        <Input.TextArea rows={8} value={positioningJsonText} disabled={positioningJsonSaving} placeholder={`也可以直接粘贴 JSON，例如：\n{\n  \"strongStoryHook\": \"十年一线实战经验\",\n  \"recommendedMatchRate\": 85\n}`} onChange={event => { setPositioningJsonText(event.target.value); setPositioningJsonFileName(''); setPositioningJsonPreview(undefined); setPositioningJsonError('') }} />
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
    <Modal title="兼职账号邀请码" open={Boolean(studentInvitation)} onCancel={() => setStudentInvitation(undefined)} footer={<Button type="primary" icon={<CopyOutlined />} onClick={() => void copyStudentInvitationCode()}>复制邀请码</Button>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert type="success" showIcon message="邀请码已生成" description="请将邀请码和注册手机号一并提供给学员。" />
        <DetailFieldGrid columns={1} items={[
          { key: 'student', label: '学员', value: studentInvitation?.studentNameSnapshot || studentInvitation?.name },
          { key: 'name', label: '注册姓名', value: studentInvitation?.name },
          { key: 'mobile', label: '注册手机号', value: studentInvitation?.mobile },
          { key: 'inviteCode', label: '邀请码', value: <Typography.Text code>{studentInvitation?.inviteCode}</Typography.Text> },
          { key: 'expiresAt', label: '有效期至', value: formatTimestamp(studentInvitation?.expiresAt) },
        ]} />
      </Space>
    </Modal>
    <Modal title="学员确认链接" open={Boolean(shareLink)} onCancel={() => setShareLink(undefined)} footer={<Button type="primary" icon={<CopyOutlined />} onClick={() => shareLink && void navigator.clipboard.writeText(shareLink)}>复制链接</Button>}><Alert type="success" showIcon icon={<LinkOutlined />} message="链接已生成" description={shareLink} /></Modal>
  </section>
}












