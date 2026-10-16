import { useState } from 'react'
import { Alert, Button, Drawer, Empty } from 'antd'
import BusinessTable from '../../components/BusinessTable'
import { money, type UnclassifiedDetail } from '../../services/salesPerformance'
import { usePerformanceModule } from './PerformanceModule'
import { Resource, useResource } from './PerformanceShared'

function History() {
 const { api, domain } = usePerformanceModule()
 const [page, setPage] = useState(1)
 const state = useResource(signal => api.unclassifiedHistory(page, 20, signal), [domain, page])
 return <><Alert type="info" showIcon title="这些事实尚无可靠的业务域归属，不计入销售或交付业绩、排名和目标达成率。" />
  <Resource state={state}>{state.data?.total === 0 ? <Empty description="暂无未归类历史" /> :
   <BusinessTable<UnclassifiedDetail> tableKey="performance-unclassified-history" mode="compact" search={false}
    rowKey={row => `${row.fact.kind}:${row.fact.id}:${row.fact.occurredAt ?? ''}`}
    dataSource={state.data?.list ?? []} scroll={{x: 760}}
    pagination={{current: page, pageSize: 20, total: state.data?.total ?? 0, showSizeChanger: false, onChange: setPage}}
    columns={[
     {title: '业务编号', render: (_, row) => row.fact.number ?? '未记录'},
     {title: '事实指标', render: (_, row) => row.fact.label ?? '未记录'},
     {title: '发生日期', render: (_, row) => row.fact.occurredAt?.replace('T', ' ') ?? '未记录'},
     {title: '金额', render: (_, row) => row.fact.amount == null ? '未记录' : money(row.fact.amount)},
     {title: '状态', render: (_, row) => row.fact.state ?? '未记录'},
     {title: '归属缺失原因', dataIndex: 'attributionReason'},
    ]} />}</Resource></>
}
export default function UnclassifiedHistory({permissions}: {permissions: string[]}) {
 const {permission} = usePerformanceModule()
 const [open, setOpen] = useState(false)
 const allowed = ['query', 'self', 'detail'].every(action => permissions.includes(permission(`zsjos:sales-performance:${action}`)))
 if (!allowed) return null
 return <><Button onClick={() => setOpen(true)}>我的未归类历史</Button>
  <Drawer title="我的未归类历史" width="min(960px, 100vw)" open={open} onClose={() => setOpen(false)} destroyOnHidden>
   {open && <History />}
  </Drawer></>
}
