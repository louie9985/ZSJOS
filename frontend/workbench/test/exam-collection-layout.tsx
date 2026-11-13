// Isolated layout fixture: mounts the production exam collection page with synthetic data only.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { OverlayCoordinatorProvider } from '../src/components/OverlayCoordinator'
import ExamCollectionPage from '../src/pages/ExamCollectionPage'
import { http } from '../src/services/api'
import { examCollectionApi, type ExamTaskDetail } from '../src/services/examCollection'

http.defaults.adapter = async () => { throw new Error('禁止真实接口请求') }
const now = Date.now()
const task = (id: number, title: string, status: 'OPEN' | 'COMPLETED', collected: number, expected: number, exceptions: number, completionReason?: string): ExamTaskDetail => ({
  id, title, description: '', status, revision: 1, createdAt: now - id * 86400000, completionReason, expected, collected, exceptions, additional: id === 2 ? 2 : 0,
  fields: [], classIds: id === 4 ? [] : [11], url: `http://192.168.2.17:10086/exam-collection#token=fixture-only-token-${id}-abcdefghijklmnopqrstuvwxyz0123456789`,
})
const tasks = [task(1, '2026年11月执业药师考试资料收集', 'OPEN', 38, 53, 3), task(2, '测试2', 'COMPLETED', 70, 70, 0, '已全部收齐，按计划结束收集。'), task(3, '测试202611091341', 'COMPLETED', 0, 53, 0, '1'), task(4, '测试3', 'OPEN', 0, 0, 0)]
examCollectionApi.page = async ({ status, keyword }) => { const list = tasks.filter(t => (!status || t.status === status) && (!keyword || t.title.includes(keyword))); return { list, total: list.length } }
examCollectionApi.detail = async id => tasks.find(t => t.id === id)!
examCollectionApi.members = async () => ({ total: 53, list: Array.from({ length: 20 }, (_, i) => ({ serviceRelationId: 100 + i, personId: i, studentName: `学员${i + 1}`, mobile: `1380000${String(i).padStart(4, '0')}`, className: '执业药师精品一班', archiveName: i % 3 ? `学员${i + 1}` : undefined, archiveMobile: i % 3 ? `1380000${String(i).padStart(4, '0')}` : undefined, archiveIdCard: i % 3 ? '110101199001011234' : undefined, plannerName: '规划师甲', active: true, lastSubmissionId: i % 2 ? i : undefined })) })
examCollectionApi.submissions = async () => ({ list: [], total: 0 })
examCollectionApi.events = async () => ({ list: [], total: 0 })
const permissions = ['query', 'create', 'notify', 'members', 'complete', 'reopen', 'resolve'].map(a => `zsjos:exam-collection:${a}`)
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><OverlayCoordinatorProvider>
  <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}><ExamCollectionPage permissions={permissions} /></div>
</OverlayCoordinatorProvider></App></ThemeProvider></BrowserRouter>)
