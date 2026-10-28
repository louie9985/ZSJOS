import LeadInboxSortControls from '../components/LeadInboxSortControls'
import { myOperationsApi } from '../services/myOperations'
import { managementApi } from '../services/managementApi'
import { LeadAppealPendingTag } from '../components/LeadAppealSummary'
import { useWorkbenchPageGuard } from '../components/WorkbenchPageNavigation'
import LeadTaskIndicator, { leadTaskReminderText } from '../components/LeadTaskIndicator'
import { MENU_TASK_INVALIDATED_EVENT } from '../services/menuTaskRefresh'
import BusinessTable from '../components/BusinessTable'
import { supervisorLeadSelectionEligibility, type SupervisorLeadBatchAction } from '../services/subordinateLeadActions'
import { createIdempotencyKey } from '../services/idempotency'
import { InboxAvatarControls, InboxAvatarError, useInboxAvatarRail } from '../components/InboxAvatarRail'
import { productSpecText } from '../services/productSpecs'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Dropdown,
  Form,
  Empty,
  Grid,
  Input,
  List,
  message,
  Modal,
  Select,
  Skeleton,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography
} from 'antd'
import { ArrowLeftOutlined, DeleteOutlined, DownOutlined, ExportOutlined, EyeOutlined, ReloadOutlined, RollbackOutlined, SwapOutlined } from '@ant-design/icons'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { api, type AdvancedFilterGroup, type AssignmentUser, type DictData, type LeadBatchAction, type LeadInboxFilterProfile, type LeadSortField, type ManagedLead, type ManagedLeadPageParams, type SubordinateBatchResult } from '../services/api'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'
import { NameAvatar } from '../components/LeadDetailOverview'
import LeadDetail from '../components/LeadDetail'
import { LEAD_FOLLOW_UP_CHANGED } from '../services/leadFollowUpEvents'
import {
  dictionaryDisplayLabel,
  leadQualificationDisplayLabel,
  leadRelationTypesLabel,
  leadSnapshotLabel,
  hasNextLeadInboxPage,
  isLeadInboxUnauthorized,
  mergeUniqueLeads,
  pinLeadFirst,
  prioritizeLeads,
  sortLeadsBySubmission,
  protocolDisplayLabel,
  resolveLeadSelection,
  tryStartLeadPageRequest
} from '../services/leadManagement'
import {
  DICT_TYPE,
  LEAD_ASSIGNMENT_STATUS_LABELS,
  LEAD_DISPATCH_MODE_LABELS,
  LEAD_FILTER_SECTION,
  LEAD_HANDLING_STAGE_LABELS,
  LEAD_OPERATIONAL_STATUS_LABELS,
  LEAD_FOLLOW_UP_STATUS_LABELS
} from '../constants'
import { parseLeadDetailTab, shouldBlockLeadSwitch } from '../services/leadFollowUp'
import { formatTimestamp } from '../services/time'
import { useRealtimeEvent } from '../components/RealtimeProvider'
import { useInboxTableLayout } from '../services/inboxLayout'
import { type ProColumns } from '@ant-design/pro-components'
import type { TableProps } from 'antd'
import type { TableRowSelection } from 'antd/es/table/interface'
import {
  LEAD_INBOX_REFRESH_RETRY_DELAYS_MS,
  LEAD_INBOX_UNSEEN_EVENT,
  clearLeadUnseen,
  unseenLeadIds,
  type UnseenLeadDetail
} from '../services/leadInboxUnseen'

const PAGE_SIZE = 20
type LeadAudience = 'all'
type LeadPageLoadOptions = { preferredSelectedId?: number; silent?: boolean; pageSize?: number; preserveSelection?: boolean }
type LeadBatchFormAction = LeadBatchAction

const LEAD_SORT_FIELD_BY_COLUMN_KEY: Partial<Record<string, LeadSortField>> = {
  leadNo: 'leadNo',
  submittedName: 'submittedName',
  submittedMobile: 'submittedMobile',
  submittedWechatId: 'submittedWechatId',
  source: 'sourceType',
  category: 'leadCategory',
  channel: 'sourceChannelId',
  assignmentStatus: 'assignmentStatus',
  dispatchMode: 'dispatchMode',
  assignmentAttemptCount: 'assignmentAttemptCount',
  publicPoolAt: 'publicPoolAt',
  countedAt: 'countedAt',
  firstFollowUpAt: 'currentAssignmentFirstFollowUpAt',
  firstFollowUpDeadlineAt: 'currentAssignmentFirstFollowUpDeadlineAt',
  qualificationStartedAt: 'qualificationStartedAt',
  qualificationDeadlineAt: 'qualificationDeadlineAt',
  suspendedAt: 'suspendedAt',
  validDescription: 'validDescription',
  invalidDescription: 'invalidDescription',
  appealDeadlineAt: 'appealDeadlineAt',
  closedAt: 'closedAt',
  closeReason: 'closeReason',
  nextFollowUpAt: 'nextFollowUpAt',
  submittedAt: 'submittedAt',
  lastActivityAt: 'lastActivityAt',
  qualifiedAt: 'qualifiedAt',
  convertedAt: 'convertedAt',
  remark: 'remark',
  updateTime: 'updateTime',
}

const LEAD_BATCH_ACTIONS: Array<{ type: LeadBatchFormAction; label: string; permissions: string[]; icon: React.ReactNode; danger?: boolean }> = [
  { type: 'transfer', label: '批量转派', permissions: ['zsjos:lead:owner-transfer', 'zsjos:subordinate-sales:lead-transfer', 'zsjos:lead:qualification:manage'], icon: <SwapOutlined /> },
  { type: 'restore', label: '批量恢复', permissions: ['zsjos:subordinate-sales:lead-restore', 'zsjos:lead:qualification:manage'], icon: <RollbackOutlined /> },
  { type: 'recycle', label: '批量回收', permissions: ['zsjos:subordinate-sales:lead-recycle', 'zsjos:lead:qualification:manage'], icon: <DeleteOutlined />, danger: true },
  { type: 'release-claim-pool', label: '释放至抢单池', permissions: ['zsjos:subordinate-sales:lead-release-claim-pool', 'zsjos:lead:qualification:manage'], icon: <ExportOutlined />, danger: true },
  { type: 'release-public-sea', label: '释放至公海池', permissions: ['zsjos:lead:owner-release-public-sea', 'zsjos:subordinate-sales:lead-release-public-sea', 'zsjos:lead:qualification:manage'], icon: <ExportOutlined />, danger: true },
]

const LEAD_BATCH_ACTION_LABELS: Record<LeadBatchFormAction, string> = Object.fromEntries(
  LEAD_BATCH_ACTIONS.map(action => [action.type, action.label.replace('批量', '')])
) as Record<LeadBatchFormAction, string>

