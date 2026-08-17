import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Empty,
  Skeleton,
  Segmented,
  Space,
  Spin,
  Tag,
  Tabs,
  Typography
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useLocation } from 'react-router-dom'
import { api, type AdvancedFilterGroup, type DictData, type LeadInboxFilterProfile, type ManagedLead } from '../services/api'
import { AdvancedFilterToolbar, filterCount } from '../components/AdvancedFilter'
import { NameAvatar } from '../components/LeadDetailOverview'
import LeadDetail from '../components/LeadDetail'
import {
  defaultInboxStage,
  dictionaryDisplayLabel,
  hasNextLeadInboxPage,
  isLeadInboxUnauthorized,
  mergeUniqueLeads,
  protocolDisplayLabel,
  tryStartLeadPageRequest
} from '../services/leadManagement'
import {
  DICT_TYPE,
  LEAD_QUALIFICATION_STATUS_LABELS,
  LEAD_FOLLOW_UP_STATUS_LABELS
} from '../constants'
import { shouldBlockLeadSwitch } from '../services/leadFollowUp'
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
type LeadAudience = 'all' | 'submitter' | 'owner'
type LeadRelationScope = 'all' | 'submitted' | 'owned'

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

export default function LeadManagementPage({ permissions }: { permissions: string[] }) {
  const location = useLocation()
  const routeState = location.state as { leadId?: number; openFollowUp?: boolean; relationScope?: LeadRelationScope } | null
  const requestedLeadId = routeState?.leadId
  const canViewSubmitted = permissions.includes('zsjos:lead:query-submitted')
  const canViewOwned = permissions.includes('zsjos:lead:query-owned')
  const initialRelationScope = routeState?.relationScope === 'submitted' && canViewSubmitted
    ? 'submitted'
    : routeState?.relationScope === 'owned' && canViewOwned ? 'owned' : 'all'
  const [relationScope, setRelationScope] = useState<LeadRelationScope>(initialRelationScope)
  const audience: LeadAudience = relationScope === 'submitted' ? 'submitter' : relationScope === 'owned' ? 'owner' : 'all'
  const [items, setItems] = useState<ManagedLead[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [initialLoading, setInitialLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [initialError, setInitialError] = useState('')
  const [loadMoreError, setLoadMoreError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [inboxGroup, setInboxGroup] = useState('all')
  const [inboxStage, setInboxStage] = useState('all')
  const [filterProfile, setFilterProfile] = useState<LeadInboxFilterProfile>({ groups: [] })
  const [filterLoading, setFilterLoading] = useState(true)
  const [metadataError, setMetadataError] = useState('')
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
  const listScrollRef = useRef<HTMLDivElement>(null)
  const listSentinelRef = useRef<HTMLDivElement>(null)
  const itemIdsRef = useRef<number[]>([])

  useEffect(() => {
    if (routeState?.relationScope === 'submitted' && canViewSubmitted) setRelationScope('submitted')
    else if (routeState?.relationScope === 'owned' && canViewOwned) setRelationScope('owned')
    else if (routeState?.relationScope) setRelationScope('all')
  }, [canViewOwned, canViewSubmitted, routeState?.relationScope])

  const loadMetadata = useCallback(async () => {
    const version = ++metadataVersion.current
    setMetadataError('')
    setCategoryError(false)
    setChannelError(false)
    setFilterLoading(true)
    const filterProfileRequest = audience === 'all'
      ? Promise.resolve({ groups: [] } as LeadInboxFilterProfile)
      : api.leadInboxFilterProfile(audience)
    const results = await Promise.allSettled([
      filterProfileRequest,
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_SOURCE_CHANNEL)
    ])
    if (version !== metadataVersion.current) return
    if (results[0].status === 'fulfilled') setFilterProfile(results[0].value)
    if (results[1].status === 'fulfilled') setCategories(results[1].value)
    else { setCategories([]); setCategoryError(true) }
    if (results[2].status === 'fulfilled') setChannels(results[2].value)
    else { setChannels([]); setChannelError(true) }
    if (results.some(result => result.status === 'rejected')) setMetadataError('筛选项加载不完整，可重试恢复字典和筛选配置。')
    setFilterLoading(false)
  }, [audience])

  const loadPage = useCallback(async (targetPage: number, replace: boolean, version: number) => {
    const requestKey = tryStartLeadPageRequest(activePageRequests.current, version, targetPage)
    if (!requestKey) return
    if (replace) {
      setInitialLoading(true)
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
        inboxGroup: inboxGroup === 'all' ? undefined : inboxGroup,
        inboxStage: inboxStage === 'all' ? undefined : inboxStage,
        relationScope
      }
      const result = audience === 'all'
        ? await api.allLeadPage(params)
        : await api.managedLeadInboxPage(audience, params)
      if (version !== requestVersion.current) return
      setItems(current => replace ? result.list : mergeUniqueLeads(current, result.list))
      setTotal(result.total)
      setPageNo(targetPage)
      if (replace) setSelectedId(current => requestedLeadId
        || (current && result.list.some(item => item.id === current) ? current : result.list[0]?.id))
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
        if (replace) setInitialLoading(false)
        else setLoadingMore(false)
      }
    }
  }, [advancedFilter, audience, inboxGroup, inboxStage, keyword, relationScope, requestedLeadId])

  useEffect(() => { void loadMetadata() }, [loadMetadata])
  useEffect(() => {
    const version = ++requestVersion.current
    setPageNo(1)
    setInitialLoading(false)
    setLoadingMore(false)
    setInitialError('')
    setLoadMoreError('')
    if (listScrollRef.current) listScrollRef.current.scrollTop = 0
    void loadPage(1, true, version)
  }, [loadPage])

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true)
    setDetailError('')
    try {
      setDetail(await api.managedLead(id))
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

  // 详情已经渲染出来即算看过，自动选中与手工点击一视同仁
  useEffect(() => {
    if (selectedId) setUnseenIds(clearLeadUnseen(selectedId))
  }, [selectedId])

  const refreshAfterLeadChange = useCallback(async (id: number) => {
    const version = ++requestVersion.current
    await Promise.all([loadMetadata(), loadPage(1, true, version), loadDetail(id)])
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
  }, [loadMetadata, loadPage])

  // 接单成功由 LeadAssignmentHost 打标记，这里据此刷新，销售不必手动刷新页面
  useEffect(() => {
    const onUnseenChange = (event: Event) => {
      const ids = (event as CustomEvent<UnseenLeadDetail>).detail?.leadIds ?? []
      setUnseenIds(ids)
      const added = ids.find(id => !itemIdsRef.current.includes(id))
      if (added != null) void refreshUntilVisible(added)
    }
    window.addEventListener(LEAD_INBOX_UNSEEN_EVENT, onUnseenChange)
    return () => window.removeEventListener(LEAD_INBOX_UNSEEN_EVENT, onUnseenChange)
  }, [refreshUntilVisible])

  // 转派、回收等由他人触发的归属变化同样要落到列表上
  useRealtimeEvent('zsjos_lead_assignment', () => { void refreshUntilVisible() })

  const activeGroup = useMemo(
    () => filterProfile.groups.find(item => item.key === inboxGroup),
    [filterProfile.groups, inboxGroup]
  )
  useEffect(() => {
    if (!filterProfile.groups.length || filterProfile.groups.some(item => item.key === inboxGroup)) return
    const firstGroup = filterProfile.groups[0]
    setInboxGroup(firstGroup.key)
    setInboxStage(defaultInboxStage(filterProfile.groups, firstGroup.key))
  }, [filterProfile.groups, inboxGroup])
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
    setSelectedId(id)
  }
  const changeInboxGroup = (key: string) => {
    setInboxGroup(key)
    setInboxStage(defaultInboxStage(filterProfile.groups, key))
  }
  const changeRelationScope = (value: string | number) => {
    setRelationScope(value as LeadRelationScope)
    setInboxGroup('all')
    setInboxStage('all')
    setAdvancedFilter(undefined)
  }
  const detailContent = detailLoading
    ? <Skeleton active paragraph={{ rows: 10 }}/>
    : detailError
      ? <Alert type="error" showIcon message={detailError} action={<Button size="small" icon={<ReloadOutlined/>} onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button>}/>
      : detail
        ? <LeadDetail lead={detail} categories={categories} categoryLabel={categoryLabel} channelLabel={channelLabel}
          mode={audience} autoExpandFollowUp={Boolean(routeState?.openFollowUp && requestedLeadId === detail.id)}
          onDirtyChange={setFollowUpDirty} onChanged={() => void refreshAfterLeadChange(detail.id)}/>
        : <Empty description="从左侧选择一条客资"/>

  return <section className="workspace-page lead-management-page">
    <header className="lead-management-scope-shell">
      <Segmented
        value={relationScope}
        onChange={changeRelationScope}
        options={[
          { value: 'all', label: '全部' },
          { value: 'submitted', label: '我提交的', disabled: !canViewSubmitted },
          { value: 'owned', label: '我负责的', disabled: !canViewOwned }
        ]}
      />
    </header>
    {audience !== 'all' && filterCount(advancedFilter) === 0 && <header className="lead-inbox-filter-shell">
      {metadataError && <Alert className="lead-inbox-metadata-error" type="warning" showIcon message={metadataError} action={<Button type="link" size="small" onClick={() => void loadMetadata()}>重试</Button>}/>} 
      {filterLoading
        ? <Skeleton active title={false} paragraph={{ rows: 2 }}/>
        : filterProfile.groups.length > 0
          ? <>
            <Tabs
              className="lead-inbox-group-tabs"
              activeKey={inboxGroup}
              onChange={changeInboxGroup}
              items={filterProfile.groups.map(group => ({
                key: group.key,
                label: group.label
              }))}
            />
            {activeGroup?.sections.length ? <div className="lead-inbox-filter-sections">
              {activeGroup.sections.map(section => <div className="lead-inbox-filter-row" key={section.key}>
                <span className="lead-inbox-filter-label">{section.label}</span>
                <div className="lead-inbox-filter-options">
                  {section.options.map(option => <button
                    type="button"
                    key={option.key}
                    className={inboxStage === option.key ? 'active' : ''}
                    aria-pressed={inboxStage === option.key}
                    onClick={() => setInboxStage(option.key)}
                  >{option.label}</button>)}
                </div>
              </div>)}
            </div> : null}
          </>
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用筛选配置"/>}
    </header>}
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
