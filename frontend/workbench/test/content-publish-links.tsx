// UTF-8. Isolated fixtures for publish registration and published-work links.
// No real business requests or mutations: the adapter answers only the listed
// endpoints and records the publish payload so the browser check can assert on it.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import AccountPublishedWorks from '../src/components/AccountPublishedWorks'
import ContentProductionPage from '../src/pages/ContentProductionPage'
import ContentReviewBatchPage from '../src/pages/ContentReviewBatchPage'
import { http } from '../src/services/api'
import type { ContentReviewBatch } from '../src/services/materialApi'
import type { MediaContent, MediaStudentDetail } from '../src/services/api'
import '../src/styles/index.css'

const ACCOUNT_ID = 41
const view = new URLSearchParams(location.search).get('view') || 'review'
const fixture = { requests: [] as unknown[], posts: 0, view }
Object.assign(window, { publishLinksFixture: fixture })

const batch: ContentReviewBatch = {
  id: 9, batchNo: 'CR-PUBLISH-9', studentPersonId: 20, studentName: '发布登记验收学员',
  accountId: ACCOUNT_ID, accountIds: [ACCOUNT_ID], operatorUserId: 1, directorUserId: 2,
  currentStage: 'DONE', status: 'COMPLETED', version: 3, submittedAt: 1790049600000,
  relationSnapshot: {}, availableActions: ['REGISTER_PUBLISH'],
  contextSnapshot: { accountSnapshots: [{ id: ACCOUNT_ID, nickname: '发布验收账号', platformLabel: '抖音', operatorName: '验收运营', directorName: '验收编导' }] },
  items: [{
    id: 90, contentId: 900, contentVersionId: 9000, contentRecordVersion: 7, sortNo: 0, version: 2,
    directorDecision: 'APPROVED', finalDecision: 'APPROVED', resultStatus: 'READY_TO_PUBLISH', collectMaterial: false,
    files: [],
    contentSnapshot: { contentNo: 'CT-PUBLISH-900', contentVersionNo: 2, titleSnapshot: '已通过终审、等待登记发布的作品',
      plannedPublishAt: '2026-09-20T09:30:00', purposeLabelSnapshot: '建立信任', formatLabelSnapshot: '图文',
      scriptText: '登记发布时填写的应当是作品的真实发布时间。' },
  }],
}

const detailContents: MediaStudentDetail['contents'] = [{
  id: 900, accountId: ACCOUNT_ID, contentNo: 'CT-PUBLISH-900', title: '已完成登记的作品',
  status: 'published', currentVersionNo: 2, publishedAt: 1790049600000,
  publishedUrl: 'https://example.com/published-work', version: 4, availableActions: [],
}, {
  id: 901, accountId: ACCOUNT_ID, contentNo: 'CT-NOLINK-901', title: '没有链接的历史作品',
  status: 'published', currentVersionNo: 1, publishedAt: 1790049500000, version: 2, availableActions: [],
}, {
  id: 902, accountId: ACCOUNT_ID, contentNo: 'CT-DRAFT-902', title: '尚未发布的草稿',
  status: 'topic', version: 1, availableActions: [],
}]

const mediaContent: MediaContent = {
  id: 900, contentNo: 'CT-PUBLISH-900', accountId: ACCOUNT_ID, title: '已完成登记的作品',
  status: 'published', currentVersionNo: 2, publishedUrl: 'https://example.com/published-work',
  publishedAt: 1790049600000, version: 4, availableActions: [],
}
const mediaVersions = [{
  id: 9000, contentId: 900, versionNo: 2, stage: 'published', reviewDecision: 'approved',
  plannedPublishAt: '2026-09-20T09:30:00', titleSnapshot: '已完成登记的作品',
  detailUrl: 'https://example.com/detail', leadResourceUrl: 'https://example.com/lead',
  referenceWorkUrl: 'https://example.com/reference', commentHook: '评论区钩子示例',
  files: [],
}]

http.defaults.adapter = async config => {
  const url = config.url || ''
  const response = (payload: unknown) => ({ config, data: payload, status: 200, statusText: 'OK', headers: {} })
  if (config.method === 'get') {
    let data: unknown = []
    if (url.endsWith('/content-review/batch/page')) data = { list: [batch], total: 1 }
    else if (url.endsWith('/content-review/batch/get')) data = structuredClone(batch)
    else if (url.endsWith('/content-review/batch/history')) data = [batch]
    else if (url.endsWith('/simple-list')) data = []
    else if (url.endsWith('/content/version/list')) data = structuredClone(mediaVersions)
    else if (url.endsWith('/content/get')) data = structuredClone(mediaContent)
    else if (url.endsWith('/content/page')) data = { list: [mediaContent], total: 1 }
    else if (url.includes('dict-data')) data = []
    return response({ code: 0, data })
  }
  if (url.includes('/publish')) {
    fixture.posts++
    fixture.requests.push(JSON.parse(String(config.data)))
    return response({ code: 0, data: true })
  }
  throw new Error(`Unexpected fixture request: ${config.method} ${url}`)
}

function Fixture() {
  return view === 'review'
    ? <div style={{ height: '100dvh' }}><ContentReviewBatchPage permissions={['zsjos:content-review:query', 'zsjos:content-review:director-review']} /></div>
    : view === 'production'
      ? <ContentProductionPage permissions={['zsjos:content:query', 'zsjos:content:edit']} />
      : <section className="workspace-page"><AccountPublishedWorks accountId={ACCOUNT_ID} contents={detailContents} canQuery /></section>
}

createRoot(document.getElementById('root')!).render(<MemoryRouter><App><Fixture /></App></MemoryRouter>)
