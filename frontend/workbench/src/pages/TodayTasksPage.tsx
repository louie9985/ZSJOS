import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, List, Pagination, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { CheckCircleOutlined, ClockCircleOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type BpmTask, type BusinessTask, type BusinessTaskBucket, type PageResult } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 6
type TaskView = 'pending' | 'done'

const errorText = (error: unknown, fallback: string) => error instanceof ApiError && error.code === 403
  ? '暂无权限，请联系管理员配置对应功能权限'
  : error instanceof Error ? error.message : fallback

function BusinessTaskPanel({ onOpenAssignment }: { onOpenAssignment: () => void }) {
  const navigate = useNavigate()
  const [view, setView] = useState<TaskView>('pending')
  const [bucket, setBucket] = useState<BusinessTaskBucket>('today')
  const [pageNo, setPageNo] = useState(1)
  const [page, setPage] = useState<PageResult<BusinessTask>>({ list: [], total: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      setPage(await api.businessTaskList({ status: view, bucket: view === 'pending' ? bucket : undefined, pageNo, pageSize: PAGE_SIZE }))
    } catch (loadError) { setError(errorText(loadError, '业务任务加载失败')) }
    finally { setLoading(false) }
  }, [bucket, pageNo, view])
  useEffect(() => { void load() }, [load])

  const open = (task: BusinessTask) => {
    if (!task.actionable) return
    if (task.actionCode === 'OPEN_LEAD_ASSIGNMENT') { onOpenAssignment(); return }
    if (task.actionCode === 'OPEN_LEAD_FOLLOW_UP') {
      navigate(APP_ROUTES.OWNED_LEADS, { state: { leadId: task.bizId, openFollowUp: true } }); return
    }
    if (task.actionCode === 'OPEN_WORK_PLAN_ITEM' || task.actionCode === 'REVIEW_WORK_PLAN_ITEM') {
      navigate(`${APP_ROUTES.WORK_PLANS}?itemId=${task.bizId}`)
    }
  }

  return <section className="task-center-panel" aria-label="业务任务">
    <header className="task-panel-header">
      <div><Typography.Title level={4}>业务任务</Typography.Title><Typography.Text type="secondary">中世健业务待办</Typography.Text></div>
      <Button icon={<ReloadOutlined/>} aria-label="刷新业务任务" onClick={() => void load()}/>
    </header>
    <Segmented block value={view} onChange={value => { setView(value as TaskView); setPageNo(1) }} options={[{ label: '待办', value: 'pending' }, { label: '已办', value: 'done' }]}/>
    {view === 'pending' && <Segmented className="task-bucket-control" block value={bucket} onChange={value => { setBucket(value as BusinessTaskBucket); setPageNo(1) }}
      options={[{ label: '无截止', value: 'unscheduled' }, { label: '逾期', value: 'overdue' }, { label: '今日', value: 'today' }, { label: '未来', value: 'future' }]}/>}
    {error && <Alert className="task-panel-alert" type={error.includes('暂无权限') ? 'warning' : 'error'} showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {loading ? <Skeleton active paragraph={{ rows: 6 }}/> : <>
      <List className="task-panel-list" dataSource={page.list} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={view === 'pending' ? '暂无业务待办' : '暂无业务已办'}/> }}
        renderItem={task => <List.Item actions={task.actionable && view === 'pending' ? [<Button key="open" type="text" icon={<RightOutlined/>} aria-label="处理业务任务" onClick={() => open(task)}/>] : undefined}>
          <List.Item.Meta avatar={view === 'pending' ? <ClockCircleOutlined/> : <CheckCircleOutlined/>}
            title={<Space wrap><span>{task.title}</span>{task.overdue && <Tag color="error">已逾期</Tag>}{task.status === 'cancelled' && <Tag>已取消</Tag>}</Space>}
            description={<Space wrap><span>{task.summary || '业务任务'}</span><span>{formatTimestamp(task.dueAt || task.completedAt || task.cancelledAt, '无时间')}</span></Space>}/>
        </List.Item>}/>
      {page.total > PAGE_SIZE && <Pagination size="small" current={pageNo} pageSize={PAGE_SIZE} total={page.total} showSizeChanger={false} onChange={setPageNo}/>}
    </>}
  </section>
}

function BpmTaskPanel() {
  const navigate = useNavigate()
  const [view, setView] = useState<TaskView>('pending')
  const [pageNo, setPageNo] = useState(1)
  const [page, setPage] = useState<PageResult<BpmTask>>({ list: [], total: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setPage(await api.bpmTaskPage(view === 'pending' ? 'todo' : 'done', { pageNo, pageSize: PAGE_SIZE })) }
    catch (loadError) { setError(errorText(loadError, '审批任务加载失败')) }
    finally { setLoading(false) }
  }, [pageNo, view])
  useEffect(() => { void load() }, [load])
  return <section className="task-center-panel" aria-label="审批任务">
    <header className="task-panel-header">
      <div><Typography.Title level={4}>审批任务</Typography.Title><Typography.Text type="secondary">BPM 流程待办</Typography.Text></div>
      <Button icon={<ReloadOutlined/>} aria-label="刷新审批任务" onClick={() => void load()}/>
    </header>
    <Segmented block value={view} onChange={value => { setView(value as TaskView); setPageNo(1) }} options={[{ label: '待办', value: 'pending' }, { label: '已办', value: 'done' }]}/>
    {error && <Alert className="task-panel-alert" type={error.includes('暂无权限') ? 'warning' : 'error'} showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {loading ? <Skeleton active paragraph={{ rows: 6 }}/> : <>
      <List className="task-panel-list" dataSource={page.list} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={view === 'pending' ? '暂无审批待办' : '暂无审批已办'}/> }}
        renderItem={task => <List.Item actions={view === 'pending' ? [<Button key="open" type="text" icon={<RightOutlined/>} aria-label="查看审批" onClick={() => navigate(`${APP_ROUTES.BPM_TODO}?taskId=${encodeURIComponent(task.id)}`)}/>] : undefined}>
          <List.Item.Meta avatar={view === 'pending' ? <ClockCircleOutlined/> : <CheckCircleOutlined/>}
            title={task.processInstance?.name || task.name}
            description={<Space wrap><span>{task.name}</span><span>{task.processInstance?.startUser?.nickname || '流程发起人'}</span><span>{formatTimestamp(task.endTime || task.createTime)}</span></Space>}/>
        </List.Item>}/>
      {page.total > PAGE_SIZE && <Pagination size="small" current={pageNo} pageSize={PAGE_SIZE} total={page.total} showSizeChanger={false} onChange={setPageNo}/>}
    </>}
  </section>
}

export default function TodayTasksPage({ onOpenAssignment }: { onOpenAssignment: () => void }) {
  return <section className="workspace-page today-tasks-page">
    <div className="task-center-grid"><BusinessTaskPanel onOpenAssignment={onOpenAssignment}/><BpmTaskPanel/></div>
  </section>
}
