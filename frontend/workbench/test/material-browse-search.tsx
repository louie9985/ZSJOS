// Isolated synthetic transport: no live material, account or dictionary records are changed.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MaterialLibraryPage from '../src/pages/MaterialLibraryPage'
import { http } from '../src/services/api'
import type { Material, MaterialFieldDefinition } from '../src/services/materialApi'
import '../src/styles/index.css'

const fields: MaterialFieldDefinition[] = [
  { key: 'topic', label: '主题', type: 'text', searchable: true },
  { key: 'category', label: '分类', type: 'dict-multi', searchable: true, dictType: 'synthetic' },
  { key: 'copy', label: '文案', type: 'textarea', searchable: true },
  { key: 'rich', label: '富文本', type: 'rich-text', searchable: true },
  { key: 'works', label: '作品', type: 'repeat-group', children: [{ key: 'text', label: '说明', type: 'text', searchable: true }] },
  { key: 'private', label: '未配置检索字段', type: 'text' }
]
const svg = (width: number, height: number) => `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="#cbe5f5"/><text x="20" y="45" font-size="24">${width} × ${height}</text></svg>`)}`
const covers = [svg(800, 400), svg(400, 700), svg(400, 1800), undefined, '/synthetic-missing-cover.png']
const rows: Material[] = Array.from({ length: 23 }, (_, index) => ({
  id: index + 1, materialNo: `MAT-${index + 1}`, materialTypeId: 2, materialTypeName: '验收素材', title: `素材 ${index + 1}`,
  status: 'EFFECTIVE', source: 'MANUAL', version: 1, ownerUserId: 1, ownerName: '测试作者',
  likeCount: 0, referenceCount: 0, favoriteCount: 0, liked: false, favorited: false, pinned: false, priority: 0,
  availableActions: [], coverPreviewUrl: covers[index % 5], currentEffectiveVersionId: index + 100,
  currentVersion: { id: index + 100, materialId: index + 1, versionNo: 1, schemaVersionId: 1, status: 'EFFECTIVE',
    title: `素材 ${index + 1}`, version: 1, fields, files: [],
    values: { topic: '摄影', category: ['old'], copy: '前文'.repeat(80) + '长文命中' + '后文'.repeat(80),
      rich: '<p>富文本 &amp; 中文</p>', works: [{ text: '第一条' }, { text: '独立命中' }], private: '不可展示' },
    dictSnapshot: { category: [{ value: 'old', label: '历史分类' }] }
  }
}))
const params = new URLSearchParams(location.search)
const adaptation = params.has('adaptation')
const longPersonaLabel = '用于检查完整显示的超长账号类型字典选项'
const publication = params.has('publication')
const browseRows = publication ? rows.slice(0, 1).map(row => ({ ...row, materialTypeName: '爆款账号',
  currentVersion: { ...row.currentVersion!, title: 'Published account', values: { account_name: 'Published account' },
    fields: [{ key: 'account_name', label: '账号名称', type: 'text' as const }] } })) : rows
let failed = false
let dictionaryFailed = false
const personalRows: Material[] = publication ? ['DRAFT', 'IN_APPROVAL', 'REJECTED', 'EFFECTIVE'].map((status, index) => ({
  ...browseRows[0], id: index + 1, currentDraftVersionId: status === 'EFFECTIVE' ? undefined : 200 + index,
  currentVersion: { ...browseRows[0].currentVersion!, id: 200 + index, materialId: index + 1,
    status: status as Material['status'] & NonNullable<Material['currentVersion']>['status'],
    title: `Personal ${status}`, values: { account_name: `Personal ${status}` },
    pendingApproverNames: status === 'IN_APPROVAL' ? ['Fixture reviewer'] : [],
    rejectionReason: status === 'REJECTED' ? 'Fixture rejection' : undefined }
})) : rows
http.defaults.adapter = async config => {
  const url = config.url || ''; let data: unknown
  if (url.endsWith('/material-type/list')) data = [{ id: 2, code: publication ? 'viral_account' : 'synthetic', name: '验收素材', status: 0 }]
  else if (url.endsWith('/material/browse/page') || url.endsWith('/material/page')) {
    document.documentElement.dataset.listUrl = url
    if (url.endsWith('/material/page') && config.params?.mine !== true) throw new Error('Personal request must include mine=true')
    document.documentElement.dataset.request = JSON.stringify(config.params)
    if (params.has('error') && !failed) { failed = true; throw new Error('素材加载失败（验收）') }
    if (params.has('denied')) return { data: { code: 403, msg: '无权访问素材库' }, status: 200, statusText: 'OK', headers: {}, config }
    const keyword = config.params?.keyword || ''
    const source = url.endsWith('/material/page') ? personalRows : browseRows
    const list = keyword === '不存在' ? [] : source.filter(row =>
      (!config.params?.versionStatus || row.currentVersion?.status === config.params.versionStatus)
      && (!adaptation || !config.params?.accountType || (row.id % 2 ? 'persona-a' : 'persona-b') === config.params.accountType)
      && (!adaptation || !config.params?.profession || config.params.profession === 'profession-a' || row.id % 3 === 0)
      && (!adaptation || !config.params?.accountStage || (row.id % 2 ? 'stage-a' : 'stage-b') === config.params.accountStage))
    const offset = (config.params.pageNo - 1) * config.params.pageSize
    data = { list: list.slice(offset, offset + config.params.pageSize), total: list.length }
    await new Promise(resolve => setTimeout(resolve, 80))
  } else if (/\/material\/\d+$/.test(url)) {
    document.documentElement.dataset.detailRequest = url
    data = personalRows.find(row => row.id === Number(url.split('/').pop()))
  } else if (/\/material\/\d+\/version\/list$/.test(url)) {
    document.documentElement.dataset.historyRequest = url
    data = [personalRows.find(row => row.id === Number(url.split('/').at(-3)))?.currentVersion]
  } else if (/\/material\/browse\/\d+$/.test(url)) {
    document.documentElement.dataset.detailRequest = url
    data = browseRows.find(row => row.id === Number(url.split('/').pop()))
    if (!data) return { data: { code: 403, msg: '无权浏览未生效素材' }, status: 200, statusText: 'OK', headers: {}, config }
  } else if (/\/material\/browse\/\d+\/version\/list$/.test(url)) {
    document.documentElement.dataset.historyRequest = url
    data = [browseRows[0].currentVersion]
  }
  else if (url.endsWith('/recommendation-account-candidates')) { document.documentElement.dataset.unexpectedRecommendation = 'true'; throw new Error('Unexpected recommendation request') }
  else if (url.includes('/dict-data/')) {
    if (adaptation && params.has('dictError') && !dictionaryFailed) { dictionaryFailed = true; throw new Error('字典加载失败（验收）') }
    data = !adaptation ? [] : [
      { dictType: 'zsjos_persona_type', value: 'persona-a', label: longPersonaLabel },
      { dictType: 'zsjos_persona_type', value: 'persona-b', label: '账号类型乙' },
      { dictType: 'zsjos_material_profession', value: 'profession-a', label: '业务定位甲' },
      { dictType: 'zsjos_material_profession', value: 'profession-b', label: '业务定位乙' },
      { dictType: 'zsjos_media_account_stage', value: 'stage-a', label: '账号期段甲' },
      { dictType: 'zsjos_media_account_stage', value: 'stage-b', label: '账号期段乙' }
    ]
  }
  else throw new Error(`Unexpected fixture request: ${url}`)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
createRoot(document.getElementById('root')!).render(<App><MemoryRouter>
  <MaterialLibraryPage permissions={publication ? ['zsjos:material:query', 'zsjos:material:manage', 'zsjos:material:create',
    'zsjos:material:update', 'zsjos:material:submit', 'zsjos:material-approval:approve', 'bpm:process-instance:cancel'] : ['zsjos:material:query']} />
</MemoryRouter></App>)
