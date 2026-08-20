import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Empty,
  Skeleton,
  Space,
  Spin,
  Tag,
  Typography
} from 'antd'
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { api, type AdvancedFilterGroup, type DictData, type LeadSimpleStatus, type ManagedLead } from '../services/api'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
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
  tryStartLeadPageRequest
} from '../services/leadManagement'
import {
  DICT_TYPE,
  LEAD_QUALIFICATION_STATUS_LABELS,
  LEAD_FOLLOW_UP_STATUS_LABELS
} from '../constants'
import { parseLeadDetailTab, shouldBlockLeadSwitch } from '../services/leadFollowUp'
import { formatTimestamp } from '../services/time'
import { useRealtimeEvent } from '../components/RealtimeProvider'
import {
  LEAD_INBOX_REFRESH_RETRY_DELAYS_MS,
  LEAD_INBOX_UNSEEN_EVENT,
  clearLeadUnseen,
  unseenLeadIds,
  type UnseenLeadDetail
} from '../services/leadInboxUnseen'

const PAGE_SIZE = 20
type LeadAudience = 'all'
type LeadSimpleStatusSelection = 'all' | LeadSimpleStatus
type LeadPageLoadOptions = { preferredSelectedId?: number; silent?: boolean }

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
  const routeState = location.state as { leadId?: number; openFollowUp?: boolean } | null
  const queryLeadId = Number(searchParams.get('leadId')) || undefined
  const requestedLeadId = routeState?.leadId || queryLeadId
  const requestedTab = parseLeadDetailTab(searchParams.get('tab'))
    || (routeState?.openFollowUp ? 'follow-ups' : undefined)
  const returnToValue = searchParams.get('returnTo')
  const returnTo = returnToValue?.startsWith('/zsjos/sales-order-approvals') ? returnToValue : undefined
  const audience: LeadAudience = 'all'
  const [items, setItems] = useState<ManagedLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
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
  const requestVersion = useRef(0)
  const metadataVersion = useRef(0)
  const activePageRequests = useRef(new Set<string>())
  const routeSelectionRef = useRef<number | undefined>(requestedLeadId)
  const unseenIdsRef = useRef(unseenIds)
  const listScrollRef = useRef<HTMLDivElement>(null)
  const listSentinelRef = useRef<HTMLDivElement>(null)
  const itemIdsRef = useRef<number[]>([])

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
      const params = {
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined, advancedFilter,
        simpleStatus: simpleStatus === 'all' ? undefined : simpleStatus,
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
  }, [advancedFilter, audience, keyword, requestedLeadId, simpleStatus])

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

  useEffect(() => {
    if (selectedId) void loadDetail(selectedId)
    else setDetail(undefined)
  }, [loadDetail, selectedId])

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
  const hasMore = hasNextLeadInboxPage(pageNo, PAGE_SIZE, total)

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
  }
  const detailContent = detailLoading
    ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError
      ? <Alert type="error" showIcon message={detailError} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail
        ? <LeadDetail lead={detail} categories={categories} categoryLabel={categoryLabel} channelLabel={channelLabel}
          mode={audience} autoExpandFollowUp={Boolean(routeState?.openFollowUp && requestedLeadId === detail.id)}
          initialTab={requestedLeadId === detail.id ? requestedTab : undefined}
          onDirtyChange={setFollowUpDirty} onChanged={() => void refreshAfterLeadChange(detail.id)}/>
        : <Empty description="从左侧选择一条客资"/>

  if (detailOnly) {
    return <section className="workspace-page lead-management-page lead-management-detail-only">
      <main className="lead-inbox-detail-pane">{detailContent}</main>
    </section>
  }

  return <section className="workspace-page lead-management-page">
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
    <div className="lead-inbox-layout">
      <aside className="lead-inbox-list-pane">
        <div className="lead-inbox-toolbar"><AdvancedFilterToolbar scene="lead" placeholder="搜索姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={setKeyword} onChange={setAdvancedFilter}/></div>
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
              <div className="lead-inbox-item-meta"><Badge status="processing"/><span>{channelLabel(item.sourceChannel)} · {categoryLabel(item.leadCategory)} · {formatTimestamp(item.submittedAt)}</span></div>
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
    </div>
  </section>
}
