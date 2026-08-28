import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Pagination, Segmented, Skeleton, Space, Statistic, Tag, Typography } from 'antd'
import { CheckCircleOutlined, ClockCircleOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type BusinessTask, type BusinessTaskBucket, type BusinessTaskSummary, type PageResult } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 6
type TaskView = 'pending' | 'done'

export const canQueryBpmTasks = (permissions: readonly string[]) => permissions.includes('bpm:task:query')

const errorText = (error: unknown, fallback: string) => (error instanceof ApiError && error.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : error instanceof Error ? error.message : fallback)

const bucketLabels: Record<BusinessTaskBucket, string> = {
  overdue: '逾期',
  today: '今日',
  future: '未来',
  unscheduled: '无截止'
}

const bucketOrder: BusinessTaskBucket[] = ['overdue', 'today', 'future', 'unscheduled']
const workPlanActions = new Set(['OPEN_WORK_TASK', 'CONFIRM_WORK_TASK', 'SUMMARIZE_WORK_PLAN'])

function BusinessTaskPanel({ onOpenAssignment }: { onOpenAssignment: () => void }) {
  const navigate = useNavigate()
  const [view, setView] = useState<TaskView>('pending')
  const [bucket, setBucket] = useState<BusinessTaskBucket>('today')
  const [pageNo, setPageNo] = useState(1)
  const [page, setPage] = useState<PageResult<BusinessTask>>({ list: [], total: 0 })
  const [summary, setSummary] = useState<BusinessTaskSummary>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [pageResult, summaryResult] = await Promise.all([
        view === 'pending'
          ? api.businessTaskList({ status: view, bucket, pageNo, pageSize: PAGE_SIZE })
          : api.businessTaskList({ status: view, pageNo, pageSize: PAGE_SIZE }),
        view === 'pending' ? api.businessTaskSummary() : Promise.resolve(undefined)
      ])
      setPage(pageResult)
      if (summaryResult) setSummary(summaryResult)
    } catch (loadError) {
      setError(errorText(loadError, '业务任务加载失败'))
    } finally {
      setLoading(false)
    }
  }, [bucket, pageNo, view])

  useEffect(() => {
    void load()
  }, [load])

  const open = (task: BusinessTask) => {
    if (!task.actionable) return
    if (task.actionCode === 'OPEN_LEAD_ASSIGNMENT') {
      onOpenAssignment()
      return
    }
    if (task.actionCode === 'OPEN_LEAD_FOLLOW_UP') {
      navigate(APP_ROUTES.LEAD_MANAGEMENT, { state: { leadId: task.bizId, openFollowUp: true } })
      return
    }
    if (task.actionCode === 'OPEN_SALES_ORDER_REVISION') {
      navigate(`${APP_ROUTES.MY_SALES_ORDERS}?orderId=${task.bizId}`)
      return
    }
    if (task.actionCode?.startsWith('OPEN_STUDENT_') && task.serviceRelationId) {
      navigate(APP_ROUTES.MY_STUDENTS, {
        state: {
          serviceRelationId: task.serviceRelationId,
          openContactTask: true,
          taskId: task.targetRecordId,
          taskType: task.taskType
        }
      })
      return
    }
  }

  const completeBirthdayCare = async (task: BusinessTask) => {
    try {
      await api.completeBirthdayCare(task.id)
      await load()
    } catch (taskError) {
      setError(errorText(taskError, '生日关怀完成失败'))
    }
  }

  const pendingSummary = useMemo(() => ({
    overdue: summary?.overdue ?? 0,
    today: summary?.today ?? 0,
    future: summary?.future ?? 0,
    unscheduled: summary?.unscheduled ?? 0
  }), [summary])

  return (
    <section className="task-center-panel" aria-label="业务任务">
      <header className="task-panel-header">
        <div>
          <Typography.Title level={4}>今日待办</Typography.Title>
          <Typography.Text type="secondary">中世健业务待办</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label="刷新业务任务" onClick={() => void load()} />
      </header>
      {view === 'pending' && (
        <div className="task-summary-grid" aria-label="业务待办汇总">
          {bucketOrder.map((item) => (
            <Card key={item} size="small" className="task-summary-card" bordered={false}>
              <Statistic title={bucketLabels[item]} value={pendingSummary[item]} />
            </Card>
          ))}
        </div>
      )}
      <Segmented
        block
        value={view}
        onChange={(value) => {
          setView(value as TaskView)
          setPageNo(1)
        }}
        options={[
          { label: '待办', value: 'pending' },
          { label: '已办', value: 'done' }
        ]}
      />
      {view === 'pending' && (
        <Segmented
          className="task-bucket-control"
          block
          value={bucket}
          onChange={(value) => {
            setBucket(value as BusinessTaskBucket)
            setPageNo(1)
          }}
          options={bucketOrder.map((item) => ({
            label: `${bucketLabels[item]}${pendingSummary[item] ? ` ${pendingSummary[item]}` : ''}`,
            value: item
          }))}
        />
      )}
      {error && (
        <Alert
          className="task-panel-alert"
          type={error.includes('暂无权限') ? 'warning' : 'error'}
          showIcon
          title={error}
          action={<Button size="small" onClick={() => void load()}>重试</Button>}
        />
      )}
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <>
          <div className="task-panel-list" role="list">
            {page.list.length ? (
              page.list.map((task) => (
                <div className="task-panel-item" role="listitem" key={task.id}>
                  <span className="task-panel-item-icon">{view === 'pending' ? <ClockCircleOutlined /> : <CheckCircleOutlined />}</span>
                  <div className="task-panel-item-copy">
                    <Space wrap>
                      <Typography.Text strong>{task.title}</Typography.Text>
                      {task.overdue && <Tag color="error">已逾期</Tag>}
                      {task.status === 'cancelled' && <Tag>已取消</Tag>}
                    </Space>
                    <Space wrap>
                      <Typography.Text type="secondary">{task.summary || '业务任务'}</Typography.Text>
                      <Typography.Text type="secondary">{formatTimestamp(task.dueAt || task.completedAt || task.cancelledAt, '无时间')}</Typography.Text>
                    </Space>
                  </div>
                  {task.actionCode && workPlanActions.has(task.actionCode) && view === 'pending' ? (
                    <Tag>已搁置</Tag>
                  ) : task.actionCode === 'COMPLETE_BIRTHDAY_CARE' && view === 'pending' ? (
                    <Button type="text" icon={<CheckCircleOutlined />} aria-label="完成生日关怀" onClick={() => void completeBirthdayCare(task)} />
                  ) : task.actionable && view === 'pending' ? (
                    <Button type="text" icon={<RightOutlined />} aria-label="处理业务任务" onClick={() => open(task)} />
                  ) : null}
                </div>
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={view === 'pending' ? '暂无业务待办' : '暂无业务已办'} />
            )}
          </div>
          {page.total > PAGE_SIZE && <Pagination size="small" current={pageNo} pageSize={PAGE_SIZE} total={page.total} showSizeChanger={false} onChange={setPageNo} />}
        </>
      )}
    </section>
  )
}

