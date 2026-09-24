// UTF-8. Isolated browser fixture; synthetic data and no business API writes.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button, Space } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SubordinateSalesPage from '../src/pages/SubordinateSalesPage'
import { api, type AdvancedFilterField, type SubordinateSales } from '../src/services/api'

let failCatalog = false
let failList = false
const requests: string[] = []
const base = { username: 'synthetic', mobile: '', presence: 'offline', accepting: false,
  canReceiveNewLeads: false, todayPendingCount: 0, validLeadCount: 0, convertedLeadCount: 0,
  effectiveOrderAmount: 0 } as SubordinateSales
const rows = [
  { ...base, userId: 1, name: '启用测试员工', accountStatus: 0 },
  { ...base, userId: 2, name: '停用测试员工', accountStatus: 1 },
]
api.advancedFilterCatalog = async () => {
  if (failCatalog) { failCatalog = false; throw new Error('模拟状态选项加载失败') }
  return { fields: [{ fieldKey: 'subordinate.accountStatus', label: '账号状态', valueType: 'select',
    operators: ['eq'], options: [{ value: '0', label: '启用' }, { value: '1', label: '停用' }] } as AdvancedFilterField], relativeDateOptions: [] }
}
api.advancedFilterTemplates = async () => []
api.subordinateSalesPage = async params => {
  requests.push(`page=${params.pageNo}, status=${params.accountStatus ?? 'all'}, keyword=${params.keyword || '-'}`)
  const log = document.getElementById('requests')
  if (log) log.textContent = requests.slice(-4).join(' | ')
  if (failList) { failList = false; throw new Error('模拟列表加载失败') }
  // Different latencies let rapid tab switching exercise the stale-response guard.
  await new Promise(resolve => setTimeout(resolve, params.accountStatus === 1 ? 500 : 50))
  const list = rows.filter(row => (params.accountStatus == null || row.accountStatus === params.accountStatus)
    && (!params.keyword || row.name.includes(params.keyword)))
  return { list, total: list.length }
}

function Fixture() {
  const [revision, setRevision] = useState(0)
  return <><Space wrap>
    <Button onClick={() => setRevision(value => value + 1)}>重新进入</Button>
    <Button onClick={() => { failCatalog = true; setRevision(value => value + 1) }}>模拟选项失败</Button>
    <Button onClick={() => { failList = true; setRevision(value => value + 1) }}>模拟列表失败</Button>
    <a href="?narrow=1">390px 验收</a>
  </Space><div id="requests" style={{ overflowWrap: 'anywhere' }} />
    <div style={{ height: 'calc(100vh - 90px)' }}><SubordinateSalesPage key={revision} permissions={[]} /></div></>
}
const narrow = new URLSearchParams(location.search).has('narrow')
createRoot(document.getElementById('root')!).render(narrow
  ? <iframe title="390px 下属销售" src="subordinate-sales-status.html" style={{ width: 390, height: 800, border: 0 }} />
  : <ThemeProvider><App><Fixture /></App></ThemeProvider>)
