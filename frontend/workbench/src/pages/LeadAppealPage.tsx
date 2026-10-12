import BusinessTable from '../components/BusinessTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Empty,
  Segmented,
  Skeleton,
  Spin,
  Tag,
  Typography,
  message
} from 'antd'
import { useLocation, useSearchParams } from 'react-router-dom'
import { api, type AdvancedFilterGroup, type LeadAppeal } from '../services/api'
import SubjectAvatar from '../components/SubjectAvatar'
import { formatTimestamp } from '../services/time'
import { invalidReasonSnapshotLabel } from '../services/leadManagement'
import { AdvancedFilterToolbar } from '../components/AdvancedFilter'
import { useInboxTableLayout } from '../services/inboxLayout'

import LeadAppealReviewDetail from '../components/LeadAppealReviewDetail'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

const statusLabel: Record<string, string> = {
  sales_manager_reviewing: '负责人主管复核中',
  quality_reviewing: '质控复核中',
  chairman_reviewing: '董事长终审中',
  overturned: '已改判有效',
  upheld: '维持无效',
  withdrawn: '已撤回'
}
const stageLabel: Record<string, string> = { sales_manager: '负责人主管', quality: '质控部门', chairman: '董事长' }

type LeadAppealLocationState = {
  appealId?: number
  leadId?: number
  handled?: boolean
}

function parseOptionalBoolean(value: string | null) {
  if (value == null) return undefined
  return value === 'true' || value === '1' || value === 'done'
}

function parseOptionalNumber(value: string | number | undefined) {
  if (value == null || value === '') return undefined
  const next = Number(value)
  return Number.isFinite(next) ? next : undefined
}

