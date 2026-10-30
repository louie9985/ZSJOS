import HomeTodoSummary from '../components/HomeTodoSummary'
import HomeMetricCards from '../components/HomeMetricCards'
import { useWorkbenchPageNavigation } from '../components/WorkbenchPageNavigation'
import HomeCalendarPanel from '../components/HomeCalendarPanel'
import HomeAnnouncementPanel from '../components/HomeAnnouncementPanel'
import { diagnosisApi, diagnosisTaskUrl } from "../services/mediaAccountProfile";
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Pagination, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { CheckCircleOutlined, ClockCircleOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type BusinessTask, type BusinessTaskBucket, type BusinessTaskSummary, type PageResult } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

import BusinessReadScope, { type BusinessReadScopeValue } from '../components/BusinessReadScope'

const PAGE_SIZE = 6


export const canQueryBpmTasks = (permissions: readonly string[]) => permissions.includes('bpm:task:query')
export const canReadAnnouncements = (permissions: readonly string[]) => permissions.includes('system:notice:read')

const errorText = (error: unknown, fallback: string) =>
  error instanceof ApiError && error.code === 403 ? '暂无权限，请联系管理员配置对应功能权限' : error instanceof Error ? error.message : fallback

const bucketLabels: Record<BusinessTaskBucket, string> = {
  overdue: '逾期',
  today: '今日',
  future: '未来',
  unscheduled: '其他待办'
}

