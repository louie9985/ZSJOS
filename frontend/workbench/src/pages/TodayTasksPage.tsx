import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Badge, Button, Calendar, Card, Empty, Pagination, Segmented, Skeleton, Space, Statistic, Tag, Typography } from 'antd'
import { CalendarOutlined, CheckCircleOutlined, ClockCircleOutlined, NotificationOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import zhCNCalendarLocale from 'antd/es/calendar/locale/zh_CN'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type Announcement, type BusinessTask, type BusinessTaskBucket, type BusinessTaskSummary, type PageResult } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

const PAGE_SIZE = 6
const ANNOUNCEMENT_LIMIT = 5
type TaskView = 'pending' | 'done'

export const canQueryBpmTasks = (permissions: readonly string[]) => permissions.includes('bpm:task:query')
export const canReadAnnouncements = (permissions: readonly string[]) => permissions.includes('system:notice:read')
export const canOpenAllCalendar = (permissions: readonly string[]) => permissions.includes('zsjos:media-calendar:all-query')

const errorText = (error: unknown, fallback: string) =>
  error instanceof ApiError && error.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : error instanceof Error ? error.message : fallback

const bucketLabels: Record<BusinessTaskBucket, string> = {
  overdue: '逾期',
  today: '今日',
  future: '未来',
  unscheduled: '无截止'
}

const bucketOrder: BusinessTaskBucket[] = ['overdue', 'today', 'future', 'unscheduled']
const workPlanActions = new Set(['OPEN_WORK_TASK', 'CONFIRM_WORK_TASK', 'SUMMARIZE_WORK_PLAN'])
const homeCalendarLocale = {
  ...zhCNCalendarLocale,
  lang: {
    ...zhCNCalendarLocale.lang,
    shortMonths: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'],
    shortWeekDays: ['日', '一', '二', '三', '四', '五', '六']
  }
}
const emptySummary: BusinessTaskSummary = {
  overdue: 0,
  today: 0,
  future: 0,
  unscheduled: 0
}
const placeholderStatCards = Array.from({ length: 10 }, (_, index) => `待配置指标 ${index + 1}`)

function PlaceholderStatCard({ label }: { label: string }) {
  return <Card className="home-stat-card placeholder" bordered={false}>
    <Typography.Text type="secondary">{label}</Typography.Text>
    <Statistic value="--" />
    <Typography.Text type="secondary">暂未接入数据</Typography.Text>
  </Card>
}

function SummaryRegion({
  businessSummary,
  bpmCount,
  showBpmTasks,
  loading,
  businessError,
  bpmError,
  onRefresh
}: {
  businessSummary?: BusinessTaskSummary
  bpmCount?: number
  showBpmTasks: boolean
  loading: boolean
  businessError: string
  bpmError: string
  onRefresh: () => Promise<void>
}) {
  const navigate = useNavigate()
  const businessCount = bucketOrder.reduce((total, bucket) => total + (businessSummary?.[bucket] ?? 0), 0)

  return (
    <section className="home-summary-region" aria-label="待办概览">
      <Card className="home-stat-card business" bordered={false}>
        <div className="home-stat-card-head">
          <Typography.Text type="secondary">业务待办</Typography.Text>
          <Button type="text" icon={<ReloadOutlined />} aria-label="刷新待办计数" onClick={() => void onRefresh()} />
        </div>
        {loading && !businessSummary ? <Skeleton active paragraph={false} /> : <Statistic value={businessCount} suffix="项" />}
        {businessError && <Typography.Text type="danger">{businessError}</Typography.Text>}
        <div className="home-stat-breakdown">
          {bucketOrder.map((bucket) => (
            <span key={bucket}>
              {bucketLabels[bucket]} {businessSummary?.[bucket] ?? 0}
            </span>
          ))}
        </div>
      </Card>
      {showBpmTasks && (
        <Card
          className="home-stat-card approval"
          bordered={false}
          role="button"
          tabIndex={0}
          onClick={() => navigate(APP_ROUTES.BPM_TODO)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') navigate(APP_ROUTES.BPM_TODO)
          }}
        >
          <div className="home-stat-card-head">
            <Typography.Text type="secondary">审批待办</Typography.Text>
            <RightOutlined aria-hidden />
          </div>
          {loading && bpmCount === undefined ? <Skeleton active paragraph={false} /> : <Statistic value={bpmCount ?? 0} suffix="项" />}
          {bpmError && <Typography.Text type="danger">{bpmError}</Typography.Text>}
          <Typography.Text type="secondary">工作流程待办任务</Typography.Text>
        </Card>
      )}
      {!showBpmTasks && <PlaceholderStatCard label="审批待办" />}
      {placeholderStatCards.map(label => <PlaceholderStatCard key={label} label={label} />)}
    </section>
  )
}

