import HomeMetricCards from '../components/HomeMetricCards'
import HomeCalendarPanel from '../components/HomeCalendarPanel'
import HomeAnnouncementPanel from '../components/HomeAnnouncementPanel'
import { diagnosisApi, diagnosisTaskUrl } from "../services/mediaAccountProfile";
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Empty, Pagination, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { CheckCircleOutlined, ClockCircleOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type BusinessTask, type BusinessTaskBucket, type BusinessTaskSummary, type PageResult } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

import BusinessReadScope, { type BusinessReadScopeValue } from '../components/BusinessReadScope'

const PAGE_SIZE = 6
type TaskView = 'pending' | 'done'

export const canQueryBpmTasks = (permissions: readonly string[]) => permissions.includes('bpm:task:query')
export const canReadAnnouncements = (permissions: readonly string[]) => permissions.includes('system:notice:read')

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
const emptySummary: BusinessTaskSummary = {
  overdue: 0,
  today: 0,
  future: 0,
  unscheduled: 0
}


function BusinessTaskPanel({
  summaryError,
  summary,
  onOpenAssignment,
  onRefreshSummary,
  tenantReadAll
}: {
  summaryError?: string
  summary?: BusinessTaskSummary
  tenantReadAll: boolean
  onOpenAssignment: () => void
  onRefreshSummary: () => Promise<void>
}) {
  const navigate = useNavigate()
  const [readScope, setReadScope] = useState<BusinessReadScopeValue>({ readScope: 'SELF' })
  const [scopeSummary, setScopeSummary] = useState<BusinessTaskSummary>()
  const sequence = useRef(0)
  const readOnly = readScope.readScope !== 'SELF'
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
    const current = ++sequence.current
    setPage({ list: [], total: 0 }); setScopeSummary(undefined); setError('')
    if (readScope.readScope === 'USER' && !readScope.targetUserId) { setLoading(false); return }
    setLoading(true)
    try {
      const [result, counts] = await Promise.all([
        api.businessTaskList({ ...(tenantReadAll ? readScope : {}), status: view,
          ...(view === 'pending' ? { bucket } : {}), pageNo, pageSize: PAGE_SIZE }),
        api.businessTaskSummary(tenantReadAll ? readScope : undefined)
      ])
      if (current === sequence.current) { setPage(result); setScopeSummary(counts) }
    } catch (loadError) {
      if (current === sequence.current) setError(errorText(loadError, '业务任务加载失败'))
    } finally { if (current === sequence.current) setLoading(false) }
  }, [bucket, pageNo, view, readScope, tenantReadAll])

  useEffect(() => {
    void load()
  }, [load])

  const refresh = async () => {
    await Promise.all([load(), onRefreshSummary()])
  }

  const open = (task: BusinessTask) => {
    if (readOnly || !task.actionable) return
    if (task.actionCode === 'MEDIA_ACCOUNT_DIAGNOSIS') {
      void diagnosisApi.tasks(task.bizId).then(items => { const item = items.find(x => x.taskId === task.id); if (item) navigate(diagnosisTaskUrl(item)); else setError("该诊断任务已处理或不再可用"); }).catch(e => setError(errorText(e, "诊断任务加载失败")))
      return
    }
    if (task.actionCode === 'STUDENT_DELIVERY_CONFIRM') {
      // The stage task is scoped to its service relation; open that student's
      // workbench context so the existing delivery-stage form enforces the same
      // relation boundary on the server.
      navigate(APP_ROUTES.MY_STUDENTS, {
        state: {
          serviceRelationId: task.serviceRelationId,
          openContactTask: true,
          taskId: task.targetRecordId ?? task.bizId,
          taskType: 'student_delivery_stage'
        }
      })
      return
    }
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
      navigate(APP_ROUTES.LEAD_MANAGEMENT, { state: { leadId: task.bizId, tab: 'assist-history', assistRequestId: task.targetRecordId ?? task.bizId } })
      return
    }
    if (task.actionCode === 'OPEN_SALES_ORDER_REVISION') {
      navigate(`${APP_ROUTES.SALES_ORDERS}?orderId=${task.bizId}`)
      return
    }
    if (task.actionCode === 'COMPLETE_POSITIONING_INTERVIEW' && task.serviceRelationId) {
      navigate(APP_ROUTES.MY_STUDENTS, {
        state: {
          serviceRelationId: task.serviceRelationId,
          openContactTask: true,
          taskId: task.targetRecordId,
          taskType: 'positioning_interview'
        }
      })
      return
    }
    if (task.actionCode === 'COMPLETE_TOPIC') {
      // 媒体选题任务：跳转到制作工单页面
      navigate(APP_ROUTES.MEDIA_PRODUCTION_TICKETS)
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
    if (readOnly || !task.actionable) return
    try {
      await api.completeEmployeeReminder(task.id)
      await refresh()
    } catch (taskError) {
      setError(errorText(taskError, '员工提醒完成失败'))
    }
  }

  const pendingSummary = useMemo(() => scopeSummary ?? (readOnly ? emptySummary : summary ?? emptySummary), [scopeSummary, readOnly, summary])

  return (
    <section className="home-panel home-business-panel" aria-label="业务待办">
      <header className="home-panel-header">
        <div>
          <Typography.Title level={4}>业务待办</Typography.Title>
          <Typography.Text type="secondary">按截止时间查看和处理任务</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label="刷新业务任务" onClick={() => void refresh()} />
      </header>
      {summaryError && <Alert type="error" title={summaryError} action={<Button onClick={() => void onRefreshSummary()}>重试</Button>} />}
      {tenantReadAll && <BusinessReadScope value={readScope} onChange={value => { setPageNo(1); setReadScope(value) }} />}
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
                      {readOnly && <Tag>{task.assigneeName || "未分配"}</Tag>}
                      {task.overdue && <Tag color="error">已逾期</Tag>}
                      {task.status === 'cancelled' && <Tag>已取消</Tag>}
                    </Space>
                    <Space wrap>
                      <Typography.Text type="secondary">{task.summary || '业务任务'}</Typography.Text>
                      <Typography.Text type="secondary">{formatTimestamp(task.dueAt || task.completedAt || task.cancelledAt, '无时间')}</Typography.Text>
                    </Space>
                  </div>
                  {!readOnly && task.actionCode && workPlanActions.has(task.actionCode) && view === 'pending' ? (
                    <Tag>已搁置</Tag>
                  ) : !readOnly && task.actionCode && ['COMPLETE_BIRTHDAY_CARE', 'COMPLETE_EMPLOYEE_CONTRACT_EXPIRY', 'COMPLETE_EMPLOYEE_ENTRY_ANNIVERSARY'].includes(task.actionCode) && view === 'pending' ? (
                    <Button type="text" icon={<CheckCircleOutlined />} aria-label="完成员工提醒" onClick={() => void completeEmployeeReminder(task)} />
                  ) : !readOnly && task.actionable && view === 'pending' ? (
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

export default function TodayTasksPage({ permissions, onOpenAssignment, tenantReadAll = false }: { permissions: string[]; onOpenAssignment: () => void; tenantReadAll?: boolean }) {
  const [businessSummary, setBusinessSummary] = useState<BusinessTaskSummary>()
  const [businessError, setBusinessError] = useState('')
  const loadSummary = useCallback(async () => {
    setBusinessError('')
    try { setBusinessSummary(await api.businessTaskSummary()) }
    catch (error) { setBusinessError(errorText(error, '业务待办计数加载失败')) }
  }, [])

  useEffect(() => {
    void loadSummary()
  }, [loadSummary])

  return (
    <section className="workspace-page today-tasks-page">
      <div className="home-dashboard-grid">
        <section className="home-summary-region" aria-label="首页指标"><HomeMetricCards /></section>
                <HomeCalendarPanel permissions={permissions} />
        <BusinessTaskPanel summaryError={businessError} tenantReadAll={tenantReadAll} summary={businessSummary} onOpenAssignment={onOpenAssignment} onRefreshSummary={loadSummary} />
        <HomeAnnouncementPanel enabled={canReadAnnouncements(permissions)} />
      </div>
    </section>
  )
}
