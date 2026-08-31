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
  Typography
} from 'antd'
import { ArrowLeftOutlined, DeleteOutlined, DownOutlined, ExportOutlined, EyeOutlined, ReloadOutlined, RollbackOutlined, SwapOutlined } from '@ant-design/icons'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { api, type AdvancedFilterGroup, type AssignmentUser, type DictData, type LeadBatchAction, type LeadSimpleStatus, type LeadSortField, type ManagedLead, type ManagedLeadPageParams, type SubordinateBatchResult } from '../services/api'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'
import { NameAvatar } from '../components/LeadDetailOverview'
import LeadDetail from '../components/LeadDetail'
import {
  dictionaryDisplayLabel,
  hasNextLeadInboxPage,
  isLeadInboxUnauthorized,
  mergeUniqueLeads,
  pinLeadFirst,
  prioritizeLeads,
  protocolDisplayLabel,
  resolveLeadSelection,
  snapshotOrDictionaryDisplayLabel,
  tryStartLeadPageRequest
} from '../services/leadManagement'
import {
  DICT_TYPE,
  LEAD_ASSIGNMENT_STATUS_LABELS,
  LEAD_DISPATCH_MODE_LABELS,
  LEAD_HANDLING_STAGE_LABELS,
  LEAD_OPERATIONAL_STATUS_LABELS,
  LEAD_QUALIFICATION_STATUS_LABELS,
  LEAD_FOLLOW_UP_STATUS_LABELS
} from '../constants'
import { parseLeadDetailTab, shouldBlockLeadSwitch } from '../services/leadFollowUp'
import { formatTimestamp } from '../services/time'
import { useRealtimeEvent } from '../components/RealtimeProvider'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
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
const LEAD_TABLE_COLUMN_MIN_WIDTH = 80
const LEAD_TABLE_COLUMN_WIDTHS_KEY = 'crm-lead-management-table-column-widths'
type LeadAudience = 'all'
type LeadSimpleStatusSelection = 'all' | LeadSimpleStatus
type LeadPageLoadOptions = { preferredSelectedId?: number; silent?: boolean; pageSize?: number }
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

function clampLeadTableColumnWidth(width: number) {
  return Math.max(LEAD_TABLE_COLUMN_MIN_WIDTH, Math.round(width))
}

function readLeadTableColumnWidths(): Record<string, number> {
  try {
    const stored = window.localStorage.getItem(LEAD_TABLE_COLUMN_WIDTHS_KEY)
    if (!stored) return {}
    const parsed = JSON.parse(stored) as Record<string, unknown>
    return Object.fromEntries(Object.entries(parsed)
      .filter((entry): entry is [string, number] => Number.isFinite(entry[1]))
      .map(([key, width]) => [key, clampLeadTableColumnWidth(width)]))
  } catch {
    return {}
  }
}

function isLeadTableResizeEdge(element: HTMLElement, clientX: number) {
  return element.getBoundingClientRect().right - clientX <= 12
}

function startLeadTableColumnResize(event: React.PointerEvent<HTMLElement>, width: number,
                                    onResize: (width: number) => void) {
  if (!isLeadTableResizeEdge(event.currentTarget, event.clientX)) return
  event.preventDefault()
  event.stopPropagation()
  const startX = event.clientX
  const startWidth = width
  const handlePointerMove = (moveEvent: PointerEvent) => {
    onResize(clampLeadTableColumnWidth(startWidth + moveEvent.clientX - startX))
  }
  const finishResize = () => {
    window.removeEventListener('pointermove', handlePointerMove)
    window.removeEventListener('pointerup', finishResize)
    window.removeEventListener('pointercancel', finishResize)
  }
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', finishResize)
  window.addEventListener('pointercancel', finishResize)
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

const SIMPLE_STATUS_OPTIONS: Array<{ key: LeadSimpleStatusSelection; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'first_follow_pending', label: '待首跟' },
  { key: 'following', label: '待跟进' },
  { key: 'qualification_pending', label: '待判定' },
  { key: 'deal_pending_approval', label: '成交待审核' },
  { key: 'won', label: '已成交' },
  { key: 'invalid', label: '已判无效' },
  { key: 'closed', label: '已关闭' },
  { key: 'suspended', label: '已挂起' }
]

