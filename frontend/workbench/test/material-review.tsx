// Synthetic transport for the real page. Never touches live material or BPM records.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MaterialLibraryPage from '../src/pages/MaterialLibraryPage'
import { http } from '../src/services/api'
import type { Material, MaterialFieldDefinition } from '../src/services/materialApi'
import '../src/styles/index.css'

const params = new URLSearchParams(location.search)
const fields: MaterialFieldDefinition[] = [
  { key: 'account_name', label: '账号名称', type: 'text', section: 'ACCOUNT_DETAIL' },
  { key: 'homepage', label: '主页链接', type: 'https-link', section: 'ACCOUNT_DETAIL' },
  { key: 'works', label: '最火作品', type: 'repeat-group', section: 'DIRECTOR_ANALYSIS', children: [
    { key: 'link', label: '作品链接', type: 'https-link' }
  ] },
  { key: 'plan', label: '搭建建议', type: 'textarea', section: 'BUILD_SUGGESTION' }
]
const type = { id: 2, code: 'viral_account', name: '爆款账号', status: 0, currentSchema: { fields } }
const rows: Material[] = (['DRAFT', 'IN_APPROVAL', 'EFFECTIVE', 'REJECTED'] as const).map((status, index) => ({
  id: index + 1, materialTypeId: 2, materialTypeName: '爆款账号', materialNo: `MAT-TEST-${index + 1}`,
  title: ['草稿拆解', '修订审核中', '已通过拆解', '驳回待修改'][index], status: index === 1 ? 'EFFECTIVE' : status,
  source: 'MANUAL', version: 1, ownerUserId: 10, ownerName: '测试作者', likeCount: 3, referenceCount: 2,
  favoriteCount: 0, liked: false, favorited: false, pinned: false, priority: 0, coverFileId: 99,
  currentDraftVersionId: status === 'EFFECTIVE' ? undefined : index + 10,
  currentEffectiveVersionId: index === 1 || index === 2 ? 20 : undefined,
  availableActions: params.has('no-action') ? [] : status === 'IN_APPROVAL' ? ['CANCEL'] : ['UPDATE'],
  currentVersion: { id: index + 10, materialId: index + 1, versionNo: index === 1 ? 2 : 1, schemaVersionId: 1,
    status, title: ['草稿拆解', '修订审核中', '已通过拆解', '驳回待修改'][index], fields, version: 1,
    values: { account_name: ['草稿拆解', '修订审核中', '已通过拆解', '驳回待修改'][index],
      homepage: 'https://example.com/home', works: [{ link: 'https://example.com/work' }], plan: '保留主题与原始内容' },
    dictSnapshot: {}, files: [], processInstanceId: index === 1 ? 'synthetic-process' : undefined,
    pendingApproverNames: index === 1 ? ['审核员甲', '审核员乙'] : [], submittedAt: index ? 1790208000000 : undefined,
    rejectionReason: status === 'REJECTED' ? '请补充拆解依据' : undefined
  }
}))
let cancelFailed = false
http.defaults.adapter = async config => {
  const url = config.url || ''; let data: unknown
  if (url.endsWith('/material-type/list')) data = [type]
  else if (url.endsWith('/material/page')) {
    const status = config.params?.versionStatus
    document.documentElement.dataset.filter = status || 'ALL'
    const list = rows.filter(item => !status || item.currentVersion?.status === status)
    data = { list, total: list.length }
  } else if (url.endsWith('/cancel-by-start-user')) {
    if (params.has('cancel-error') && !cancelFailed) { cancelFailed = true; throw new Error('该流程配置不允许撤回') }
    const body = JSON.parse(config.data)
    if (body.id !== 'synthetic-process' || !body.reason.trim()) throw new Error('撤回参数无效')
    rows[1].currentVersion!.status = 'DRAFT'; rows[1].availableActions = ['UPDATE']
    document.documentElement.dataset.cancelled = 'true'; data = true
  } else if (url.endsWith('/version/list')) {
    const id = Number(url.split('/').at(-3)); data = [rows.find(row => row.id === id)!.currentVersion]
  } else if (/\/material\/\d+$/.test(url)) {
    const row = rows.find(item => item.id === Number(url.split('/').pop()))!
    if (config.method === 'put') {
      const body = JSON.parse(config.data)
      row.currentVersion = { ...row.currentVersion!, status: 'DRAFT', values: body.values }
      row.currentDraftVersionId = row.currentVersion.id; row.version++; data = row.id
    } else data = structuredClone(row)
  } else if (url.endsWith('/submit')) {
    const row = rows.find(item => item.id === Number(url.split('/').at(-2)))!
    row.currentVersion!.status = 'IN_APPROVAL'; row.availableActions = ['CANCEL']
    document.documentElement.dataset.resubmitted = String(row.id); data = true
  } else if (config.method === 'get') data = []
  else throw new Error(`Unexpected mutation ${config.method} ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const permissions = ['zsjos:material:update', 'zsjos:material:submit', ...(params.has('no-cancel') ? [] : ['bpm:process-instance:cancel'])]
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={['/?view=mine']}><App>
  <MaterialLibraryPage permissions={permissions} />
</App></MemoryRouter>)
