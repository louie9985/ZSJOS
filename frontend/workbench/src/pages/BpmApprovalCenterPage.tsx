import BusinessTable from '../components/BusinessTable'
import { MATERIAL_APPROVAL_INVALID_TASK } from '../services/materialApprovalApi'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Empty,
  Grid,
  Input,
  Result,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Typography
} from 'antd'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileSearchOutlined,
  ReloadOutlined
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import DateTimeText from '../components/DateTimeText'
import BpmApprovalDetail from '../components/bpm/BpmApprovalDetail'
import { bpmStatusColor, bpmStatusLabel, bpmVariableLabel, hasChinese } from '../components/bpm/bpmStatus'
import {
  api,
  ApiError,
  AuthenticationError,
  type BpmApprovalBrief,
  type BpmTask,
  type SimpleUser
} from '../services/api'
import { useInboxTableLayout } from '../services/inboxLayout'

import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

type BpmTaskView = 'todo' | 'done'

const PAGE_SIZE = 20
const SALES_ORDER_PERMISSION_DENIED = 1_900_006_011
const LEAD_APPEAL_PERMISSION_DENIED = 1_900_003_041

/** 状态文案与颜色统一来自 @/components/bpm/bpmStatus，避免两处各维护一份。 */
const taskStatusLabel = bpmStatusLabel
const taskStatusColor = bpmStatusColor

function taskSubject(task: BpmTask) {
  return task.processInstance?.name?.trim() || task.name?.trim() || '审批任务'
}

/**
 * 列表摘要：只有拿到中文标签才拼。
 *
 * <p>后端的 {@code summary} 是 KeyValue，label 恒为 null，key 又是英文流程变量名——
 * 直接 {@code key：value} 拼出来就是「deptLeaderUsers：21」这种给审批人看的东西。
 * 因此先用后端的中文 label，再退回通用变量名映射，仍未命中就丢弃该项。
 * 全部丢完时返回空数组，由调用方给 '-'，而不是漏出内部字段名。
 */
function taskSummary(task?: BpmTask) {
  const summary = task?.processInstance?.summary || []
  return summary
    .map(item => {
      const label = item.label && hasChinese(item.label) ? item.label : bpmVariableLabel(item.key)
      return [label, item.value].filter(Boolean).join('：')
    })
    .filter(Boolean)
}

/** 业务摘要优先展示业务单据标题与字段；未接入业务域的流程退回流程摘要。 */
function briefTitle(task: BpmTask, briefs: Record<string, BpmApprovalBrief>) {
  return briefs[task.id]?.title
}

function briefSummary(task: BpmTask, briefs: Record<string, BpmApprovalBrief>) {
  const brief = briefs[task.id]
  if (!brief) return []
  const fields = (brief.fields || [])
    .map(field => [field.label, field.value].filter(Boolean).join('：'))
    .filter(Boolean)
  if (fields.length === 0) return []
  return [brief.title, brief.subtitle, ...fields].filter(Boolean) as string[]
}

function formatDuration(duration?: number) {
  if (duration == null) return '-'
  const minutes = Math.max(0, Math.floor(duration / 60_000))
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const remainder = minutes % 60
  return [days ? `${days} 天` : '', hours ? `${hours} 小时` : '', `${remainder} 分钟`].filter(Boolean).join(' ')
}

/**
 * 已接入专属业务审批页的流程：这些流程的待办/已办统一在各自业务页处理，
 * 不再出现在通用审批中心，避免同一笔业务在两个入口重复展示、重复处理。
 * 与后端 ZsjosBpmBusinessTaskTargetServiceImpl 支持的业务入口保持一致。
 */
const BUSINESS_APPROVAL_PROCESS_KEYS = [
  'zsjos_sales_order_dual_approval',
  'zsjos_lead_appeal_review',
  'zsjos_viral_account_review',
  'zsjos_viral_content_review'
].join(',')