function productText(lead: ManagedLead) {
  const product = lead.primaryProduct
  return product ? [product.spuName || '未明确课程', product.skuName].filter(Boolean).join(' / ') : '未填写意向产品'
}

function LeadStateTags({ lead }: { lead: ManagedLead }) {
  return <Space size={4}>
    <Tag color={lead.qualificationStatus === 'invalid' ? 'red' : lead.qualificationStatus === 'valid' ? 'green' : 'gold'}>
      {protocolDisplayLabel(LEAD_QUALIFICATION_STATUS_LABELS, lead.qualificationStatus, '未知有效状态')}
    </Tag>
    {lead.followUpStatus && <Tag color="blue">{protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, lead.followUpStatus, '未知跟进状态')}</Tag>}
    {lead.operationalStatus === 'suspended' && <Tag color="orange">已挂起</Tag>}
  </Space>
}

export default function LeadManagementPage({ permissions, detailOnly = false }: { permissions: string[]; detailOnly?: boolean }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const routeState = location.state as { leadId?: number; leadNo?: string; openFollowUp?: boolean; relationScope?: 'submitted' | 'owned' } | null
  const queryLeadId = Number(searchParams.get('leadId')) || undefined
  const requestedLeadNo = routeState?.leadNo || searchParams.get('leadNo') || undefined
  const screens = Grid.useBreakpoint()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const { useTableLayout } = useInboxTableLayout()
  const requestedLeadId = routeState?.leadId || queryLeadId
  const requestedTab = parseLeadDetailTab(searchParams.get('tab'))
    || (routeState?.openFollowUp ? 'follow-ups' : undefined)
  const returnToValue = searchParams.get('returnTo')
  const returnTo = returnToValue?.startsWith('/zsjos/sales-order-approvals') ? returnToValue : undefined
  const audience: LeadAudience = 'all'
  const [items, setItems] = useState<ManagedLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [leadPageSize, setLeadPageSize] = useState(PAGE_SIZE)
  const [sortField, setSortField] = useState<LeadSortField>()
  const [sortOrder, setSortOrder] = useState<'ascend' | 'descend'>()
  const [columnWidths, setColumnWidths] = useState<Record<string, number>>(readLeadTableColumnWidths)
  const [initialLoading, setInitialLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [initialError, setInitialError] = useState('')
  const [loadMoreError, setLoadMoreError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [simpleStatus, setSimpleStatus] = useState<LeadSimpleStatusSelection>('all')
  const [categories, setCategories] = useState<DictData[]>([])
  const [channels, setChannels] = useState<DictData[]>([])
  const [categoryError, setCategoryError] = useState(false)
  const [channelError, setChannelError] = useState(false)
  const [selectedId, setSelectedId] = useState<number | undefined>(requestedLeadId)
  const [detail, setDetail] = useState<ManagedLead>()
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
  const unseenIdsRef = useRef(unseenIds)
  const listScrollRef = useRef<HTMLDivElement>(null)
  const listSentinelRef = useRef<HTMLDivElement>(null)
  const itemIdsRef = useRef<number[]>([])

  const clearLeadSelection = useCallback(() => {
    setSelectedRowKeys([])
    setSelectedLeadMap(new Map())
  }, [])

  useEffect(() => { clearLeadSelection() }, [advancedFilter, clearLeadSelection, keyword, simpleStatus])
  useEffect(() => {
    try {
      window.localStorage.setItem(LEAD_TABLE_COLUMN_WIDTHS_KEY, JSON.stringify(columnWidths))
    } catch {
      // Table resizing remains usable when browser storage is unavailable.
    }
  }, [columnWidths])

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
        pageNo: targetPage,
        pageSize: options.pageSize ?? leadPageSize,
        keyword: keyword || undefined, advancedFilter,
        relationScope: routeState?.relationScope,
        simpleStatus: simpleStatus === 'all' ? undefined : simpleStatus,
        sortField,
        sortOrder,
      }
      const result = audience === 'all'
        ? await api.allLeadPage(params)
        : await api.managedLeadInboxPage(audience, params)
      if (version !== requestVersion.current) return
      setItems(current => {
        const previousPinned = routeSelectionRef.current === undefined
          ? undefined
          : current.find(item => item.id === routeSelectionRef.current)
        const next = prioritizeLeads(
          replace ? result.list : mergeUniqueLeads(current, result.list),
          [...unseenIdsRef.current].reverse()
        )
        return previousPinned && !next.some(item => item.id === previousPinned.id)
          ? pinLeadFirst(next, previousPinned)
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
      if (replace) setSelectedId(current => resolveLeadSelection(result.list, {
        preferredId: options.preferredSelectedId,
        currentId: current,
        requestedId: routeSelectionRef.current ?? requestedLeadId,
        preserveRequestedId: routeSelectionRef.current !== undefined
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
  }, [advancedFilter, audience, keyword, leadPageSize, requestedLeadId, routeState?.relationScope, simpleStatus, sortField, sortOrder])

  useEffect(() => { void loadMetadata() }, [loadMetadata])
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
    if (requestedLeadId && window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
  }, [requestedLeadId])
  // 视口拉宽回到桌面端时，关闭移动端详情抽屉、回到双栏布局
  useEffect(() => { if (screens.md) setDrawerOpen(false) }, [screens.md])

  const loadDetail = useCallback(async (id: number, silent = false) => {
    if (!silent) setDetailLoading(true)
    setDetailError('')
    try {
      const loaded = await api.managedLead(id)
      setDetail(loaded)
      setItems(current => current.some(item => item.id === id) ? current : pinLeadFirst(current, loaded))
    } catch (loadError) {
      setDetail(undefined)
      setDetailError(loadError instanceof Error ? loadError.message : '客资详情加载失败')
    } finally {
      if (!silent) setDetailLoading(false)
    }
  }, [])

  const loadDetailByNo = useCallback(async (leadNo: string) => {
    setDetailLoading(true)
    setDetailError('')
    try {
      const loaded = await api.managedLeadByNo(leadNo)
      setSelectedId(loaded.id)
      setDetail(loaded)
      setItems(current => current.some(item => item.id === loaded.id) ? current : pinLeadFirst(current, loaded))
    } catch (loadError) {
      setDetail(undefined)
      setDetailError(loadError instanceof Error ? loadError.message : '客资详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }, [])

  useEffect(() => {
    if (selectedId) void loadDetail(selectedId)
    else setDetail(undefined)
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
    const version = ++requestVersion.current
    if (listScrollRef.current) listScrollRef.current.scrollTop = 0
    const [, refreshedItems] = await Promise.all([
      loadMetadata(),
      loadPage(1, true, version, { preferredSelectedId: id, silent: true })
    ])
    if (!refreshedItems || refreshedItems.some(item => item.id === id)) await loadDetail(id, true)
  }, [loadDetail, loadMetadata, loadPage])

  useEffect(() => { itemIdsRef.current = items.map(item => item.id) }, [items])

  /**
   * 接单后把新客资拉进列表。
   * 接单与列表查询是两次请求，后端写入对读可见有延迟，所以按节奏重试直到新客资出现。
   */
  const refreshUntilVisible = useCallback(async (leadId?: number) => {
    for (const delay of LEAD_INBOX_REFRESH_RETRY_DELAYS_MS) {
      if (delay > 0) await new Promise(resolve => window.setTimeout(resolve, delay))
      const version = ++requestVersion.current
      await Promise.all([loadMetadata(), loadPage(1, true, version)])
      if (leadId == null || itemIdsRef.current.includes(leadId)) return
    }
    if (leadId != null && !itemIdsRef.current.includes(leadId)) {
      try {
        const loaded = await api.managedLead(leadId)
        setItems(current => pinLeadFirst(current, loaded))
      } catch {
        // The list refresh remains best effort; object authorization is enforced by managedLead.
      }
    }
  }, [loadMetadata, loadPage])

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
    }, { root, rootMargin: '240px 0px', threshold: 0 })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, initialLoading, loadMoreError, loadPage, loadingMore, pageNo])

  const selectLead = (id: number) => {
    if (shouldBlockLeadSwitch(followUpDirty) && !window.confirm('当前表单尚未提交，切换客资将丢失已填写内容。确定继续吗？')) return
    setFollowUpDirty(false)
    routeSelectionRef.current = undefined
    setSelectedId(id)
    if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
  }
  const detailContent = detailLoading
    ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError
      ? <Alert type="error" showIcon message={detailError} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail
        ? <LeadDetail lead={detail} categories={categories} categoryLabel={categoryLabel} channelLabel={channelLabel}
          mode={audience} autoExpandFollowUp={Boolean(routeState?.openFollowUp && requestedLeadId === detail.id)}
          initialTab={requestedLeadId === detail.id ? requestedTab : undefined}
          onDirtyChange={setFollowUpDirty} onChanged={() => void refreshAfterLeadChange(detail.id)} hideProviderOwner/>
        : <Empty description="从左侧选择一条客资"/>

  const allLeadTableColumns: ProColumns<ManagedLead>[] = [
      { key: 'leadNo', title: '客资编号', dataIndex: 'leadNo', width: 170 },
      { key: 'submittedName', title: '姓名', dataIndex: 'submittedName', width: 130 },
      { key: 'submittedMobile', title: '手机号', dataIndex: 'submittedMobile', width: 130, render: (_, item) => item.submittedMobile || '-' },
      { key: 'submittedWechatId', title: '微信号', dataIndex: 'submittedWechatId', width: 140, render: (_, item) => item.submittedWechatId || '-' },
      { key: 'source', title: '来源', render: (_: unknown, item: ManagedLead) => item.sourceLabel || item.sourceType || '-' },
      { key: 'sourceUser', title: '提交人', render: (_: unknown, item: ManagedLead) => item.sourceUserName || '-' },
      { key: 'owner', title: '所属销售', render: (_: unknown, item: ManagedLead) => item.ownerUserName || '-' },
      { key: 'category', title: '分类', render: (_: unknown, item: ManagedLead) => snapshotOrDictionaryDisplayLabel(item.leadCategoryLabelSnapshot, categories, item.leadCategory, categoryError) },
      { key: 'channel', title: '渠道', render: (_: unknown, item: ManagedLead) => channelLabel(item.sourceChannel) },
      { key: 'region', title: '地区', render: (_: unknown, item: ManagedLead) => [item.provinceName, item.cityName].filter(Boolean).join(' / ') || '-' },
      { key: 'product', title: '意向产品', render: (_: unknown, item: ManagedLead) => productText(item) },
      { key: 'qualificationStatus', title: '有效性状态', render: (_: unknown, item: ManagedLead) => protocolDisplayLabel(LEAD_QUALIFICATION_STATUS_LABELS, item.qualificationStatus, '未知') },
      { key: 'followUpStatus', title: '跟进状态', render: (_: unknown, item: ManagedLead) => item.followUpStatus ? protocolDisplayLabel(LEAD_FOLLOW_UP_STATUS_LABELS, item.followUpStatus, '未知') : '-' },
      { key: 'operationalStatus', title: '运营状态', render: (_: unknown, item: ManagedLead) => protocolDisplayLabel(LEAD_OPERATIONAL_STATUS_LABELS, item.operationalStatus, item.operationalStatus || '-') },
      { key: 'handlingStage', title: '处理阶段', render: (_: unknown, item: ManagedLead) => protocolDisplayLabel(LEAD_HANDLING_STAGE_LABELS, item.handlingStage, item.handlingStage || '-') },
      { key: 'assignmentStatus', title: '分配状态', render: (_: unknown, item: ManagedLead) => protocolDisplayLabel(LEAD_ASSIGNMENT_STATUS_LABELS, item.assignmentStatus, item.assignmentStatus || '-') },
      { key: 'dispatchMode', title: '分配方式', render: (_: unknown, item: ManagedLead) => protocolDisplayLabel(LEAD_DISPATCH_MODE_LABELS, item.dispatchMode, item.dispatchMode || '-') },
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
      { key: 'invalidReason', title: '无效原因', render: (_: unknown, item: ManagedLead) => item.invalidReasonLabelSnapshot || item.invalidReason || '-' },
      { key: 'invalidDescription', title: '无效说明', dataIndex: 'invalidDescription', width: 220, ellipsis: true, render: (_, item) => item.invalidDescription || '-' },
      { key: 'appealDeadlineAt', title: '申诉截止时间', dataIndex: 'appealDeadlineAt', width: 170, render: (_, item) => formatTimestamp(item.appealDeadlineAt) },
      { key: 'closedAt', title: '关闭时间', dataIndex: 'closedAt', width: 170, render: (_, item) => formatTimestamp(item.closedAt) },
      { key: 'closeReason', title: '关闭原因', dataIndex: 'closeReason', width: 220, ellipsis: true, render: (_, item) => item.closeReason || '-' },
      { key: 'relationTypes', title: '当前关系', dataIndex: 'relationTypes', render: (_, item) => item.relationTypes?.join(' / ') || '-' },
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
    const width = columnWidths[columnKey] ?? Number(column.width ?? 140)
    return {
      ...column,
      width,
      ellipsis: column.ellipsis ?? true,
      sorter: backendSortField ? true : undefined,
      sortOrder: backendSortField === sortField ? sortOrder : null,
      onHeaderCell: columnKey === 'action' ? column.onHeaderCell : () => ({
        className: 'lead-table-resizable-header',
        onPointerMove: event => {
          event.currentTarget.style.cursor = isLeadTableResizeEdge(event.currentTarget, event.clientX)
            ? 'col-resize'
            : ''
        },
        onPointerLeave: event => { event.currentTarget.style.cursor = '' },
        onPointerDownCapture: event => startLeadTableColumnResize(event, width, nextWidth => {
          setColumnWidths(current => ({ ...current, [columnKey]: nextWidth }))
        }),
        onClickCapture: event => {
          if (!isLeadTableResizeEdge(event.currentTarget, event.clientX)) return
          event.preventDefault()
          event.stopPropagation()
        },
      }),
    }
  })
  const leadTableScrollWidth = leadTableColumns.reduce((totalWidth, column) => totalWidth + Number(column.width ?? 140), 0)

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

  const openBatchAction = async (action: LeadBatchFormAction) => {
    if (!selectedRowKeys.length) return
    setBatchAction(action)
    batchIdempotencyKey.current = crypto.randomUUID()
    batchForm.resetFields()
    if (action === 'transfer' || action === 'release-public-sea') {
      try {
        try { setBatchCandidates(await api.ownerTransferCandidates()) }
        catch { setBatchCandidates(await api.subordinateTransferCandidates()) }
      }
      catch (error) { setBatchCandidates([]); message.error(error instanceof Error ? error.message : '销售候选加载失败') }
    }
  }

  const submitBatchAction = async () => {
    if (!batchAction) return
    const values = await batchForm.validateFields()
    setBatchSaving(true)
    try {
      const result = await api.batchLeadAction(batchAction, selectedRowKeys.map(Number), {
        reason: values.reason?.trim() || '', targetUserId: values.targetUserId, collaboratorUserId: values.collaboratorUserId,
        idempotencyKey: batchIdempotencyKey.current || crypto.randomUUID(),
      })
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
    .filter(action => action.permissions.some(permission => permissions.includes(permission)))
    .map(action => ({
      key: action.type,
      icon: action.icon,
      label: action.label.replace('批量', ''),
      danger: action.danger,
      onClick: () => void openBatchAction(action.type),
    }))

  if (detailOnly) {
    return <section className="workspace-page lead-management-page lead-management-detail-only">
      <main className="lead-inbox-detail-pane">{detailContent}</main>
    </section>
  }

  return <><section className={`workspace-page lead-management-page${useTableLayout ? ' lead-management-table-page' : ''}`}>
    <header className="lead-simple-status-shell" role="group" aria-label="客资状态筛选">
      {returnTo && <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate(returnTo)}>返回订单审批</Button>}
      {SIMPLE_STATUS_OPTIONS.map(option => <button
        type="button"
        key={option.key}
        className={simpleStatus === option.key ? 'active' : ''}
        aria-pressed={simpleStatus === option.key}
        onClick={() => setSimpleStatus(option.key)}
      >{option.label}</button>)}
      <Button icon={<ReloadOutlined/>} onClick={() => { void loadMetadata(); void loadPage(1, true, ++requestVersion.current); if (selectedId) void loadDetail(selectedId, true) }}>刷新</Button>
    </header>
    <div className={useTableLayout ? 'lead-management-table-shell' : 'lead-inbox-layout'}>
      {useTableLayout ? <>
        <ProTable<ManagedLead>
          className="lead-management-table"
          rowKey="id"
          search={false}
          toolBarRender={() => [
            <div key="lead-table-toolbar-left" className="lead-management-table-toolbar-left">
              <Space className="lead-management-batch-toolbar" wrap>
                <Typography.Text type="secondary">已选 {selectedRowKeys.length} 条</Typography.Text>
                <Dropdown menu={{ items: batchMenuItems }} disabled={!selectedRowKeys.length || !batchMenuItems.length}>
                  <Button icon={<DownOutlined />} disabled={!selectedRowKeys.length || !batchMenuItems.length}>批量操作</Button>
                </Dropdown>
              </Space>
              <div className="lead-management-table-filter-toolbar">
                <AdvancedFilterToolbar scene="lead" pageKey="lead_management" placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/>
              </div>
            </div>
          ]}
          options={{ density: true, fullScreen: true, setting: true, reload: () => { void loadMetadata(); void loadPage(1, true, ++requestVersion.current) } }}
          columnsState={{ persistenceKey: 'crm-lead-management-table-columns', persistenceType: 'localStorage' }}
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
          scroll={{ x: leadTableScrollWidth }}
          locale={{ emptyText: initialError ? '客资列表加载失败' : '当前筛选下暂无客资' }}
          columns={leadTableColumns}
          onChange={handleLeadTableChange}
          rowSelection={leadRowSelection}
          tableAlertRender={false}
          tableAlertOptionRender={false}
        />
      </> : <>
      <aside className="lead-inbox-list-pane">
        <div className="lead-inbox-toolbar"><AdvancedFilterToolbar scene="lead" pageKey="lead_management" placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
        {initialError && <Alert className="lead-list-error" type={isLeadInboxUnauthorized(initialError) ? 'warning' : 'error'} showIcon
          message={isLeadInboxUnauthorized(initialError) ? '无权查看客资收件箱' : '客资列表加载失败'} description={initialError}
          action={!isLeadInboxUnauthorized(initialError) ? <Button size="small" onClick={() => void loadPage(1, true, requestVersion.current)}>重试</Button> : undefined}/>}
        <div ref={listScrollRef} className="lead-inbox-scroll">
          {initialLoading ? <div className="lead-list-skeletons">
            {Array.from({ length: 5 }, (_, index) => <div className="lead-inbox-item" key={index}><Skeleton active avatar paragraph={{ rows: 2 }}/></div>)}
          </div> : !items.length && !initialError ? <Empty description="当前筛选下暂无客资"/> : items.map(item => {
            const active = item.id === selectedId
            const unseen = !active && unseenIds.includes(item.id)
            return <button key={item.id} type="button"
              className={['lead-inbox-item', active && 'active', unseen && 'unseen'].filter(Boolean).join(' ')}
              onClick={() => selectLead(item.id)}>
              <div className="lead-inbox-item-main">
                <NameAvatar name={item.submittedName} size={36} />
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
              <div className="lead-inbox-item-meta"><Badge status="processing"/><span>{channelLabel(item.sourceChannel)} · {snapshotOrDictionaryDisplayLabel(item.leadCategoryLabelSnapshot, categories, item.leadCategory, categoryError)} · {formatTimestamp(item.submittedAt)}</span></div>
            </button>
          })}
          {!initialLoading && items.length > 0 && <div ref={listSentinelRef} className="lead-list-sentinel">
            {loadMoreError
              ? <Alert type="error" showIcon message="更多客资加载失败" description={loadMoreError}
                action={<Button size="small" onClick={() => void loadPage(pageNo + 1, false, requestVersion.current)}>重试</Button>}/>
              : loadingMore
                ? <div className="lead-list-loading"><Spin size="small"/> 加载中</div>
                : hasMore
                  ? <Typography.Text type="secondary">继续下滑加载</Typography.Text>
                  : <Typography.Text type="secondary" className="lead-list-end">已加载全部 {total} 条客资</Typography.Text>}
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
