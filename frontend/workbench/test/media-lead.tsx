// UTF-8. Isolated visual fixture; no production data or permissions are changed.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import MediaLeadAnalysisPage from '../src/pages/MediaLeadAnalysisPage'
import MediaLeadTargetPage from '../src/pages/MediaLeadTargetPage'
import { http } from '../src/services/api'

const scopes = [
  { key: 'CENTER:20', title: '新媒体中心', scopeType: 'CENTER', scopeId: 20 },
  { key: 'DEPT:10', parentKey: 'CENTER:20', title: '新媒体一部', scopeType: 'DEPT', scopeId: 10 },
  { key: 'USER:1', parentKey: 'DEPT:10', title: '测试运营', scopeType: 'USER', scopeId: 1 }
]
const target = { id: 3, scopeType: 'DEPT', scopeId: 10, name: '新媒体一部', periodStart: '2026-09-01', targetCount: 20, actualCount: 13, manual: true, version: 0 }
const overview = {
  asOf: '2026-09-28', target,
  periods: [
    ['yesterday', '昨日'], ['today', '今日'], ['lastWeek', '上周'], ['week', '本周'],
    ['lastTwoMonth', '上上月'], ['lastMonth', '上月'], ['month', '本月'], ['year', '本年'], ['all', '全部']
  ].map(([key, label], index) => ({ key, label, total: 20 + index, valid: 13, invalid: 3, converted: 6, validRate: .65, convertedRate: 6 / 13 })),
  members: [{ userId: 1, name: '测试运营', targetCount: 15, yesterday: 2, yesterdayConverted: 1, today: 3, todayConverted: 1, week: 5, weekValid: 4, weekConverted: 2, lastWeek: 6, lastWeekValid: 4, lastWeekConverted: 1, month: 19, monthValid: 13, monthConverted: 6, lastMonth: 18, lastMonthValid: 12, lastMonthConverted: 5, monthProgress: 13 / 15 }],
  calendar: [{ date: '2026-09-10', total: 17, valid: 11, invalid: 3, pending: 3 }, { date: '2026-09-28', total: 3, valid: 2, invalid: 0, pending: 1 }],
  funnel: { submitted: 20, valid: 13, converted: 6 },
  currentMonthChannels: [{ label: '视频号', count: 8 }, { label: '小红书', count: 5 }],
  lastMonthChannels: [{ label: '视频号', count: 9 }, { label: '小红书', count: 4 }],
  currentMonthCategories: [{ label: '考研', count: 7 }, { label: '留学', count: 6 }],
  lastMonthCategories: [{ label: '考研', count: 5 }, { label: '留学', count: 8 }]
}
const saved: unknown[] = []
const queries: unknown[] = []
const detailQueries: unknown[] = []
const paging = { fail: false, delay: 0 }
Object.assign(window, { mediaLeadPaging: paging })
let mistakenCenterActive = true
let mistakenCenterVersion = 0
;(window as Window & { mediaLeadMutations?: unknown[] }).mediaLeadMutations = saved
;(window as Window & { mediaLeadQueries?: unknown[] }).mediaLeadQueries = queries
;(window as Window & { mediaLeadDetailQueries?: unknown[] }).mediaLeadDetailQueries = detailQueries
http.defaults.adapter = async config => {
  const path = config.url ?? ''
  if (path.endsWith('/overview')) queries.push({ path, params: config.params })
  if (path.endsWith('/detail-page')) detailQueries.push({ path, params: config.params })
  let data: unknown = []
  if (config.method?.toLowerCase() === 'put') {
    const payload = JSON.parse(String(config.data))
    saved.push({ path, payload })
    if (path.endsWith('/org/unset')) { mistakenCenterActive = false; mistakenCenterVersion++ }
    else if (path.endsWith('/org') && payload.deptId === 10) { mistakenCenterActive = true; mistakenCenterVersion++ }
    data = true
  }
  else if (path.endsWith('/tree')) data = scopes
  else if (path.endsWith('/detail-page')) data = (config.params as { start?: string } | undefined)?.start === '2026-08-04' ? [
    { leadNo: 'KZ202608040001', submittedAt: Date.parse('2026-08-04T10:20:00+08:00'), contributorName: '测试运营', status: 'valid', statusLabel: '有效', channelLabel: '视频号', categoryLabel: '考研', converted: true, orderEffectiveAt: Date.parse('2026-08-05T12:00:00+08:00') }
  ] : [
    { leadNo: 'KZ202609280001', submittedAt: Date.parse('2026-09-28T10:20:00+08:00'), contributorName: '测试运营', status: 'valid', statusLabel: '有效', channelLabel: '视频号', categoryLabel: '考研', converted: true, orderEffectiveAt: Date.parse('2026-09-28T12:00:00+08:00') },
    { leadNo: 'KZ202609280002', submittedAt: Date.parse('2026-09-28T11:00:00+08:00'), contributorName: '测试运营', status: 'invalid', statusLabel: '无效', channelLabel: '视频号', categoryLabel: '考研', converted: false }
  ]
  else if (path.endsWith('/overview')) {
    const month = (config.params as { start?: string } | undefined)?.start?.slice(0, 7)
    data = month === '2026-09' ? overview : { ...overview,
      calendar: month === '2026-08' ? [{ date: '2026-08-04', total: 5, valid: 3, invalid: 1, pending: 1 }, { date: '2026-08-16', total: 3, valid: 2, invalid: 0, pending: 1 }] : [],
      funnel: month === '2026-08' ? { submitted: 8, valid: 5, converted: 2 } : { submitted: 0, valid: 0, converted: 0 }
    }
    if (location.search.includes('legacy')) data = { ...(data as typeof overview), funnel: undefined }
  }
  else if (path.endsWith('/revisions')) data = [{
    id: 9, targetId: 3, beforeJson: JSON.stringify({ targetCount: 15, manual: true }),
    afterJson: JSON.stringify({ targetCount: 20, manual: true }),
    reason: '团队目标调整', operatorId: 7, createTime: '2026-09-28T10:30:00'
  }]
  else if (path.endsWith('/list')) data = [
    { id: 1, scopeType: 'USER', scopeId: 1, name: '测试运营', periodStart: '2026-09-01', targetCount: 15, manual: true, version: 0 },
    target, { id: 4, scopeType: 'CENTER', scopeId: 20, name: '新媒体中心', periodStart: '2026-09-01', targetCount: 20, manual: false, version: 0 }
  ]
  else if (path.endsWith('/orgs')) data = [
    { deptId: 20, centerId: 20, kind: 'CENTER', name: '新媒体中心', centerName: '新媒体中心', version: 0 },
    ...(mistakenCenterActive ? [{ deptId: 10, centerId: 10, kind: 'CENTER', name: '新媒体一部', centerName: '新媒体一部', version: mistakenCenterVersion }] : []),
    { deptId: 10, centerId: 20, kind: 'DEPT', name: '新媒体一部', centerName: '新媒体中心' }
  ]
  else if (path.endsWith('/departments')) data = [{ id: 20, name: '新媒体中心', parentId: 0 }, { id: 10, name: '新媒体一部', parentId: 20 }]
  if (path.endsWith('/detail-page')) {
    if (paging.delay) await new Promise(resolve => setTimeout(resolve, paging.delay))
    if (paging.fail) throw new Error('明细加载失败')
    const seed = data as Record<string, unknown>[]
    const all = location.search.includes('paging') ? Array.from({ length: 65 }, (_, i) => ({ ...seed[i % seed.length], leadNo: 'KZ-PAGE-' + (i + 1) })) : seed
    const { pageNo = 1, pageSize = 20 } = config.params
    data = { list: all.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: all.length }
  }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = ['zsjos:media-lead-analysis:query', 'zsjos:media-lead-analysis:department', 'zsjos:media-lead-analysis:center', 'zsjos:media-lead-analysis:detail', 'zsjos:media-lead-target:query', 'zsjos:media-lead-target:update', 'zsjos:media-lead-target:configure']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>{location.search.includes('target') ? <MediaLeadTargetPage permissions={permissions} /> : <MediaLeadAnalysisPage permissions={permissions} />}</App></ThemeProvider></BrowserRouter>)
