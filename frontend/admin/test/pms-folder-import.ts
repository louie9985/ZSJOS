// UTF-8. Synthetic server adapter; real production component, hashing, queue, and HTTP client.
import { createApp, h, ref } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { Dialog } from '../src/components/Dialog'
import type { ImportTask, ImportRequest } from '../src/api/pms/kb/importing'
const fixture = {
  fail: false,
  failUpload: false,
  uploads: [] as string[],
  concurrent: 0,
  maximum: 0
}
Object.assign(window, { importFixture: fixture })
let task: ImportTask | undefined = JSON.parse(localStorage.getItem('import-fixture') || 'null')
function save() {
  if (task) {
    task.success = task.items.filter((i) => i.status === 'SUCCESS').length
    task.skipped = task.items.filter((i) => i.status === 'SKIPPED').length
    task.failed = task.items.filter((i) => i.status === 'FAILED').length
    task.status = task.success + task.skipped === task.total ? 'COMPLETED' : 'OPEN'
    localStorage.setItem('import-fixture', JSON.stringify(task))
  }
}
service.defaults.adapter = async (config) => {
  const url = config.url || ''
  let data: unknown
  const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  if (fixture.fail) throw new Error('加载失败，请重试')
  if (url.endsWith('/policy'))
    data = {
      maxFiles: 1000,
      maxFileSize: 104857600,
      maxTotalSize: 2147483648,
      maxDepth: 20,
      concurrency: 3,
      fileTypes: ['txt', 'pdf', 'docx']
    }
  else if (url.endsWith('/prepare'))
    data = (body as ImportRequest).files.map((f, i) => ({
      ...f,
      id: i + 1,
      status: f.digest ? 'PENDING' : 'SKIPPED',
      reason: f.digest ? '' : '不支持的文件类型'
    }))
  else if (url.endsWith('/create')) {
    const req = body as ImportRequest
    task = {
      id: 1,
      libraryId: req.libraryId || 17,
      folderId: req.folderId || 0,
      mode: req.mode,
      rootName: req.rootName,
      status: 'OPEN',
      total: req.files.length,
      success: 0,
      skipped: 0,
      failed: 0,
      items: req.files.map((f, i) => ({
        ...f,
        id: i + 1,
        status: f.digest ? 'PENDING' : 'SKIPPED'
      }))
    }
    save()
    data = task
  } else if (url.endsWith('/upload')) {
    fixture.concurrent++
    fixture.maximum = Math.max(fixture.maximum, fixture.concurrent)
    const item = task!.items.find((i) => i.id === Number((body as FormData).get('itemId')))!
    await new Promise((resolve) => setTimeout(resolve, 350))
    fixture.concurrent--
    if (fixture.failUpload && item.relativePath.endsWith('/b.txt')) {
      item.status = 'FAILED'
      save()
      throw new Error('存储失败，请重试')
    }
    fixture.uploads.push(item.relativePath)
    item.status = 'SUCCESS'
    save()
    data = task
  } else if (url.endsWith('/get')) data = task
  else if (url.endsWith('/resume')) {
    task!.items.forEach((i) => {
      if (i.status === 'FAILED') i.status = 'PENDING'
    })
    save()
    data = task
  } else if (url.endsWith('/page')) data = { list: task ? [task] : [], total: task ? 1 : 0 }
  else if (url.endsWith('/finish')) {
    task!.status = 'FINISHED'
    localStorage.setItem('import-fixture', JSON.stringify(task))
    data = task
  } else if (url.includes('simple-list')) data = []
  else throw new Error('Unexpected fixture request: ' + url)
  return {
    config,
    status: 200,
    statusText: 'OK',
    headers: {},
    data: { code: 0, data: structuredClone(data) },
    request: { responseType: 'json' }
  }
}
const boot = createApp({})
setupStore(boot)
const { default: Import } = await import('../src/views/pms/kb/importing/KnowledgeFolderImport.vue')
const app = createApp({
  setup() {
    const dialog = ref<InstanceType<typeof Import>>()
    return () =>
      h('main', [
        h('button', { onClick: () => dialog.value?.open() }, '从文件夹创建'),
        h(
          'button',
          {
            onClick: () =>
              dialog.value?.open({ libraryId: 18, folderId: 9, label: '销售资料 / 培训' })
          },
          '上传到已有库'
        ),
        h('button', { onClick: () => dialog.value?.history() }, '我的导入记录'),
        h(Import, { ref: dialog })
      ])
  }
})
const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }]
})
setupStore(app)
await setupI18n(app)
app.use(router)
app.use(ElementPlus)
app.directive('hasPermi', {})
app.component('Dialog', Dialog)
app.component('UploadImg', { render: () => h('span', '封面上传控件（独立夹具）') })
await router.isReady()
app.mount('#app')