function BusinessTaskPanel({
  summary,
  onOpenAssignment,
  onRefreshSummary
}: {
  summary?: BusinessTaskSummary
  onOpenAssignment: () => void
  onRefreshSummary: () => Promise<void>
}) {
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
          ...(view === 'pending' ? { bucket } : {}),
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

  const refresh = async () => {
    await Promise.all([load(), onRefreshSummary()])
  }

  const open = (task: BusinessTask) => {
    if (!task.actionable) return
    if (task.actionCode === 'OPEN_LEAD_ASSIGNMENT') {
      onOpenAssignment()
      return
    }
    if (task.actionCode === 'OPEN_LEAD_FOLLOW_UP') {
      navigate(APP_ROUTES.LEAD_MANAGEMENT, {
        state: { leadId: task.bizId, openFollowUp: true }
      })
      return
    }
    if (task.actionCode === 'OPEN_LEAD_SUBMITTER_ASSIST') {
      navigate(APP_ROUTES.LEAD_MANAGEMENT, { state: { leadId: task.bizId } })
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
    }
  }

  const completeEmployeeReminder = async (task: BusinessTask) => {
    try {
      await api.completeEmployeeReminder(task.id)
      await refresh()
    } catch (taskError) {
      setError(errorText(taskError, '员工提醒完成失败'))
    }
  }

  const pendingSummary = useMemo(() => summary ?? emptySummary, [summary])

  return (
    <section className="home-panel home-business-panel" aria-label="业务待办">
      <header className="home-panel-header">
        <div>
          <Typography.Title level={4}>业务待办</Typography.Title>
          <Typography.Text type="secondary">按截止时间查看和处理任务</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label="刷新业务任务" onClick={() => void refresh()} />
      </header>
      <div className="home-task-controls">
        <Segmented
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
      </div>
      {error && (
        <Alert
          className="task-panel-alert"
          type={error.includes('暂无权限') ? 'warning' : 'error'}
          showIcon
          title={error}
          action={
            <Button size="small" onClick={() => void refresh()}>
              重试
            </Button>
          }
        />
      )}
      {loading ? (
        <Skeleton active paragraph={{ rows: 5 }} />
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
                  ) : task.actionCode && ['COMPLETE_BIRTHDAY_CARE', 'COMPLETE_EMPLOYEE_CONTRACT_EXPIRY', 'COMPLETE_EMPLOYEE_ENTRY_ANNIVERSARY'].includes(task.actionCode) && view === 'pending' ? (
                    <Button type="text" icon={<CheckCircleOutlined />} aria-label="完成员工提醒" onClick={() => void completeEmployeeReminder(task)} />
                  ) : task.actionable && view === 'pending' ? (
                    <Button type="text" icon={<RightOutlined />} aria-label="处理业务任务" onClick={() => open(task)} />
                  ) : null}
                </div>
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={view === 'pending' ? '暂无业务待办' : '暂无业务已办'} />
            )}
          </div>
          {page.total > PAGE_SIZE && (
            <Pagination size="small" current={pageNo} pageSize={PAGE_SIZE} total={page.total} showSizeChanger={false} onChange={setPageNo} />
          )}
        </>
      )}
    </section>
  )
}

function HomeCalendarPanel({ enabled }: { enabled: boolean }) {
  const navigate = useNavigate()
  const openCalendar = () => {
    if (enabled) navigate(APP_ROUTES.MEDIA_ALL_CALENDAR)
  }

  return (
    <section className="home-panel home-calendar-panel" aria-label="日历">
      <header className="home-panel-header compact">
        <Typography.Title level={4}>
          <CalendarOutlined /> 日历
        </Typography.Title>
        <Button type="text" icon={<RightOutlined />} aria-label="查看完整日历" disabled={!enabled} onClick={openCalendar} />
      </header>
      <Calendar
        fullscreen={false}
        locale={homeCalendarLocale}
        headerRender={() => null}
        onSelect={(_, info) => {
          if (info.source === 'date') openCalendar()
        }}
      />
    </section>
  )
}