function LeadBatchResultModal({ result, open, onClose }: { result?: SubordinateBatchResult; open: boolean; onClose: () => void }) {
  const resultType = result?.failureCount === 0 ? 'success' : result?.successCount === 0 ? 'error' : 'warning'
  return <Modal open={open} title="批量操作完成" onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>}>
    {result && <>
      <Alert type={resultType} showIcon message={`成功 ${result.successCount} 条，失败 ${result.failureCount} 条`} />
      <List className="lead-batch-result-list" size="small" dataSource={result.items} renderItem={item => <List.Item>
        <Space>
          <Tag color={item.success ? 'success' : 'error'}>{item.success ? '成功' : '失败'}</Tag>
          <Typography.Text>{item.leadNo || '客资编号不可用'}</Typography.Text>
          <Typography.Text type={item.success ? undefined : 'danger'}>{item.message}</Typography.Text>
        </Space>
      </List.Item>} />
    </>}
  </Modal>
}

const LEAD_FILTER_ALL = 'all'

function productText(lead: ManagedLead) {
  const product = lead.primaryProduct
  return product ? [product.spuName || '未明确课程', productSpecText(product) || product.skuName].filter(Boolean).join(' / ') : '未填写意向产品'
}

function LeadStateTags({ lead }: { lead: ManagedLead }) {
  if (lead.status === 'closed') return <Tag>{leadQualificationDisplayLabel(lead)}</Tag>
  return <Space size={4}>
    <Tag color={lead.qualificationStatus === 'invalid' ? 'red' : lead.qualificationStatus === 'valid' ? 'green' : 'gold'}>
      {leadQualificationDisplayLabel(lead, '未知有效状态')}
    </Tag><LeadAppealPendingTag lead={lead} />
    {lead.followUpStatus && <Tag color="blue">{protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, lead.followUpStatus, '未知跟进状态')}</Tag>}
    {lead.operationalStatus === 'suspended' && <Tag color="orange">已挂起</Tag>}
  </Space>
}

