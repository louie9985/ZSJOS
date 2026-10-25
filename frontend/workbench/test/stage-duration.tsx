// UTF-8. Isolated visual verification; no real business requests.
import { createRoot } from 'react-dom/client'
import { App, Form } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { AccountProfiles } from '../src/pages/ContentReviewBatchPage'
import ContentReviewAccountPreview from '../src/components/ContentReviewAccountPreview'
import { contentReviewApi, type ContentReviewBatch } from '../src/services/materialApi'
import '../src/styles/index.css'
const asOf = Date.parse('2026-10-21T00:01:00+08:00')
const rows = [
  { id: 1, nickname: '正常记录账号', sStageLabel: 'S1 定位期', sStageEnteredAt: asOf - 60000,
    sStageDurationAsOf: asOf, sStageDurationDays: 0, sStageTimingSource: 'RECORDED' as const },
  { id: 2, nickname: '历史恢复账号', sStageLabel: 'S2 内容验证期', sStageEnteredAt: asOf - 12 * 86400000,
    sStageDurationAsOf: asOf, sStageDurationDays: 12, sStageTimingSource: 'HISTORY_RECONSTRUCTED' as const },
  { id: 3, nickname: '缺失记录账号', sStageLabel: 'S1 定位期' },
]
contentReviewApi.accountProfilePreview = async () => rows
const batch = { contextSnapshot: { accountSnapshots: rows }, accounts: [] } as unknown as ContentReviewBatch
function Fixture() {
  const [form] = Form.useForm()
  return <main style={{ padding: 16, containerType: 'inline-size', containerName: 'content-review-detail' }}><h2>本轮审批快照</h2>
    <AccountProfiles batch={batch} accountLink={account => account.accountName} />
    <h2>提交预览</h2><Form form={form}><ContentReviewAccountPreview studentPersonId={1} accountIds={[1, 2, 3]} /></Form>
  </main>
}
createRoot(document.getElementById('root')!).render(<MemoryRouter><ThemeProvider><App><Fixture /></App></ThemeProvider></MemoryRouter>)
