import { locateContentError, submitContentReview, contentSaveError, contentResultUncertain, type ContentSavePhase, approvalHasStarted } from '../services/contentReviewErrors'
import { isWorkOrderLinkField, serializeWorkOrderDynamicValues } from '../services/workOrderForm'
import StudentContentDraftPicker from '../components/StudentContentDraftPicker'
import { restoreDraftWorks } from '../services/contentReviewDraft'
import type { ContentReviewBatch } from '../services/materialApi'
import { prepareContentReviewWorks } from '../services/contentReviewAttachments'
import AccountPositioningHistory from '../components/AccountPositioningHistory'
import StudentOverviewBackground from '../components/StudentOverviewBackground'
import PositioningDialog from '../components/PositioningDialog'
import StudentPartnerBindingDialog from '../components/StudentPartnerBindingDialog'
import PositioningSnapshot from '../components/PositioningSnapshot'
import { MenuFoldOutlined, MenuUnfoldOutlined, SearchOutlined, ExclamationCircleOutlined, CopyOutlined, EditOutlined, EyeOutlined, FileSearchOutlined, ImportOutlined, LinkOutlined, PlusOutlined, PlayCircleOutlined, ReloadOutlined,
  SendOutlined, UploadOutlined, UserSwitchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Cascader, Checkbox, DatePicker, Empty, Form, Image, Input, InputNumber, Modal, Radio, Select, Skeleton, Space, Switch, Tabs, Tag, Tooltip, Typography, Upload } from 'antd'
import type { InputRef } from 'antd'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { useWorkbenchPageGuard, useWorkbenchPageNavigation } from '../components/WorkbenchPageNavigation'
import { APP_ROUTES } from '../constants'
import dayjs from 'dayjs'
import DetailFieldGrid from '../components/DetailFieldGrid'
import ContentApprovalDraft from '../components/ContentApprovalDraft'
import { NameAvatar } from '../components/LeadDetailOverview'
import StudentDetail from '../components/StudentDetail'
import OperatorAssignmentDialog from '../components/OperatorAssignmentDialog'
import OverflowToolbar, { type ToolbarAction } from '../components/OverflowToolbar'
import ServicePositioningCard from '../components/ServicePositioningCard'
import AccountMaintenancePanel from '../components/AccountMaintenancePanel'
import ProductionTicketPositioningCard from '../components/ProductionTicketPositioningCard'
import WorkOrderAttachmentPicker from '../components/WorkOrderAttachmentPicker'
import ResourceLink from '../components/ResourceLink'
import ResourceLinkInput from '../components/ResourceLinkInput'
import { ApiError, api, type AreaNode, type DictData, type DirectorTemplateSnapshot, type MediaStudentDetail, type MyStudent, type PositioningCard, type PositioningCardImportSource, type ProductionTicketCreateContext, type StudentContactContext } from '../services/api'
import { DICT_TYPE } from '../constants'
import { hasPermission } from '../services/managementAccess'
import { formatTimestamp } from '../services/time'
import { DirectorAutoSaveCoordinator, type DirectorAutoSaveState } from '../services/directorAutoSave'
import { mergePositioningJsonValues, parsePositioningJson, serializePositioningFormValues, type PositioningJsonImportPreview } from '../services/positioningJsonImport'
import { workOrderApi, type WorkOrderDepartment, type WorkOrderFile, type WorkOrderTemplate } from '../services/workOrderApi'
import AccountPublishedWorks from '../components/AccountPublishedWorks'
import { StudentDeliveryPanel } from '../components/StudentDeliveryPanel'
import { contentReviewApi, partnerStudentInvitationApi, type PartnerStudentInvitation, type PartnerStudentInvitationContext } from '../services/materialApi'
import PositioningCardMaterialPicker from '../components/PositioningCardMaterialPicker'
import PositioningCardAttachments from '../components/PositioningCardAttachments'
import PositioningCardFields from '../components/PositioningCardFields'
import PositioningInterviewDialog from '../components/PositioningInterviewDialog'
import { accountProfileApi, type AccountProfile } from '../services/mediaAccountProfile'
import { StudentPlannerOperations } from './RegistrationPages'
import { positioningInterviewApi, type InterviewContext } from '../services/positioningInterviewApi'
import { PositioningManualSave, isPendingPositioningFile } from '../services/positioningManualSave'

