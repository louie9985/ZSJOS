import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Empty,
  Grid,
  Result,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Typography
} from 'antd'
import {
  AuditOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileSearchOutlined,
  LinkOutlined,
  ReloadOutlined
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import DateTimeText from '../components/DateTimeText'
import DetailFieldGrid from '../components/DetailFieldGrid'
import { api, ApiError, AuthenticationError, type BpmTask } from '../services/api'
import { useInboxTableLayout } from '../services/inboxLayout'
import { ProTable } from '@ant-design/pro-components'
import ResizableDetailDrawer from '../components/ResizableDetailDrawer'

type BpmTaskView = 'todo' | 'done'

const PAGE_SIZE = 20
const SALES_ORDER_PERMISSION_DENIED = 1_900_006_011
const LEAD_APPEAL_PERMISSION_DENIED = 1_900_003_041

const TASK_STATUS_LABELS: Record<number, string> = {
  [-2]: '已跳过',
  [-1]: '未开始',
  0: '待审批',
  1: '审批中',
  2: '已通过',
  3: '已拒绝',
  4: '已取消',
  5: '已退回',
  7: '通过中'
}

const TASK_STATUS_COLORS: Record<number, string> = {
  [-2]: 'default',
  [-1]: 'default',
  0: 'processing',
  1: 'processing',
  2: 'success',
  3: 'error',
  4: 'default',
  5: 'warning',
  7: 'processing'
}

function taskStatusLabel(status?: number) {
  return status === undefined ? '未知状态' : TASK_STATUS_LABELS[status] || `状态 ${status}`
}

function taskStatusColor(status?: number) {
  return status === undefined ? 'default' : TASK_STATUS_COLORS[status] || 'default'
}

function taskSubject(task: BpmTask) {
  return task.processInstance?.name?.trim() || task.name?.trim() || '审批任务'
}

function taskSummary(task?: BpmTask) {
  const summary = task?.processInstance?.summary || []
  return summary
    .map(item => [item.key, item.value].filter(Boolean).join('：'))
    .filter(Boolean)
}

function BpmTaskDetail({
  task,
  view,
  canUpdate,
  locating,
  onOpenBusiness
}: {
  task?: BpmTask
  view: BpmTaskView
  canUpdate: boolean
  locating: boolean
  onOpenBusiness: (task: BpmTask) => void
}) {
  if (!task) return <Empty description="从左侧选择一条审批任务"/>
  const summary = taskSummary(task)
  const startUser = task.processInstance?.startUser?.nickname || '-'
  const assignee = task.assigneeUser?.nickname || task.ownerUser?.nickname || '-'
  const actionText = view === 'todo' ? '进入业务审批' : '查看业务单据'
  return <article className="business-inbox-detail bpm-approval-detail">
    <header className="business-inbox-detail-hero bpm-approval-detail-hero">
      <Avatar size={44} icon={<AuditOutlined/>}/>
      <div className="business-inbox-detail-heading">
        <div>
          <Space size={8} wrap>
            <Typography.Title level={4}>{taskSubject(task)}</Typography.Title>
            <Tag color={taskStatusColor(task.status)}>{taskStatusLabel(task.status)}</Tag>
          </Space>
          <Typography.Text type="secondary">{task.name || '流程节点'}</Typography.Text>
        </div>
      </div>
      <Button
        type={view === 'todo' && canUpdate ? 'primary' : 'default'}
        icon={<LinkOutlined/>}
        loading={locating}
        onClick={() => onOpenBusiness(task)}
      >
        {actionText}
      </Button>
    </header>

    <section className="business-inbox-card bpm-approval-card">
      <Typography.Text type="secondary">流程摘要</Typography.Text>
      {summary.length > 0
        ? <div className="bpm-approval-summary-list">
          {summary.map((item, index) => <Tag key={`${item}-${index}`}>{item}</Tag>)}
        </div>
        : <Typography.Paragraph type="secondary">该流程没有返回可展示的摘要字段。</Typography.Paragraph>}
    </section>

    <section className="business-inbox-card bpm-approval-card">
      <DetailFieldGrid columns={2} items={[
        { key: 'processName', label: '流程名称', value: task.processInstance?.name || '-' },
        { key: 'taskName', label: '当前节点', value: task.name || '-' },
        { key: 'startUser', label: '发起人', value: startUser },
        { key: 'assignee', label: view === 'todo' ? '当前处理人' : '已处理人', value: assignee },
        { key: 'processStartedAt', label: '发起时间', value: <DateTimeText value={task.processInstance?.createTime}/> },
        { key: 'taskCreatedAt', label: view === 'todo' ? '到达时间' : '任务到达', value: <DateTimeText value={task.createTime}/> },
        ...(view === 'done'
          ? [{ key: 'taskEndedAt', label: '完成时间', value: <DateTimeText value={task.endTime}/> }]
          : []),
        { key: 'formName', label: '表单名称', value: task.formName || '-' },
        { key: 'processInstanceId', label: '流程实例', value: task.processInstanceId, span: 2 as const },
        { key: 'taskId', label: '任务编号', value: task.id, span: 2 as const },
        ...(task.reason
          ? [{ key: 'reason', label: '审批意见', value: task.reason, span: 2 as const }]
          : [])
      ]}/>
    </section>

    {view === 'todo' && <Alert
      type="info"
      showIcon
      message="审批动作在业务详情或完整 BPM 表单中完成"
      description={canUpdate
        ? '当前页面负责承载审批任务入口。涉及动态表单、下一节点审批人、加签、转办等复杂动作时，不在列表页直接复制 BPM 状态机。'
        : '当前账号没有 bpm:task:update 权限，只能查看审批任务。'}
    />}
  </article>
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
  const [counts, setCounts] = useState<Record<BpmTaskView, number>>({ todo: 0, done: 0 })
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [countLoading, setCountLoading] = useState(true)
  const [error, setError] = useState('')
  const [loadMoreError, setLoadMoreError] = useState('')
  const [countError, setCountError] = useState('')
  const [unauthorized, setUnauthorized] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [locatingTaskId, setLocatingTaskId] = useState<string>()
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

  const loadCounts = useCallback(async () => {
    if (!canQuery) return
    const seq = ++countSeq.current
    setCountLoading(true)
    setCountError('')
    try {
      const [todo, done] = await Promise.all([
        api.bpmTaskPage('todo', { pageNo: 1, pageSize: 1 }),
        api.bpmTaskPage('done', { pageNo: 1, pageSize: 1 })
      ])
      if (seq !== countSeq.current) return
      setCounts({ todo: todo.total, done: done.total })
    } catch (loadError) {
      if (seq !== countSeq.current) return
      setCountError(loadError instanceof Error ? loadError.message : '审批数量加载失败')
    } finally {
      if (seq === countSeq.current) setCountLoading(false)
    }
  }, [canQuery])

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
      const result = await api.bpmTaskPage(view, { pageNo: 1, pageSize: PAGE_SIZE })
      if (seq !== requestSeq.current) return
      setTasks(result.list)
      setTotal(result.total)
      setLoadedPage(1)
      setCounts(current => ({ ...current, [view]: result.total }))
      setSelectedId(current => preserveSelection && result.list.some(item => item.id === current) ? current : result.list[0]?.id)
    } catch (loadError) {
      if (seq !== requestSeq.current) return
      setTasks([])
      setSelectedId(undefined)
      setTotal(0)
      setLoadedPage(0)
      setUnauthorized(loadError instanceof AuthenticationError)
      setError(loadError instanceof Error ? loadError.message : '审批任务加载失败')
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [canQuery, view])

  const loadMore = useCallback(async () => {
    if (!canQuery || loading || loadingMoreRef.current || tasks.length >= total) return
    loadingMoreRef.current = true
    setLoadingMore(true)
    setLoadMoreError('')
    const seq = requestSeq.current
    const nextPage = loadedPage + 1
    try {
      const result = await api.bpmTaskPage(view, { pageNo: nextPage, pageSize: PAGE_SIZE })
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
  }, [canQuery, loadedPage, loading, tasks.length, total, view])

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
    const sentinel = sentinelRef.current
    const root = listRef.current
    if (!sentinel || !root || loading || loadMoreError || tasks.length >= total) return
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadMore()
    }, { root, rootMargin: "240px 0px" })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [loadMore, loadMoreError, loading, tasks.length, total])
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

  const openBusiness = async (task: BpmTask) => {
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
          && [SALES_ORDER_PERMISSION_DENIED, LEAD_APPEAL_PERMISSION_DENIED].includes(locateError.code))) {
        message.info('当前账号无权打开该业务审批')
        return
      }
      message.error('审批任务定位失败，请重试')
    } finally {
      setLocatingTaskId(undefined)
    }
  }

  if (!canQuery) {
    return <section className="workspace-page bpm-approval-page">
      <Result status="403" title="无权访问审批中心" subTitle="当前账号缺少 bpm:task:query 权限。"/>
    </section>
  }

  const detail = <BpmTaskDetail
    task={selectedTask}
    view={view}
    canUpdate={canUpdate}
    locating={!!selectedTask && locatingTaskId === selectedTask.id}
    onOpenBusiness={openBusiness}
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
      : useTableLayout ? <ProTable<BpmTask>
        className="business-inbox-table"
        rowKey="id"
        search={false}
        options={{ density: true, fullScreen: true, setting: true }}
        columnsState={{ persistenceKey: 'crm-bpm-approval-table-columns', persistenceType: 'localStorage' }}
        loading={loading}
        dataSource={tasks}
        pagination={false}
        scroll={{ x: 1000 }}
        locale={{ emptyText: <Empty description={view === 'todo' ? '暂无待办审批' : '暂无已办审批'} /> }}
        columns={[
          { title: '任务', render: (_, task) => taskSubject(task), width: 220 },
          { title: '流程节点', dataIndex: 'name', render: value => value || '流程节点' },
          { title: '发起人', render: (_, task) => task.processInstance?.startUser?.nickname || '-' },
          { title: view === 'todo' ? '到达时间' : '完成时间', render: (_, task) => <DateTimeText value={view === 'todo' ? task.createTime : task.endTime} /> },
          { title: '操作', width: 88, fixed: 'right', render: (_, task) => <Button type="link" onClick={() => selectTask(task)}>详细</Button> }
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
                  const summary = taskSummary(task)
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
                          <strong>{taskSubject(task)}</strong>
                          <Tag color={taskStatusColor(task.status)}>{taskStatusLabel(task.status)}</Tag>
                        </div>
                        <span>{task.name || '流程节点'} · 发起人：{task.processInstance?.startUser?.nickname || '-'}</span>
                        <span>{summary[0] || task.formName || task.processInstanceId}</span>
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
