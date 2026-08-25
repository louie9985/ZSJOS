import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Pagination, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmPerformanceAssessment, type HrmPerformanceAssessmentSummary } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, PERFORMANCE_STAGE_TYPE_LABELS, RESULT_AUDIT_STATUS_COLORS, RESULT_AUDIT_STATUS_LABELS } from '../services/hrm'
import { QuotaReadonlyTable, StageTable } from '../components/QuotaTable'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const PAGE_SIZE = 10

function fmtDate(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }

/** 员工端我的绩效档案：只看历史归档结果，不承载任何动作。 */
export default function HrmMyPerformanceHistoryPage() {
  const [items, setItems] = useState<HrmPerformanceAssessmentSummary[]>([])
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
      const result = await api.hrm.portal.performance.page({ pageNo: page, pageSize: PAGE_SIZE })
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

  const openDetail = async (row: HrmPerformanceAssessmentSummary) => {
    const version = ++detailVersion.current
    setDetailLoading(true); setDetail(undefined)
    try {
      const result = await api.hrm.portal.performance.get(row.id)
      if (version !== detailVersion.current) return
      setDetail(result)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '详情加载失败')
    } finally {
      if (version === detailVersion.current) setDetailLoading(false)
    }
  }

  const columns: ColumnsType<HrmPerformanceAssessmentSummary> = [
    { title: '考核计划', dataIndex: 'name', width: 200, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '考核周期', width: 170, render: (_, row) => `${fmtDate(row.startTime)} ~ ${fmtDate(row.endTime)}` },
    { title: '最终得分', dataIndex: 'score', width: 100, align: 'right', render: (value?: number) => value != null ? String(value) : '-' },
    { title: '结果等级', dataIndex: 'resultLevel', width: 110, align: 'center', render: (value?: string) => value ? <Tag color="success">{value}</Tag> : '-' },
    { title: '绩效系数', dataIndex: 'coefficient', width: 100, align: 'right', render: (value?: number) => value != null ? String(value) : '-' },
    {
      title: '审核状态', dataIndex: 'resultAuditStatus', width: 110, align: 'center',
      render: (value?: number) => value != null ? <Tag color={RESULT_AUDIT_STATUS_COLORS[value]}>{RESULT_AUDIT_STATUS_LABELS[value]}</Tag> : '-'
    },
    { title: '归档时间', dataIndex: 'archiveTime', width: 120, render: fmtDate },
    {
      title: '操作', width: 90, align: 'center', fixed: 'right',
      render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无绩效档案"/>
        : <>
          <HrmProTable<HrmPerformanceAssessmentSummary> advanced persistenceKey="my-performance-history" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1100 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-my-performance-history-page">
    <div className="page-heading">
      <span className="hrm-muted">仅展示已归档的考核结果</span>
      <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
    </div>
    {planStatus.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`绩效状态字典加载失败：${planStatus.error}`} action={<Button size="small" onClick={planStatus.reload}>重试</Button>}/>}
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.name || '绩效档案'} width="min(960px, 96vw)" open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active paragraph={{ rows: 10 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={3} bordered items={[
          { key: 'name', label: '计划', children: detail.name || '-' },
          { key: 'stage', label: '当前阶段', children: detail.stageType != null ? PERFORMANCE_STAGE_TYPE_LABELS[detail.stageType] : '-' },
          { key: 'score', label: '最终得分', children: detail.score != null ? String(detail.score) : '-' },
          { key: 'level', label: '结果等级', children: detail.resultLevel ? <Tag color="success">{detail.resultLevel}</Tag> : '-' },
          { key: 'coefficient', label: '绩效系数', children: detail.coefficient != null ? String(detail.coefficient) : '-' },
          {
            key: 'audit', label: '审核状态', children: detail.resultAuditStatus != null
              ? <Tag color={RESULT_AUDIT_STATUS_COLORS[detail.resultAuditStatus]}>{RESULT_AUDIT_STATUS_LABELS[detail.resultAuditStatus]}</Tag> : '-'
          },
          { key: 'start', label: '开始时间', children: fmtDate(detail.startTime) },
          { key: 'end', label: '结束时间', children: fmtDate(detail.endTime) },
          { key: 'archive', label: '归档时间', children: fmtDate(detail.archiveTime) }
        ]}/>
        <h4 className="hrm-drawer-subtitle">指标得分</h4>
        {detail.quotas?.length ? <QuotaReadonlyTable quotas={detail.quotas}/> : <Empty description="暂无指标"/>}
        <h4 className="hrm-drawer-subtitle">评分阶段</h4>
        {detail.reviewStages?.length ? <StageTable stages={detail.reviewStages}/> : <Empty description="暂无评分阶段"/>}
      </>}
    </Drawer>
  </section>
}