function AnnouncementPanel({ enabled }: { enabled: boolean }) {
  const navigate = useNavigate()
  const [items, setItems] = useState<Announcement[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    if (!enabled) {
      setItems([])
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      setItems(
        (
          await api.announcementPage({
            pageNo: 1,
            pageSize: ANNOUNCEMENT_LIMIT
          })
        ).list
      )
    } catch (loadError) {
      setError(errorText(loadError, '公告加载失败'))
    } finally {
      setLoading(false)
    }
  }, [enabled])

  useEffect(() => {
    void load()
  }, [load])

  const openAnnouncement = (id: number) => navigate(`${APP_ROUTES.ANNOUNCEMENTS}?announcementId=${id}`)

  return (
    <section className="home-panel home-announcement-panel" aria-label="公告栏">
      <header className="home-panel-header compact">
        <Typography.Title level={4}>
          <NotificationOutlined /> 公告栏
        </Typography.Title>
        {enabled && <Button type="text" icon={<ReloadOutlined />} aria-label="刷新公告" onClick={() => void load()} />}
      </header>
      {error && (
        <Alert
          type="error"
          showIcon
          title={error}
          action={
            <Button size="small" onClick={() => void load()}>
              重试
            </Button>
          }
        />
      )}
      <div className="home-announcement-list">
        {!enabled ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公告查看权限" />
        ) : loading ? (
          <Skeleton active paragraph={{ rows: 5 }} />
        ) : items.length ? (
          items.map((item) => (
            <button type="button" className={`home-announcement-item${item.read ? '' : ' unread'}${item.highlighted ? ' highlighted' : ''}`} key={item.id} onClick={() => openAnnouncement(item.id)}>
              <span className="home-announcement-title">
                <Badge status={item.read ? 'default' : 'processing'} />
                {item.highlighted && <Tag color="gold">置顶</Tag>}
                <span className="home-announcement-title-text">{item.title}</span>
              </span>
              <time>{formatTimestamp(item.publishTime)}</time>
            </button>
          ))
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公告" />
        )}
      </div>
      {enabled && (
        <Button className="home-announcement-all" type="link" onClick={() => navigate(APP_ROUTES.ANNOUNCEMENTS)}>
          查看所有公告 <RightOutlined />
        </Button>
      )}
    </section>
  )
}

export default function TodayTasksPage({ permissions, onOpenAssignment }: { permissions: string[]; onOpenAssignment: () => void }) {
  const showBpmTasks = canQueryBpmTasks(permissions)
  const [businessSummary, setBusinessSummary] = useState<BusinessTaskSummary>()
  const [bpmCount, setBpmCount] = useState<number>()
  const [summaryLoading, setSummaryLoading] = useState(true)
  const [businessError, setBusinessError] = useState('')
  const [bpmError, setBpmError] = useState('')

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true)
    setBusinessError('')
    setBpmError('')
    await Promise.all([
      api
        .businessTaskSummary()
        .then(setBusinessSummary)
        .catch((error) => setBusinessError(errorText(error, '业务待办计数加载失败'))),
      showBpmTasks
        ? api
            .bpmTaskPage('todo', { pageNo: 1, pageSize: 1 })
            .then((page) => setBpmCount(page.total))
            .catch((error) => setBpmError(errorText(error, '审批待办计数加载失败')))
        : Promise.resolve()
    ])
    setSummaryLoading(false)
  }, [showBpmTasks])

  useEffect(() => {
    void loadSummary()
  }, [loadSummary])

  return (
    <section className="workspace-page today-tasks-page">
      <div className="home-dashboard-grid">
        <SummaryRegion
          businessSummary={businessSummary}
          bpmCount={bpmCount}
          showBpmTasks={showBpmTasks}
          loading={summaryLoading}
          businessError={businessError}
          bpmError={bpmError}
          onRefresh={loadSummary}
        />
        <HomeCalendarPanel enabled={canOpenAllCalendar(permissions)} />
        <BusinessTaskPanel summary={businessSummary} onOpenAssignment={onOpenAssignment} onRefreshSummary={loadSummary} />
        <AnnouncementPanel enabled={canReadAnnouncements(permissions)} />
      </div>
    </section>
  )
}
