import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Input, Modal, Pagination, Select, Skeleton, Space, Tag, Timeline, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmPerformanceAssessment, type HrmPerformanceAssessmentSummary, type HrmPerformanceProcessRecord, type HrmPerformanceQuota, type HrmPerformanceQuotaSave } from '../services/api'
import { useDict } from '../services/useDict'
import {
  APPEAL_STATUS_COLORS, APPEAL_STATUS_LABELS, CONFIRM_PASS, HRM_DICT,
  PERFORMANCE_STAGE_TYPE_LABELS, RESULT_AUDIT_STATUS_COLORS, RESULT_AUDIT_STATUS_LABELS
} from '../services/hrm'
import { QuotaReadonlyTable, QuotaEditableTable, StageTable } from '../components/QuotaTable'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }
function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

/** 员工端绩效：我的考核列表 + 详情 + 目标确认/填指标/自评/评分/结果确认/申诉。 */
export default function HrmMyPerformancePage() {
  const [items, setItems] = useState<HrmPerformanceAssessmentSummary[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [detail, setDetail] = useState<HrmPerformanceAssessment>()
  const [detailTitle, setDetailTitle] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)
  const detailVersion = useRef(0)
  const [stageId, setStageId] = useState<number>()
  const [processRecords, setProcessRecords] = useState<HrmPerformanceProcessRecord[]>([])

  const [action, setAction] = useState<'fill' | 'score' | 'confirmTarget' | 'confirmResult' | 'appeal' | 'audit' | 'reject' | 'handleAppeal'>()
  const [draftQuotas, setDraftQuotas] = useState<HrmPerformanceQuotaSave[]>([])
  const [actionComment, setActionComment] = useState('')
  const [processing, setProcessing] = useState(false)

  const planStatus = useDict(HRM_DICT.PERFORMANCE_PLAN_STATUS)

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.portal.performance.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '绩效加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const loadDetail = useCallback(async (id: number, targetStageId?: number) => {
    const version = ++detailVersion.current
    setDetailLoading(true); setDetail(undefined)
    try {
      const [result, records] = await Promise.all([
        api.hrm.portal.performance.get(id, targetStageId),
        api.hrm.portal.performance.processRecordList(id, targetStageId)
      ])
      if (version !== detailVersion.current) return
      setDetail(result); setStageId(targetStageId); setProcessRecords(records)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '详情加载失败')
    } finally {
      if (version === detailVersion.current) setDetailLoading(false)
    }
  }, [])

  const openDetail = async (row: HrmPerformanceAssessmentSummary) => {
    setDetailTitle(`${row.name || '绩效'} · ${row.startTime ? `${fmtDate(row.startTime)} 起` : ''}`)
    await loadDetail(row.id)
  }

  const openAction = (mode: NonNullable<typeof action>) => {
    const quotas = detail?.currentStage?.type === 4 ? detail.quotas : detail?.quotas
    setDraftQuotas((quotas || []).map((quota: HrmPerformanceQuota) => ({
      id: quota.id, dimensionId: quota.dimensionId, name: quota.name, description: quota.description,
      standard: quota.standard, weight: quota.weight, scoreType: quota.scoreType,
      targetValue: quota.targetValue, actualValue: quota.actualValue,
      selfScore: quota.selfScore, reviewerScore: quota.reviewerScore, finalScore: quota.finalScore,
      comment: quota.comment, sort: quota.sort
    })))
    setActionComment('')
    setAction(mode)
  }

  const closeAction = () => { setAction(undefined); setDraftQuotas([]); setActionComment('') }

  const submitAction = async () => {
    if (!detail || !action) return
    const assessmentId = detail.id!
    setProcessing(true)
    try {
      if (action === 'fill') {
        await api.hrm.portal.performance.fillQuota({ assessmentId, quotas: draftQuotas })
      } else if (action === 'score') {
        const reviewStageId = detail.currentReviewStage?.id ?? stageId
        if (!reviewStageId) { message.error('未找到评分阶段'); setProcessing(false); return }
        await api.hrm.portal.performance.score({ assessmentId, reviewStageId, comment: actionComment, quotas: draftQuotas })
      } else if (action === 'confirmTarget') {
        await api.hrm.portal.performance.confirmTarget({ assessmentId, pass: CONFIRM_PASS.PASS })
      } else if (action === 'confirmResult') {
        await api.hrm.portal.performance.confirmResult({ assessmentId, pass: CONFIRM_PASS.PASS, comment: actionComment })
      } else if (action === 'appeal') {
        await api.hrm.portal.performance.submitAppeal({ assessmentId, appealReason: actionComment, reviewStageIds: [] })
      } else if (action === 'audit') {
        await api.hrm.portal.performance.handleResultAudit({ assessmentId, pass: CONFIRM_PASS.PASS, comment: actionComment, stageId })
      } else if (action === 'handleAppeal') {
        await api.hrm.portal.performance.handleAppeal({ assessmentId, pass: CONFIRM_PASS.PASS, comment: actionComment, stageId })
      }
      message.success('操作成功')
      closeAction()
      loadDetail(assessmentId, stageId)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally { setProcessing(false) }
  }

  const stage = detail?.currentStage || detail?.currentReviewStage

  const columns: ColumnsType<HrmPerformanceAssessmentSummary> = [
    { title: '考核计划', dataIndex: 'name', width: 200, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '当前阶段', dataIndex: 'stageType', width: 120, render: (value?: number) => value != null ? PERFORMANCE_STAGE_TYPE_LABELS[value] || value : '-' },
    { title: '得分', dataIndex: 'score', width: 90, align: 'right', render: (value?: number) => value != null ? String(value) : '-' },
    { title: '结果等级', dataIndex: 'resultLevel', width: 100, align: 'center', render: (value?: string) => value ? <Tag color="success">{value}</Tag> : '-' },
    { title: '考核周期', width: 130, render: (_, row) => `${fmtDate(row.startTime)} ~ ${fmtDate(row.endTime)}` },
    { title: '归档时间', dataIndex: 'archiveTime', width: 130, render: fmtDate },
    {
      title: '操作', width: 100, align: 'center', fixed: 'right',
      render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>进入</Button>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无绩效考核"/>
        : <>
          <HrmProTable<HrmPerformanceAssessmentSummary> advanced persistenceKey="my-performance" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1000 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  // 当前阶段的可用动作
  const stageActions: Array<{ key: typeof action; label: string; primary?: boolean; danger?: boolean }> = []
  if (detail?.stageType === 1) stageActions.push({ key: 'fill', label: '填写指标', primary: true })
  if (detail?.canConfirmTarget) stageActions.push({ key: 'confirmTarget', label: '确认目标', primary: true })
  if (detail?.stageType === 3) stageActions.push({ key: 'score', label: '提交自评', primary: true })
  if (detail?.currentReviewStage?.canScore || detail?.currentReviewStage?.canHandle) stageActions.push({ key: 'score', label: '提交评分', primary: true })
  if (detail?.stageType === 5 && detail.resultAuditStatus === 1) stageActions.push({ key: 'audit', label: '处理结果审核', primary: true })
  if (detail?.stageType === 6) stageActions.push({ key: 'confirmResult', label: '确认结果', primary: true })
  if (detail?.appealStatus === 1) stageActions.push({ key: 'handleAppeal', label: '处理申诉' })
  if (detail?.resultAuditStatus === 3 || detail?.appealStatus === 3) stageActions.push({ key: 'appeal', label: '发起申诉', danger: true })

  const actionTitleMap: Record<string, string> = {
    fill: '填写指标体系', score: '提交评分', confirmTarget: '确认考核目标',
    confirmResult: '确认考核结果', appeal: '发起申诉', audit: '审核考核结果', handleAppeal: '处理申诉', reject: '驳回评分'
  }

  return <section className="workspace-page hrm-page hrm-my-performance-page">
    {planStatus.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`绩效状态字典加载失败：${planStatus.error}`} action={<Button size="small" onClick={planStatus.reload}>重试</Button>}/>}
    <div className="page-heading">
      <span></span>
      <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detailTitle} width="min(960px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active paragraph={{ rows: 12 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={3} bordered items={[
          { key: 'name', label: '计划', children: detail.name || '-' },
          { key: 'stage', label: '当前阶段', children: detail.stageType != null ? PERFORMANCE_STAGE_TYPE_LABELS[detail.stageType] : '-' },
          { key: 'handler', label: '当前处理人', children: detail.currentHandlerName || '-' },
          { key: 'score', label: '绩效得分', children: detail.score != null ? String(detail.score) : '-' },
          { key: 'level', label: '结果等级', children: detail.resultLevel ? <Tag color="success">{detail.resultLevel}</Tag> : '-' },
          {
            key: 'audit', label: '审核状态', children: detail.resultAuditStatus != null
              ? <Tag color={RESULT_AUDIT_STATUS_COLORS[detail.resultAuditStatus]}>{RESULT_AUDIT_STATUS_LABELS[detail.resultAuditStatus]}</Tag> : '-'
          },
          {
            key: 'appeal', label: '申诉状态', children: detail.appealStatus != null
              ? <Tag color={APPEAL_STATUS_COLORS[detail.appealStatus]}>{APPEAL_STATUS_LABELS[detail.appealStatus]}</Tag> : '-'
          },
          { key: 'start', label: '开始时间', children: fmtDate(detail.startTime) },
          { key: 'end', label: '结束时间', children: fmtDate(detail.endTime) }
        ]}/>

        <h4 className="hrm-drawer-subtitle">指标得分</h4>
        {detail.quotas?.length ? <QuotaReadonlyTable quotas={detail.quotas}/> : <Empty description="暂无指标"/>}

        <h4 className="hrm-drawer-subtitle">评分阶段</h4>
        {detail.reviewStages?.length ? <StageTable stages={detail.reviewStages}/> : <Empty description="暂无评分阶段"/>}

        {stage?.comment && <Alert className="hrm-inline-alert" type="info" showIcon message={`${stage.name || '当前阶段'}意见：${stage.comment}`}/>}

        {stageActions.length > 0 && <div className="hrm-drawer-actions">
          <Space>
            {stageActions.map(item => item.key && <Button
              key={item.key} type={item.primary ? 'primary' : 'default'} danger={item.danger} disabled={processing}
              onClick={() => openAction(item.key!)}>
              {item.label}
            </Button>)}
          </Space>
        </div>}

        <h4 className="hrm-drawer-subtitle">流程记录</h4>
        {processRecords.length
          ? <Timeline items={processRecords.map(record => ({
            children: <><div className="hrm-timeline-title">{record.title || '-'}</div>
              {record.content && <div className="hrm-timeline-content">{record.content}</div>}
              <div className="hrm-timeline-meta">{record.operatorName || '系统'} · {fmtTime(record.operateTime)}</div></>
          }))}/>
          : <Empty description="暂无流程记录"/>}
      </>}
    </Drawer>

    <Modal title={action ? actionTitleMap[action] : ''} open={!!action} onCancel={closeAction} onOk={() => void submitAction()}
      confirmLoading={processing} width="min(960px, 96vw)" destroyOnClose>
      {action === 'fill' && detail?.quotas?.length
        ? <QuotaEditableTable quotas={detail.quotas} mode="fill" onChange={setDraftQuotas}/>
        : action === 'score' && detail?.quotas?.length
          ? <>
            <QuotaEditableTable quotas={detail.quotas} mode="score" onChange={setDraftQuotas}/>
            <Input.TextArea className="hrm-modal-comment" rows={3} value={actionComment}
              onChange={event => setActionComment(event.target.value)} placeholder="评分说明"/>
          </>
          : <><p className="hrm-muted">提交后将推进到下一阶段。</p>
            <Input.TextArea rows={3} value={actionComment} onChange={event => setActionComment(event.target.value)}
              placeholder={action === 'appeal' ? '请填写申诉原因' : '填写意见（选填）'}/></>}
      {action === 'fill' && <Alert className="hrm-modal-alert" message="请填写实际完成情况与自评分，确认后进入下一环节" type="info" showIcon/>}
    </Modal>
  </section>
}
