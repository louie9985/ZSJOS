// Synthetic acceptance entry, excluded from the production application.
// 素材管理详情抽屉的只读审批轨迹：?bpm=1 授予 bpm:process-instance:query。
import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { setupGlobCom } from '../src/components'
import { setupElementPlus } from '../src/plugins/elementPlus'
import { useUserStore } from '../src/store/modules/user'
import { service } from '../src/config/axios/service'
import '../src/styles/index.scss'

const params = new URLSearchParams(location.search)
const fields = [
  { key: 'account_name', label: '账号名称', type: 'text', section: 'ACCOUNT_DETAIL' }
]
const version = {
  id: 11, materialId: 1, versionNo: 2, schemaVersionId: 1, status: 'IN_APPROVAL', title: '修订审核中',
  coverPreviewUrl: '', summary: '审批快照摘要', values: { account_name: '修订审核中' }, fields,
  dictSnapshot: {}, files: [], processInstanceId: 'synthetic-process',
  processDefinitionVersion: 2, submittedAt: '2026-09-18T08:00:00Z'
}
const material = {
  id: 1, materialTypeId: 2, materialTypeName: '爆款账号', materialNo: 'MAT-TEST-2', title: '修订审核中',
  status: 'EFFECTIVE', source: 'MANUAL', version: 1, ownerUserId: 10, ownerName: '测试作者',
  likeCount: 0, referenceCount: 0, favoriteCount: 0, pinned: false, priority: 0,
  currentDraftVersionId: 11, currentVersion: version,
  availableActions: ['CANCEL']
}
service.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = []
  if (url.endsWith('/zsjos/material/page')) data = { list: [material], total: 1 }
  else if (url.endsWith('/zsjos/material-type/list')) data = [
    { id: 2, code: 'viral_account', name: '爆款账号', status: 0, allowManualCreate: true, currentSchema: { fields } },
    { id: 3, code: 'viral_content', name: '爆款内容', status: 0, allowManualCreate: true, currentSchema: { fields } }
  ]
  else if (url.endsWith('/version/list')) data = [version]
  else if (url.includes('/bpm/process-instance/get-approval-detail')) data = {
    status: 1,
    activityNodes: [
      { id: 'StartUserNode', name: '发起人', nodeType: 10, status: 2, tasks: [
        { id: 't-start', assigneeUser: { id: 10, nickname: '测试作者' }, endTime: '2026-09-18T08:00:00Z' }] },
      { id: 'Activity_0d6b214f', name: '爆款审核', nodeType: 11, status: 0, tasks: [
        { id: 't-review', assigneeUser: { id: 12, nickname: '程伟' }, createTime: '2026-09-18T08:05:00Z' }] },
      { id: 'EndEvent', name: '结束', nodeType: 99, status: -1, tasks: [] }
    ],
    todoTask: { id: 't-review', name: '爆款审核', status: 1, processInstanceId: 'synthetic-process' },
    processInstance: { id: 'synthetic-process', name: '爆款账号拆解审核', status: 1, businessKey: 'material-version:11' }
  }
  else if (url.includes('/bpm/comment/list-by-process-instance-id')) data = []
  else if (url.endsWith('/zsjos/material/1')) data = material
  else if (url.endsWith('/zsjos/material-approval/page')) data = { list: [], total: 0 }
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config, request: { responseType: 'json' } }
}
async function mount() {
  const app = createApp({ render: () => h(RouterView) })
  const path = '/zsjos/material-library/manage'
  setupStore(app)
  const { default: Page } = await import('../src/views/zsjos/material/index.vue')
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component: Page }] })
  await setupI18n(app)
  setupGlobCom(app)
  setupElementPlus(app)
  useUserStore().permissions = new Set([
    'zsjos:material:manage', 'zsjos:material:create',
    ...(params.has('bpm') ? ['bpm:process-instance:query'] : [])
  ])
  const { setupAuth } = await import('../src/directives')
  setupAuth(app)
  app.use(router)
  await router.push(path)
  await router.isReady()
  app.mount('#app')
}
void mount()
