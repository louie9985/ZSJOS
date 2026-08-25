import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, Progress, Skeleton, Space } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type EamStatistics, type EamStatisticsItem } from '../services/api'

function StatList({ items, total }: { items: EamStatisticsItem[]; total: number }) {
  if (!items.length) return <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
  return <ul className="eam-stat-list">
    {items.map(item => <li key={item.key} className="eam-stat-row">
      <div className="eam-stat-label">
        <span className="eam-stat-name">{item.name}</span>
        <span className="eam-stat-count">{item.count}</span>
      </div>
      <Progress percent={total ? Math.round((item.count / total) * 100) : 0} size="small" showInfo={false}/>
    </li>)}
  </ul>
}

export default function EamStatisticsPage() {
  const [data, setData] = useState<EamStatistics>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setData(await api.eam.statistics()) }
    catch (e) { setError(e instanceof Error ? e.message : '统计数据加载失败') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])

  if (loading && !data) return <section className="workspace-page eam-statistics-page"><Skeleton active paragraph={{ rows: 10 }}/></section>
  if (error) return <section className="workspace-page eam-statistics-page">
    <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
  </section>
  if (!data) return <section className="workspace-page eam-statistics-page"><Empty description="暂无统计数据"/></section>

  const total = data.totalCount ?? 0

  return <section className="workspace-page eam-statistics-page">
    <div className="page-heading">
      <Space><Button icon={<ReloadOutlined/>} onClick={() => void load()} loading={loading}>刷新</Button></Space>
    </div>

    <div className="eam-stats-grid">
      <section className="lead-card eam-stat-card">
        <div className="eam-stat-card-label">资产总数</div>
        <div className="eam-stat-card-value">{total}</div>
      </section>
    </div>

    <div className="eam-stats-columns">
      <section className="lead-card">
        <div className="eam-section-title">按状态分布</div>
        <StatList items={data.statusStats ?? []} total={total}/>
      </section>
      <section className="lead-card">
        <div className="eam-section-title">按分类分布</div>
        <StatList items={data.categoryStats ?? []} total={total}/>
      </section>
      <section className="lead-card">
        <div className="eam-section-title">按使用部门分布</div>
        <StatList items={data.deptStats ?? []} total={total}/>
      </section>
    </div>
  </section>
}
