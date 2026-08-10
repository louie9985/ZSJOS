import { useCallback, useEffect, useState } from 'react'
import { Alert, Badge, Button, Empty, List, Segmented, Skeleton, Space, Tag, Typography } from 'antd'
import { ClockCircleOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api, type BusinessTask, type BusinessTaskBucket, type BusinessTaskSummary } from '../services/api'
import { APP_ROUTES } from '../constants'
import { formatTimestamp } from '../services/time'

const EMPTY_SUMMARY: BusinessTaskSummary = { unscheduled: 0, overdue: 0, today: 0, future: 0 }
const LABELS: Record<Exclude<BusinessTaskBucket, 'unscheduled'>, string> = { overdue: '逾期', today: '今日', future: '未来' }
export default function TodayTasksPage({ onOpenAssignment }: { onOpenAssignment: () => void }) {
  const navigate = useNavigate()
  const [bucket, setBucket] = useState<Exclude<BusinessTaskBucket, 'unscheduled'>>('today')
  const [summary, setSummary] = useState(EMPTY_SUMMARY)
  const [unscheduled, setUnscheduled] = useState<BusinessTask[]>([])
  const [tasks, setTasks] = useState<BusinessTask[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [counts, pinned, current] = await Promise.all([
        api.businessTaskSummary(), api.businessTaskPage('unscheduled', { pageNo: 1, pageSize: 100 }),
        api.businessTaskPage(bucket, { pageNo: 1, pageSize: 100 })
      ])
      setSummary(counts); setUnscheduled(pinned.list); setTasks(current.list)
    } catch (loadError) { setError(loadError instanceof Error ? loadError.message : '待办加载失败') }
    finally { setLoading(false) }
  }, [bucket])
  useEffect(() => { void load() }, [load])
  const open = (task: BusinessTask) => {
    if (task.actionCode === 'OPEN_LEAD_ASSIGNMENT') { onOpenAssignment(); return }
    navigate(APP_ROUTES.OWNED_LEADS, { state: { leadId: task.bizId, openFollowUp: true } })
  }
  const renderList = (items: BusinessTask[], empty: string) => <List
    dataSource={items} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={empty}/> }}
    renderItem={task => <List.Item actions={[<Button key="open" type="text" icon={<RightOutlined/>} aria-label="处理待办" onClick={() => open(task)}/>]}>
      <List.Item.Meta avatar={<ClockCircleOutlined/>} title={<Space wrap><span>{task.title}</span>{task.overdue && <Tag color="error">已逾期</Tag>}</Space>}
        description={<Space wrap><span>{task.summary || '客资任务'}</span><span>{formatTimestamp(task.dueAt, '无截止时间')}</span></Space>}/>
    </List.Item>}/>
  return <section className="workspace-page today-tasks-page">
    <div className="today-task-toolbar"><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button></div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>} 
    {loading ? <Skeleton active/> : <>
      {unscheduled.length > 0 && <section className="unscheduled-tasks"><Typography.Title level={5}>待接任务 <Badge count={summary.unscheduled}/></Typography.Title>{renderList(unscheduled, '暂无无截止任务')}</section>}
      <Segmented block value={bucket} onChange={value => setBucket(value as typeof bucket)} options={(Object.keys(LABELS) as Array<keyof typeof LABELS>).map(key => ({ value: key, label: `${LABELS[key]} ${summary[key]}` }))}/>
      <div className="dated-task-list">{renderList(tasks, `暂无${LABELS[bucket]}待办`)}</div>
    </>}
  </section>
}