export default function LeadManagementPage({ permissions, detailOnly = false, salesUserId, partnerId, operationsUserId, onScopeChanged, onDirtyChange }: { permissions: string[]; detailOnly?: boolean; salesUserId?: number; partnerId?: number; operationsUserId?: number; onScopeChanged?: () => void; onDirtyChange?: (dirty: boolean) => void }) {
  const operationsReadOnly = operationsUserId !== undefined
  const embedded = salesUserId !== undefined || partnerId !== undefined || operationsReadOnly
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const routeState = (!embedded ? location.state : null) as { leadId?: number; leadNo?: string; openFollowUp?: boolean; tab?: string; relationScope?: 'submitted' | 'owned' } | null
  const queryLeadId = !embedded ? Number(searchParams.get('leadId')) || undefined : undefined
  const requestedLeadNo = !embedded ? routeState?.leadNo || searchParams.get('leadNo') || undefined : undefined
  const screens = Grid.useBreakpoint()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const { useTableLayout: preferredTableLayout } = useInboxTableLayout()
  const useTableLayout = embedded || preferredTableLayout
  const requestedLeadId = routeState?.leadId || queryLeadId
  const requestedTab = !operationsReadOnly ? parseLeadDetailTab(searchParams.get('tab'))
    || parseLeadDetailTab(routeState?.tab)
    || (routeState?.openFollowUp ? 'follow-ups' : undefined) : undefined
  const returnToValue = !embedded ? searchParams.get('returnTo') : null
  const returnTo = returnToValue?.startsWith('/zsjos/sales-order-approvals') ? returnToValue : undefined
  const audience: LeadAudience = 'all'
  const [items, setItems] = useState<ManagedLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [leadPageSize, setLeadPageSize] = useState(PAGE_SIZE)
  const [sortField, setSortField] = useState<LeadSortField>()
  const [sortOrder, setSortOrder] = useState<'ascend' | 'descend'>()
  const orderLeadRows = useCallback((rows: ManagedLead[]) => {
    const ordered = !sortField || !sortOrder ? sortLeadsBySubmission(rows) : rows
    return salesUserId === undefined && (!sortField || !sortOrder) ? prioritizeLeads(ordered, []) : ordered
  }, [salesUserId, sortField, sortOrder])
  const [initialLoading, setInitialLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [initialError, setInitialError] = useState('')
  const [loadMoreError, setLoadMoreError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  // 分级筛选：服务端下发一级归类与各二级行的选项，三个选中项取交集。
  const [filterProfile, setFilterProfile] = useState<LeadInboxFilterProfile>({ groups: [] })
  const [filterProfileError, setFilterProfileError] = useState('')
  const [inboxGroup, setInboxGroup] = useState<string>(LEAD_FILTER_ALL)
  const [sectionOptions, setSectionOptions] = useState<Record<string, string>>({})
  const [categories, setCategories] = useState<DictData[]>([])
  const [channels, setChannels] = useState<DictData[]>([])
  const [categoryError, setCategoryError] = useState(false)
  const [channelError, setChannelError] = useState(false)
  const [selectedId, setSelectedId] = useState<number | undefined>(requestedLeadId)
  const [detail, setDetail] = useState<ManagedLead>()
  const [detailRefreshVersion, setDetailRefreshVersion] = useState(0)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [followUpDirty, setFollowUpDirty] = useState(false)
  const [unseenIds, setUnseenIds] = useState<number[]>(() => unseenLeadIds())
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [selectedLeadMap, setSelectedLeadMap] = useState<Map<number, ManagedLead>>(new Map())
  const [batchAction, setBatchAction] = useState<LeadBatchFormAction>()
  const [batchCandidates, setBatchCandidates] = useState<AssignmentUser[]>([])
  const [batchResult, setBatchResult] = useState<SubordinateBatchResult>()
  const [batchResultOpen, setBatchResultOpen] = useState(false)
  const [batchSaving, setBatchSaving] = useState(false)
  const batchIdempotencyKey = useRef<string | undefined>(undefined)
  const [batchForm] = Form.useForm<{ targetUserId?: number; collaboratorUserId?: number; reason: string }>()
  const requestVersion = useRef(0)
  const metadataVersion = useRef(0)
  const activePageRequests = useRef(new Set<string>())
  const routeSelectionRef = useRef<number | undefined>(requestedLeadId)
  const detailRequestVersion = useRef(0)
  const unseenIdsRef = useRef(unseenIds)
  const avatarRail = useInboxAvatarRail('zsjos.lead-management.list-collapsed')
  const { scrollRef: listScrollRef } = avatarRail
  const listSentinelRef = useRef<HTMLDivElement>(null)
  const itemIdsRef = useRef<number[]>([])

  useEffect(() => { onDirtyChange?.(followUpDirty) }, [followUpDirty, onDirtyChange])
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])
  useWorkbenchPageGuard(location.pathname, async () => !followUpDirty || window.confirm('当前表单尚未提交，离开将丢失已填写内容。确定继续吗？'), salesUserId !== undefined)

  // 路由复用当前页面时仍以新的深链接为准，不让旧列表选择或详情响应覆盖接单目标。
  useEffect(() => {
    routeSelectionRef.current = requestedLeadId
    if (!requestedLeadId) return
    setSelectedId(requestedLeadId)
    setFollowUpDirty(false)
  }, [requestedLeadId])

  const clearLeadSelection = useCallback(() => {
    setSelectedRowKeys([])
    setSelectedLeadMap(new Map())
  }, [])

  useEffect(() => { clearLeadSelection() }, [advancedFilter, clearLeadSelection, inboxGroup, keyword, sectionOptions])

  const loadMetadata = useCallback(async () => {
    const version = ++metadataVersion.current
    setCategoryError(false)
    setChannelError(false)
    const results = await Promise.allSettled([
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)
    ])
    if (version !== metadataVersion.current) return
    if (results[0].status === 'fulfilled') setCategories(results[0].value)
    else { setCategories([]); setCategoryError(true) }
    if (results[1].status === 'fulfilled') setChannels(results[1].value)
    else { setChannels([]); setChannelError(true) }
  }, [])

  const loadFilterProfile = useCallback(async () => {
    setFilterProfileError('')
    try {
      setFilterProfile(await (operationsUserId !== undefined ? myOperationsApi.profile() : partnerId !== undefined ? managementApi.partnerLeadFilterProfile() : api.leadInboxFilterProfile('management')))
    } catch (profileError) {
      // 管理员维护的筛选项不可用时不得回退到硬编码选项，只提示并保留关键词与高级筛选。
      setFilterProfile({ groups: [] })
      setFilterProfileError(profileError instanceof Error ? profileError.message : '客资筛选方案加载失败')
    }
  }, [partnerId, operationsUserId])

  const loadPage = useCallback(async (
    targetPage: number,
    replace: boolean,
    version: number,
    options: LeadPageLoadOptions = {}
  ) => {
    const requestKey = tryStartLeadPageRequest(activePageRequests.current, version, targetPage)
    if (!requestKey) return
    if (replace) {
      if (!options.silent) setInitialLoading(true)
      setInitialError('')
    } else {
      setLoadingMore(true)
      setLoadMoreError('')
    }
    try {
      const params: ManagedLeadPageParams = {
        pendingTasksFirst: salesUserId === undefined && !operationsReadOnly && (!sortField || !sortOrder),
        pageNo: targetPage,
        pageSize: options.pageSize ?? leadPageSize,
        keyword: keyword || undefined, advancedFilter,
        relationScope: routeState?.relationScope,
        // 方案未加载成功时不带 audience 与筛选项，避免服务端因缺少方案而拒绝列表请求。
        ...(filterProfile.groups.length ? {
          audience: 'management' as const,
          inboxGroup,
          inboxStage: sectionOptions[LEAD_FILTER_SECTION.CURRENT_STAGE],
          inboxQuick: sectionOptions[LEAD_FILTER_SECTION.QUICK_CONDITION],
        } : {}),
        sortField,
        sortOrder,
      }
      const result = operationsUserId !== undefined
        ? await myOperationsApi.leads(operationsUserId, params)
        : partnerId !== undefined
        ? await managementApi.partnerLeads(partnerId, params)
        : salesUserId !== undefined
        ? await api.subordinateSalesLeads(salesUserId, params)
        : audience === 'all'
        ? await api.allLeadPage(params)
        : await api.managedLeadInboxPage(audience, params)
      if (version !== requestVersion.current) return
      setItems(current => {
        const previousPinned = routeSelectionRef.current === undefined
          ? undefined
          : current.find(item => item.id === routeSelectionRef.current)
        const next = orderLeadRows(
          replace ? result.list : mergeUniqueLeads(current, result.list)
        )
        return !useTableLayout && previousPinned && !next.some(item => item.id === previousPinned.id)
          ? orderLeadRows(pinLeadFirst(next, previousPinned))
          : next
      })
      setTotal(result.total)
      setSelectedLeadMap(current => {
        if (!current.size) return current
        const next = new Map(current)
        result.list.forEach(item => { if (next.has(item.id)) next.set(item.id, item) })
        return next
      })
      setPageNo(targetPage)
      if (replace && !options.preserveSelection) setSelectedId(current => resolveLeadSelection(result.list, {
        preferredId: options.preferredSelectedId,
        currentId: current,
        requestedId: routeSelectionRef.current ?? requestedLeadId,
        preserveRequestedId: routeSelectionRef.current !== undefined,
        // Table view is a paged data grid; loading it must not implicitly open its first row.
        // Keep an explicit deep link or an already selected row, but leave a fresh visit empty.
        fallbackToFirst: !useTableLayout
      }))
      return result.list
    } catch (loadError) {
      if (version === requestVersion.current) {
        const message = loadError instanceof Error ? loadError.message : '客资列表加载失败'
        if (replace) {
          setInitialError(message)
        } else {
          setLoadMoreError(message)
        }
      }
    } finally {
      activePageRequests.current.delete(requestKey)
      if (version === requestVersion.current) {
        if (replace) {
          if (!options.silent) setInitialLoading(false)
        } else {
          setLoadingMore(false)
        }
      }
    }
  }, [operationsUserId, partnerId, advancedFilter, audience, filterProfile.groups.length, inboxGroup, keyword, leadPageSize, salesUserId, requestedLeadId, routeState?.relationScope, sectionOptions, sortField, sortOrder, orderLeadRows, useTableLayout])

  useEffect(() => { void loadMetadata(); void loadFilterProfile() }, [loadFilterProfile, loadMetadata])
  useEffect(() => {
    if (detailOnly) return
    const version = ++requestVersion.current
    setPageNo(1)
    setInitialLoading(false)
    setLoadingMore(false)
    setInitialError('')
    setLoadMoreError('')
    if (listScrollRef.current) listScrollRef.current.scrollTop = 0
    void loadPage(1, true, version)
  }, [detailOnly, loadPage])
  // 深链接直达某客资时，移动端直接把详情抽屉弹出来，避免用户以为列表为空
  useEffect(() => {
    if (requestedLeadId && (useTableLayout || window.matchMedia('(max-width: 768px)').matches)) setDrawerOpen(true)
  }, [requestedLeadId, useTableLayout])
  // 视口拉宽回到桌面端时，关闭移动端详情抽屉、回到双栏布局
  useEffect(() => { if (screens.md && !useTableLayout) setDrawerOpen(false) }, [screens.md, useTableLayout])

  const loadDetail = useCallback(async (id: number, silent = false) => {
    const version = ++detailRequestVersion.current
    if (!silent) setDetailLoading(true)
    setDetailError('')
    try {
      const loaded = operationsUserId !== undefined ? await myOperationsApi.lead(operationsUserId, id) : partnerId !== undefined ? await managementApi.partnerLead(id) : await api.managedLead(id)
      if (version !== detailRequestVersion.current) return
      setDetail(loaded)
      // 静默刷新不能把已被服务端筛选排除的客资重新插入列表。
      setItems(current => orderLeadRows(current.some(item => item.id === id) ? current.map(item => item.id === id ? loaded : item) : silent || useTableLayout ? current : pinLeadFirst(current, loaded)))
    } catch (loadError) {
      if (version !== detailRequestVersion.current) return
      setDetail(undefined)
      setDetailError(loadError instanceof Error ? loadError.message : '客资详情加载失败')
    } finally {
      if (version === detailRequestVersion.current) setDetailLoading(false)
    }
  }, [orderLeadRows, partnerId, operationsUserId, useTableLayout])

  const loadDetailByNo = useCallback(async (leadNo: string) => {
    const version = ++detailRequestVersion.current
    setDetailLoading(true)
    setDetailError('')
    try {
      const loaded = await api.managedLeadByNo(leadNo)
      if (version !== detailRequestVersion.current) return
      setSelectedId(loaded.id)
      setDetail(loaded)
      setItems(current => orderLeadRows(current.some(item => item.id === loaded.id) ? current.map(item => item.id === loaded.id ? loaded : item) : useTableLayout ? current : pinLeadFirst(current, loaded)))
    } catch (loadError) {
      if (version !== detailRequestVersion.current) return
      setDetail(undefined)
      setDetailError(loadError instanceof Error ? loadError.message : '客资详情加载失败')
    } finally {
      if (version === detailRequestVersion.current) setDetailLoading(false)
    }
  }, [orderLeadRows, partnerId, operationsUserId, useTableLayout])

  useEffect(() => {
    if (selectedId) void loadDetail(selectedId)
    else setDetail(undefined)
    return () => { detailRequestVersion.current++ }
  }, [loadDetail, selectedId])
  useEffect(() => {
    if (!selectedId && requestedLeadNo) void loadDetailByNo(requestedLeadNo)
  }, [loadDetailByNo, requestedLeadNo, selectedId])

  // 详情已经渲染出来即算看过，自动选中与手工点击一视同仁
  useEffect(() => {
    if (selectedId) setUnseenIds(clearLeadUnseen(selectedId))
  }, [selectedId])

  const refreshAfterLeadChange = useCallback(async (id: number) => {
    setSelectedId(id)
    if (routeSelectionRef.current === id) routeSelectionRef.current = undefined
    const version = ++requestVersion.current
    if (listScrollRef.current) listScrollRef.current.scrollTop = 0
    await Promise.all([
      loadMetadata(),
      loadPage(useTableLayout ? pageNo : 1, true, version, { preferredSelectedId: id, silent: true, preserveSelection: true }),
      loadDetail(id, true)
    ])
  }, [loadDetail, loadMetadata, loadPage, pageNo, useTableLayout])

  useEffect(() => {
    const onFollowUpChanged = (event: Event) => {
      const leadId = (event as CustomEvent<number>).detail
      if (routeSelectionRef.current === leadId) routeSelectionRef.current = undefined
      if (!detailOnly) void loadPage(useTableLayout ? pageNo : 1, true, ++requestVersion.current, { silent: true, preserveSelection: true })
      if (selectedId === leadId) {
        // Keep the mounted form and its draft; only invalidate the server-backed history.
        setDetailRefreshVersion(value => value + 1)
        void loadDetail(leadId, true)
      }
    }
    window.addEventListener(LEAD_FOLLOW_UP_CHANGED, onFollowUpChanged)
    return () => window.removeEventListener(LEAD_FOLLOW_UP_CHANGED, onFollowUpChanged)
  }, [detailOnly, loadDetail, loadPage, pageNo, selectedId, useTableLayout])

  const refreshPage = () => {
    setDetailRefreshVersion(value => value + 1)
    void loadMetadata()
    void loadFilterProfile()
    void loadPage(1, true, ++requestVersion.current, { preserveSelection: true })
    if (selectedId) void loadDetail(selectedId, true)
  }

  useEffect(() => { itemIdsRef.current = items.map(item => item.id) }, [items])

  /**
   * 接单后把新客资拉进列表。
   * 接单与列表查询是两次请求，后端写入对读可见有延迟，所以按节奏重试直到新客资出现。
   */
  const refreshUntilVisible = useCallback(async (leadId?: number) => {
    if (embedded) { await loadPage(1, true, ++requestVersion.current); return }
    for (const delay of LEAD_INBOX_REFRESH_RETRY_DELAYS_MS) {
      if (delay > 0) await new Promise(resolve => window.setTimeout(resolve, delay))
      const version = ++requestVersion.current
      await Promise.all([loadMetadata(), loadPage(1, true, version)])
      if (leadId == null || itemIdsRef.current.includes(leadId)) return
    }
    if (!useTableLayout && leadId != null && !itemIdsRef.current.includes(leadId)) {
      try {
        const loaded = await api.managedLead(leadId)
        setItems(current => orderLeadRows(pinLeadFirst(current, loaded)))
      } catch {
        // The list refresh remains best effort; object authorization is enforced by managedLead.
      }
    }
  }, [loadMetadata, loadPage, orderLeadRows, salesUserId, embedded, useTableLayout])

  // 接单成功由 LeadAssignmentHost 打标记，这里据此刷新，销售不必手动刷新页面
  useEffect(() => {
    const onUnseenChange = (event: Event) => {
      const ids = (event as CustomEvent<UnseenLeadDetail>).detail?.leadIds ?? []
      setUnseenIds(ids)
      unseenIdsRef.current = ids
      const added = ids.find(id => !itemIdsRef.current.includes(id))
      if (added != null) void refreshUntilVisible(added)
    }
    window.addEventListener(LEAD_INBOX_UNSEEN_EVENT, onUnseenChange)
    return () => window.removeEventListener(LEAD_INBOX_UNSEEN_EVENT, onUnseenChange)
  }, [refreshUntilVisible])

  // 转派、回收等由他人触发的归属变化同样要落到列表上
  useRealtimeEvent('zsjos_lead_assignment', () => { void refreshUntilVisible() })

  const refreshTaskIndicators = useCallback(() => {
    // A background read must not invalidate the request that owns a visible loading state.
    if (!detailOnly && activePageRequests.current.size === 0) void loadPage(useTableLayout ? pageNo : 1, true, ++requestVersion.current, { silent: true, preserveSelection: true })
  }, [detailOnly, loadPage, pageNo, useTableLayout])
  useRealtimeEvent('zsjos-workbench-task-invalidated', refreshTaskIndicators)
  useEffect(() => {
    if (detailOnly) return
    const refreshVisible = () => { if (document.visibilityState === 'visible') refreshTaskIndicators() }
    const timer = window.setInterval(refreshVisible, 60_000)
    window.addEventListener(MENU_TASK_INVALIDATED_EVENT, refreshTaskIndicators)
    window.addEventListener('focus', refreshVisible)
    document.addEventListener('visibilitychange', refreshVisible)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener(MENU_TASK_INVALIDATED_EVENT, refreshTaskIndicators)
      window.removeEventListener('focus', refreshVisible)
      document.removeEventListener('visibilitychange', refreshVisible)
    }
  }, [detailOnly, refreshTaskIndicators])

  const categoryLabel = useCallback(
    (value?: string) => dictionaryDisplayLabel(categories, value, categoryError),
    [categories, categoryError]
  )
  const channelLabel = useCallback(
    (value?: string) => dictionaryDisplayLabel(channels, value, channelError),
    [channelError, channels]
  )
  const hasMore = hasNextLeadInboxPage(pageNo, leadPageSize, total)

  useEffect(() => {
    const root = listScrollRef.current
    const sentinel = listSentinelRef.current
    if (!root || !sentinel || !hasMore || initialLoading || loadingMore || loadMoreError) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) {
        void loadPage(pageNo + 1, false, requestVersion.current)
      }
    }, { root, rootMargin: avatarRail.collapsed ? '240px' : '240px 0px', threshold: 0 })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [avatarRail.collapsed, useTableLayout, hasMore, initialLoading, loadMoreError, loadPage, loadingMore, pageNo])

  const selectLead = (id: number) => {
    if (id === selectedId) {
      if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
      return
    }
    if (shouldBlockLeadSwitch(followUpDirty) && !window.confirm('当前表单尚未提交，切换客资将丢失已填写内容。确定继续吗？')) return
    setFollowUpDirty(false)
    routeSelectionRef.current = undefined
    setSelectedId(id)
    if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
  }
  // 一级归类决定二级行内容；归类不可用时退回首个分组，与服务端默认一致。
  const activeGroup = filterProfile.groups.find(group => group.key === inboxGroup) ?? filterProfile.groups[0]
  const detailContent = detailLoading
    ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError
      ? <Alert type="error" showIcon message={detailError} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail
        ? <LeadDetail lead={detail} refreshVersion={detailRefreshVersion} categories={categories} categoryLabel={categoryLabel} channelLabel={channelLabel}
          readOnly={operationsReadOnly} mode={partnerId !== undefined || operationsReadOnly ? "manager-readonly" : audience} autoExpandFollowUp={Boolean(routeState?.openFollowUp && requestedLeadId === detail.id)}
          initialTab={requestedLeadId === detail.id ? requestedTab : undefined}
          onDirtyChange={setFollowUpDirty} onChanged={() => { void refreshAfterLeadChange(detail.id); onScopeChanged?.() }} profileVariant="contact-rows"/>
        : <Empty description="从左侧选择一条客资"/>

  const allLeadTableColumns: ProColumns<ManagedLead>[] = [
      { key: 'leadNo', title: '客资编号', dataIndex: 'leadNo', width: 170 },
      { key: 'submittedName', title: '姓名', dataIndex: 'submittedName', width: 130,
        render: (_, item) => <Space size={6}><LeadTaskIndicator lead={item} compact />{item.submittedName}</Space> },
      { key: 'submittedMobile', title: '手机号', dataIndex: 'submittedMobile', width: 130, render: (_, item) => item.submittedMobile || '-' },
      { key: 'submittedWechatId', title: '微信号', dataIndex: 'submittedWechatId', width: 140, render: (_, item) => item.submittedWechatId || '-' },
      { key: 'source', title: '来源', render: (_: unknown, item: ManagedLead) => item.sourceLabel || (item.sourceType ? '来源名称未配置' : '—') },
      { key: 'sourceUser', title: '提交人', render: (_: unknown, item: ManagedLead) => item.sourceUserName || '-' },
      { key: 'owner', title: '负责人', render: (_: unknown, item: ManagedLead) => [item.ownerUserName, item.ownerIdentity ? item.ownerIdentityLabel : undefined].filter(Boolean).join(' · ') || '-' },
      { key: 'salesStage', title: '销售阶段', render: (_: unknown, item: ManagedLead) => item.salesStageLabelSnapshot || '未记录' },
      { key: 'category', title: '分类', render: (_: unknown, item: ManagedLead) => leadSnapshotLabel(item.leadCategoryLabelSnapshot, item.leadCategory) },
      { key: 'channel', title: '渠道', render: (_: unknown, item: ManagedLead) => leadSnapshotLabel(item.sourceChannelLabelSnapshot, item.sourceChannel) },
      { key: 'region', title: '地区', render: (_: unknown, item: ManagedLead) => [item.provinceName, item.cityName].filter(Boolean).join(' / ') || '-' },
      { key: 'product', title: '意向产品', render: (_: unknown, item: ManagedLead) => productText(item) },
      { key: 'qualificationStatus', title: '有效性状态', render: (_: unknown, item: ManagedLead) => <Space wrap>{item.status === 'closed' || item.qualificationStatus ? leadQualificationDisplayLabel(item) : '—'}<LeadAppealPendingTag lead={item} /></Space> },
      { key: 'followUpStatus', title: '跟进状态', render: (_: unknown, item: ManagedLead) => item.followUpStatus ? protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, item.followUpStatus, '未知状态') : '—' },
      { key: 'operationalStatus', title: '运营状态', render: (_: unknown, item: ManagedLead) => item.operationalStatus ? protocolDisplayLabel(LEAD_OPERATIONAL_STATUS_LABELS, item.operationalStatus, '未知状态') : '—' },
      { key: 'handlingStage', title: '处理阶段', render: (_: unknown, item: ManagedLead) => item.handlingStage ? protocolDisplayLabel(LEAD_HANDLING_STAGE_LABELS, item.handlingStage, '未知阶段') : '—' },
      { key: 'assignmentStatus', title: '分配状态', render: (_: unknown, item: ManagedLead) => item.assignmentStatus ? protocolDisplayLabel(LEAD_ASSIGNMENT_STATUS_LABELS, item.assignmentStatus, '未知状态') : '—' },
      { key: 'dispatchMode', title: '分配方式', render: (_: unknown, item: ManagedLead) => item.dispatchMode ? protocolDisplayLabel(LEAD_DISPATCH_MODE_LABELS, item.dispatchMode, '未知方式') : '—' },
      { key: 'assignmentAttemptCount', title: '分配尝试次数', dataIndex: 'assignmentAttemptCount', render: (_, item) => item.assignmentAttemptCount ?? '-' },
      { key: 'publicPoolAt', title: '进入公海时间', dataIndex: 'publicPoolAt', width: 170, render: (_, item) => formatTimestamp(item.publicPoolAt) },
      { key: 'countedAt', title: '计入业绩时间', dataIndex: 'countedAt', width: 170, render: (_, item) => formatTimestamp(item.countedAt) },
      { key: 'firstFollowUpAt', title: '首次跟进时间', dataIndex: 'currentAssignmentFirstFollowUpAt', width: 170, render: (_, item) => formatTimestamp(item.currentAssignmentFirstFollowUpAt) },
      { key: 'firstFollowUpDeadlineAt', title: '首跟截止时间', dataIndex: 'currentAssignmentFirstFollowUpDeadlineAt', width: 170, render: (_, item) => formatTimestamp(item.currentAssignmentFirstFollowUpDeadlineAt) },
      { key: 'qualificationStartedAt', title: '判定开始时间', dataIndex: 'qualificationStartedAt', width: 170, render: (_, item) => formatTimestamp(item.qualificationStartedAt) },
      { key: 'qualificationDeadlineAt', title: '判定截止时间', dataIndex: 'qualificationDeadlineAt', width: 170, render: (_, item) => formatTimestamp(item.qualificationDeadlineAt) },
      { key: 'suspendedAt', title: '挂起时间', dataIndex: 'suspendedAt', width: 170, render: (_, item) => formatTimestamp(item.suspendedAt) },
      { key: 'qualifiedBy', title: '判定人', render: (_: unknown, item: ManagedLead) => item.qualifiedByUserName || '-' },
      { key: 'validDescription', title: '有效说明', dataIndex: 'validDescription', width: 220, ellipsis: true, render: (_, item) => item.validDescription || '-' },
      { key: 'salesOrderSubmittedAt', title: '订单提交时间', dataIndex: 'salesOrderSubmittedAt', width: 170, render: (_, item) => formatTimestamp(item.salesOrderSubmittedAt) },
      { key: 'invalidReason', title: '无效原因', render: (_: unknown, item: ManagedLead) => leadSnapshotLabel(item.invalidReasonLabelSnapshot, item.invalidReason) },
      { key: 'invalidDescription', title: '无效说明', dataIndex: 'invalidDescription', width: 220, ellipsis: true, render: (_, item) => item.invalidDescription || '-' },
      { key: 'appealDeadlineAt', title: '申诉截止时间', dataIndex: 'appealDeadlineAt', width: 170, render: (_, item) => formatTimestamp(item.appealDeadlineAt) },
      { key: 'closedAt', title: '关闭时间', dataIndex: 'closedAt', width: 170, render: (_, item) => formatTimestamp(item.closedAt) },
      { key: 'closeReason', title: '关闭原因', dataIndex: 'closeReason', width: 220, ellipsis: true, render: (_, item) => item.closeReason || '-' },
      { key: 'relationTypes', title: '当前关系', dataIndex: 'relationTypes', render: (_, item) => leadRelationTypesLabel(item.relationTypes) },
      { key: 'nextFollowUpAt', title: '下次跟进时间', dataIndex: 'nextFollowUpAt', width: 170, render: (_, item) => formatTimestamp(item.nextFollowUpAt) },
      { key: 'submittedAt', title: '提交时间', dataIndex: 'submittedAt', width: 170, render: (_, item) => formatTimestamp(item.submittedAt) },
      { key: 'lastActivityAt', title: '最近活动时间', dataIndex: 'lastActivityAt', width: 170, render: (_, item) => formatTimestamp(item.lastActivityAt) },
      { key: 'qualifiedAt', title: '判定时间', dataIndex: 'qualifiedAt', width: 170, render: (_, item) => formatTimestamp(item.qualifiedAt) },
      { key: 'convertedAt', title: '成交转化时间', dataIndex: 'convertedAt', width: 170, render: (_, item) => formatTimestamp(item.convertedAt) },
      { key: 'remark', title: '备注', dataIndex: 'remark', width: 220, ellipsis: true, render: (_, item) => item.remark || '-' },
      { key: 'updateTime', title: '更新时间', dataIndex: 'updateTime', width: 170, render: (_, item) => formatTimestamp(item.updateTime) }
  ]
  const leadTableColumnSource: ProColumns<ManagedLead>[] = [...allLeadTableColumns, {
    key: 'action', title: '操作', width: 88, fixed: 'right' as const, hideInSetting: true,
    render: (_: unknown, item: ManagedLead) => <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => selectLead(item.id)}>详细</Button>
  }]
  const leadTableColumns: ProColumns<ManagedLead>[] = leadTableColumnSource.map(column => {
    const columnKey = String(column.key)
    const backendSortField = LEAD_SORT_FIELD_BY_COLUMN_KEY[columnKey]
    return {
      ...column,
      sorter: partnerId === undefined && backendSortField ? true : undefined,
      sortOrder: backendSortField === sortField ? sortOrder : null,
    }
  })

  const handleLeadTableChange: TableProps<ManagedLead>['onChange'] = (_pagination, _filters, sorter, extra) => {
    if (extra.action !== 'sort') return
    const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter
    const nextOrder = activeSorter?.order || undefined
    const nextSortField = nextOrder
      ? LEAD_SORT_FIELD_BY_COLUMN_KEY[String(activeSorter.columnKey)]
      : undefined
    clearLeadSelection()
    setSortField(nextSortField)
    setSortOrder(nextSortField ? nextOrder : undefined)
  }

  const scopedBatchEligibility = (action: LeadBatchFormAction) => supervisorLeadSelectionEligibility(
    ({ 'release-claim-pool': 'claimPool', 'release-public-sea': 'publicSea', transfer: 'transfer', restore: 'restore', recycle: 'recycle' } as Record<LeadBatchFormAction, SupervisorLeadBatchAction>)[action],
    selectedRowKeys.length, selectedRowKeys.map(id => selectedLeadMap.get(Number(id))).filter((row): row is ManagedLead => Boolean(row))
  )
  const openBatchAction = async (action: LeadBatchFormAction) => {
    if (operationsReadOnly || !selectedRowKeys.length || (salesUserId !== undefined && (initialLoading || initialError || !scopedBatchEligibility(action).allowed))) return
    setBatchAction(action)
    batchIdempotencyKey.current = createIdempotencyKey()
    batchForm.resetFields()
    if (action === 'transfer' || action === 'release-public-sea') {
      try {
        try { setBatchCandidates(salesUserId !== undefined
          ? action === 'transfer' ? await api.subordinateBatchTransferCandidates(selectedRowKeys.map(Number)) : await api.subordinateTransferCandidates()
          : await api.ownerTransferCandidates()) }
        catch (error) { if (salesUserId !== undefined) throw error; setBatchCandidates(await api.subordinateTransferCandidates()) }
      }
      catch (error) { setBatchCandidates([]); message.error(error instanceof Error ? error.message : '销售候选加载失败') }
    }
  }

  const submitBatchAction = async () => {
    if (operationsReadOnly || !batchAction || (salesUserId !== undefined && (initialLoading || initialError || !scopedBatchEligibility(batchAction).allowed))) return
    const values = await batchForm.validateFields()
    setBatchSaving(true)
    try {
      const execute = salesUserId === undefined ? api.batchLeadAction : api.batchSupervisorLeadAction
      const result = await execute(batchAction, selectedRowKeys.map(Number), {
        reason: values.reason?.trim() || '', targetUserId: values.targetUserId, collaboratorUserId: values.collaboratorUserId,
        idempotencyKey: batchIdempotencyKey.current || createIdempotencyKey(),
      })
      onScopeChanged?.()
      setBatchResult(result); setBatchResultOpen(true); setBatchAction(undefined); clearLeadSelection(); batchIdempotencyKey.current = undefined
      const version = ++requestVersion.current
      await loadPage(1, true, version, { silent: true })
      if (selectedId) await loadDetail(selectedId, true)
    } catch (error) { message.error(error instanceof Error ? error.message : '批量操作失败') }
    finally { setBatchSaving(false) }
  }

  const updateLeadSelection = useCallback((keys: React.Key[], rows: ManagedLead[]) => {
    const limitedKeys = keys.slice(0, 100)
    if (keys.length > 100) message.warning('最多选择 100 条客资')
    const ids = new Set(limitedKeys.map(Number))
    setSelectedRowKeys(limitedKeys)
    setSelectedLeadMap(current => {
      const next = new Map([...current].filter(([id]) => ids.has(id)))
      rows.filter(row => ids.has(row.id)).forEach(row => next.set(row.id, row))
      return next
    })
  }, [])

  const leadRowSelection: TableRowSelection<ManagedLead> = {
    selectedRowKeys,
    preserveSelectedRowKeys: true,
    onChange: updateLeadSelection,
  }

  const batchMenuItems = LEAD_BATCH_ACTIONS
    .filter(() => partnerId === undefined && !operationsReadOnly)
    .filter(action => salesUserId === undefined
      ? action.permissions.some(permission => permissions.includes(permission))
      : permissions.includes(`zsjos:subordinate-sales:lead-${action.type}`))
    .map(action => ({
      key: action.type,
      icon: action.icon,
      label: action.label.replace('批量', ''),
      danger: action.danger,
      disabled: salesUserId !== undefined && (initialLoading || Boolean(initialError) || !scopedBatchEligibility(action.type).allowed),
      title: salesUserId !== undefined ? scopedBatchEligibility(action.type).message : undefined,
      onClick: () => void openBatchAction(action.type),
    }))

  if (detailOnly) {
    return <section className="workspace-page lead-management-page lead-management-detail-only">
      <main className="lead-inbox-detail-pane">{detailContent}</main>
    </section>
  }

  return <><section className={`workspace-page lead-management-page${useTableLayout ? ' lead-management-table-page' : ''}${embedded ? ' lead-management-embedded' : ''}`}>
    <header className="lead-simple-status-shell" role="group" aria-label="客资分级筛选">
      {returnTo && <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate(returnTo)}>返回订单审批</Button>}
      {filterProfileError && <Space><Typography.Text type="secondary">{filterProfileError}</Typography.Text><Button size="small" onClick={() => void loadFilterProfile()}>重试筛选方案</Button></Space>}
    </header>
    {activeGroup && <>
      <div className="lead-filter-sections">
        <div className="lead-filter-row" role="group" aria-label="客资业务归类">
          <span className="lead-filter-row-label">业务归类</span>
          {filterProfile.groups.map(group => <button
            type="button"
            key={group.key}
            className={inboxGroup === group.key ? 'active' : ''}
            aria-pressed={inboxGroup === group.key}
            onClick={() => { setInboxGroup(group.key); setSectionOptions({}) }}
          >{group.label}</button>)}
        </div>
        {activeGroup.sections.map(section => <div
          className="lead-filter-row"
          role="group"
          key={section.key}
          aria-label={section.label}
        >
          <span className="lead-filter-row-label">{section.label}</span>
          {section.options.map(option => <button
            type="button"
            key={`${section.key}:${option.key}`}
            className={(sectionOptions[section.key] ?? LEAD_FILTER_ALL) === option.key ? 'active' : ''}
            aria-pressed={(sectionOptions[section.key] ?? LEAD_FILTER_ALL) === option.key}
            onClick={() => setSectionOptions(current => ({ ...current, [section.key]: option.key }))}
          >{option.label}</button>)}
        </div>)}
      </div>
      <div className="lead-filter-actions">
        <Button icon={<ReloadOutlined/>} onClick={refreshPage}>刷新</Button>
      </div>
    </>}
    <div className={useTableLayout ? 'lead-management-table-shell' : `lead-inbox-layout inbox-avatar-layout${avatarRail.collapsed ? ' is-avatar-collapsed' : ''}`}>
      {useTableLayout ? <>
        <BusinessTable<ManagedLead> tableKey={operationsReadOnly ? 'my-operations-leads' : partnerId !== undefined ? 'partner-leads' : salesUserId === undefined ? 'lead-management-page-1' : 'subordinate-sales-leads'}
          className="lead-management-table"
          rowKey="id"

          batchActions={partnerId === undefined && !operationsReadOnly && <Dropdown menu={{ items: batchMenuItems }} disabled={!selectedRowKeys.length || !batchMenuItems.length}>
            <Button icon={<DownOutlined />} disabled={!selectedRowKeys.length || !batchMenuItems.length}>批量操作</Button>
          </Dropdown>}
          filters={partnerId !== undefined ? <Input.Search allowClear placeholder="搜索客资" onSearch={setKeyword}/> : <AdvancedFilterToolbar scene="lead" pageKey={operationsReadOnly ? 'my_operations_leads' : salesUserId === undefined ? 'lead_management' : 'subordinate_sales_leads'} placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/>}
          onReload={refreshPage}
          error={initialError}
          unauthorized={Boolean(initialError && isLeadInboxUnauthorized(initialError))}
          widthPersistenceKey={operationsReadOnly ? 'crm-my-operations-leads-widths' : partnerId !== undefined ? 'crm-partner-leads-widths' : salesUserId === undefined ? 'crm-lead-management-table-column-widths' : 'crm-subordinate-sales-leads-column-widths'}
          columnsState={{ persistenceKey: operationsReadOnly ? 'crm-my-operations-leads-columns' : partnerId !== undefined ? 'crm-partner-leads-columns' : salesUserId === undefined ? 'crm-lead-management-table-columns' : 'crm-subordinate-sales-leads-columns', persistenceType: 'localStorage' }}
          loading={initialLoading}
          dataSource={items}
          pagination={{
            current: pageNo,
            pageSize: leadPageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            onChange: (nextPage, nextPageSize) => {
              const sizeChanged = nextPageSize !== leadPageSize
              if (sizeChanged) setLeadPageSize(nextPageSize)
              void loadPage(sizeChanged ? 1 : nextPage, true, ++requestVersion.current,
                sizeChanged ? { pageSize: nextPageSize } : undefined)
            },
          }}
          locale={{ emptyText: initialError ? '客资列表加载失败' : '当前筛选下暂无客资' }}
          columns={partnerId === undefined ? leadTableColumns : [...leadTableColumns,
            { title: '提交时归属', key: 'partnerOwnerSnapshot', width: 140, render: (_, lead) => lead.partnerOwnerNameSnapshot || '未记录' },
          ]}
          onChange={handleLeadTableChange}
          rowSelection={partnerId === undefined && !operationsReadOnly ? leadRowSelection : undefined}

        />
      </> : <>
      <aside className="lead-inbox-list-pane">
        <div className="inbox-avatar-toolbar lead-inbox-controls"><InboxAvatarControls label="客资" listId="lead-avatar-list" collapsed={avatarRail.collapsed} filtered={Boolean(keyword || advancedFilter?.conditions.length || advancedFilter?.groups.length)} onChange={avatarRail.change} />
        <div className="inbox-avatar-filters" ref={avatarRail.filterRef} hidden={avatarRail.collapsed}><div className="lead-inbox-toolbar">
          <AdvancedFilterToolbar scene="lead" pageKey={operationsReadOnly ? 'my_operations_leads' : salesUserId === undefined ? 'lead_management' : 'subordinate_sales_leads'} placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/>
          <LeadInboxSortControls field={sortField} order={sortOrder} submissionOnly={salesUserId !== undefined}
            onChange={(field, order) => {
              clearLeadSelection()
              setSortField(field)
              setSortOrder(order)
            }} />
          <div className="lead-inbox-result-count" role="status" aria-live="polite">
            {initialLoading ? <Typography.Text type="secondary">正在统计…</Typography.Text>
              : initialError ? <Typography.Text type="secondary">数量暂不可用</Typography.Text>
                : <><span>当前结果 <strong>{total}</strong> 条</span><Typography.Text type="secondary">已加载 {items.length} 条</Typography.Text></>}
          </div>
        </div></div></div>
        {initialError && (avatarRail.collapsed ? <InboxAvatarError message={initialError} expand={() => avatarRail.change(false)} retry={isLeadInboxUnauthorized(initialError) ? undefined : () => void loadPage(1, true, requestVersion.current)} /> : <Alert className="lead-list-error" type={isLeadInboxUnauthorized(initialError) ? 'warning' : 'error'} showIcon
          message={isLeadInboxUnauthorized(initialError) ? '无权查看客资收件箱' : '客资列表加载失败'} description={initialError}
          action={!isLeadInboxUnauthorized(initialError) ? <Button size="small" onClick={() => void loadPage(1, true, requestVersion.current)}>重试</Button> : undefined}/>)}
        <div id="lead-avatar-list" aria-busy={initialLoading || loadingMore} ref={listScrollRef} className="lead-inbox-scroll">
          {initialLoading ? <div className="lead-list-skeletons">
            {Array.from({ length: 5 }, (_, index) => <div className="lead-inbox-item" key={index}>{avatarRail.collapsed ? <Skeleton.Avatar active size={36} /> : <Skeleton active avatar paragraph={{ rows: 2 }}/>}</div>)}
          </div> : !items.length && !initialError ? (avatarRail.collapsed ? <span className="inbox-avatar-empty" role="status">暂无客资</span> : <Empty description="当前筛选下暂无客资"/>) : items.map(item => {
            const active = item.id === selectedId
            const unseen = !active && unseenIds.includes(item.id)
            const pending = Boolean(item.pendingTasks?.length)
            const reminder = pending ? ` · 待处理：${leadTaskReminderText(item)}` : ''
            return <Tooltip key={item.id} title={avatarRail.collapsed ? `${item.submittedName || '未填写姓名'} · ${item.leadNo || '暂无客资编号'}${unseen ? ' · 新客资' : ''}${reminder}` : undefined} trigger={['hover', 'focus']}><button type="button" aria-label={`${item.submittedName || '未填写姓名'} · ${item.leadNo || '暂无客资编号'}${unseen ? ' · 新客资' : ''}${reminder}`} aria-current={active ? 'true' : undefined}
              className={['lead-inbox-item', active && 'active', unseen && 'unseen', pending && 'has-pending-tasks'].filter(Boolean).join(' ')}
              onClick={() => selectLead(item.id)}>
              {pending && <span className="lead-task-border" aria-hidden="true" />}
              <div className="lead-inbox-item-main">
                <NameAvatar name={item.submittedName} seed={item.leadNo} size={36} />
                {unseen && avatarRail.collapsed && <span className="inbox-avatar-unseen" aria-hidden="true" />}
                <div className="lead-inbox-item-copy">
                  <div className="lead-inbox-item-title">
                    {/* 标签与姓名同级：塞进 strong 会被姓名的 ellipsis 一起裁掉 */}
                    <span className="lead-inbox-item-name">
                      <strong>{item.submittedName}</strong>
                      {unseen && <Tag className="lead-inbox-new-tag" color="error" bordered={false}>新</Tag>}
                    </span>
                    <LeadStateTags lead={item}/>
                  </div>
                  <span>{item.leadNo}</span>
                  <span>{productText(item)}</span>
                  <span>{item.submittedMobile || '无手机号'} · {item.submittedWechatId || '无微信号'}</span>
                </div>
              </div>
              {pending && !avatarRail.collapsed && <LeadTaskIndicator lead={item} />}
              <div className="lead-inbox-item-meta"><Badge status="processing"/><span>{item.salesStageLabelSnapshot || '未记录'} · {channelLabel(item.sourceChannel)} · {leadSnapshotLabel(item.leadCategoryLabelSnapshot, item.leadCategory)} · {formatTimestamp(item.submittedAt)}</span></div>
            </button></Tooltip>
          })}
          {!initialLoading && items.length > 0 && <div ref={listSentinelRef} className="lead-list-sentinel">
            {loadMoreError
              ? avatarRail.collapsed ? <InboxAvatarError message={loadMoreError} expand={() => avatarRail.change(false)} retry={() => void loadPage(pageNo + 1, false, requestVersion.current)} /> : <Alert type="error" showIcon message="更多客资加载失败" description={loadMoreError}
                action={<Button size="small" onClick={() => void loadPage(pageNo + 1, false, requestVersion.current)}>重试</Button>}/>
              : loadingMore
                ? <div className="lead-list-loading" aria-label="加载更多客资"><Spin size="small"/>{!avatarRail.collapsed && ' 加载中'}</div>
                : hasMore
                  ? <Typography.Text type="secondary">{avatarRail.collapsed ? '更多' : '继续下滑加载'}</Typography.Text>
                  : <Typography.Text type="secondary" className="lead-list-end">{avatarRail.collapsed ? `共${total}条` : `已加载全部 ${total} 条客资`}</Typography.Text>}
          </div>}
        </div>
      </aside>
      <main className="lead-inbox-detail-pane">{detailContent}</main>
      </>}
    </div>
  </section>
  <Modal open={Boolean(batchAction)} title={batchAction ? `批量${LEAD_BATCH_ACTION_LABELS[batchAction]}客资` : undefined} confirmLoading={batchSaving} onOk={() => void submitBatchAction()} onCancel={() => setBatchAction(undefined)}>
    {batchAction && <>
      <Alert type="info" showIcon message={`已选择 ${selectedRowKeys.length} 条客资，后端将逐条校验并返回成功或失败结果。`} style={{ marginBottom: 16 }} />
      <Form form={batchForm} layout="vertical">
        {batchAction === 'transfer' && <Form.Item name="targetUserId" label="目标销售" rules={[{ required: true, message: '请选择目标销售' }]}><Select showSearch optionFilterProp="label" options={batchCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>}
        {batchAction === 'release-public-sea' && <Form.Item name="collaboratorUserId" label="公海跟进销售（可不填）"><Select allowClear showSearch optionFilterProp="label" options={batchCandidates.map(user => ({ value: user.id, label: user.nickname }))} /></Form.Item>}
        <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }, { max: 500, message: '最多 500 个字符' }]}><Input.TextArea rows={4} maxLength={500} showCount /></Form.Item>
      </Form>
    </>}
  </Modal>
  <LeadBatchResultModal result={batchResult} open={batchResultOpen} onClose={() => setBatchResultOpen(false)} />
  <ResizableDetailDrawer
    desktopResizable={useTableLayout}
    className="lead-inbox-mobile-drawer"
    title={detail?.submittedName || '客资详情'}
    placement={useTableLayout ? 'right' : 'bottom'}
    height={useTableLayout ? undefined : '82vh'}
    open={drawerOpen}
    onClose={() => setDrawerOpen(false)}
    destroyOnClose={false}
  >
    {detailContent}
  </ResizableDetailDrawer>
  </>
}