const bucketOrder: BusinessTaskBucket[] = ['overdue', 'today', 'future']
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
  tenantReadAll,
  canCancel,
  other = false
}: {
  summaryError?: string
  summary?: BusinessTaskSummary
  canCancel: boolean
  other?: boolean
  tenantReadAll: boolean
  onOpenAssignment: () => void
  onRefreshSummary: () => Promise<void>
}) {
  const navigate = useNavigate()
  const navigation = useWorkbenchPageNavigation()
  const [readScope, setReadScope] = useState<BusinessReadScopeValue>({ readScope: 'SELF' })
  const [scopeSummary, setScopeSummary] = useState<BusinessTaskSummary>()
  const sequence = useRef(0)
  const readOnly = readScope.readScope !== 'SELF'
  const title = other ? '其他待办' : '业务待办'
  const [bucket, setBucket] = useState<BusinessTaskBucket>(other ? 'unscheduled' : 'today')
  const [pageNo, setPageNo] = useState(1)
  const [page, setPage] = useState<PageResult<BusinessTask>>({
    list: [],
    total: 0
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cancelTask, setCancelTask] = useState<BusinessTask>()
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState('')
  const [cancelForm] = Form.useForm<{ reason: string }>()
  const submitCancel = async () => {
    if (!cancelTask || cancelling) return
    let values: { reason: string }
    try { values = await cancelForm.validateFields() } catch { return }
    setCancelling(true); setCancelError('')
    try {
      await api.cancelBusinessTask(cancelTask.id, values.reason.trim())
      setCancelTask(undefined); cancelForm.resetFields()
      await refresh()
    } catch (e) { setCancelError(errorText(e, '取消待办失败，请重试')) }
    finally { setCancelling(false) }
  }


  const load = useCallback(async () => {
    const current = ++sequence.current
    setPage({ list: [], total: 0 }); setScopeSummary(undefined); setError('')
    if (readScope.readScope === 'USER' && !readScope.targetUserId) { setLoading(false); return }
    setLoading(true)
    try {
      const [result, counts] = await Promise.all([
        api.businessTaskList({ ...(tenantReadAll ? readScope : {}), status: 'pending',
          bucket, pageNo, pageSize: PAGE_SIZE }),
        api.businessTaskSummary(tenantReadAll ? readScope : undefined)
      ])
      if (current === sequence.current) { setPage(result); setScopeSummary(counts) }
    } catch (loadError) {
      if (current === sequence.current) setError(errorText(loadError, '业务任务加载失败'))
    } finally { if (current === sequence.current) setLoading(false) }
  }, [bucket, pageNo, readScope, tenantReadAll])

  useEffect(() => {
    void load()
  }, [load])

  const refresh = async () => {
    await Promise.all([load(), onRefreshSummary()])
  }

  const open = async (task: BusinessTask) => {
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
      if (navigation && !await navigation.validate(APP_ROUTES.LEAD_MANAGEMENT, APP_ROUTES.LEAD_MANAGEMENT)) return
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
    <section className={`home-panel home-business-panel${other ? " home-other-panel" : ""}`} aria-label={title}>
      <Modal title="取消待办" open={!!cancelTask} okText="确认取消待办" cancelText="返回"
        confirmLoading={cancelling} closable={!cancelling} maskClosable={!cancelling}
        cancelButtonProps={{ disabled: cancelling }} onOk={() => void submitCancel()}
        onCancel={() => { if (!cancelling) setCancelTask(undefined) }}>
        <Typography.Paragraph>{cancelTask?.title}</Typography.Paragraph>
        <Alert type="info" showIcon title="仅从待办中移除，不终止相关业务；原记录及取消原因会保留。" />
        <Form form={cancelForm} layout="vertical">
          <Form.Item name="reason" label="取消原因" rules={[
            { required: true, whitespace: true, message: '请填写取消原因' },
            { max: 500, message: '取消原因最多500字' }
          ]}>
            <Input.TextArea rows={3} maxLength={500} showCount disabled={cancelling} />
          </Form.Item>
        </Form>
        {cancelError && <Alert type="error" showIcon title={cancelError} />}
      </Modal>
      <header className="home-panel-header">
        <div>
          <Typography.Title level={4}>{title}{other ? ` ${pendingSummary.unscheduled}` : ""}</Typography.Title>
          <Typography.Text type="secondary">{other ? '查看和处理其他任务' : '按截止时间查看和处理任务'}</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} aria-label={`刷新${title}`} onClick={() => void refresh()} />
      </header>
      {summaryError && <Alert type="error" title={summaryError} action={<Button onClick={() => void onRefreshSummary()}>重试</Button>} />}
      {tenantReadAll && <BusinessReadScope value={readScope} onChange={value => { setPageNo(1); setReadScope(value) }} />}
      <div className="home-task-controls">
        {!other && (
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
                  <span className="task-panel-item-icon"><ClockCircleOutlined /></span>
                  <div className="task-panel-item-copy">
                    <Space wrap>
                      <Typography.Text strong>{task.title}</Typography.Text>
                      {readOnly && <Tag>{task.assigneeName || "未分配"}</Tag>}
                      {task.overdue && <Tag color="error">已逾期</Tag>}
                      {task.status === 'cancelled' && <Tag>已取消</Tag>}
                    </Space>
                    <Space wrap>
                      <Typography.Text type="secondary">{task.summary || '业务任务'}</Typography.Text>
                      {task.dueAt && <Typography.Text type="secondary">{formatTimestamp(task.dueAt)}</Typography.Text>}
                    </Space>
                  </div>
                  {!readOnly && canCancel && task.cancellable && <Button type="text" onClick={() => {
                    cancelForm.resetFields(); setCancelError(''); setCancelTask(task)
                  }}>取消待办</Button>}
                  {!readOnly && task.actionCode && workPlanActions.has(task.actionCode) ? (
                    <Tag>已搁置</Tag>
                  ) : !readOnly && task.actionCode && ['COMPLETE_BIRTHDAY_CARE', 'COMPLETE_EMPLOYEE_CONTRACT_EXPIRY', 'COMPLETE_EMPLOYEE_ENTRY_ANNIVERSARY'].includes(task.actionCode) ? (
                    <Button type="text" icon={<CheckCircleOutlined />} aria-label="完成员工提醒" onClick={() => void completeEmployeeReminder(task)} />
                  ) : !readOnly && task.actionable ? (
                    <Button type="text" icon={<RightOutlined />} aria-label="处理业务任务" onClick={() => open(task)} />
                  ) : null}
                </div>
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`暂无${title}`} />
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
  const pageRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const page = pageRef.current
    const content = page?.closest('.ant-layout-content')
    if (!page || !content) return
    // Measure the shell's remaining space, including tabs and optional status banners.
    const resize = () => page.style.setProperty('--home-content-height', `${content.clientHeight}px`)
    const observer = new ResizeObserver(resize)
    observer.observe(content)
    resize()
    return () => observer.disconnect()
  }, [])
  const [businessSummary, setBusinessSummary] = useState<BusinessTaskSummary>()
  const [businessError, setBusinessError] = useState('')
  const [summaryLoading, setSummaryLoading] = useState(true)
  const summarySequence = useRef(0)
  const canReadBusiness = permissions.includes('zsjos:business-task:query')
  const [metricRefreshVersion, setMetricRefreshVersion] = useState(0)
  const loadSummary = useCallback(async (refreshMetrics = true) => {
    const request = ++summarySequence.current
    setBusinessError(''); setBusinessSummary(undefined); setSummaryLoading(canReadBusiness)
    if (!canReadBusiness) return
    try { const data = await api.businessTaskSummary(); if (request === summarySequence.current) setBusinessSummary(data) }
    catch (error) { if (request === summarySequence.current) setBusinessError(errorText(error, '业务待办计数加载失败')) }
    finally { if (request === summarySequence.current) { setSummaryLoading(false); if (refreshMetrics) setMetricRefreshVersion(version => version + 1) } }
  }, [canReadBusiness])
  useEffect(() => { void loadSummary(false) }, [loadSummary])

  return (
    <section ref={pageRef} className="workspace-page today-tasks-page">
      <div className="home-dashboard-grid">
        <section className="home-summary-region" aria-label="首页指标"><HomeTodoSummary permissions={permissions} summary={businessSummary} loading={summaryLoading} error={businessError} refreshVersion={metricRefreshVersion} onRefresh={() => loadSummary()} /><HomeMetricCards refreshVersion={metricRefreshVersion} slots={9} /></section>
        <HomeCalendarPanel permissions={permissions} />
        <BusinessTaskPanel summaryError={businessError} canCancel={permissions.includes('zsjos:business-task:cancel')} tenantReadAll={tenantReadAll} summary={businessSummary} onOpenAssignment={onOpenAssignment} onRefreshSummary={loadSummary} />
        <BusinessTaskPanel summaryError={businessError} canCancel={permissions.includes('zsjos:business-task:cancel')} other tenantReadAll={tenantReadAll} summary={businessSummary} onOpenAssignment={onOpenAssignment} onRefreshSummary={loadSummary} />
        <HomeAnnouncementPanel enabled={canReadAnnouncements(permissions)} />
      </div>
    </section>
  )
}
