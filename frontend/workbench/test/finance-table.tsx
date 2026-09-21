// UTF-8. Test-only API fixtures; no requests reach business services.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Space } from 'antd'
import { CashbackPage, WithdrawalPage } from '../src/pages/ManagementPages'
import { http } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'

const rows = ['pending_review', 'approved', 'rejected', 'paid', 'cancelled'].map((status, index) => ({
  id: index + 1, withdrawalNo: `TEST-W-${index + 1}`, applicantUserId: 1,
  applicationAmount: index === 0 ? 0 : 1250.75, status,
  accountNameSnapshot: '测试收款账户', maskedCardNumber: '****0000', bankNameSnapshot: '测试银行',
  submittedAt: '2026-09-21T10:30:00'
}))
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/withdrawal/page')) {
    const status = config.params?.status
    document.getElementById('query-state')!.textContent = `提交的状态编码：${status || '全部'}`
    const list = rows.filter(row => !status || row.status === status)
    data = { list, total: list.length }
  } else if (/\/withdrawal\/\d+\/finance-detail$/.test(url)) {
    data = rows.find(row => url.includes(`/${row.id}/`))
  } else if (url.endsWith('/cashback/page')) {
    data = { list: [{ id: 1, cashbackNo: 'TEST-C', type: 'valid', status: 'available', baseAmount: 200, rateSnapshot: 0.15, amount: 30, generatedAt: '2026-09-21T10:30:00' }], total: 1 }
  }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
function Fixture() {
  const [view, setView] = useState('withdrawal')
  return <main style={{ padding: 16 }}><Space><Button onClick={() => setView('withdrawal')}>提现验证</Button><Button onClick={() => setView('cashback')}>返现验证</Button><span id="query-state" /></Space>
    {view === 'withdrawal' ? <WithdrawalPage permissions={['zsjos:withdrawal:finance-query']} /> : <CashbackPage permissions={['zsjos:cashback:finance-query']} />}
  </main>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture /></App></ThemeProvider>)