const LIST_COLLAPSED_KEY = 'zsjos.media-students.list-collapsed'
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
  positioningRows: Array<{ id: number; accountId?: number | null }>,
  links: { preferredAccountId?: number; accountId?: number; tab?: string | null; contentId?: number; positioningCardId?: number },
) => {
  const linkedContentAccountId = contents.find(item => item.id === links.contentId)?.accountId
  const linkedPositioningAccountId = positioningRows.find(item => item.id === links.positioningCardId)?.accountId
  const requested = links.preferredAccountId || links.accountId || accountIdFromTab(links.tab || null)
    || linkedContentAccountId || linkedPositioningAccountId
  return accounts.some(item => item.id === requested) ? requested ?? undefined : undefined
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

/**
 * 账号标签页文案。
 *
 * 逐级加消歧信息，够用即止 —— 标签页宽度有限，一上来就拼全字段会把真正能区分
 * 两个账号的那一段挤到看不见。三级分别是：
 * 昵称 → 昵称 + 平台 → 昵称 + 平台 + 账号编号；同级还撞就补内部序号兜底。
 *
 * 两种情况必须带上账号编号（昵称本身区分不出来的那种）：
 * - 空昵称：显示「未命名账号 · 账号编号」，不然一排"未命名账号"没法认；
 * - 同平台重名：账号编号是唯一能把它们分开的东西。
 *
 * 见 docs/ui-guidelines.md「学员名下的每个真实媒体账号以账号昵称作为与"概览"同级的标签页」。
 */
export const buildMediaAccountTabLabels = (accounts: MediaStudentDetail['accounts']) => {
  const base = accounts.map(account => account.nickname?.trim()
    || ['未命名账号', account.accountNo || `账号 ${account.id}`].join(' · '))
  const platformCandidates = accounts.map((account, index) => account.nickname?.trim()
    ? [base[index], account.platformLabel?.trim() || '未标注平台'].join(' · ') : base[index])
  const numberedCandidates = accounts.map((account, index) => account.nickname?.trim()
    ? [platformCandidates[index], account.accountNo || `#${account.id}`].join(' · ') : platformCandidates[index])
  return new Map(accounts.map((account, index) => {
    if (base.filter(label => label === base[index]).length === 1) return [account.id, base[index]]
    if (platformCandidates.filter(label => label === platformCandidates[index]).length === 1) return [account.id, platformCandidates[index]]
    if (numberedCandidates.filter(label => label === numberedCandidates[index]).length === 1) return [account.id, numberedCandidates[index]]
    return [account.id, `${numberedCandidates[index]} · #${account.id}`]
  }))
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
  const workspaceNavigation = useWorkbenchPageNavigation()
  const initialLocationKey = useRef(location.key)
  const acceptedLocationSearch = useRef(location.search)
  const { message, modal } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [listCollapsed, setListCollapsed] = useState(() => {
    try { return localStorage.getItem(LIST_COLLAPSED_KEY) === 'true' } catch { return false }
  })
  const listScrollRef = useRef<HTMLDivElement>(null)
  const loadMoreRef = useRef<HTMLDivElement>(null)
  const listBusy = useRef(false)
  const listSelectionNavigation = useRef(false)
  const [hasMore, setHasMore] = useState(false)
  const [moreError, setMoreError] = useState('')
  const searchRef = useRef<InputRef>(null)
  const focusSearch = useRef(false)
  const listPositions = useRef({ expanded: { top: 0, left: 0 }, collapsed: { top: 0, left: 0 } })
  const changeListCollapsed = (collapsed: boolean, search = false) => {
    const scroll = listScrollRef.current
    if (scroll) listPositions.current[listCollapsed ? 'collapsed' : 'expanded'] = { top: scroll.scrollTop, left: scroll.scrollLeft }
    focusSearch.current = search
    setListCollapsed(collapsed)
    try { localStorage.setItem(LIST_COLLAPSED_KEY, String(collapsed)) } catch { /* Storage is optional; the in-page toggle remains available. */ }
  }
  useLayoutEffect(() => {
    const position = listPositions.current[listCollapsed ? 'collapsed' : 'expanded']
    listScrollRef.current?.scrollTo({ top: position.top, left: position.left })
    if (!listCollapsed && focusSearch.current) { searchRef.current?.focus(); focusSearch.current = false }
  }, [listCollapsed])
  const [rows, setRows] = useState<MyStudent[]>([]), [detail, setDetail] = useState<MediaStudentDetail>()
  const [selectedServiceId, setSelectedServiceId] = useState<number>(), [selectedAccountId, setSelectedAccountId] = useState<number>()
  const [accountMissing, setAccountMissing] = useState<Record<number, number>>({})
  const updateMissing = useCallback((id: number, count: number) => setAccountMissing(current => current[id] === count ? current : { ...current, [id]: count }), [])
  const [maintenanceEditorAccountId, setMaintenanceEditorAccountId] = useState<number>()
  const [selectedId, setSelectedId] = useState<number>()
  const selectedStudent = useRef<number | undefined>(undefined)
  const firstListLoad = useRef(true)
  const loadedKeyword = useRef('')
  const searchIntent = useRef(0)
  const operatorBusy = useRef(false)
  const [assignmentTarget, setAssignmentTarget] = useState<{ personId: number; personNo?: string; name?: string; relationId: number }>()
  const [keyword, setKeyword] = useState(''), [search, setSearch] = useState(''), [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false), [detailLoading, setDetailLoading] = useState(false), [error, setError] = useState(''), [detailError, setDetailError] = useState('')
  const [interviewSummary, setInterviewSummary] = useState<InterviewContext>(), [summaryError, setSummaryError] = useState('')
  const [interviewId, setInterviewId] = useState<number>()
  const [contentPickerOpen, setContentPickerOpen] = useState(false)
  const [contentError, setContentError] = useState('')
  const [contentOptionsLoading, setContentOptionsLoading] = useState(false)
  const [contentOptionsError, setContentOptionsError] = useState('')
  const [contentUncertain, setContentUncertain] = useState(false)
  const contentCommandLock = useRef(false)
  const contentDraft = useRef<{ id: number; fingerprint: string; version?: number } | undefined>(undefined)
  const [tab, setTab] = useState(normalizeMediaStudentTab(params.get('tab'))), [dialog, setDialog] = useState<'account' | 'content' | 'positioning' | 'reject-content' | 'reject-positioning' | 'precheck' | 'operator' | 'student-partner'>(), [saving, setSaving] = useState(false), [contentSubmitAfterSave, setContentSubmitAfterSave] = useState(false)
  const [directorContext, setDirectorContext] = useState<StudentContactContext>()
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
  const [positioningReadId, setPositioningReadId] = useState<number>()
  const [positioningReadLoading, setPositioningReadLoading] = useState(false)
  const [positioningReadError, setPositioningReadError] = useState('')
  const positioningReadRun = useRef(0)
  useEffect(() => () => { positioningReadRun.current++ }, [])
  const readPositioningCard = async (id: number) => {
    const run = ++positioningReadRun.current
    setPositioningReadId(id); setPositioningDetail(undefined); setPositioningReadLoading(true); setPositioningReadError('')
    try { const card = await api.positioningCard.get(id); if (run === positioningReadRun.current) setPositioningDetail(card) }
    catch (cause) { if (run === positioningReadRun.current) setPositioningReadError(errorText(cause)) }
    finally { if (run === positioningReadRun.current) setPositioningReadLoading(false) }
  }
  const [ticketContext, setTicketContext] = useState<ProductionTicketCreateContext>(), [ticketContextLoading, setTicketContextLoading] = useState(false), [ticketContextError, setTicketContextError] = useState(''), [ticketOpen, setTicketOpen] = useState(false), [ticketSaving, setTicketSaving] = useState(false), [ticketTemplates, setTicketTemplates] = useState<WorkOrderTemplate[]>([]), [ticketDepartments, setTicketDepartments] = useState<WorkOrderDepartment[]>([]), [ticketTargetDepartments, setTicketTargetDepartments] = useState<WorkOrderDepartment[]>([]), [ticketUsers, setTicketUsers] = useState<Array<{ id: number; nickname: string }>>([]), [ticketDictionaries, setTicketDictionaries] = useState<Array<{ dictType: string; value: string; label: string }>>([]), [ticketFiles, setTicketFiles] = useState<WorkOrderFile[]>([]), [ticketAccountId, setTicketAccountId] = useState<number>(), [ticketAccountIds, setTicketAccountIds] = useState<number[]>([]), [ticketProfiles, setTicketProfiles] = useState<Record<number, AccountProfile>>({})
  const [shareLink, setShareLink] = useState<string>()
  const [studentInvitation, setStudentInvitation] = useState<PartnerStudentInvitation>()
  const [bindingStudent, setBindingStudent] = useState<{ id: number; name: string }>()
  const [invitationContext, setInvitationContext] = useState<PartnerStudentInvitationContext>()
  const [invitationLoading, setInvitationLoading] = useState(false), [invitationError, setInvitationError] = useState('')
  const [invitationOperators, setInvitationOperators] = useState<Array<{ id: number; nickname: string }>>([])
  const [invitationOperatorsLoading, setInvitationOperatorsLoading] = useState(false), [invitationOperatorsError, setInvitationOperatorsError] = useState('')
  const invitationRequest = useRef(0)
  const invitationStudentId = detail?.student.personId
  useEffect(() => { setBindingStudent(undefined) }, [invitationStudentId])
  const canInviteStudent = hasPermission(permissions, 'zsjos:partner-invitation:create-student')
  const refreshInvitation = useCallback(async () => {
    if (!invitationStudentId || !canInviteStudent) return undefined
    const request = ++invitationRequest.current
    setInvitationLoading(true); setInvitationError('')
    try {
      const context = await partnerStudentInvitationApi.context(invitationStudentId)
      if (request !== invitationRequest.current) return undefined
      setInvitationContext(context)
      setStudentInvitation(current => current && context.invitation?.status === 'active' ? context.invitation : undefined)
      if (context.opened) setDialog(current => current === 'student-partner' ? undefined : current)
      return context
    } catch (cause) {
      if (request === invitationRequest.current) { setInvitationContext(undefined); setStudentInvitation(undefined); setInvitationError(errorText(cause)) }
      return undefined
    } finally { if (request === invitationRequest.current) setInvitationLoading(false) }
  }, [invitationStudentId, canInviteStudent])
  useEffect(() => {
    setInvitationContext(undefined); setStudentInvitation(undefined)
    void refreshInvitation()
    const onFocus = () => { void refreshInvitation() }
    window.addEventListener('focus', onFocus)
    return () => { invitationRequest.current++; window.removeEventListener('focus', onFocus) }
  }, [refreshInvitation, location.key])
  const loadInvitationOperators = async () => {
    setInvitationOperatorsLoading(true); setInvitationOperatorsError(''); setInvitationOperators([])
    try { setInvitationOperators(await partnerStudentInvitationApi.operators()) }
    catch (cause) { setInvitationOperatorsError(errorText(cause)) }
    finally { setInvitationOperatorsLoading(false) }
  }
  const [autoSave, setAutoSave] = useState<DirectorAutoSaveState>({ status: 'idle' })
  const [rejectingContent, setRejectingContent] = useState<MediaStudentDetail['contents'][number]>()
  const [rejectingPositioning, setRejectingPositioning] = useState<MediaStudentDetail['positioningCards'][number]>()
  const [form] = Form.useForm<Record<string, unknown>>(), [ticketForm] = Form.useForm<Record<string, unknown>>(), listRun = useRef(0), detailRun = useRef(0)
  const stageDraftVersion = useRef<number | undefined>(undefined)
  const draftIdentity = useRef<{ serviceRelationId: number; stage: 'precheck'; session: number; templateVersionId?: number } | undefined>(undefined)
  const precheckPending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const accountCreatePending = useRef<{ fingerprint: string; key: string } | undefined>(undefined)
  const manualSave = useRef(new PositioningManualSave())
  const [positioningRefresh, setPositioningRefresh] = useState(0)
  const [positioningCanSubmit, setPositioningCanSubmit] = useState(false)
  const [positioningDirty, setPositioningDirty] = useState(false)
  const [positioningSaveError, setPositioningSaveError] = useState('')
  const [positioningConflict, setPositioningConflict] = useState(false)
  const positioningLock = useRef(false)
  const positioningBusy = saving || positioningImportSaving
  const positioningDraft = useRef<{ id: number; version: number } | undefined>(undefined)
  const autoSaveCoordinator = useRef<DirectorAutoSaveCoordinator | undefined>(undefined)
  if (!autoSaveCoordinator.current) {
    autoSaveCoordinator.current = new DirectorAutoSaveCoordinator(AUTO_SAVE_DELAY_MS, setAutoSave, () => crypto.randomUUID(), cause => cause instanceof ApiError && [1900010024, 1900014003].includes(cause.code))
  }
  useWorkbenchPageGuard(APP_ROUTES.MEDIA_STUDENTS, async destination => {
    if (destination) {
      const requested = new URL(destination, window.location.origin).searchParams
      if (Number(requested.get('personId')) === selectedId && (Number(requested.get('accountId')) || undefined) === selectedAccountId) return true
    }
    if (!dialog) return true
    if (saving || operatorBusy.current || positioningLock.current || autoSave.status === 'saving') { message.warning('正在保存，请稍后再切换'); return false }
    return new Promise(resolve => modal.confirm({ title: '学员页面有正在编辑的内容', content: '放弃未保存内容后切换，或继续编辑。',
      okText: '放弃并切换', cancelText: '继续编辑', onOk: () => { autoSaveCoordinator.current?.invalidate(); setDialog(undefined); resolve(true) }, onCancel: () => resolve(false) }))
  })

  const loadDetail = useCallback(async (personId: number, preferredServiceId?: number, preferredAccountId?: number, background = false) => {
    const run = ++detailRun.current
    const switching = selectedStudent.current !== personId
    selectedStudent.current = personId
    if (switching) { setDialog(undefined); setAssignmentTarget(undefined); setDetail(undefined); setDirectorContext(undefined); setSelectedServiceId(undefined); setSelectedAccountId(undefined) }
    // Route hints belong only to the student named by that route.
    const detailParams = Number(params.get('personId')) === personId ? params : new URLSearchParams()
    setSelectedId(personId); if (!background) setDetailLoading(true); setDetailError(''); setInterviewSummary(undefined); setSummaryError('')
    try {
      const value = await api.mediaStudents.get(personId)
      if (run !== detailRun.current) return
      if (value.student.personId !== personId) throw new Error('学员详情身份不一致，请重新加载')
      const requestedAccountId = preferredAccountId || Number(detailParams.get('accountId')) || accountIdFromTab(detailParams.get('tab'))
      if (requestedAccountId && !value.accounts.some(account => account.id === requestedAccountId)) {
        throw new Error('该账号已失效、不属于此学员或你无权查看；请返回审批核对账号。')
      }
      const service = value.student.services.find(item => item.serviceRelationId === preferredServiceId) || value.student.services[0]
      const context = service ? await api.studentContactContext(service.serviceRelationId) : undefined
      if (run !== detailRun.current) return
      if (context && context.serviceRelationId !== service?.serviceRelationId) throw new Error('学员服务上下文不一致，请重新加载')
      const accountId = resolveMediaStudentAccountId(value.accounts, value.contents,
        [...value.positioningCards, ...value.positioningDrafts], {
          preferredAccountId,
          accountId: Number(detailParams.get('accountId')) || undefined,
          tab: detailParams.get('tab'),
          contentId: Number(detailParams.get('contentId')) || undefined,
          positioningCardId: Number(detailParams.get('positioningCardId')) || undefined,
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
  const loadPage = useCallback(async (targetPage: number, preferred?: number, append = false, keepDetail = false, initialLink = false) => {
    if (append && listBusy.current) return
    listBusy.current = true
    const run = ++listRun.current; setLoading(true); setMoreError(''); if (!append) setError('')
    try {
      const result = await api.mediaStudents.page({ pageNo: targetPage, pageSize: PAGE_SIZE, keyword: (append ? loadedKeyword.current : keyword) || undefined })
      if (run !== listRun.current) return
      firstListLoad.current = false
      if (!append) loadedKeyword.current = keyword
      setRows(current => append ? [...current, ...result.list.filter(row => !current.some(existing => existing.personId === row.personId))] : result.list)
      setPageNo(targetPage); setHasMore(result.list.length > 0 && targetPage * PAGE_SIZE < result.total)
      // Appending students must not reload the selected detail or discard its editor state.
      if (!append && !keepDetail) {
        listScrollRef.current?.scrollTo({ top: 0, left: 0 })
        const current = preferred ?? selectedStudent.current
        const linked = initialLink ? Number(params.get('personId')) || undefined : undefined
        const target = linked || result.list.find(row => row.personId === current)?.personId || result.list[0]?.personId
        if (!linked) {
          const next = target != null && target === current ? new URLSearchParams(params) : new URLSearchParams()
          if (target) next.set('personId', String(target))
          else next.delete('personId')
          acceptedLocationSearch.current = next.size ? `?${next}` : ''
          setParams(next, { replace: true })
        }
        if (target) await loadDetail(target, target === current || linked ? Number(params.get('serviceRelationId')) || undefined : undefined)
        else {
          ++detailRun.current; selectedStudent.current = undefined
          setDetail(undefined); setSelectedId(undefined); setDirectorContext(undefined)
          setSelectedServiceId(undefined); setSelectedAccountId(undefined); setInterviewSummary(undefined)
          setDetailLoading(false); setDetailError(''); setSummaryError(''); setDialog(undefined); setAssignmentTarget(undefined)
        }
      }
    } catch (cause) {
      if (run === listRun.current) {
        if (append) setMoreError(errorText(cause))
        else { setRows([]); if (!keepDetail) setDetail(undefined); setError(errorText(cause)) }
      }
    } finally { if (run === listRun.current) { listBusy.current = false; setLoading(false) } }
  }, [keyword, loadDetail, params])
  const cancelPendingList = () => {
    ++searchIntent.current; ++listRun.current; listBusy.current = false; setLoading(false)
  }
  const searchStudents = async (value: string) => {
    const next = value.trim()
    const intent = ++searchIntent.current
    const allowed = await workspaceNavigation?.validate(APP_ROUTES.MEDIA_STUDENTS) ?? !dialog
    if (!allowed || intent !== searchIntent.current) return
    cancelPendingList()
    firstListLoad.current = false
    ++detailRun.current; setDetailLoading(false)
    setLoading(true)
    if (next === keyword) void loadPage(1)
    else setKeyword(next)
  }
  useEffect(() => {
    const initialLink = firstListLoad.current
    void loadPage(1, undefined, false, false, initialLink)
  }, [keyword])
  useEffect(() => () => { ++listRun.current; ++detailRun.current }, [])
  useEffect(() => {
    if (location.key === initialLocationKey.current) return
    initialLocationKey.current = location.key
    if (location.search === acceptedLocationSearch.current) { listSelectionNavigation.current = false; return }
    if (listSelectionNavigation.current) { listSelectionNavigation.current = false; acceptedLocationSearch.current = location.search; return }
    cancelPendingList()
    const linkedId = Number(params.get('personId')) || undefined
    let disposed = false
    void (async () => {
      const allowed = await workspaceNavigation?.validate(APP_ROUTES.MEDIA_STUDENTS, `${APP_ROUTES.MEDIA_STUDENTS}${location.search}`) ?? true
      if (disposed) return
      if (!allowed) { setParams(new URLSearchParams(acceptedLocationSearch.current), { replace: true }); return }
      acceptedLocationSearch.current = location.search
      if (linkedId) void loadDetail(linkedId, Number(params.get('serviceRelationId')) || undefined, Number(params.get('accountId')) || undefined)
    })()
    return () => { disposed = true }
  }, [location.key, params, selectedId, loadDetail])
  useEffect(() => {
    if (loading || error || moreError || !rows.length || !hasMore) return
    const target = loadMoreRef.current
    if (!target) return
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadPage(pageNo + 1, undefined, true)
    }, { root: listScrollRef.current, rootMargin: '160px' })
    observer.observe(target)
    return () => observer.disconnect()
  }, [loading, error, moreError, rows.length, hasMore, pageNo, loadPage, listCollapsed])
  useEffect(() => () => autoSaveCoordinator.current?.dispose(), [])

  const loadTicketContext = async (accountId: number, sceneCode: string) => { setTicketContext(undefined); setTicketContextError(''); setTicketContextLoading(true); try { const [context, targetDepartments] = await Promise.all([api.productionTicket.createContext(accountId, sceneCode), workOrderApi.candidateDepartments(sceneCode)]); setTicketContext(context); setTicketTargetDepartments(targetDepartments.list.map(dept => ({ id: dept.id, name: dept.name }))); const assignmentType = context.allowedAssignmentTypes.includes('PERSON') ? 'PERSON' : 'DEPARTMENT'; ticketForm.setFieldsValue({ sceneCode, assignmentType, assigneeUserId: undefined, targetDeptId: undefined }) } catch (cause) { setTicketContextError(errorText(cause)) } finally { setTicketContextLoading(false) } }
  const openTicket = async (sceneCode: 'media_design_edit' | 'filming_field_work') => {
    setTicketOpen(true); setTicketAccountId(undefined); setTicketAccountIds([]); setTicketProfiles({}); setTicketContext(undefined); setTicketContextError(''); setTicketContextLoading(true); setTicketFiles([]); ticketForm.resetFields()
    try { const [catalog, departments, users, dictionaries] = await Promise.all([workOrderApi.templates(), workOrderApi.departments(), workOrderApi.users(), workOrderApi.dictionaries()]); const templates = (catalog.list || []).filter(item => item.code === sceneCode && item.processorType === 'PRODUCTION_TICKET'); setTicketTemplates(templates); setTicketDepartments(departments); setTicketUsers(users); setTicketDictionaries(dictionaries); if (!templates.length) setTicketContextError('当前没有已发布且你可发起的该类型工单模板'); else ticketForm.setFieldsValue({ sceneCode, accountIds: [] }) } catch (cause) { setTicketContextError(errorText(cause)) } finally { setTicketContextLoading(false) }
  }
  const selectTicketAccounts = async (accountIds: number[]) => {
    setTicketAccountIds(accountIds); setTicketAccountId(accountIds[0]); setTicketContext(undefined); setTicketContextError('')
    if (!accountIds.length || !ticketTemplates[0]) return
    setTicketContextLoading(true)
    try { const profiles = await Promise.all(accountIds.map(async id => [id, await accountProfileApi.get(id)] as const)); setTicketProfiles(Object.fromEntries(profiles)); await loadTicketContext(accountIds[0], ticketTemplates[0].code) }
    catch (cause) { setTicketContextError(errorText(cause)); setTicketContextLoading(false) }
  }
  const createTicket = async () => {
    const context = ticketContext
    if (!context || !ticketAccountIds.length || !detail) return
    try { const values = await ticketForm.validateFields(); const { values: dynamicValues } = serializeWorkOrderDynamicValues((context.fields || []).filter(field => field.key !== 'account_link'), values); setTicketSaving(true); await api.productionTicket.create({ sceneCode: String(values.sceneCode), accountId: ticketAccountIds[0], accountIds: ticketAccountIds, studentPersonId: detail.student.personId, dispatchMode: values.assignmentType === 'AUTO' ? 'AUTO' : 'PERSON', assigneeUserId: values.assignmentType === 'PERSON' ? Number(values.assigneeUserId) : undefined, targetDeptId: values.assignmentType === 'DEPARTMENT' ? Number(values.targetDeptId) : undefined, operatorRemark: String(values.operatorRemark || ''), values: dynamicValues, attachmentIds: ticketFiles.map(file => file.id) }); message.success('工单已发起'); setTicketOpen(false); setTicketFiles([]); await loadDetail(detail.student.personId, selectedServiceId, ticketAccountIds[0]) } catch (cause) { if (!(cause as { errorFields?: unknown }).errorFields) message.error(errorText(cause)) } finally { setTicketSaving(false) }
  }
  const selectedService = detail?.student.services.find(item => item.serviceRelationId === selectedServiceId) || detail?.student.services[0]
  const assignmentReady = !loading && !detailLoading && !detailError && !error && selectedId != null
    && detail?.student.personId === selectedId && selectedService != null
    && directorContext?.serviceRelationId === selectedService.serviceRelationId
  const assignmentValid = useRef<() => boolean>(() => false)
  assignmentValid.current = () => Boolean(assignmentReady && assignmentTarget
    && selectedStudent.current === assignmentTarget.personId
    && selectedService?.serviceRelationId === assignmentTarget.relationId && !listBusy.current)

  const resetAutoSave = () => {
    stageDraftVersion.current = undefined; draftIdentity.current = undefined; positioningDraft.current = undefined
    manualSave.current = new PositioningManualSave(); setPositioningDirty(false); setPositioningSaveError(''); setPositioningConflict(false)
    return autoSaveCoordinator.current!.begin()
  }
  const loadPositioningImportSources = async () => {
    if (!detail || !selectedService || !hasPermission(permissions, 'zsjos:positioning-card:query')) return
    setPositioningImportLoading(true); setPositioningImportError('')
    try {
      const sources = await api.positioningCard.importSources({ studentPersonId: detail.student.personId, serviceRelationId: selectedService.serviceRelationId })
      setPositioningImportSources(sources)
    } catch (cause) {
      setPositioningImportSources([])
      setPositioningImportError(cause instanceof ApiError && cause.code === 403 ? '无权读取可导入的定位卡' : errorText(cause))
    } finally { setPositioningImportLoading(false) }
  }
  const loadContentOptions = async () => {
    setContentOptionsLoading(true); setContentOptionsError('')
    api.invalidateDictDataCache()
    try {
      const [classes, purposes, formats] = await Promise.all([api.dictDataByType('zsjos_content_class'), api.dictDataByType('zsjos_content_purpose'), api.dictDataByType('zsjos_content_format')])
      setContentClasses(classes); setContentPurposes(purposes); setContentFormats(formats)
    } catch (cause) { setContentOptionsError(errorText(cause)) }
    finally { setContentOptionsLoading(false) }
  }
  const open = async (type: typeof dialog, accountId?: number, positioningDraftId?: number) => { form.resetFields(); const session = resetAutoSave(); if (type === 'content') { contentDraft.current = undefined; setContentError(''); setContentUncertain(false) }; setLegacyRegionText(undefined); if (type === 'account') accountCreatePending.current = undefined; if (type === 'positioning') { setPositioningTemplate(undefined); setPositioningImportSources([]); setPositioningImportSourceId(undefined); setPositioningImportError(''); setPositioningJsonOpen(false); setPositioningJsonPreview(undefined); setPositioningJsonError('') } if (accountId) form.setFieldValue('accountId', accountId); if (type !== 'positioning' && type !== 'precheck') setDialog(type)
    if (type === 'precheck') {
      let activeContext: StudentContactContext
      try {
        if (!selectedService) { message.error('当前课程服务上下文不可用，请刷新后再试'); return }
        activeContext = await api.studentContactContext(selectedService.serviceRelationId)
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setDirectorContext(activeContext)
      } catch (cause) {
        if (autoSaveCoordinator.current!.isCurrent(session)) message.error(errorText(cause))
        return
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
        if (!selectedService) throw new Error('当前课程服务上下文不可用，请刷新后再试')
        const overview = await api.positioningCard.serviceOverview(selectedService.serviceRelationId)
        if (overview.canSelectMaster || !overview.masterCardId && overview.candidates.length) throw new Error('请先在学员概览选择持续修订的主定位卡')
        const existingCard = positioningDraftId ? await api.positioningCard.get(positioningDraftId) : overview.current
        if (existingCard && (existingCard.serviceRelationId !== selectedService.serviceRelationId || existingCard.status !== 'co_creating' || existingCard.id !== overview.masterCardId)) throw new Error('当前定位卡不可填写，请刷新定位卡区后重试')
        setPositioningCanSubmit(overview.canSubmit)
        const latestTemplate = await api.positioningCard.publishedTemplate(existingCard?.templateId)
        const template = existingCard ? mergePositioningDraftTemplate(latestTemplate, existingCard) : latestTemplate
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        if (existingCard) positioningDraft.current = { id: existingCard.id, version: existingCard.version }
        manualSave.current.draft = positioningDraft.current
        setPositioningTemplate(template)
        setPositioningInterviews(accountId ? await api.positioningCard.interviews(accountId) : [])
        const dictTypes = [...new Set(template.fields.filter(field => field.dictType).map(field => field.dictType!))]
        const [entries, areaRows] = await Promise.all([
          Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const)),
          template.fields.some(field => field.type === 'region') ? (areas.length ? Promise.resolve(areas) : api.areaTree()) : Promise.resolve(areas),
        ])
        if (!autoSaveCoordinator.current!.isCurrent(session)) return
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) })); setAreas(areaRows)
        form.setFieldsValue({ data: positioningFormValues(template.values || {}, template.fields, areaRows) })
        setDialog(type)
        void loadPositioningImportSources()
      } catch (cause) { if (autoSaveCoordinator.current!.isCurrent(session)) { setDialog(undefined); message.error(errorText(cause)) } return }
    }
    if (type === 'content') await loadContentOptions()
  }
  const importPositioningSubmission = async () => {
    if (!detail || !selectedService || !positioningImportSourceId || positioningLock.current) return
    const accountId = undefined
    if (positioningDraft.current || form.isFieldsTouched()) {
      const confirmed = await new Promise<boolean>(resolve => modal.confirm({
        title: '覆盖当前定位卡草稿？',
        content: '导入会用所选提交版本覆盖当前草稿中的定位内容，已提交的历史定位卡不会改变。',
        okText: '导入并保存', cancelText: '取消', mask: { closable: false }, keyboard: false, onOk: () => resolve(true), onCancel: () => resolve(false)
      }))
      if (!confirmed) return
    }
    if (positioningLock.current) return
    positioningLock.current = true; setPositioningImportSaving(true)
    try {
      const currentDraft = positioningDraft.current
      const result = await api.positioningCard.importSubmission({
        sourceSubmissionId: positioningImportSourceId, accountId, studentPersonId: detail.student.personId,
        serviceRelationId: selectedService.serviceRelationId, targetDraftId: currentDraft?.id,
        version: currentDraft?.version
      })
      positioningDraft.current = { id: result.id, version: result.version }
      manualSave.current.draft = positioningDraft.current
      setPositioningDirty(false); setPositioningSaveError(''); setPositioningConflict(false)
      setPositioningTemplate(result)
      form.setFieldsValue({ data: positioningFormValues(result.values || {}, result.fields, areas) })
      setPositioningImportOpen(false); setPositioningImportSourceId(undefined)
      setPositioningRefresh(v => v + 1)
      message.success(result.skippedFieldKeys.length ? `定位卡已导入，${result.skippedFieldKeys.length} 个不兼容字段未复制` : '定位卡已导入')
      const dictTypes = [...new Set(result.fields.filter(field => field.dictType).map(field => field.dictType!))]
      try {
        const entries = await Promise.all(dictTypes.map(async dictType => [dictType, await api.dictDataByType(dictType)] as const))
        setFieldDicts(current => ({ ...current, ...Object.fromEntries(entries) }))
      } catch { message.warning('定位卡已导入，但表单选项加载失败，请关闭后重新打开定位卡') }
    } catch (cause) { message.error(errorText(cause)) }
    finally { positioningLock.current = false; setPositioningImportSaving(false) }
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
      const current = (form.getFieldValue('data') || {}) as Record<string, unknown>
      const formPreview = { ...positioningJsonPreview, values: positioningFormValues(positioningJsonPreview.values, positioningTemplate.fields, areas) }
      form.setFieldValue('data', mergePositioningJsonValues(current, formPreview))
      setPositioningDirty(true)
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
  const autoSaveDialog = dialog === 'precheck'
  const draftSaveTask = () => {
    if (!autoSaveDialog || !detail || !selectedService) throw new Error('当前草稿上下文不可用，请重新打开后再试')
    const activeDialog = dialog
    const serviceRelationId = selectedService.serviceRelationId
    const values = form.getFieldsValue(true)
    const identity = activeDialog === 'precheck' ? draftIdentity.current : undefined
    const interviewAt = values.interviewAt && typeof (values.interviewAt as { format?: (value: string) => string }).format === 'function'
      ? (values.interviewAt as { format: (value: string) => string }).format('YYYY-MM-DDTHH:mm:ss') : undefined
    const data = {}
    return async (idempotencyKey: string, session: number) => {
      if (!identity || identity.session !== session || identity.stage !== activeDialog || identity.serviceRelationId !== serviceRelationId || !autoSaveCoordinator.current!.isCurrent(session)) throw new Error('当前表单会话已失效，请重新打开后再试')
      const version = stageDraftVersion.current
      if (version == null) throw new Error('当前表单版本不可用，请重新打开后再试')
      const authoritativeVersion = await api.studentDirectorPrecheckDraft(serviceRelationId, { interviewAt, data, version, idempotencyKey })
      if (autoSaveCoordinator.current!.isCurrent(session)) stageDraftVersion.current = authoritativeVersion
    }
  }
  const savePositioning = async (closeAfter: boolean, submitAfter = false) => {
    if (positioningLock.current || !detail || !selectedService || !positioningTemplate) return
    positioningLock.current = true; setSaving(true); setPositioningSaveError(''); setPositioningConflict(false)
    let draftSaved = false
    try {
      if (submitAfter) {
        if (!positioningCanSubmit) throw new Error('请先指派课程服务责任运营并确认提交权限')
        await form.validateFields()
      }
      const accountId = undefined
      const values = serializePositioningFormValues(form.getFieldValue('data') || {}, positioningTemplate.fields)
      await manualSave.current.run({
        values,
        create: data => api.positioningCard.createDraft({ accountId, studentPersonId: detail.student.personId,
          serviceRelationId: selectedService.serviceRelationId, templateId: positioningTemplate.templateId, values: data }),
        update: (draft, data) => api.positioningCard.updateDraft(draft.id, { accountId, values: data, version: draft.version }),
        upload: (id, key, file) => api.positioningCard.uploadAttachment(id, key, file),
        onUploaded: (key, items, file) => {
          form.setFieldValue(['data', key], items)
          setPositioningTemplate(current => current ? { ...current, dictSnapshots: {
            ...current.dictSnapshots, [key]: [...(Array.isArray(current.dictSnapshots[key]) ? current.dictSnapshots[key] as unknown[] : []), file],
          } } : current)
        },
      })
      positioningDraft.current = manualSave.current.draft
      draftSaved = true; setPositioningDirty(false)
      if (submitAfter) await api.positioningCard.submitReview(positioningDraft.current!.id, positioningDraft.current!.version)
      setPositioningRefresh(v => v + 1); message.success(submitAfter ? '定位卡已提交运营审核' : '定位卡草稿已保存')
      if (closeAfter) { setPositioningJsonOpen(false); setDialog(undefined) }
      await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId, true)
    } catch (cause) {
      if ((cause as { errorFields?: unknown }).errorFields) return
      positioningDraft.current = manualSave.current.draft
      setPositioningDirty(!draftSaved)
      const conflict = cause instanceof ApiError && cause.code === 1900014003
      setPositioningConflict(conflict)
      setPositioningSaveError(conflict ? (draftSaved ? '草稿已保存，但提交时版本已变化。可先复制当前内容，再重新加载。' : '定位卡版本已变化，本次内容尚未保存。可先复制当前内容，再重新加载。') : draftSaved ? `草稿已保存，但提交失败：${errorText(cause)}` : errorText(cause))
    } finally { positioningLock.current = false; setSaving(false) }
  }
  const confirmDiscardPositioning = () => !positioningDirty ? Promise.resolve(true) : new Promise<boolean>(resolve => modal.confirm({
    title: '放弃未保存的修改？', content: '当前输入和待上传附件尚未保存。',
    okText: '放弃修改', cancelText: '继续编辑', mask: { closable: false }, keyboard: false,
    onOk: () => resolve(true), onCancel: () => resolve(false),
  }))
  const reloadPositioning = async () => {
    if (positioningLock.current || !await confirmDiscardPositioning()) return
    await open('positioning')
  }
  const copyPositioningValues = async () => {
    try {
      const values = serializePositioningFormValues(form.getFieldValue('data') || {}, positioningTemplate?.fields || [])
      await navigator.clipboard.writeText(JSON.stringify(values, (_key, value) => isPendingPositioningFile(value) ? { pendingFileName: value.file.name } : value, 2))
      message.success('内容已复制，待上传附件仅包含文件名，请保留原文件')
    } catch { message.error('复制失败，请手动保留当前内容') }
  }

  const scheduleAutoSave = () => {
    if (dialog === 'positioning') { setPositioningDirty(true); return }
    if (!autoSaveDialog) return
    autoSaveCoordinator.current!.schedule(draftSaveTask())
  }
  const closeDialog = async () => {
    if (dialog === 'content' && contentCommandLock.current) return
    if (dialog === 'content' && saving) return
    if (dialog === 'positioning') {
      if (positioningLock.current || !await confirmDiscardPositioning()) return
      setPositioningJsonOpen(false); setPositioningImportOpen(false); setDialog(undefined); return
    }
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
  const resumeContentDraft = async (batch: ContentReviewBatch) => {
    setContentPickerOpen(false)
    await open('content')
    contentDraft.current = { id: batch.id, fingerprint: '', version: batch.version }; setContentError(''); setContentUncertain(false)
    const snapshots = Array.isArray(batch.contextSnapshot.accountSnapshots) ? batch.contextSnapshot.accountSnapshots : []
    form.setFieldsValue({ accountIds: batch.accountIds?.length ? batch.accountIds : [batch.accountId],
      accountSnapshots: Object.fromEntries(snapshots.map((row: Record<string, unknown>) => [String(row.id), row])),
      works: restoreDraftWorks(batch) })
  }
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
  const submit = async (submitContent = contentSubmitAfterSave) => { if (!detail || !dialog || saving) return
    if (dialog === 'content' && (contentUncertain || contentOptionsLoading || contentOptionsError || contentCommandLock.current)) return
    if (dialog === 'content') { contentCommandLock.current = true; setContentError('') }
    let contentSaved = false
    let contentPhase: ContentSavePhase = 'prepare'
    try {
      if (dialog === 'positioning') { await savePositioning(true); return }
      const validated = await form.validateFields()
      const values = dialog === 'content' ? form.getFieldsValue(true) : validated
      setSaving(true)
      if (dialog === 'content') {
        if (contentDraft.current) {
          const current = await contentReviewApi.get(contentDraft.current.id)
          if (approvalHasStarted(current) || current.status !== 'DRAFT') {
            setContentUncertain(true)
            throw new Error('本轮状态已变化，请保留当前输入并查询审批状态')
          }
        }
        const preparedWorks = await prepareContentReviewWorks(Array.isArray(values.works) ? values.works : [], (index, field, items) => form.setFieldValue(['works', index, field], items))
        const works = preparedWorks.map(work => ({
          ...work,
          plannedPublishAt: work.plannedPublishAt ? dayjs(work.plannedPublishAt as never).format('YYYY-MM-DDTHH:mm:ss') : undefined,
          purposeLabelSnapshot: work.purposeLabelSnapshot || contentPurposes.find(item => item.value === work.purposeValue)?.label || '',
          formatLabelSnapshot: work.formatLabelSnapshot || contentFormats.find(item => item.value === work.formatValue)?.label || '',
        }))
        const draftRequest = {
          studentPersonId: detail.student.personId,
          accountIds: Array.isArray(values.accountIds) ? (values.accountIds as unknown[]).map(Number).filter(Number.isFinite) : [],
          accountSnapshots: values.accountSnapshots as Record<string, Record<string, unknown>> | undefined,
          works: works as never,
        }
        const fingerprint = JSON.stringify(draftRequest)
        if (contentDraft.current?.fingerprint !== fingerprint) {
          contentPhase = 'save'
          const id = contentDraft.current
            ? await contentReviewApi.saveStudentDraft(contentDraft.current.id, { ...draftRequest, expectedVersion: contentDraft.current.version })
            : await contentReviewApi.createFromStudent(draftRequest)
          contentDraft.current = { id, fingerprint }
          contentSaved = true
          contentPhase = 'refresh'
          const savedDraft = await contentReviewApi.get(id)
          contentDraft.current.version = savedDraft.version
          savedDraft.items.forEach((item, index) => {
            form.setFieldValue(['works', index, 'sourceContentId'], item.contentId)
            form.setFieldValue(['works', index, 'sourceVersionId'], item.contentVersionId)
            Object.assign(draftRequest.works[index], { sourceContentId: item.contentId, sourceVersionId: item.contentVersionId })
          })
          contentDraft.current.fingerprint = JSON.stringify(draftRequest)
        }
        contentSaved = true
        if (submitContent) {
          contentPhase = 'submit'
          await submitContentReview(contentDraft.current!.id, contentDraft.current!.version!)
        } else {
          message.success('草稿已保存，可继续填写；下次从发起内容审批入口选择此草稿')
          return
        }
        setContentSubmitAfterSave(false)
      }
      if (dialog === 'student-partner') {
        if (invitationLoading || !invitationContext || invitationContext.opened || invitationOperatorsLoading || invitationOperatorsError) return
        const invitation = await partnerStudentInvitationApi.create({
          studentPersonId: detail.student.personId,
          assignedOperatorUserId: Number(values.assignedOperatorUserId),
          name: String(values.studentName || '').trim(),
          mobile: String(values.studentMobile || '').trim(),
          expiresAt: dayjs(values.invitationExpiresAt as dayjs.Dayjs).valueOf(),
        })
        // 生成结果优先于此前发出的焦点刷新，避免旧状态覆盖刚生成的邀请码。
        invitationRequest.current++
        setInvitationLoading(false)
        setStudentInvitation(invitation)
        setInvitationContext(current => current && { ...current, invitation })
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
      if (dialog === 'reject-content' && rejectingContent) await api.mediaContent.rejectAcceptance(rejectingContent.id, rejectingContent.version, String(values.reason))
      if (dialog === 'reject-positioning' && rejectingPositioning) await api.positioningCard.operatorReject(rejectingPositioning.id, rejectingPositioning.version, String(values.reason))
      if (autoSaveDialog) autoSaveCoordinator.current!.invalidate()
      setDialog(undefined); message.success('已保存'); await loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) {
        if (dialog === 'content') {
          locateContentError(form, cause)
          setContentError(contentSaveError(cause, contentSaved, contentPhase))
          setContentUncertain(current => current || contentResultUncertain(cause, contentPhase))
        } else message.error(errorText(cause))
        if (dialog === 'student-partner') void refreshInvitation()
        if (dialog === 'precheck' && !(cause instanceof ApiError && [1900010024, 1900014003].includes(cause.code))) {
          scheduleAutoSave()
        }
      }
    } finally { setSaving(false); contentCommandLock.current = false } }
  const openStudentPartnerInvitation = async () => {
    if (!detail) return
    const context = await refreshInvitation()
    if (!context || context.opened) return
    if (context.invitation?.status === 'active') { setStudentInvitation(context.invitation); return }
    form.resetFields()
    form.setFieldsValue({ studentName: detail.student.name || '', studentMobile: detail.student.mobile || '', assignedOperatorUserId: context.defaultOperatorUserId, invitationExpiresAt: dayjs().add(7, 'day').startOf('second') })
    setDialog('student-partner')
    void loadInvitationOperators()
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
    if (field.type === 'textarea') return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} /></Form.Item>
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
    if (field.type === 'attachment') return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><PositioningCardAttachments cardId={positioningDraft.current?.id} disabled={positioningBusy} snapshots={positioningTemplate?.dictSnapshots?.[field.key] as Array<{ id: number; name: string; size: number }> | undefined} /></Form.Item>
    if (field.type === 'material_picker') return <Form.Item key={field.key} name={name} label={label} extra={extra}><PositioningCardMaterialPicker disabled={positioningBusy} field={field} canQuery={hasPermission(permissions, 'zsjos:material:query')} /></Form.Item>
    if (field.type === 'system_history') return <Form.Item key={field.key} name={name} label={label} extra={extra}><Input.TextArea rows={3} disabled value={positioningInterviews.length ? positioningInterviews.map(item => JSON.stringify(item)).join('\n') : '暂无历史定位或采访记录'} /></Form.Item>
    return <Form.Item key={field.key} name={name} label={label} rules={rules} extra={extra}><Input /></Form.Item>
  }
  const openDirectorAction = (action: string) => {
    if (action === 'DIRECTOR_PRECHECK') void open('precheck')
    if (['START_POSITIONING_INTERVIEW', 'CONTINUE_POSITIONING_INTERVIEW', 'VIEW_POSITIONING_INTERVIEW'].includes(action) && selectedService) setInterviewId(selectedService.serviceRelationId)
    if (action === 'ASSIGN_OPERATOR' || action === 'DIRECTOR_OPERATOR_ASSIGN') {
      if (!assignmentReady || listBusy.current || !detail || !selectedService) return
      setAssignmentTarget({ personId: detail.student.personId, personNo: detail.student.personNo, name: detail.student.name, relationId: selectedService.serviceRelationId })
      void open('operator')
    }
    if (action === 'CREATE_POSITIONING_CARD') void open('positioning', selectedAccountId)
  }
  const directorStage = directorContext?.directorStage || selectedService?.directorStage || 'precheck'
  const directorActions: ToolbarAction[] = (directorContext?.availableActions || [])
    .filter(action => interviewActionSet.has(action) && String(action) !== 'CREATE_POSITIONING_CARD')
    .map(action => ({
      key: action,
      icon: action === 'DIRECTOR_PRECHECK' ? <FileSearchOutlined /> : action === 'VIEW_POSITIONING_INTERVIEW' ? <EyeOutlined /> : String(action) === 'CREATE_POSITIONING_CARD' ? <EditOutlined /> : action === 'ASSIGN_OPERATOR' || action === 'DIRECTOR_OPERATOR_ASSIGN' ? <UserSwitchOutlined /> : <PlayCircleOutlined />,
      label: actionLabels[action] || action,
      disabled: (action === 'ASSIGN_OPERATOR' || action === 'DIRECTOR_OPERATOR_ASSIGN') && !assignmentReady,
      onClick: () => openDirectorAction(action),
    }))
  // 内容审批由运营发起；编导只负责后续逐条审核。
  const contentApprovalAction: ToolbarAction[] = hasPermission(permissions, 'zsjos:content-review:submit') && hasPermission(permissions, 'zsjos:content-review:query') ? [{ key: 'CREATE_CONTENT_REVIEW', icon: <SendOutlined />, label: '发起内容审批', onClick: () => setContentPickerOpen(true) }] : []
  const overviewAccountActions: ToolbarAction[] = [
    ...(hasPermission(permissions, 'zsjos:production-ticket:create') ? [
      { key: 'CREATE_MEDIA_DESIGN_EDIT_TICKET', icon: <EditOutlined />, label: '发起剪辑设计工单', onClick: () => void openTicket('media_design_edit'), disabled: saving },
      { key: 'CREATE_FILMING_FIELD_WORK_TICKET', icon: <PlayCircleOutlined />, label: '发起拍摄外勤工单', onClick: () => void openTicket('filming_field_work'), disabled: saving },
    ] : []),
    ...(hasPermission(permissions, 'zsjos:partner:manage-all') && detail && !invitationContext?.opened ? [{
      key: 'BIND_EXISTING_STUDENT_PARTNER', icon: <LinkOutlined />, label: '绑定已有兼职账号',
      onClick: () => setBindingStudent({ id: detail.student.personId, name: detail.student.name || '未填写姓名' }), disabled: saving,
    }] : []),
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
    ...(canInviteStudent && invitationContext && !invitationContext.opened ? [{
      key: 'CREATE_STUDENT_PARTNER',
      icon: <PlusOutlined />,
      label: invitationContext.invitation?.status === 'active' ? '查看兼职邀请码' : invitationContext.invitation ? '重新生成兼职邀请码' : '开通兼职账号',
      onClick: () => void openStudentPartnerInvitation(),
      disabled: invitationLoading || saving,
    }] : []),
  ]
  const overviewContent = (plannerActions: ToolbarAction[]) => {
    const overviewToolbarActions = selectedService ? [...plannerActions, ...directorActions, ...contentApprovalAction, ...overviewAccountActions] : []
    const profileContent = <section className="lead-card media-students-profile-card">
      <div className="lead-card-header"><Typography.Text strong>学员档案</Typography.Text></div>
      <div className="lead-profile-fields">
        {[
          ['学员编号', detail?.student.personNo || '历史未记录'],
          ['手机号', detail?.student.mobile || '未填写'], ['微信号', detail?.student.wechatId || '未填写'],
        ].map(([label, value]) => <div className="lead-profile-row" key={label}><span className="lead-field-label">{label}</span><span className="lead-field-value">{value}</span></div>)}
      </div>
      <StudentOverviewBackground key={`${selectedService?.serviceRelationId}-${selectedService?.leadId}-${hasPermission(permissions, 'zsjos:student-info-form:read')}`}
        leadId={selectedService?.leadId ?? detail?.student.leadId}
        allowed={hasPermission(permissions, 'zsjos:student-info-form:read') && !!directorContext?.visibleTabs.includes('student-info')} />
      <div className="student-overview-subheading"><Typography.Text strong>当前课程服务</Typography.Text></div>
      <dl className="student-overview-fields">{[
        ['课程', selectedService?.courseName || '历史未记录'], ['规格', selectedService?.skuName || '历史未记录'],
        ['班级', selectedService?.className || '未分班'], ['开通时间', formatTimestamp(selectedService?.activatedAt)],
      ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <div className="student-overview-subheading"><Typography.Text strong>服务团队</Typography.Text></div>
      <div className="lead-profile-meta">
        {[
          ['学习规划师', directorContext?.ownerUserName || selectedService?.ownerUserName || '未指派'],
          ['责任编导', directorContext?.contentDirectorUserName || selectedService?.contentDirectorUserName || '未指派'],
          ['职业规划师', directorContext?.careerPlannerUserName || selectedService?.careerPlannerUserName || '未指派'],
          ['运营负责人', directorContext?.operatorUserName || selectedService?.operatorUserName || '未指派'],
        ].map(([label, value]) => <div className="lead-profile-row" key={label}><span className="lead-field-label">{label}</span><span className="lead-field-value">{value}</span></div>)}
      </div>
      {summaryError && <Alert type="error" message={summaryError} action={selectedId ? <Button onClick={() => void loadDetail(selectedId, selectedServiceId, selectedAccountId)}>重试</Button> : undefined} />}
    </section>
    const statusContent = <div className="student-overview-base-status">
      <div><Typography.Text type="secondary">服务状态</Typography.Text><Tag>{statusLabel(selectedService?.status)}</Tag></div>
      <div><Typography.Text type="secondary">兼职账号</Typography.Text>
        {!canInviteStudent ? <Typography.Text type="secondary">暂无查看权限</Typography.Text>
          : invitationLoading ? <Skeleton.Input active size="small" />
          : invitationError ? <Alert type="error" showIcon message={invitationError} action={<Button size="small" onClick={() => void refreshInvitation()}>重试加载兼职状态</Button>} />
          : invitationContext ? <Tag color={invitationContext.opened ? 'success' : 'default'}>{invitationContext.opened ? '兼职账号已开通' : '未开通'}</Tag>
          : <Typography.Text type="secondary">暂无状态信息</Typography.Text>}
      </div>
    </div>
    const interviewContent = <section className="student-overview-interview"><Typography.Text strong>定位访谈</Typography.Text><dl className="student-overview-fields">{[
      ['当前阶段', statusLabel(directorStage)], ['定位访谈预约时间', formatTimestamp(directorContext?.directorInterviewAt)],
      ['访谈进度', directorStage === 'positioning_interview_completed' ? '已完成定位访谈' : directorActions.some(action => action.key === 'CONTINUE_POSITIONING_INTERVIEW') ? '草稿已保存，可继续填写' : '尚未完成定位访谈'],
      ['访谈记录', interviewSummary ? `已记录 ${interviewSummary.items.filter(item => item.status).length} / ${interviewSummary.fields.filter(field => field.enabled && !field.systemField).length} 项` : '暂无访谈记录'],
      ['访谈稿', interviewSummary ? `${interviewSummary.attachments.length} 份` : '暂无访谈稿'],
    ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    {directorActions.some(action => ['VIEW_POSITIONING_INTERVIEW', 'CONTINUE_POSITIONING_INTERVIEW', 'START_POSITIONING_INTERVIEW'].includes(action.key)) && <Button onClick={() => selectedService && setInterviewId(selectedService.serviceRelationId)}>查看定位访谈及材料</Button>}
    </section>
    return <div className="media-students-overview-content">
      {bindingStudent && bindingStudent.id === detail?.student.personId && hasPermission(permissions, 'zsjos:partner:manage-all') && <StudentPartnerBindingDialog
        key={bindingStudent.id} studentPersonId={bindingStudent.id} studentName={bindingStudent.name} onClose={() => setBindingStudent(undefined)}
        onBound={() => { setBindingStudent(undefined); message.success('兼职账号已绑定'); void refreshInvitation(); void loadDetail(bindingStudent.id, selectedServiceId, selectedAccountId) }} />}

      {overviewToolbarActions.length > 0 && <OverflowToolbar actions={overviewToolbarActions} />}
      {selectedService ? <ServicePositioningCard key={selectedService.serviceRelationId} serviceRelationId={selectedService.serviceRelationId} canQuery={hasPermission(permissions, 'zsjos:positioning-card:query')} refresh={positioningRefresh} onEdit={id => void open('positioning', undefined, id)} statusContent={statusContent} profileContent={profileContent} interviewContent={interviewContent} />
        : <div className="student-overview-grid"><section className="lead-card student-overview-main"><Typography.Paragraph>暂无课程服务</Typography.Paragraph>
          {hasPermission(permissions, 'zsjos:positioning-card:query') && detail?.positioningDrafts.map(card => <Button key={card.id} icon={<EyeOutlined />} onClick={() => void readPositioningCard(card.id)}>查看保留草稿 · {card.cardNo || '定位卡'}</Button>)}
        </section><aside className="student-overview-aside"><section className="lead-card">{statusContent}</section>{profileContent}</aside></div>}
    </div>
  }

  const accountTabLabels = buildMediaAccountTabLabels(detail?.accounts || [])
  const tabs = (detail?.accounts || []).map(account => {
    return {
      key: mediaAccountTabKey(account.id),
      label: `${accountTabLabels.get(account.id) || '未命名账号 · 平台待填写'}${accountMissing[account.id] > 0 ? ` · 待补 ${accountMissing[account.id]}` : ''}`,
      children: <div className="media-account-workspace-shell">
        <div className="media-account-workspace">
          <AccountMaintenancePanel diagnosisTaskId={Number(params.get("diagnosisTaskId")) || undefined} student={detail?.student} serviceRelationId={selectedServiceId} canReadInterview={hasPermission(permissions, 'zsjos:student:positioning-interview-query')} canQueryPositioning={hasPermission(permissions, 'zsjos:positioning-card:query')} key={`${account.id}-${maintenanceEditorAccountId === account.id ? 'edit' : 'view'}`} account={account} deliveryActions={<StudentDeliveryPanel accountId={account.id} submittedBy={directorContext?.contentDirectorUserId || 0} canQuery={hasPermission(permissions, 'zsjos:student-delivery:query')} canSubmit={hasPermission(permissions, 'zsjos:student-delivery:submit')} canDefer={hasPermission(permissions, 'zsjos:student-delivery:defer')} onChanged={() => { if (selectedId) void loadDetail(selectedId, selectedServiceId, account.id, true) }} />} canQuery={hasPermission(permissions, 'zsjos:media-account:query')} onMissingChange={updateMissing} canMaintain={account.availableActions.includes('MAINTAIN_ACCOUNT')} initiallyEditing={maintenanceEditorAccountId === account.id} onEditingFinished={() => setMaintenanceEditorAccountId(undefined)} onSaved={async () => { if (selectedId) await loadDetail(selectedId, selectedServiceId, account.id, true) }} />
          <AccountPublishedWorks key={account.id} accountId={account.id} contents={detail?.contents || []} canQuery={hasPermission(permissions, 'zsjos:content:query')} />
          <section className="account-history-full" aria-label="历史定位与采访记录"><Typography.Title level={5}>历史定位与采访记录</Typography.Title>
            {hasPermission(permissions, 'zsjos:positioning-card:query') ? <AccountPositioningHistory key={`${account.id}-${selectedServiceId}`} relationId={selectedServiceId} canReadInterview={hasPermission(permissions, 'zsjos:student:positioning-interview-query')} /> : <Typography.Text type="secondary">暂无查看定位卡历史权限</Typography.Text>}
          </section>
        </div>
      </div>,
    }
  })

  const changeAccountTab = (value: string) => {
    const next = new URLSearchParams(params); next.set('tab', value)
    const accountId = accountIdFromTab(value)
    if (accountId) next.set('accountId', String(accountId)); else next.delete('accountId')
    if (selectedId) next.set('personId', String(selectedId))
    if (workspaceNavigation) void workspaceNavigation.open(`${APP_ROUTES.MEDIA_STUDENTS}?${next}`)
    else { setTab(value); setSelectedAccountId(accountId); listSelectionNavigation.current = true; setParams(next) }
  }
  const body = detailLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : detailError ? <Alert type="warning" showIcon message={detailError} action={selectedId ? <Button size="small" onClick={() => void loadDetail(selectedId, selectedServiceId)}>重试</Button> : undefined} /> : !detail ? <Empty description="从左侧选择一名学员" /> : selectedService && directorContext ? <StudentPlannerOperations student={detail.student} service={selectedService} context={directorContext} permissions={permissions} onRefresh={() => loadDetail(detail.student.personId, selectedServiceId, selectedAccountId)}>
    {plannerActions => <StudentDetail
      student={detail.student}
      service={selectedService}
      contactContext={directorContext}
      overviewOnly
      overviewContent={overviewContent(plannerActions)}
      activeTab={tab}
      onTabChange={changeAccountTab}
      extraTabs={tabs}
    />}
  </StudentPlannerOperations> : <div className="lead-inbox-detail"><Typography.Title level={4}>{detail.student.name || '未填写姓名'}</Typography.Title>
    <Tabs className="lead-detail-tabs" activeKey={tab} onChange={changeAccountTab} items={[{ key: 'overview', label: '概览', children: overviewContent([]) }, ...tabs]} />
  </div>

  return <section className="workspace-page media-students-page">
    <header className="media-students-filter-shell"><Typography.Title level={4}>{hasPermission(permissions, 'zsjos:media-student:query-all') ? '学员管理' : '我的学员'}</Typography.Title><Tooltip title="刷新"><Button aria-label="刷新学员" icon={<ReloadOutlined />} onClick={() => void loadPage(1, selectedId)} /></Tooltip></header>
    <div className={`media-students-inbox-layout${listCollapsed ? ' is-list-collapsed' : ''}`}>
      <aside className="media-students-list-pane" aria-label="学员列表">
        <div className="media-students-toolbar">
          <Tooltip title={listCollapsed ? '展开学员列表' : '收起学员列表'}><Button size={listCollapsed ? 'small' : 'middle'} type={listCollapsed ? 'text' : 'default'} aria-label={listCollapsed ? '展开学员列表' : '收起学员列表'} aria-expanded={!listCollapsed} aria-controls="media-students-list" icon={listCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => changeListCollapsed(!listCollapsed)} /></Tooltip>
          <div className="media-students-search" hidden={listCollapsed}><Input.Search ref={searchRef} allowClear value={search} onChange={e => { setSearch(e.target.value); if (!e.target.value) void searchStudents('') }} onSearch={value => void searchStudents(value)} placeholder="搜索姓名或手机号" /></div>
          {listCollapsed && <Tooltip title={keyword ? '搜索学员（已筛选）' : '搜索学员'}><Button size="small" aria-label="搜索学员" type={keyword ? 'primary' : 'text'} icon={<SearchOutlined />} onClick={() => changeListCollapsed(false, true)} /></Tooltip>}
        </div>
        {error && (listCollapsed ? <Tooltip title={error}><Button aria-label="查看学员列表错误" danger icon={<ExclamationCircleOutlined />} onClick={() => changeListCollapsed(false)} /></Tooltip> : <Alert type="error" showIcon message={error} />)}
        {error && <Tooltip title="重试加载学员"><Button aria-label="重试加载学员" loading={loading} icon={<ReloadOutlined />} onClick={() => void loadPage(1, selectedId)}>{!listCollapsed && '重试'}</Button></Tooltip>}
        <div id="media-students-list" className="media-students-scroll" ref={listScrollRef} aria-busy={loading}>
          {loading && !rows.length ? (listCollapsed ? <Skeleton.Avatar active size={36} /> : <Skeleton active />) : rows.length ? rows.map(x => <Tooltip key={x.personId} title={listCollapsed ? `${x.name || '未填写姓名'} · ${x.personNo || '暂无学员编号'}` : undefined} trigger={['hover', 'focus']}>
            <button type="button" aria-label={`${x.name || '未填写姓名'} · ${x.personNo || '暂无学员编号'}`} aria-current={selectedId === x.personId ? 'true' : undefined} className={`lead-inbox-item media-students-item${selectedId === x.personId ? ' active' : ''}`} onClick={() => { cancelPendingList(); if (workspaceNavigation) void workspaceNavigation.open(`${APP_ROUTES.MEDIA_STUDENTS}?personId=${x.personId}`); else { listSelectionNavigation.current = true; setParams({ personId: String(x.personId) }, { replace: true }); void loadDetail(x.personId) } }}>
              <NameAvatar name={x.name || '学员'} seed={x.personNo} size={36} subjectType="student" />
              <span className="media-students-item-copy"><span className="media-students-item-heading"><strong title={x.name}>{x.name || '未填写姓名'}</strong><span title={x.personNo}>{x.personNo || '暂无学员编号'}</span></span><span title={x.mobile}>手机：{x.mobile || '未填写'}</span><span title={x.wechatId}>微信：{x.wechatId || '未填写'}</span></span>
            </button>
          </Tooltip>) : !error && (listCollapsed ? <Tooltip title="暂无可见学员"><span className="media-students-rail-empty" role="status">暂无<br />学员</span></Tooltip> : <Empty description="暂无可见学员" />)}
          <div ref={loadMoreRef} className="media-students-load-more" role="status">
            {moreError ? <><span>{!listCollapsed && moreError}</span><Button size="small" aria-label="重试加载更多学员" onClick={() => void loadPage(pageNo + 1, undefined, true)}>重试</Button></>
              : loading && rows.length > 0 ? '加载中…' : rows.length > 0 && !hasMore ? (listCollapsed ? '到底了' : '已全部加载') : null}
          </div>
        </div>
      </aside>
      <main className="media-students-detail-pane">{body}</main>
    </div>
    {interviewId && <PositioningInterviewDialog relationId={interviewId} onClose={() => setInterviewId(undefined)} onChanged={() => selectedId ? loadDetail(selectedId, selectedServiceId, selectedAccountId) : Promise.resolve()} />}
    {dialog === 'operator' && !detailLoading && assignmentTarget && detail?.student.personId === assignmentTarget.personId && assignmentTarget.personId === selectedId && assignmentTarget.relationId === selectedService?.serviceRelationId && <OperatorAssignmentDialog key={`${assignmentTarget.personId}-${assignmentTarget.relationId}`}
      relationId={assignmentTarget.relationId} studentName={assignmentTarget.name} studentNo={assignmentTarget.personNo}
      isTargetCurrent={() => assignmentValid.current()} onBusyChange={busy => { operatorBusy.current = busy }} onCancel={() => setDialog(undefined)} onSaved={async () => {
        setDialog(undefined)
        await Promise.all([loadPage(1, undefined, false, true), loadDetail(assignmentTarget.personId, assignmentTarget.relationId, selectedAccountId)])
      }} />}
    <Modal width={dialog === 'content' ? 'min(1100px, calc(100vw - 32px))' : dialog === 'positioning' ? 'min(1480px, calc(100vw - 32px))' : undefined} mask={{ closable: false }} keyboard={dialog === 'positioning' ? false : undefined} closable={dialog === 'positioning' ? !positioningBusy : undefined} styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} title={dialog === 'account' ? '新增第三方账号' : dialog === 'content' ? '发起内容审批' : dialog === 'positioning' ? '填写定位卡' : dialog === 'reject-content' ? '退回内容修改' : dialog === 'reject-positioning' ? '退回定位卡修改' : dialog === 'precheck' ? '资料预审' : dialog === 'student-partner' ? '开通学员兼职账号' : '指派运营'} open={Boolean(dialog) && dialog !== 'operator'} onCancel={() => void closeDialog()} footer={dialog === 'positioning' ? [<Button key="cancel" disabled={positioningBusy} onClick={() => void closeDialog()}>取消</Button>, <Button key="save" disabled={positioningBusy} onClick={() => void savePositioning(false)}>保存草稿</Button>, <Button key="close" loading={saving} disabled={positioningBusy} onClick={() => void savePositioning(true)}>保存并关闭</Button>, <Button key="submit" type="primary" loading={saving} disabled={positioningBusy || !positioningCanSubmit} onClick={() => void savePositioning(true, true)}>保存并提交审核</Button>] : dialog === 'content' ? [<Button key="cancel" disabled={saving} onClick={() => void closeDialog()}>取消</Button>, <Button key="draft" disabled={contentUncertain || contentOptionsLoading || Boolean(contentOptionsError)} loading={saving} onClick={() => void submit(false)}>保存草稿</Button>, <Button key="submit" type="primary" disabled={contentUncertain || contentOptionsLoading || Boolean(contentOptionsError)} loading={saving} onClick={() => void submit(true)}>保存并提交审批</Button>] : undefined} onOk={() => void submit()} okText={dialog === 'positioning' ? '保存并关闭' : undefined} confirmLoading={saving}>
      <Form form={form} layout="vertical" disabled={dialog === 'positioning' && positioningBusy} onValuesChange={scheduleAutoSave}>
        {autoSaveNotice}
        {dialog === 'positioning' && !positioningCanSubmit && <Alert type="info" message="提交前请指派课程服务责任运营，并确认你拥有提交审核权限" />}
        {dialog === 'positioning' && <Space orientation="vertical" style={{ width: '100%' }}><Typography.Text type="secondary">{saving ? '正在保存草稿…' : positioningDirty ? '有未保存的修改' : '修改后请点击保存草稿'}</Typography.Text>{positioningSaveError && <Alert type="error" showIcon message={positioningSaveError} action={positioningConflict ? <Space><Button onClick={() => void copyPositioningValues()}>复制当前内容</Button><Button onClick={() => void reloadPositioning()}>重新加载</Button></Space> : undefined} />}</Space>}
        {dialog === 'account' && <Alert type="info" showIcon message="创建空白账号" description="账号归属当前学员，业务资料全部留空。创建后自动打开账号表，由编导与运营分别补充负责字段。" />}
        {dialog === 'content' && contentError && <Alert type="error" showIcon message={contentError} description={contentUncertain ? '请保留填写内容，关闭后从草稿列表查询最新状态，再继续操作。' : undefined} />}
        {dialog === 'content' && contentOptionsError && <Alert type="error" showIcon message={contentOptionsError} action={<Button onClick={() => void loadContentOptions()}>重试</Button>} />}
        {dialog === 'content' && <ContentApprovalDraft optionsLoading={contentOptionsLoading} onReloadOptions={() => void loadContentOptions()} disabled={saving || contentOptionsLoading || Boolean(contentOptionsError)} accounts={detail?.accounts || []} purposeOptions={contentPurposes.map(x => ({ value: x.value, label: x.label }))} formatOptions={contentFormats.map(x => ({ value: x.value, label: x.label }))} />}
        {dialog === 'positioning' && <><div className="media-students-positioning-toolbar"><Space wrap><Button icon={<UploadOutlined />} disabled={positioningBusy} onClick={() => { setPositioningJsonText(''); setPositioningJsonFileName(''); setPositioningJsonPreview(undefined); setPositioningJsonError(''); setPositioningJsonOpen(true) }}>导入 JSON</Button>{hasPermission(permissions, 'zsjos:positioning-card:query') && <Button icon={<ImportOutlined />} title={!positioningDraft.current?.id ? '请先保存草稿，再导入历史版本' : undefined} disabled={positioningBusy || !positioningDraft.current?.id} onClick={() => { setPositioningImportOpen(true); if (!positioningImportSources.length && !positioningImportLoading) void loadPositioningImportSources() }}>导入现有定位卡</Button>}</Space></div><PositioningCardFields fields={positioningTemplate?.fields || []} render={directorField} /></>}
        {dialog === 'precheck' && <><Alert type="info" showIcon message="核对学员、订单和服务归属后，预约定位访谈。"/><Form.Item name="confirmed" valuePropName="checked" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false||v?Promise.resolve():Promise.reject(new Error('请确认资料无误'))}]}><Checkbox>已确认资料无误</Checkbox></Form.Item><Form.Item name="interviewAt" label="定位访谈预约时间（北京时间）" rules={[{validator:(_,v)=>form.getFieldValue('submit')===false?Promise.resolve():!v?Promise.reject(new Error('请选择定位访谈预约时间')):dayjs(v).isAfter(dayjs())?Promise.resolve():Promise.reject(new Error('定位访谈预约时间必须晚于当前北京时间'))}]}><DatePicker showTime style={{ width: '100%' }} /></Form.Item><Form.Item name="submit" initialValue={true} valuePropName="checked"><Checkbox>确认完成资料预审</Checkbox></Form.Item></>}
        {dialog === 'student-partner' && <><Alert type="info" showIcon message="姓名和手机号仅用于本次兼职账号注册，不会修改学员主体资料。" />{invitationContext?.invitation && <Alert type="warning" showIcon message="原邀请码已失效，重新生成后请发送新邀请码。" />}{invitationContext?.operatorAssignmentConflict && <Alert type="warning" showIcon message="学员运营归属不一致，请明确选择本次兼职的归属运营。" />}{invitationOperatorsError && <Alert type="error" showIcon message={invitationOperatorsError} action={<Button onClick={() => void loadInvitationOperators()}>重试加载运营</Button>} />}<Form.Item name="assignedOperatorUserId" label="归属运营" extra="默认学员当前运营，可改选；仅决定本次兼职归属，不改变学员运营安排。" rules={[{ required: true, message: '请选择归属运营' }, { validator: (_, value) => !value || invitationOperators.some(operator => operator.id === value) ? Promise.resolve() : Promise.reject(new Error('该运营已不可用，请重新选择')) }]}><Select showSearch optionFilterProp="label" loading={invitationOperatorsLoading} disabled={invitationOperatorsLoading || Boolean(invitationOperatorsError)} placeholder="请选择归属运营" notFoundContent={invitationOperatorsLoading ? '加载中' : '暂无可用运营'} options={invitationOperators.map(operator => ({ value: operator.id, label: operator.nickname }))} /></Form.Item><Form.Item name="studentName" label="注册姓名" rules={[{ required: true, whitespace: true, max: 100, message: '请输入注册姓名' }]}><Input maxLength={100} /></Form.Item><Form.Item name="studentMobile" label="注册手机号" rules={[{ required: true, whitespace: true, message: '请输入注册手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号' }]}><Input maxLength={11} /></Form.Item><Form.Item name="invitationExpiresAt" label="到期时间" rules={[{ required: true, message: '请选择到期时间' }, { validator: (_, value) => !value || dayjs(value).isAfter(dayjs()) ? Promise.resolve() : Promise.reject(new Error('到期时间必须晚于当前时间')) }]}><DatePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} /></Form.Item></>}
        {(dialog === 'reject-content' || dialog === 'reject-positioning') && <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 500 }]}><Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} /></Form.Item>}
      </Form>
    </Modal>
    <PositioningDialog title="导入现有定位卡" mask={{ closable: false }} keyboard={false} open={positioningImportOpen} closable={!positioningBusy} cancelButtonProps={{ disabled: positioningBusy }} onCancel={() => { if (!positioningLock.current) setPositioningImportOpen(false) }} onOk={() => void importPositioningSubmission()} okText="导入并保存" okButtonProps={{ disabled: positioningBusy || !positioningImportSourceId }} confirmLoading={positioningImportSaving}>
      {positioningImportLoading ? <Skeleton active paragraph={{ rows: 4 }} /> : positioningImportError ? <Alert type="error" showIcon message={positioningImportError} action={<Button size="small" onClick={() => void loadPositioningImportSources()}>重试</Button>} /> : positioningImportSources.length ? <Radio.Group value={positioningImportSourceId} onChange={event => setPositioningImportSourceId(event.target.value)} className="media-students-positioning-import-list">{positioningImportSources.map(source => <Radio value={source.submissionId} key={source.submissionId}><span className="media-students-positioning-import-option"><strong>{source.accountLabel}</strong><span><Tag color={source.sameAccount ? 'blue' : undefined}>历史版本</Tag>第 {source.submissionNo} 次提交 · {formatTimestamp(source.submittedAt)} · {statusLabel(source.status)}</span></span></Radio>)}</Radio.Group> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可导入的已提交定位卡" />}
    </PositioningDialog>
    {contentPickerOpen && detail && <StudentContentDraftPicker canCreate={hasPermission(permissions, 'zsjos:content-review:create')} studentPersonId={detail.student.personId} onClose={() => setContentPickerOpen(false)} onCreate={() => { setContentPickerOpen(false); void open('content') }} onResume={batch => void resumeContentDraft(batch)} />}
    <PositioningDialog width="min(760px, calc(100vw - 32px))" title="导入定位卡 JSON" open={positioningJsonOpen} onCancel={() => { if (!positioningJsonSaving) setPositioningJsonOpen(false) }} onOk={() => void confirmPositioningJsonImport()} okText="导入到表单" okButtonProps={{ disabled: !positioningJsonPreview || !positioningJsonPreview.importable.length && !positioningJsonPreview.cleared.length }} confirmLoading={positioningJsonSaving} mask={{ closable: false }} keyboard={false}>
      <div className="media-students-json-import">
        <Alert type="info" showIcon message="仅按当前模板字段 key 匹配" description="字典字段请填写服务端稳定 value；null 表示清空该字段，未提供或校验失败的字段会保留原值。" action={<Button size="small" icon={<CopyOutlined />} onClick={() => void copyPositioningJsonPrompt()}>复制提示词</Button>} />
        <Upload.Dragger accept=".json,application/json" maxCount={1} showUploadList={false} beforeUpload={readPositioningJsonFile} disabled={positioningJsonSaving || positioningBusy}>
          <p className="ant-upload-drag-icon"><UploadOutlined /></p>
          <p>点击或拖入 UTF-8 .json 文件</p>
          {positioningJsonFileName && <Tag>{positioningJsonFileName}</Tag>}
        </Upload.Dragger>
        <Input.TextArea rows={8} value={positioningJsonText} disabled={positioningJsonSaving || positioningBusy} placeholder={`也可以直接粘贴 JSON，例如：\n{\n  \"strongStoryHook\": \"十年一线实战经验\",\n  \"recommendedMatchRate\": 85\n}`} onChange={event => { setPositioningJsonText(event.target.value); setPositioningJsonFileName(''); setPositioningJsonPreview(undefined); setPositioningJsonError('') }} />
        <Button onClick={() => previewPositioningJson()} disabled={!positioningJsonText.trim() || positioningJsonSaving}>解析并预览</Button>
        {positioningJsonError && <Alert type="error" showIcon message={positioningJsonError} />}
        {positioningJsonPreview && <div className="media-students-json-preview">
          {positioningJsonPreview.importable.length > 0 && <section><Typography.Text strong>可导入 <Tag color="success">{positioningJsonPreview.importable.length}</Tag></Typography.Text>{positioningJsonPreview.importable.map(item => <div key={item.key}><span>{item.title} <code>{item.key}</code></span><Typography.Text type="secondary">{JSON.stringify(item.value)}</Typography.Text></div>)}</section>}
          {positioningJsonPreview.cleared.length > 0 && <section><Typography.Text strong>将清空 <Tag color="warning">{positioningJsonPreview.cleared.length}</Tag></Typography.Text>{positioningJsonPreview.cleared.map(item => <div key={item.key}><span>{item.title} <code>{item.key}</code></span><Typography.Text type="secondary">null</Typography.Text></div>)}</section>}
          {positioningJsonPreview.skipped.length > 0 && <section><Typography.Text strong>已跳过 <Tag>{positioningJsonPreview.skipped.length}</Tag></Typography.Text>{positioningJsonPreview.skipped.map(item => <div key={item.key}><span>{item.title} <code>{item.key}</code></span><Typography.Text type="danger">{item.reason}</Typography.Text></div>)}</section>}
          {!positioningJsonPreview.importable.length && !positioningJsonPreview.cleared.length && !positioningJsonPreview.skipped.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="JSON 中没有可处理的字段" />}
        </div>}
      </div>
    </PositioningDialog>
    <Modal width="min(1040px, calc(100vw - 32px))" title={`发起${ticketTemplates[0]?.name || '拍剪工单'}`} open={ticketOpen} onCancel={() => setTicketOpen(false)} onOk={() => void createTicket()} okText="确认发起" okButtonProps={{ disabled: !ticketContext || Boolean(ticketContextError) || !ticketAccountIds.length }} confirmLoading={ticketSaving}>
      {ticketContextLoading ? <Skeleton active paragraph={{ rows: 8 }} /> : ticketContextError ? <Alert type="error" showIcon message={ticketContextError} /> : <Form form={ticketForm} layout="vertical"><Form.Item name="sceneCode" hidden><Input /></Form.Item><Form.Item name="accountIds" label="关联账号" rules={[{ required: true, type: 'array', min: 1, message: '请选择至少一个该学员名下的账号' }]}><Select mode="multiple" showSearch optionFilterProp="label" options={(detail?.accounts || []).map(account => ({ value: account.id, label: `${account.nickname || account.accountNo} - ${account.platformLabel || '平台未记录'}` }))} onChange={ids => void selectTicketAccounts(ids.map(Number))} /></Form.Item>{ticketAccountIds.map(id => { const profile = ticketProfiles[id]; const account = detail?.accounts.find(item => item.id === id); const homepageUrl = profile?.values.homepage_url; return <DetailFieldGrid key={id} columns={2} items={[{ key: 'account', label: '账号', value: `${account?.nickname || account?.accountNo || '账号'} - ${account?.platformLabel || '平台未记录'}` }, { key: 'homepage', label: '账号主页链接', value: typeof homepageUrl === 'string' && homepageUrl ? <ResourceLink href={homepageUrl} /> : '未填写' }, { key: 'cover', label: '主页封面图', value: profile?.files.cover?.previewUrl ? <Image width={120} src={profile.files.cover.previewUrl} /> : '未上传' }]} /> })}{ticketContext ? <><ProductionTicketPositioningCard snapshot={ticketContext.positioning} /><Form.Item name="assignmentType" label="指派方式" rules={[{ required: true }]}><Radio.Group optionType="button" options={ticketContext.allowedAssignmentTypes.map(value => ({ value, label: value === 'PERSON' ? '指定人' : value === 'AUTO' ? '自动派单' : '指定部门' }))} /></Form.Item><Form.Item noStyle shouldUpdate={(prev, next) => prev.assignmentType !== next.assignmentType}>{({ getFieldValue }) => getFieldValue('assignmentType') === 'DEPARTMENT' ? <Form.Item name="targetDeptId" label="接收部门" rules={[{ required: true }]}><Select options={ticketTargetDepartments.map(item => ({ value: item.id, label: item.name }))} /></Form.Item> : getFieldValue('assignmentType') === 'AUTO' ? <Alert type="info" showIcon message="系统将从已配置的剪拍专员中选择当前待处理工单最少的人。" /> : <Form.Item name="assigneeUserId" label="剪拍专员" rules={[{ required: true }]}><Select options={ticketContext.assigneeCandidates.map(item => ({ value: item.id, label: item.nickname }))} /></Form.Item>}</Form.Item>{(ticketContext.fields || []).filter(field => field.key !== 'account_link').map(field => <Form.Item key={field.key} name={field.key} label={field.label} rules={field.required ? [{ required: true }] : undefined}>{field.type === 'attachment' ? <WorkOrderAttachmentPicker /> : field.type === 'textarea' ? <Input.TextArea rows={3} /> : field.type === 'number' ? <InputNumber style={{ width: '100%' }} /> : field.type === 'date' || field.type === 'datetime' ? <DatePicker showTime={field.type === 'datetime'} style={{ width: '100%' }} /> : isWorkOrderLinkField(field, true) ? <ResourceLinkInput /> : <Input />}</Form.Item>)}<Form.Item name="operatorRemark" label="运营备注" rules={[{ required: true }]}><Input.TextArea rows={4} /></Form.Item><Form.Item label="附件"><WorkOrderAttachmentPicker value={ticketFiles} onChange={setTicketFiles} /></Form.Item></> : <Alert type="info" showIcon message="请选择账号后加载工单上下文。" />}</Form>}
    </Modal>
    <PositioningDialog title="定位卡内容" open={Boolean(positioningReadId || positioningDetail)} footer={null} onCancel={() => { positioningReadRun.current++; setPositioningReadId(undefined); setPositioningDetail(undefined) }}>
      {positioningReadLoading ? <Skeleton active /> : positioningReadError ? <Alert type="error" message={positioningReadError} action={<Button onClick={() => positioningReadId && void readPositioningCard(positioningReadId)}>重试</Button>} /> : positioningDetail && <PositioningSnapshot card={positioningDetail} />}
    </PositioningDialog>
    <Modal title="兼职账号邀请码" open={Boolean(studentInvitation)} onCancel={() => setStudentInvitation(undefined)} footer={<Button type="primary" icon={<CopyOutlined />} onClick={() => void copyStudentInvitationCode()}>复制邀请码</Button>}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert type="success" showIcon message="邀请码已生成" description="请将邀请码和注册手机号一并提供给学员。" />
        <DetailFieldGrid columns={1} items={[
          { key: 'student', label: '学员', value: studentInvitation?.studentNameSnapshot || studentInvitation?.name },
          { key: 'name', label: '注册姓名', value: studentInvitation?.name },
          { key: 'mobile', label: '注册手机号', value: studentInvitation?.mobile },
          { key: 'operator', label: '归属运营', value: studentInvitation?.assignedOperatorName || '历史邀请未指定' },
          { key: 'inviteCode', label: '邀请码', value: <Typography.Text code>{studentInvitation?.inviteCode}</Typography.Text> },
          { key: 'expiresAt', label: '有效期至', value: formatTimestamp(studentInvitation?.expiresAt, '-', 'second') },
        ]} />
      </Space>
    </Modal>
    <Modal title="学员确认链接" open={Boolean(shareLink)} onCancel={() => setShareLink(undefined)} footer={<Button type="primary" icon={<CopyOutlined />} onClick={() => shareLink && void navigator.clipboard.writeText(shareLink)}>复制链接</Button>}><Alert type="success" showIcon icon={<LinkOutlined />} message="链接已生成" description={shareLink} /></Modal>
  </section>
}