/**
 * 已接入员工端业务页的流程，额外提供业务详情入口；
 * 未接入的流程返回 supported=false，此时只展示通用审批界面。
 */
function useBusinessEntry(task: BpmTask | undefined, view: BpmTaskView) {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [locatingTaskId, setLocatingTaskId] = useState<string>()
  const [supportedTaskIds, setSupportedTaskIds] = useState<Record<string, boolean>>({})

  useEffect(() => {
    if (!task || supportedTaskIds[task.id] !== undefined) return
    let cancelled = false
    void api.bpmBusinessTaskTarget(task.id, view)
      .then(target => {
        if (!cancelled) setSupportedTaskIds(current => ({ ...current, [task.id]: target.supported }))
      })
      .catch(() => {
        // 探测失败不阻塞审批，只是不展示业务入口。
        if (!cancelled) setSupportedTaskIds(current => ({ ...current, [task.id]: false }))
      })
    return () => { cancelled = true }
  }, [supportedTaskIds, task, view])

  const open = async () => {
    if (!task) return
    setLocatingTaskId(task.id)
    try {
      const target = await api.bpmBusinessTaskTarget(task.id, view)
      if (!target.supported) {
        message.info(target.message)
        return
      }
      const params = new URLSearchParams()
      Object.entries(target.query).forEach(([key, value]) => {
        if (value !== undefined && value !== null) params.set(key, String(value))
      })
      const route = target.route.startsWith('/') ? target.route : `/${target.route}`
      navigate(params.toString() ? `${route}?${params.toString()}` : route, { state: target.query })
    } catch (locateError) {
      if (locateError instanceof AuthenticationError
        || (locateError instanceof ApiError
          && [SALES_ORDER_PERMISSION_DENIED, LEAD_APPEAL_PERMISSION_DENIED, MATERIAL_APPROVAL_INVALID_TASK].includes(locateError.code))) {
        message.info('当前账号无权打开该业务审批')
        return
      }
      message.error('审批任务定位失败，请重试')
    } finally {
      setLocatingTaskId(undefined)
    }
  }

  return {
    supported: task ? supportedTaskIds[task.id] === true : false,
    loading: !!task && locatingTaskId === task.id,
    open
  }
}

