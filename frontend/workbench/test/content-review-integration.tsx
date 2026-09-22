// UTF-8. Production components with an isolated transport adapter: no business data or writes.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ContentReviewBatchPage from '../src/pages/ContentReviewBatchPage'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import TabBar, { type TabItem } from '../src/components/TabBar'
import RetainedReviewRoute from '../src/layouts/RetainedReviewRoutes'
import { WorkbenchPageNavigation } from '../src/components/WorkbenchPageNavigation'
import { APP_ROUTES } from '../src/constants'
import { api, http, type WorkbenchMenu } from '../src/services/api'
import type { ContentReviewBatch } from '../src/services/materialApi'
import { initialBatches } from './content-review-layout-data'
import '../src/styles/index.css'

const params = new URLSearchParams(window.location.search)
const permissions = ['zsjos:content-review:query', 'zsjos:content-review:query-all', 'zsjos:content-review:create', 'zsjos:content-review:director-review', 'bpm:task:update', 'zsjos:media-account:query']
const people = [{ id: 10, nickname: '运营甲' }, { id: 30, nickname: '运营乙' }, { id: 20, nickname: '编导甲' }, { id: 40, nickname: '编导乙' }]
const userId = (name: string) => people.find(person => person.nickname === name)?.id
const batches: ContentReviewBatch[] = initialBatches.map(batch => ({
  id: batch.id, batchNo: batch.no, accountId: batch.accounts[0].id, accountIds: batch.accounts.map(account => account.id),
  studentPersonId: batch.studentId, studentName: batch.student, operatorUserId: userId(batch.accounts[0].operator)!,
  operatorName: batch.accounts[0].operator, directorName: batch.accounts[0].director, status: batch.status,
  currentStage: 'DIRECTOR', version: 1, submittedAt: batch.submitted ? new Date(batch.submitted).getTime() : undefined,
  processInstanceId: `process-${batch.id}`, currentTaskId: `task-${batch.id}`,
  availableActions: batch.status === 'DIRECTOR_REVIEW' ? ['DIRECTOR_DECIDE'] : batch.status === 'DRAFT' ? ['SUBMIT', 'CANCEL'] : [],
  accounts: batch.accounts.map(account => ({ accountId: account.id, accountName: account.name, platformLabel: account.platform,
    platformValue: account.platform, operatorUserId: userId(account.operator), operatorName: account.operator,
    directorUserId: userId(account.director), directorName: account.director })),
  relationSnapshot: {}, contextSnapshot: { accountSnapshots: batch.accounts.map(account => ({ id: account.id, nickname: account.name,
    platformLabel: account.platform, platformValue: account.platform, ownerOperatorUserId: userId(account.operator), operatorName: account.operator,
    directorUserId: userId(account.director), directorName: account.director, sStageLabel: '内容起步期', currentStatusLabel: '稳定更新', productGoal: '建立信任', publishFrequency: '每周三条', bottleneckLabel: '提高开头吸引力' })) },
  items: batch.works.map((work, index) => ({ id: work.id, contentId: work.id, contentVersionId: work.id, contentRecordVersion: 1,
    sortNo: index, version: 1, collectMaterial: false, files: [], contentSnapshot: { title: work.title, titleSnapshot: work.title,
      topic: work.topic, scriptText: work.script, purposeLabelSnapshot: work.purpose, formatLabelSnapshot: work.format,
      contentNo: `WORK-${work.id}`, contentVersionNo: 1, plannedPublishAt: '2026-09-25 08:30', commentHook: '分享你的早餐搭配。' } }))
}))
let lastQuery = ''
api.bpmApprovalDetail = async () => ({ activityNodes: [] })
api.bpmCommentList = async () => []
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  let code = 0
  let msg = ''
  if (config.method !== 'get') throw new Error('验收入口禁止业务写入')
  if (url.endsWith('/content-review/batch/page')) {
    const q = config.params || {}; lastQuery = JSON.stringify(q)
    document.documentElement.dataset.reviewQuery = lastQuery
    if (params.get('listError') === '1') { code = 500; msg = '列表加载失败（隔离场景）' }
    const states = String(q.statuses || '').split(',').filter(Boolean)
    const list = batches.filter(batch => (!states.length || states.includes(batch.status)) && (!q.status || batch.status === q.status)
      && (!q.mine || batch.operatorUserId === 10)
      && (!q.submittedFrom || day(batch.submittedAt) >= q.submittedFrom) && (!q.submittedTo || Boolean(batch.submittedAt) && day(batch.submittedAt) <= q.submittedTo)
      && batch.accounts!.some(account => (!q.operatorUserId || account.operatorUserId === q.operatorUserId)
        && (!q.directorUserId || account.directorUserId === q.directorUserId) && (!q.platformValue || account.platformValue === q.platformValue))
      && (!q.keyword || [batch.studentName, batch.batchNo, ...batch.accounts!.map(account => account.accountName), ...batch.items.flatMap(item => [item.contentSnapshot.title, item.contentSnapshot.topic, item.contentSnapshot.scriptText])].some(value => String(value || '').includes(q.keyword))))
    if (q.keyword === '慢查询') await new Promise(resolve => setTimeout(resolve, 650))
    data = { list, total: list.length }
  } else if (url.endsWith('/content-review/batch/get')) {
    data = batches.find(batch => batch.id === Number(config.params.id))
  } else if (url.endsWith('/simple-list')) data = people
  else if (url.includes('/dict-data/')) data = ['抖音', '小红书', '视频号'].map((label, index) => ({ id: index + 1, label, value: label, dictType: 'zsjos_account_platform', status: 0 }))
  else if (url.endsWith('/media-students/page')) data = { list: initialBatches.map(batch => ({ personId: batch.studentId, name: batch.student, services: [] })), total: initialBatches.length }
  else if (/\/media-students\/\d+$/.test(url)) {
    const batch = initialBatches.find(item => item.studentId === Number(url.split('/').pop()))!
    if (params.get('accountDenied') === '1') { code = 403; msg = '无权查看该学员' }
    data = { student: { personId: batch.studentId, name: batch.student, services: [] },
      accounts: params.get('accountMissing') === '1' ? [] : batch.accounts.map(account => ({ id: account.id, nickname: account.name, accountNo: `ACC-${account.id}`,
        platformLabel: account.platform, version: 1, primaryProblems: [], availableActions: ['MAINTAIN_ACCOUNT'], detailSnapshots: [], taskLine: [] })),
      positioningCards: [], positioningDrafts: [], contents: [], productionTickets: [], operationTimeline: [], studentTaskLine: [], taskLine: [], pendingStats: { accountCount: batch.accounts.length, positioningCount: 0, contentCount: 0, productionCount: 0 } }
  } else if (/\/media-account\/\d+\/profile$/.test(url)) {
    const id = Number(url.split('/').at(-2)); const account = initialBatches.flatMap(batch => batch.accounts).find(item => item.id === id)!
    data = { account: { id, nickname: account.name, accountNo: `ACC-${id}`, version: 1, availableActions: [] }, config: { id: 1, versionNo: 1,
      fields: [{ key: 'nickname', label: '账号昵称', type: 'text', enabled: true, ownerType: 'OPERATOR', group: 'PROFILE', requiredForComplete: false, sourceType: 'MANUAL', snapshotPolicy: 'SNAPSHOT' }] },
      values: { nickname: account.name }, snapshots: [], editableFields: ['nickname'], missingFields: [], missingByOwner: {}, sourceNotes: {}, files: {}, canViewHistory: false, operatorName: account.operator, directorName: account.director }
  } else if (url.endsWith('/page')) data = { list: [], total: 0 }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code, msg, data } }
}
function day(value: unknown) { return value ? new Date(Number(value)).toLocaleDateString('sv-SE') : '' }
const menus: WorkbenchMenu[] = [APP_ROUTES.CONTENT_REVIEW, APP_ROUTES.MEDIA_STUDENTS].map((path, index) => ({ id: index + 1, name: index ? '媒体学员' : '内容审核', path, parentId: 0, hidden: false, noCache: false, alwaysShow: false, children: [] }))
function FixtureShell() {
  const location = useLocation()
  const [tabs, setTabs] = useState<TabItem[]>([])
  return <WorkbenchPageNavigation canOpen={path => menus.some(menu => menu.path === path) && !(params.get('noMenu') === '1' && path === APP_ROUTES.MEDIA_STUDENTS)}>
    <div style={{ height: '100dvh', display: 'flex', flexDirection: 'column', background: 'var(--crm-bg-layout)' }}>
      <div style={{ padding: 'var(--crm-sp-3) var(--crm-pane-pad)', color: 'var(--crm-text-secondary)' }}>中世健 · 正式组件验收 · 隔离示例数据</div>
      <TabBar tabs={tabs} setTabs={setTabs} currentMenu={menus.find(menu => menu.path === location.pathname)} />
      <div style={{ flex: 1, minHeight: 0 }}>
        {menus.filter(menu => menu.path === location.pathname || tabs.some(tab => tab.key === menu.path)).map(menu => <RetainedReviewRoute key={menu.path} active={menu.path === location.pathname}>
          {menu.path === APP_ROUTES.CONTENT_REVIEW ? <ContentReviewBatchPage permissions={permissions} /> : <MediaStudentsPage permissions={permissions} />}
        </RetainedReviewRoute>)}
      </div>
    </div>
  </WorkbenchPageNavigation>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><MemoryRouter initialEntries={[APP_ROUTES.CONTENT_REVIEW]}><FixtureShell /></MemoryRouter></App></ThemeProvider>)
