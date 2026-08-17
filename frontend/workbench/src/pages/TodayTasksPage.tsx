import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Pagination, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { CheckCircleOutlined, ClockCircleOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type BpmTask, type BusinessTask, type BusinessTaskBucket, type PageResult } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 6
type TaskView = 'pending' | 'done'

export const canQueryBpmTasks = (permissions: readonly string[]) => permissions.includes('bpm:task:query')

const errorText = (error: unknown, fallback: string) => (error instanceof ApiError && error.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : error instanceof Error ? error.message : fallback)

function BusinessTaskPanel({ onOpenAssignment }: { onOpenAssignment: () => void }) {
  const navigate = useNavigate()
  const [view, setView] = useState<TaskView>('pending')
  const [bucket, setBucket] = useState<BusinessTaskBucket>('today')
  const [pageNo, setPageNo] = useState(1)
  const [page, setPage] = useState<PageResult<BusinessTask>>({
    list: [],
    total: 0
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setPage(
        await api.businessTaskList({
          status: view,
          bucket: view === 'pending' ? bucket : undefined,
          pageNo,
          pageSize: PAGE_SIZE
        })
      )
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
      navigate(APP_ROUTES.LEAD_MANAGEMENT, {
        state: { leadId: task.bizId, openFollowUp: true, relationScope: 'owned' }
      })
      return
    }
    if (task.actionCode === 'OPEN_SALES_ORDER_REVISION') {
      navigate(`${APP_ROUTES.MY_SALES_ORDERS}?orderId=${task.bizId}`)
      return
    }
    if (task.actionCode === 'OPEN_WORK_PLAN_ITEM' || task.actionCode === 'REVIEW_WORK_PLAN_ITEM') {
      navigate(`${APP_ROUTES.WORK_PLANS}?itemId=${task.bizId}`)
    }
  }

  return (
    <section className="task-center-panel" aria-label="业务任务">
      <header className="task-panel-header">
        <div>
          <Typography.Title level={4}>业务任务</Typography.Title>
          <Typography.Text type="secondary">中世健业务待办</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label="刷新业务任务" onClick={() => void load()} />
      </header>
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
          options={[
            { label: '无截止', value: 'unscheduled' },
            { label: '逾期', value: 'overdue' },
            { label: '今日', value: 'today' },
            { label: '未来', value: 'future' }
          ]}
        />
      )}
      {error && (
        <Alert
          className="task-panel-alert"
          type={error.includes('暂无权限') ? 'warning' : 'error'}
          showIcon
          title={error}
          action={
            <Button size="small" onClick={() => void load()}>
              重试
            </Button>
          }
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
                  {task.actionable && view === 'pending' && <Button type="text" icon={<RightOutlined />} aria-label="处理业务任务" onClick={() => open(task)} />}
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

function BpmTaskPanel() {
  const navigate = useNavigate()
  const [view, setView] = useState<TaskView>('pending')
  const [pageNo, setPageNo] = useState(1)
  const [page, setPage] = useState<PageResult<BpmTask>>({ list: [], total: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setPage(
        await api.bpmTaskPage(view === 'pending' ? 'todo' : 'done', {
          pageNo,
          pageSize: PAGE_SIZE
        })
      )
    } catch (loadError) {
      setError(errorText(loadError, '审批任务加载失败'))
    } finally {
      setLoading(false)
    }
  }, [pageNo, view])
  useEffect(() => {
    void load()
  }, [load])
  return (
    <section className="task-center-panel" aria-label="审批任务">
      <header className="task-panel-header">
        <div>
          <Typography.Title level={4}>审批任务</Typography.Title>
          <Typography.Text type="secondary">BPM 流程待办</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label="刷新审批任务" onClick={() => void load()} />
      </header>
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
      {error && (
        <Alert
          className="task-panel-alert"
          type={error.includes('暂无权限') ? 'warning' : 'error'}
          showIcon
          title={error}
          action={
            <Button size="small" onClick={() => void load()}>
              重试
            </Button>
          }
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
                    <Typography.Text strong>{task.processInstance?.name || task.name}</Typography.Text>
                    <Space wrap>
                      <Typography.Text type="secondary">{task.name}</Typography.Text>
                      <Typography.Text type="secondary">{task.processInstance?.startUser?.nickname || '流程发起人'}</Typography.Text>
                      <Typography.Text type="secondary">{formatTimestamp(task.endTime || task.createTime)}</Typography.Text>
                    </Space>
                  </div>
                  {view === 'pending' && <Button type="text" icon={<RightOutlined />} aria-label="查看审批" onClick={() => navigate(`${APP_ROUTES.BPM_TODO}?taskId=${encodeURIComponent(task.id)}`)} />}
                </div>
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={view === 'pending' ? '暂无审批待办' : '暂无审批已办'} />
            )}
          </div>
          {page.total > PAGE_SIZE && <Pagination size="small" current={pageNo} pageSize={PAGE_SIZE} total={page.total} showSizeChanger={false} onChange={setPageNo} />}
        </>
      )}
    </section>
  )
}

export default function TodayTasksPage({ permissions, onOpenAssignment }: { permissions: string[]; onOpenAssignment: () => void }) {
  const showBpmTasks = canQueryBpmTasks(permissions)
  return (
    <section className="workspace-page today-tasks-page">
      <div className={`task-center-grid${showBpmTasks ? '' : ' single-panel'}`}>
        <BusinessTaskPanel onOpenAssignment={onOpenAssignment} />
        {showBpmTasks && <BpmTaskPanel />}
      </div>
    </section>
  )
}