function BpmTaskSummaryPanel() {
  const navigate = useNavigate()
  const [count, setCount] = useState<number>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const page = await api.bpmTaskPage('todo', { pageNo: 1, pageSize: 1 })
      setCount(page.total)
    } catch (loadError) {
      setError(errorText(loadError, '审批数量加载失败'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const openBpmTodo = () => navigate(APP_ROUTES.BPM_TODO)

  return (
    <section className="task-center-panel" aria-label="审批待办">
      <header className="task-panel-header">
        <div>
          <Typography.Title level={4}>审批待办</Typography.Title>
          <Typography.Text type="secondary">跳转至工作流程待办任务</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label="刷新审批数量" onClick={() => void load()} />
      </header>
      {error && (
        <Alert
          className="task-panel-alert"
          type={error.includes('暂无权限') ? 'warning' : 'error'}
          showIcon
          title={error}
          action={<Button size="small" onClick={() => void load()}>重试</Button>}
        />
      )}
      <Card
        className="task-bpm-summary-card"
        bordered={false}
        onClick={openBpmTodo}
        role="button"
        tabIndex={0}
        aria-label="前往工作流程待办任务"
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') openBpmTodo()
        }}
      >
        <Statistic title="未完成审批" value={loading ? 0 : count || 0} />
        <Typography.Text type="secondary">点击进入工作流程 - 待办任务</Typography.Text>
      </Card>
    </section>
  )
}

export default function TodayTasksPage({ permissions, onOpenAssignment }: { permissions: string[]; onOpenAssignment: () => void }) {
  const showBpmTasks = canQueryBpmTasks(permissions)
  return (
    <section className="workspace-page today-tasks-page">
      <div className={`task-center-grid${showBpmTasks ? '' : ' single-panel'}`}>
        <BusinessTaskPanel onOpenAssignment={onOpenAssignment} />
        {showBpmTasks && <BpmTaskSummaryPanel />}
      </div>
    </section>
  )
}
