import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import HomeCalendarPanel from '../src/components/HomeCalendarPanel'
import { calendarRestDays, type RestDay } from '../src/services/calendarRestDays'
import { todayWallTime } from '../src/components/calendarViewLayout'
import { ApiError, http } from '../src/services/api'
const params = new URLSearchParams(location.search)
const key = 'home-rest-days-fixture'
const today = todayWallTime().format('YYYY-MM-DD')
const initial: RestDay[] = [{ date: today, rest: true, version: 1 }]
const rows = (): RestDay[] => JSON.parse(localStorage.getItem(key) || 'null') ?? initial
let failures = 0
http.defaults.adapter = async () => { throw new Error('隔离验收禁止真实接口调用') }
calendarRestDays.list = async (start, end) => {
  if (params.has('loadfail')) throw new Error('模拟读取失败')
  if (params.has('delay')) await new Promise(resolve => setTimeout(resolve, start.includes('-11-') ? 10 : 250))
  return rows().filter(row => row.date >= start && row.date <= end)
}
calendarRestDays.save = async changes => {
  if (params.has('fail') && failures++ === 0) throw new Error('模拟保存失败')
  if (params.has('conflict') && failures++ === 0) throw new ApiError(1900018061,'休息日已被其他人更新，请刷新后核对重试')
  const map = Object.fromEntries(rows().map(row => [row.date, row]))
  for (const row of changes) map[row.date] = { ...row, version: (map[row.date]?.version ?? 0) + 1 }
  localStorage.setItem(key, JSON.stringify(Object.values(map)))
  return changes
}
const permissions = params.has('denied') ? [] : ['zsjos:business-task:query', ...(params.has('readonly') ? [] : ['zsjos:calendar-rest-day:manage'])]
createRoot(document.getElementById('root')!).render(<ThemeProvider><App>
  <main style={{ padding: 16 }}><div className="home-dashboard-grid"><div className="home-summary-region" /><HomeCalendarPanel permissions={permissions} /><div className="home-business-panel" /><div className="home-announcement-panel" /></div></main>
</App></ThemeProvider>)
