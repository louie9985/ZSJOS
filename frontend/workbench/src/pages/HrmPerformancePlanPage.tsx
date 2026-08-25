import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Modal, Pagination, Select, Skeleton, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmPerformancePlan } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, PERFORMANCE_PLAN_STATUS_LABELS, PERFORMANCE_PLAN_STATUS_COLORS, PERFORMANCE_STAGE_TYPE_LABELS } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import HrmPerformancePlanForm from '../components/HrmPerformancePlanForm'

const PAGE_SIZE = 10

function fmtEndTime(value?: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DD') : '-'
}

/** 管理端绩效计划：列表 + 状态流转。 */
export default function HrmPerformancePlanPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmPerformancePlan[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [filterStatus, setFilterStatus] = useState<number>()
  const [detail, setDetail] = useState<HrmPerformancePlan>()
  const [detailOpen, setDetailOpen] = useState(false)
  const detailVersion = useRef(0)

  const [formOpen, setFormOpen] = useState(false)
  const [formPlan, setFormPlan] = useState<HrmPerformancePlan>()
  const [acting, setActing] = useState(false)

  const planStatus = useDict(HRM_DICT.PERFORMANCE_PLAN_STATUS)
  const canCreate = permissions.includes('hrm:performance:plan:create')
  const canUpdate = permissions.includes('hrm:performance:plan:update')
  const canDelete = permissions.includes('hrm:performance:plan:delete')

  const loadPage = useCallback(async (page: number, status?: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.performance.plan.page({ pageNo: page, pageSize: PAGE_SIZE, status })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '绩效计划加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void loadPage(pageNo, filterStatus) }, [loadPage, pageNo, filterStatus])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1, filterStatus) }, [loadPage, filterStatus])

  const openDetail = async (row: HrmPerformancePlan) => {
    const version = ++detailVersion.current
    setDetail(row); setDetailOpen(true)
    try {
      const result = await api.hrm.performance.plan.get(row.id)
      if (version !== detailVersion.current) return
      setDetail(result)
    } catch (e) {
      if (version === detailVersion.current) message.error(e instanceof Error ? e.message : '详情加载失败')
    }
  }

  const openForm = async (plan?: HrmPerformancePlan) => {
    if (!plan) {
      setFormPlan(undefined); setFormOpen(true)
      return
    }
    try {
      setFormPlan(await api.hrm.performance.plan.get(plan.id))
      setFormOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '绩效计划详情加载失败')
    }
  }

  const doAction = (title: string, content: string, run: () => Promise<boolean>) => {
    Modal.confirm({
      title, content, okText: '确认',
      onOk: async () => {
        setActing(true)
        try { await run(); message.success('操作成功'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '操作失败'); throw e }
        finally { setActing(false) }
      }
    })
  }

  const handleDelete = (row: HrmPerformancePlan) => {
    Modal.confirm({
      title: '删除绩效计划',
      content: `确定要删除「${row.name}」吗？删除后该计划下的考核记录一并移除，无法恢复。`,
      okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.performance.plan.delete(row.id); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }

  /** 按计划状态渲染可用的流转按钮。后端下发 operationType / *Ready 标志，据此而非硬编码状态机。 */
  const actionButtons = (row: HrmPerformancePlan) => {
    const buttons: ReactNode[] = []
    // 草稿/未开始 → 启动
    if ((row.status === 1 || row.status === 2) && canUpdate) {
      buttons.push(<Button key="start" type="link" size="small" disabled={acting} onClick={() => doAction('启动计划', '启动后员工将进入考核，计划不可再编辑。', () => api.hrm.performance.plan.start(row.id))}>启动</Button>)
    }
    // 进行中可开启评分
    if (row.scoringReady && canUpdate) {
      buttons.push(<Button key="scoring" type="link" size="small" disabled={acting} onClick={() => doAction('开启评分', '开启后评分人可以对员工进行评分。', () => api.hrm.performance.plan.openScoring(row.id))}>开启评分</Button>)
    }
    if (row.interviewReady) {
      buttons.push(<Button key="interview" type="link" size="small" disabled={acting} onClick={() => doAction('发起面谈', '发起绩效面谈，通知员工进行面谈。', () => api.hrm.performance.plan.startInterview(row.id))}>发起面谈</Button>)
    }
    if (row.archiveReady) {
      buttons.push(<Button key="archive" type="link" size="small" disabled={acting} onClick={() => doAction('归档计划', '归档后考核结果定稿并进入绩效档案。', () => api.hrm.performance.plan.archive(row.id))}>归档</Button>)
    }
    // 进行中可终止
    if (row.status === 3 && canUpdate) {
      buttons.push(<Button key="terminate" type="link" size="small" danger disabled={acting} onClick={() => doAction('终止计划', '终止后计划结束，不再产生新考核。', () => api.hrm.performance.plan.terminate(row.id))}>终止</Button>)
    }
    return buttons
  }

  const columns: ColumnsType<HrmPerformancePlan> = [
    { title: '计划名称', dataIndex: 'name', width: 200, fixed: 'left', ellipsis: true },
    { title: '周期类型', dataIndex: 'cycleType', width: 100, render: (value?: number) => value != null ? PERFORMANCE_STAGE_TYPE_LABELS[value] || value : '-' },
    { title: '考核周期', width: 130, render: (_, row) => row.cycle || '-' },
    { title: '参评人数', dataIndex: 'employeeCount', width: 100, align: 'right', render: (value?: number) => value != null ? `${value} 人` : '-' },
    { title: '已完成', dataIndex: 'finishedCount', width: 100, align: 'right', render: (value?: number) => value != null ? `${value} 人` : '-' },
    {
      title: '当前阶段', dataIndex: 'stageType', width: 110, align: 'center',
      render: (value?: number) => value != null ? <Tag color="processing">{PERFORMANCE_STAGE_TYPE_LABELS[value] || value}</Tag> : '-'
    },
    {
      title: '状态', dataIndex: 'status', width: 110, align: 'center',
      render: (value?: number) => value != null
        ? <Tag color={PERFORMANCE_PLAN_STATUS_COLORS[value] || 'default'}>{PERFORMANCE_PLAN_STATUS_LABELS[value] || value}</Tag>
        : '-'
    },
    { title: '截止时间', dataIndex: 'endTime', width: 130, render: fmtEndTime },
    {
      title: '操作', width: 220, align: 'center', fixed: 'right',
      render: (_, row) => <Space size="small">
        <Button type="link" size="small" onClick={() => void openDetail(row)}>详情</Button>
        {(row.status === 1 || row.status === 2) && canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}
        {canDelete && (row.status === 1 || row.status === 2) && <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>}
        {actionButtons(row)}
      </Space>
    }
  ]

  const content = loading && !items.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无绩效计划"/>
        : <>
          <HrmProTable<HrmPerformancePlan> advanced persistenceKey="performance-plan" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} scroll={{ x: 1500 }} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-performance-plan-page">
    <div className="page-heading">
      <Select allowClear placeholder="状态" value={filterStatus} onChange={value => { setFilterStatus(value); setPageNo(1) }}
        style={{ width: 130 }} loading={planStatus.loading} options={planStatus.options}/>
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => void openForm()}>新建计划</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail?.name || '计划详情'} width="min(960px, 96vw)" open={detailOpen} onClose={() => setDetailOpen(false)} destroyOnClose>
      {detail && <Descriptions column={2} bordered size="small" items={[
        { key: 'name', label: '计划名称', children: detail.name },
        { key: 'status', label: '状态', children: detail.status != null ? <Tag color={PERFORMANCE_PLAN_STATUS_COLORS[detail.status]}>{PERFORMANCE_PLAN_STATUS_LABELS[detail.status]}</Tag> : '-' },
        { key: 'cycle', label: '考核周期', children: detail.cycle || '-' },
        { key: 'employeeCount', label: '参评人数', children: detail.employeeCount != null ? `${detail.employeeCount} 人` : '-' },
        { key: 'finished', label: '已完成', children: detail.finishedCount != null ? `${detail.finishedCount} 人` : '-' },
        { key: 'template', label: '考核模板', children: detail.assessmentTemplateName || '-' },
        { key: 'resultTemplate', label: '结果模板', children: detail.resultTemplateName || '-' },
        { key: 'endTime', label: '截止时间', children: fmtEndTime(detail.endTime) },
        { key: 'targetConfirm', label: '目标确认', children: detail.targetConfirmation ? '开启' : '关闭' },
        { key: 'resultAudit', label: '结果审核', children: detail.resultAudit ? '开启' : '关闭' },
        { key: 'syncToSalary', label: '同步薪资', children: detail.syncToSalary ? '是' : '否' },
        { key: 'paidForMonth', label: '计薪月份', children: detail.paidForMonth || '-' },
        { key: 'description', label: '考核说明', children: detail.description || '-', span: 2 }
      ]}/>}
    </Drawer>

    <HrmPerformancePlanForm open={formOpen} plan={formPlan} onClose={() => setFormOpen(false)} onSaved={reload}/>
  </section>
}