export default function BpmApprovalCenterPage({ permissions, initialView }: {
  permissions: string[]
  initialView?: BpmTaskView
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const { message } = App.useApp()
  const screens = Grid.useBreakpoint()
  const resolvedInitialView = initialView || (location.pathname === APP_ROUTES.BPM_DONE ? 'done' : 'todo')
  const [view, setView] = useState<BpmTaskView>(resolvedInitialView)
  const [tasks, setTasks] = useState<BpmTask[]>([])
  const [selectedId, setSelectedId] = useState<string>()
  const [total, setTotal] = useState(0)
  const [loadedPage, setLoadedPage] = useState(0)
  const [tablePage, setTablePage] = useState(1)
  const [tablePageSize, setTablePageSize] = useState(PAGE_SIZE)
  const [keyword, setKeyword] = useState('')
  const [counts, setCounts] = useState<Record<BpmTaskView, number>>({ todo: 0, done: 0 })
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [countLoading, setCountLoading] = useState(true)
  const [error, setError] = useState('')
  const [loadMoreError, setLoadMoreError] = useState('')
  const [countError, setCountError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [users, setUsers] = useState<SimpleUser[]>([])
  const { useTableLayout } = useInboxTableLayout()
  const requestSeq = useRef(0)
  const countSeq = useRef(0)
  const listRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)
  const loadingMoreRef = useRef(false)

  const canQuery = permissions.includes('bpm:task:query')
  const canUpdate = permissions.includes('bpm:task:update')

  const selectedTask = useMemo(
    () => tasks.find(item => item.id === selectedId),
    [selectedId, tasks]
  )

  const businessEntry = useBusinessEntry(selectedTask, view)

  // 业务摘要由后端 Provider 按任务批量下发：前端不做流程 Key → 业务语义的映射。
  const [briefs, setBriefs] = useState<Record<string, BpmApprovalBrief>>({})
  const briefKey = useMemo(() => tasks.map(item => item.id).join(','), [tasks])
  useEffect(() => {
    const taskIds = briefKey ? briefKey.split(',') : []
    if (taskIds.length === 0) {
      setBriefs({})
      return
    }
    let cancelled = false
    void api.bpmApprovalBusinessSummaryBatch(taskIds, view)
      .then(result => { if (!cancelled) setBriefs(result || {}) })
      .catch(() => { if (!cancelled) setBriefs({}) })
    return () => { cancelled = true }
  }, [briefKey, view])

  const loadCounts = useCallback(async () => {
    if (!canQuery) return
    const seq = ++countSeq.current
    setCountLoading(true)
    setCountError('')
    try {
      const [todo, done] = await Promise.all([
        api.bpmTaskPage('todo', { pageNo: 1, pageSize: 1, excludeProcessDefinitionKeys: BUSINESS_APPROVAL_PROCESS_KEYS }),
        api.bpmTaskPage('done', { pageNo: 1, pageSize: 1, excludeProcessDefinitionKeys: BUSINESS_APPROVAL_PROCESS_KEYS })
      ])
      if (seq !== countSeq.current) return
      setCounts({ todo: todo.total, done: done.total })
    } catch (loadError) {
      if (seq !== countSeq.current) return
      console.error('[BpmApprovalCenter] loadCounts - error:', loadError)
      setCountError(loadError instanceof Error ? loadError.message : '审批数量加载失败')
    } finally {
      if (seq === countSeq.current) setCountLoading(false)
    }
  }, [canQuery, permissions])

  const appendTasks = (current: BpmTask[], incoming: BpmTask[]) => {
    const rows = new Map(current.map(task => [task.id, task]))
    incoming.forEach(task => rows.set(task.id, task))
    return Array.from(rows.values())
  }

  const loadFirstPage = useCallback(async (preserveSelection = false) => {
    if (!canQuery) return
    const seq = ++requestSeq.current
    setLoading(true)
    setError('')
    setLoadMoreError('')
    setUnauthorized(false)
    try {
      const result = await api.bpmTaskPage(view, { pageNo: useTableLayout ? tablePage : 1, pageSize: useTableLayout ? tablePageSize : PAGE_SIZE, name: keyword.trim() || undefined, excludeProcessDefinitionKeys: BUSINESS_APPROVAL_PROCESS_KEYS })
      if (seq !== requestSeq.current) return
      setTasks(result.list)
      setTotal(result.total)
      setLoadedPage(useTableLayout ? tablePage : 1)
      setCounts(current => ({ ...current, [view]: result.total }))
      setSelectedId(current => preserveSelection && result.list.some(item => item.id === current) ? current : result.list[0]?.id)
    } catch (loadError) {
      if (seq !== requestSeq.current) return
      console.error('[BpmApprovalCenter] loadFirstPage - error:', loadError)
      setTasks([])
      setSelectedId(undefined)
      setTotal(0)
      setLoadedPage(0)
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(loadError instanceof Error ? loadError.message : '审批任务加载失败')
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [canQuery, keyword, permissions, tablePage, tablePageSize, useTableLayout, view])

  const loadMore = useCallback(async () => {
    if (!canQuery || loading || loadingMoreRef.current || tasks.length >= total) return
    loadingMoreRef.current = true
    setLoadingMore(true)
    setLoadMoreError('')
    const seq = requestSeq.current
    const nextPage = loadedPage + 1
    try {
      const result = await api.bpmTaskPage(view, {
        pageNo: nextPage,
        pageSize: PAGE_SIZE,
        name: keyword.trim() || undefined,
        excludeProcessDefinitionKeys: BUSINESS_APPROVAL_PROCESS_KEYS,
      })
      if (seq !== requestSeq.current) return
      setTasks(current => appendTasks(current, result.list))
      setTotal(result.total)
      setLoadedPage(nextPage)
      setCounts(current => ({ ...current, [view]: result.total }))
    } catch (loadError) {
      if (seq !== requestSeq.current) return
      setLoadMoreError(loadError instanceof Error ? loadError.message : '更多审批任务加载失败')
    } finally {
      loadingMoreRef.current = false
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }, [canQuery, keyword, loadedPage, loading, tasks.length, total, view])

  useEffect(() => {
    const nextView = location.pathname === APP_ROUTES.BPM_DONE ? 'done' : 'todo'
    setView(nextView)
    setTasks([])
    setTotal(0)
    setLoadedPage(0)
    setSelectedId(undefined)
    setError('')
    setLoadMoreError('')
    listRef.current?.scrollTo({ top: 0 })
  }, [location.pathname])

  useEffect(() => { void loadCounts() }, [loadCounts])
  useEffect(() => { void loadFirstPage() }, [loadFirstPage])
  useEffect(() => {
    // 转办、委派、加签、抄送需要人员候选；无处理权限时不请求。
    if (!canUpdate) return
    let cancelled = false
    void api.simpleUsers()
      .then(list => { if (!cancelled) setUsers(list) })
      .catch(() => { if (!cancelled) setUsers([]) })
    return () => { cancelled = true }
  }, [canUpdate])
  useEffect(() => {
    const sentinel = sentinelRef.current
    const root = listRef.current
    if (useTableLayout || !sentinel || !root || loading || loadMoreError || tasks.length >= total) return
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadMore()
    }, { root, rootMargin: "240px 0px" })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [loadMore, loadMoreError, loading, tasks.length, total, useTableLayout])
  useEffect(() => {
    if (screens.md) setDrawerOpen(false)
  }, [screens.md])

  const changeView = (next: string) => {
    const nextView = next as BpmTaskView
    setView(nextView)
    navigate(nextView === 'done' ? APP_ROUTES.BPM_DONE : APP_ROUTES.BPM_TODO)
  }

  const reload = () => {
    void loadCounts()
    listRef.current?.scrollTo({ top: 0 })
    void loadFirstPage(true)
  }

  const selectTask = (task: BpmTask) => {
    setSelectedId(task.id)
    if (useTableLayout || window.matchMedia('(max-width: 768px)').matches) setDrawerOpen(true)
  }

  if (!canQuery) {
    return <section className="workspace-page bpm-approval-page">
      <Result status="403" title="无权访问审批中心" subTitle="当前账号缺少 bpm:task:query 权限。"/>
    </section>
  }

  const detail = <BpmApprovalDetail
    task={selectedTask}
    view={view}
    canUpdate={canUpdate}
    users={users}
    businessBrief={selectedTask ? briefs[selectedTask.id] : undefined}
    businessRoute={selectedTask ? (briefs[selectedTask.id]?.route
      ? { route: briefs[selectedTask.id]!.route!, query: briefs[selectedTask.id]!.query }
      : null) : undefined}
    businessEntry={businessEntry.supported
      ? { loading: businessEntry.loading, onOpen: () => void businessEntry.open() }
      : undefined}
    onActionSuccess={() => {
      void loadCounts()
      void loadFirstPage(true)
    }}
  />

  return <section className={`workspace-page business-inbox-page bpm-approval-page${useTableLayout ? ' business-inbox-table-page' : ''}`}>
    <header className="business-inbox-scope-bar bpm-approval-header">
      <div className="bpm-approval-title">
        <Typography.Title level={4}>审批中心</Typography.Title>
        <Typography.Text type="secondary">工作流程中的待办、已办统一在这里查看；具体审批动作回到 BPM 或对应业务页完成。</Typography.Text>
      </div>
      <Space wrap>
        <div className="bpm-approval-count-card">
          <ClockCircleOutlined/>
          <span>待办</span>
          <strong>{countLoading ? '-' : counts.todo}</strong>
        </div>
        <div className="bpm-approval-count-card">
          <CheckCircleOutlined/>
          <span>已办</span>
          <strong>{countLoading ? '-' : counts.done}</strong>
        </div>
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
      {countError && <Alert
        className="bpm-approval-count-error"
        type="warning"
        showIcon
        message={countError}
        action={<Button type="link" size="small" onClick={() => void loadCounts()}>重试</Button>}
      />}
      <Input.Search allowClear value={keyword} placeholder="搜索任务名称" onSearch={value => { setKeyword(value); setTablePage(1) }} onChange={event => { if (!event.target.value) { setKeyword(''); setTablePage(1) } }} style={{ width: 260 }}/>
      <Tabs
        className="bpm-approval-tabs"
        activeKey={view}
        onChange={changeView}
        items={[
          { key: 'todo', label: `待办任务 ${counts.todo}` },
          { key: 'done', label: `已办任务 ${counts.done}` }
        ]}
      />
    </header>

    {unauthorized
      ? <Result status="403" title="审批任务加载被拒绝" subTitle={error || '请确认当前账号是否具备 BPM 查询权限。'}/>
      : useTableLayout ? <BusinessTable<BpmTask> tableKey="bpm-approval-center-page-1" error={error} onReload={() => void loadFirstPage(true)}
        className="business-inbox-table"
        rowKey="id"

        columnsState={{ persistenceKey: 'crm-bpm-approval-table-columns', persistenceType: 'localStorage' }}
        loading={loading}
        dataSource={tasks}
        pagination={{ current: tablePage, pageSize: tablePageSize, total, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showQuickJumper: true, onChange: (page, size) => { setTablePage(page); setTablePageSize(size) } }}
        scroll={{ x: 1560 }}
        locale={{ emptyText: <Empty description={view === 'todo' ? '暂无待办审批' : '暂无已办审批'} /> }}
        columns={[
          { title: '任务', key: 'task', render: (_, task) => taskSubject(task), width: 220 },
          { title: '状态', key: 'status', dataIndex: 'status', width: 110, render: value => <Tag color={taskStatusColor(Number(value))}>{taskStatusLabel(Number(value))}</Tag> },
          { title: '流程名称', key: 'processName', width: 200, ellipsis: true, render: (_, task) => task.processInstance?.name || '-' },
          { title: '流程节点', key: 'node', dataIndex: 'name', width: 160, ellipsis: true, render: value => value || '流程节点' },
          { title: '流程摘要', key: 'summary', width: 300, ellipsis: true, render: (_, task) => {
            const business = briefSummary(task, briefs)
            return (business.length > 0 ? business : taskSummary(task)).join('；') || '-'
          } },
          { title: '发起人', key: 'startUser', width: 130, render: (_, task) => task.processInstance?.startUser?.nickname || '-' },
          { title: view === 'todo' ? '当前处理人' : '已处理人', key: 'assignee', width: 130, render: (_, task) => task.assigneeUser?.nickname || task.ownerUser?.nickname || '-' },
          { title: '流程发起时间', key: 'processCreateTime', width: 170, render: (_, task) => <DateTimeText value={task.processInstance?.createTime}/> },
          { title: '审批意见', key: 'reason', dataIndex: 'reason', width: 240, ellipsis: true, render: value => value || '-' },
          // 以下四列只服务于排查，默认收起（用户仍可在列设置里打开），
          // 否则表格要横向滚 2300px 才能看全，而审批人判断用的就是左边那几列。
          { title: '任务到达时间', key: 'taskCreateTime', dataIndex: 'createTime', width: 170, hideInTable: true, render: value => <DateTimeText value={value as BpmTask['createTime']}/> },
          { title: '任务完成时间', key: 'taskEndTime', dataIndex: 'endTime', width: 170, hideInTable: true, render: value => <DateTimeText value={value as BpmTask['endTime']}/> },
          { title: '处理耗时', key: 'duration', dataIndex: 'durationInMillis', width: 150, hideInTable: true, render: value => formatDuration(value as number | undefined) },
          { title: '表单名称', key: 'formName', dataIndex: 'formName', width: 180, ellipsis: true, hideInTable: true, render: value => value || '-' },
          { title: '操作', key: 'action', width: 88, fixed: 'right', hideInSetting: true, render: (_, task) => <Button type="link" onClick={() => selectTask(task)}>详细</Button> }
        ]}
      /> : <div className="business-inbox-layout bpm-approval-layout">
        <aside className="business-inbox-list-pane">
          {error && <Alert
            className="business-inbox-error"
            type="error"
            showIcon
            message={error}
            action={<Button size="small" onClick={() => { listRef.current?.scrollTo({ top: 0 }); void loadFirstPage() }}>重试</Button>}
          />}
          <div className="business-inbox-scroll" ref={listRef}>
            {loading && tasks.length === 0
              ? <Skeleton active paragraph={{ rows: 8 }}/>
              : !loading && tasks.length === 0 && !error
                ? <Empty description={view === 'todo' ? '暂无待办审批' : '暂无已办审批'}/>
                : <>
                {tasks.map(task => {
                  const active = task.id === selectedId
                  const business = briefSummary(task, briefs)
                  const summary = business.length > 0 ? business : taskSummary(task)
                  return <button
                    key={task.id}
                    type="button"
                    className={active ? 'business-inbox-item bpm-approval-item active' : 'business-inbox-item bpm-approval-item'}
                    onClick={() => selectTask(task)}
                  >
                    <div className="business-inbox-item-main">
                      <Avatar icon={<FileSearchOutlined/>}/>
                      <div className="business-inbox-item-copy">
                        <div className="business-inbox-item-title">
                          <strong>{briefTitle(task, briefs) || taskSubject(task)}</strong>
                          <Tag color={taskStatusColor(task.status)}>{taskStatusLabel(task.status)}</Tag>
                        </div>
                        <span>{task.name || '流程节点'} · 发起人：{task.processInstance?.startUser?.nickname || '-'}</span>
                        {/* 摘要拼不出来时给 '-'：绝不退回 processInstanceId，
                            那是内部 UUID，对审批人没有任何意义。 */}
                        <span>{summary[0] || task.formName || '-'}</span>
                      </div>
                    </div>
                    <div className="business-inbox-item-meta">
                      <Badge status={view === 'todo' ? 'processing' : 'default'}/>
                      <span>{view === 'todo' ? '到达' : '完成'} <DateTimeText value={view === 'todo' ? task.createTime : task.endTime}/></span>
                    </div>
                  </button>
                })}
                <div ref={sentinelRef} className="bpm-approval-load-sentinel" aria-hidden="true" />
                {loadingMore && <div className="bpm-approval-load-state"><Button type="text" size="small" loading>正在加载更多</Button></div>}
                {loadMoreError && <Alert
                  className="bpm-approval-load-error"
                  type="error"
                  showIcon
                  message={loadMoreError}
                  action={<Button size="small" onClick={() => void loadMore()}>重试</Button>}
                />}
                {!loadingMore && !loadMoreError && tasks.length >= total && total > 0 && (
                  <div className="bpm-approval-load-state">已加载全部 {total} 条</div>
                )}
              </>}
          </div>
        </aside>
        <main className="business-inbox-detail-pane">{detail}</main>
      </div>}

    <ResizableDetailDrawer
      desktopResizable={useTableLayout}
      title="审批任务"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
      width="100%"
      placement="right"
    >
      {detail}
    </ResizableDetailDrawer>
  </section>
}