export default function LeadAppealPage() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const locationState = (location.state || {}) as LeadAppealLocationState
  const targetAppealId = parseOptionalNumber(searchParams.get('appealId') || locationState.appealId)
  const targetLeadId = parseOptionalNumber(searchParams.get('leadId') || locationState.leadId)
  const targetHandled = parseOptionalBoolean(searchParams.get('handled'))
    ?? locationState.handled
  const hasTarget = targetAppealId !== undefined || targetLeadId !== undefined

  const [handled, setHandled] = useState(targetHandled ?? false)
  const [items, setItems] = useState<LeadAppeal[]>([])
  const [selected, setSelected] = useState<LeadAppeal>()
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [cursor, setCursor] = useState<string>()
  const cursorRef = useRef<string>(undefined)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const dirtyRef = useRef(false)
  const preserveSelection = useRef(false)
  const selectionRef = useRef<LeadAppeal | undefined>(undefined)
  selectionRef.current = selected
  const targetLocated = useRef(false)
  const requestVersion = useRef(0)
  dirtyRef.current = dirty
  const allowLeave = () => {
    if (busy) { message.warning('正在提交，请稍候'); return false }
    return !dirtyRef.current || window.confirm('放弃尚未提交的处理内容？')
  }
  const openItem = (item: LeadAppeal) => {
    if (item.id === selectionRef.current?.id) { setDrawerOpen(true); return }
    if (item.id !== selectionRef.current?.id && !allowLeave()) return
    setDirty(false); setSelected(item); setDrawerOpen(true)
  }
  const [hasMore, setHasMore] = useState(true)
  const [error, setError] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [advancedFilter, setAdvancedFilter] = useState<AdvancedFilterGroup>()
  const [tablePage, setTablePage] = useState(1)
  const [tablePageSize, setTablePageSize] = useState(20)
  const [tableTotal, setTableTotal] = useState(0)
  const listScrollRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)
  const { useTableLayout, isDesktop } = useInboxTableLayout()

  useEffect(() => {
    targetLocated.current = false
  }, [targetAppealId, targetLeadId, targetHandled])

  useEffect(() => {
    if (targetHandled !== undefined) setHandled(targetHandled)
  }, [targetHandled])

  const isTargetItem = useCallback((item: LeadAppeal) => {
    if (targetAppealId !== undefined) return item.id === targetAppealId
    if (targetLeadId !== undefined) return item.leadId === targetLeadId
    return false
  }, [targetAppealId, targetLeadId])

  const locateTarget = useCallback(async () => {
    if (!hasTarget || targetLocated.current) return false
    let nextCursor: string | undefined
    do {
      const result = await api.leadAppealInboxCursor(targetHandled ?? handled, {
        cursor: nextCursor,
        limit: 50,
        keyword: keyword || undefined,
        advancedFilter
      })
      const target = result.list.find(isTargetItem)
      if (target) {
        setItems(current => [target, ...current.filter(item => item.id !== target.id)])
        targetLocated.current = true
        setSelected(target)
        if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
        return true
      }
      nextCursor = result.nextCursor
    } while (nextCursor)
    targetLocated.current = true
    setSelected(undefined)
    setDrawerOpen(false)
    message.warning('未找到定位的申诉记录')
    return false
  }, [advancedFilter, handled, hasTarget, isTargetItem, keyword, targetHandled, useTableLayout])

  const load = useCallback(async (append = false) => {
    const version = ++requestVersion.current
    if (append) setLoadingMore(true)
    else setLoading(true)
    setError('')
    try {
      const result = useTableLayout
        ? await api.leadAppealInboxPage(handled, { pageNo: tablePage, pageSize: tablePageSize, keyword: keyword || undefined, advancedFilter })
        : await api.leadAppealInboxCursor(handled, { cursor: append ? cursorRef.current : undefined, limit: 20, keyword: keyword || undefined, advancedFilter })
      if (version !== requestVersion.current) return
      setTableTotal('total' in result ? result.total : 0)
      setItems(current => useTableLayout || !append
        ? result.list
        : append
        ? [...current, ...result.list.filter(item => !current.some(existing => existing.id === item.id))]
        : result.list)
      if ('nextCursor' in result) { cursorRef.current = result.nextCursor; setCursor(result.nextCursor); setHasMore(result.hasMore) }
      if (!append && !preserveSelection.current && !dirtyRef.current) {
        const target = !targetLocated.current ? result.list.find(isTargetItem) : undefined
        const existing = result.list.find(item => item.id === selectionRef.current?.id)
        setSelected(target || existing || (useTableLayout ? undefined : result.list[0]))
        if (target) { targetLocated.current = true; setDrawerOpen(true) }
        else if (hasTarget && !targetLocated.current) void locateTarget()
      }

    } catch (e) {
      if (version !== requestVersion.current) return
      setError(e instanceof Error ? e.message : '申诉处理列表加载失败')
    } finally {
      if (version === requestVersion.current) { setLoading(false); setLoadingMore(false) }
    }
  }, [advancedFilter, handled, hasTarget, isTargetItem, keyword, locateTarget, tablePage, tablePageSize, useTableLayout])

  useEffect(() => { void load() }, [advancedFilter, handled, keyword, load])
  useEffect(() => {
    const node = sentinelRef.current
    if (useTableLayout || !node || !hasMore || loading || loadingMore || !cursor) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) void load(true)
    }, { root: node.parentElement, rootMargin: '160px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [cursor, hasMore, load, loading, loadingMore, useTableLayout])
  useEffect(() => {
    if (useTableLayout) return
    const node = listScrollRef.current
    if (!node) return
    const onScroll = () => {
      if (!loading && !loadingMore && hasMore && cursor && node.scrollHeight - node.scrollTop - node.clientHeight < 80) {
        void load(true)
      }
    }
    node.addEventListener('scroll', onScroll)
    return () => node.removeEventListener('scroll', onScroll)
  }, [cursor, hasMore, load, loading, loadingMore, useTableLayout])

  const refreshList = () => {
    preserveSelection.current = true
    void load().finally(() => { preserveSelection.current = false })
  }
  const detail = selected ? <LeadAppealReviewDetail key={selected.id} item={selected} onDirtyChange={setDirty} onBusyChange={setBusy} onChanged={refreshList} /> : <Empty description="选择一条申诉查看客资详情" />
  const changeScope = (change: () => void) => {
    if (!allowLeave()) return
    setDirty(false); setSelected(undefined); setDrawerOpen(false); targetLocated.current = true; change()
  }
  useEffect(() => {
    if (!dirty) return
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty])

  return <section className={`workspace-page business-inbox-page lead-appeal-inbox-page${useTableLayout ? ' business-inbox-table-page' : ''}`}>
    <header className="business-inbox-scope-bar">
      <div className="business-inbox-scope-row">
        <Segmented
          value={handled ? 'done' : 'todo'}
          onChange={value => changeScope(() => { setHandled(value === 'done'); setTablePage(1) })}
          options={[{ value: 'todo', label: '待处理' }, { value: 'done', label: '已处理' }]}
        />
      </div>
    </header>
    {useTableLayout ? <><BusinessTable<LeadAppeal> filters={<><AdvancedFilterToolbar scene="lead_appeal" pageKey="lead_appeal" placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号" keyword={keyword} value={advancedFilter} onKeyword={value => changeScope(() => { setKeyword(value); setTablePage(1) })} onChange={value => changeScope(() => { setAdvancedFilter(value); setTablePage(1) })}/></>} tableKey="lead-appeal-page-1" error={error} onReload={() => void load()}
      className="business-inbox-table"
      rowKey="id"

      columnsState={{ persistenceKey: 'crm-lead-appeal-table-columns', persistenceType: 'localStorage' }}
      loading={loading}
      dataSource={items}
      pagination={{ current: tablePage, pageSize: tablePageSize, total: tableTotal, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showQuickJumper: true, onChange: (page, size) => changeScope(() => { setTablePage(page); setTablePageSize(size) }) }}
      scroll={{ x: 2800 }}
      locale={{ emptyText: <Empty description="暂无申诉" /> }}
      columns={[
        { title: '客资编号', dataIndex: 'leadNo', width: 150 },
        { title: '姓名', dataIndex: 'leadName', width: 140 },
        { title: '申诉轮次', dataIndex: 'roundNo', width: 100, render: (_, item) => `第 ${item.roundNo} 轮` },
        { title: '处理阶段', render: (_, item) => stageLabel[item.reviewStage] || (item.reviewStage ? '未知阶段' : '—') },
        { title: '状态', render: (_, item) => <Tag color={item.status === 'upheld' ? 'error' : 'processing'}>{statusLabel[item.status] || (item.status ? '未知状态' : '—')}</Tag> },
        { title: '申请人', dataIndex: 'applicantUserName', width: 130, render: value => value || '-' },
        { title: '申诉理由', dataIndex: 'reason', width: 240, ellipsis: true },
        { title: '申诉附件', width: 100, render: (_, item) => `${item.evidence.length} 个` },
        { title: '原无效原因', width: 180, ellipsis: true, render: (_, item) => invalidReasonSnapshotLabel(item.invalidReasonSnapshot) || '-' },
        { title: '原无效说明', dataIndex: 'invalidDescriptionSnapshot', width: 240, ellipsis: true, render: value => value || '-' },
        { title: '原无效附件', width: 110, render: (_, item) => `${item.invalidEvidenceSnapshot.length} 个` },
        { title: '处理人', dataIndex: 'reviewerUserName', width: 130, render: value => value || '-' },
        { title: '裁决意见', dataIndex: 'decisionReason', width: 240, ellipsis: true, render: value => value || '-' },
        { title: '裁决附件', width: 100, render: (_, item) => `${item.decisionEvidence.length} 个` },
        { title: '可再次申诉', dataIndex: 'canSubmitNextRound', width: 110, render: value => value ? '是' : '否' },
        { title: '提交时间', dataIndex: 'submittedAt', render: (_, item) => formatTimestamp(item.submittedAt), width: 170 },
        { title: '处理时间', dataIndex: 'decidedAt', render: (_, item) => formatTimestamp(item.decidedAt), width: 170 },
        { title: '操作', key: 'action', width: 88, fixed: 'right', hideInSetting: true, render: (_, item) => <Button type="link" onClick={() => openItem(item)}>详细</Button> }
      ]}
    /></> : <div className="business-inbox-layout">
      <aside className="business-inbox-list-pane">
        <div className="business-inbox-toolbar">
          <AdvancedFilterToolbar
            scene="lead_appeal"
            pageKey="lead_appeal"
            placeholder="搜索客资编号 / 姓名 / 手机号 / 微信号"
            keyword={keyword}
            value={advancedFilter}
            onKeyword={value => changeScope(() => setKeyword(value))}
            onChange={value => changeScope(() => setAdvancedFilter(value))}
          />
        </div>
        {error && <Alert className="business-inbox-error" type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
        <div ref={listScrollRef} className="business-inbox-scroll">
          {loading
            ? <Skeleton active paragraph={{ rows: 8 }}/>
            : items.length
              ? items.map(item => <button
                type="button"
                key={item.id}
                className={`business-inbox-item${selected?.id === item.id ? ' active' : ''}`}
                onClick={() => openItem(item)}
              >
                <div className="business-inbox-item-main">
                  <SubjectAvatar seed={item.leadNo} label="" />
                  <div className="business-inbox-item-copy">
                    <div className="business-inbox-item-title">
                      <strong>{item.leadName}</strong>
                      <Tag>{statusLabel[item.status]}</Tag>
                    </div>
                    <span>{item.leadNo}</span>
                    <span>第 {item.roundNo} 次 · {stageLabel[item.reviewStage]}</span>
                    <span>{item.reason}</span>
                  </div>
                </div>
                <div className="business-inbox-item-meta">
                  <span>{formatTimestamp(item.submittedAt)}</span>
                </div>
              </button>)
              : <Empty description="暂无申诉"/>}
          {!loading && items.length > 0 && <div ref={sentinelRef} className="business-inbox-list-state">
            <Typography.Text type="secondary">
              {loadingMore ? <><Spin size="small"/> 加载中</> : hasMore ? '继续下滑加载' : '已加载全部申诉'}
            </Typography.Text>
          </div>}
        </div>
      </aside>
      <main className="business-inbox-detail-pane">{isDesktop && detail}</main>
    </div>}
    <ResizableDetailDrawer desktopResizable={useTableLayout} className="business-inbox-mobile-drawer" open={drawerOpen && (useTableLayout || !isDesktop)} onClose={() => { if (allowLeave()) { setDrawerOpen(false); setDirty(false); setSelected(undefined) } }} title="客资详情" placement="right" width="100%">
      {(useTableLayout || !isDesktop) && drawerOpen && detail}
    </ResizableDetailDrawer>
  </section>
}
