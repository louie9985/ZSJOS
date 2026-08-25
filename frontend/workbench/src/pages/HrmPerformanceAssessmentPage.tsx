import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Pagination, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmPerformanceAssessment } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, PERFORMANCE_STAGE_TYPE_LABELS, RESULT_AUDIT_STATUS_COLORS, RESULT_AUDIT_STATUS_LABELS, APPEAL_STATUS_COLORS, APPEAL_STATUS_LABELS } from '../services/hrm'
import { QuotaReadonlyTable, StageTable } from '../components/QuotaTable'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

/** 管理端绩效档案：全员考核记录查询与查看。 */
export default function HrmPerformanceAssessmentPage() {
  const [items, setItems] = useState<HrmPerformanceAssessment[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [detail, setDetail] = useState<HrmPerformanceAssessment>()
  const [detailLoading, setDetailLoading] = useState(false)
  const detailVersion = useRef(0)

  const planStatus = useDict(HRM_DICT.PERFORMANCE_PLAN_STATUS)

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.performance.assessment.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '绩效档案加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const openDetail = async (row: HrmPerformanceAssessment) => {
    if (row.id == null) return
    const version = ++detailVersion.current
    setDetailLoading(true); setDetail(undefined)
    try {
      const [result, records] = await Promise.all([
        api.hrm.performance.assessment.get(row.id),
        api.hrm.performance.assessment.processRecordList(row.id)
      ])
      if (version !== detailVersion.current) return
      setDetail(result)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '详情加载失败')
    } finally {
      if (version === detailVersion.current) setDetailLoading(false)
    }
  }

  const columns: ColumnsType<HrmPerformanceAssessment> = [
    { title: '员工', dataIndex: 'employeeName', width: 110, fixed: 'left', render: (value?: string) => value || '-' },
    { title: '工号', dataIndex: 'jobNumber', width: 110, render: (value?: string) => value || '-' },
    { title: '部门', dataIndex: 'deptName', width: 140, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '考核计划', dataIndex: 'name', width: 180, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '当前阶段', dataIndex: 'stageType', width: 110, render: (value?: number) => value != null ? PERFORMANCE_STAGE_TYPE_LABELS[value] || value : '-' },
    { title: '得分', dataIndex: 'score', width: 90, align: 'right', render: (value?: number) => value != null ? String(value) : '-' },
    { title: '结果等级', dataIndex: 'resultLevel', width: 100, align: 'center', render: (value?: string) => value ? <Tag color="success">{value}</Tag> : '-' },
    {
      title: '审核', dataIndex: 'resultAuditStatus', width: 100, align: 'center',
      render: (value?: number) => value != null ? <Tag color={RESULT_AUDIT_STATUS_COLORS[value]}>{RESULT_AUDIT_STATUS_LABELS[value]}</Tag> : '-'
    },
    { title: '考核周期', width: 170, render: (_, row) => `${fmtDate(row.startTime)} ~ ${fmtDate(row.endTime)}` },
    {
      title: '操作', width: 90, align: 'center', fixed: 'right',
      render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无绩效档案"/>
        : <>
          <HrmProTable<HrmPerformanceAssessment> advanced persistenceKey="performance-assessment" onReload={reload} rowKey={row => row.id ?? row.employeeId ?? 0} columns={columns} dataSource={items}
            pagination={false} scroll={{ x: 1300 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-performance-assessment-page">
    <div className="page-heading">
      <span></span>
      <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </div>
    {planStatus.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`绩效状态字典加载失败：${planStatus.error}`} action={<Button size="small" onClick={planStatus.reload}>重试</Button>}/>}
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail ? `${detail.employeeName || ''} · ${detail.name || ''}` : '绩效档案'} width="min(960px, 96vw)"
      open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active paragraph={{ rows: 12 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={3} bordered items={[
          { key: 'employee', label: '员工', children: `${detail.employeeName || '-'}${detail.jobNumber ? `（${detail.jobNumber}）` : ''}` },
          { key: 'dept', label: '部门', children: detail.deptName || '-' },
          { key: 'name', label: '考核计划', children: detail.name || '-' },
          { key: 'stage', label: '当前阶段', children: detail.stageType != null ? PERFORMANCE_STAGE_TYPE_LABELS[detail.stageType] : '-' },
          { key: 'handler', label: '当前处理人', children: detail.currentHandlerName || '-' },
          { key: 'score', label: '得分', children: detail.score != null ? String(detail.score) : '-' },
          { key: 'level', label: '结果等级', children: detail.resultLevel ? <Tag color="success">{detail.resultLevel}</Tag> : '-' },
          { key: 'coefficient', label: '绩效系数', children: detail.coefficient != null ? String(detail.coefficient) : '-' },
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
        {detail.currentStage?.comment && <Alert className="hrm-inline-alert" type="info" showIcon message={`当前阶段意见：${detail.currentStage.comment}`}/>}
      </>}
    </Drawer>
  </section>
}
