// UTF-8. Isolated synthetic employee cards; no business API writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SubordinateSalesCard from '../src/components/SubordinateSalesCard'
import type { SubordinateSales } from '../src/services/api'

const base = { userId: 1, name: '销售甲', username: 'sales-test', mobile: '测试号码', accountStatus: 0,
  presence: 'online', accepting: true, canReceiveNewLeads: true, todayPendingCount: 2,
  todayFollowUpRemainingCount: 2, todayFollowUpTotalCount: 8, todayAssignedCount: 10, todayMissedCount: 1,
  todayReceivedCount: 9, todayQualifiedCount: 6, todayFollowUpRecordCount: 12, todayOrderAmount: 25079.4,
  pendingQualificationCount: 5, validLeadCount: 247, convertedLeadCount: 16, effectiveOrderAmount: 25079.4,
} as SubordinateSales
const rows = [base, { ...base, userId: 2, name: '销售乙', todayFollowUpRemainingCount: 0 },
  { ...base, userId: 3, name: '名称很长的测试销售员工', accountStatus: 1, presence: 'offline', accepting: false,
    canReceiveNewLeads: false, todayFollowUpRemainingCount: 0, todayFollowUpTotalCount: 0 },
  { ...base, userId: 4, name: '统计待更新', todayFollowUpRemainingCount: undefined, todayFollowUpTotalCount: undefined,
    todayOrderAmount: undefined, pendingQualificationCount: undefined }] as SubordinateSales[]
function Fixture() {
  const [selected, setSelected] = useState<number>()
  return <section className="workspace-page subordinate-sales-page" style={{ height: '100vh' }}>
    <div className="subordinate-inbox-layout show-list">
      <aside className="subordinate-sales-list-pane"><div className="subordinate-sales-list">
        {rows.map(sales => <SubordinateSalesCard key={sales.userId} sales={sales} selected={selected === sales.userId} onSelect={() => setSelected(sales.userId)} />)}
      </div></aside>
      <main className="subordinate-sales-detail-pane">已选择：{rows.find(row => row.userId === selected)?.name || '未选择'}</main>
    </div>
  </section>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>)
