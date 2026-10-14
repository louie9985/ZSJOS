// UTF-8. Production attachment components with synthetic HTTP responses.
import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { Dialog } from '../src/components/Dialog'
import { Editor } from '../src/components/Editor'
import type { KnowledgeAttachment } from '../src/api/pms/kb/content/document'
const state = { fail: false, uploads: [] as string[], saved: [] as number[] }
Object.assign(window, { attachmentFixture: state })
let nextId = 1
const uploaded: KnowledgeAttachment[] = []
let stored: KnowledgeAttachment[] = []
let shared: number[] = []
let active = false
service.defaults.adapter = async (config) => {
  let data: unknown
  const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
  if (config.url === '/pms/kb/document/get') data = { id: 1, title: '培训文档', type: 3, content: '<p>培训文档正文</p>', labelIds: [], attachments: stored }
  else if (config.url === '/pms/kb/document/update') { stored = uploaded.filter(f => body.attachmentFileIds.includes(f.fileId)); data = true }
  else if (config.url === '/pms/kb/document-share/get') data = active ? { id: 1, documentId: 1, shareUserIds: [], attachmentFileIds: shared, url: 'https://example.test/share' } : null
  else if (config.url === '/pms/kb/document-share/open' || config.url === '/pms/kb/document-share/settings') { active = true; shared = body.attachmentFileIds; data = { id: 1, documentId: 1, shareUserIds: [], attachmentFileIds: shared, url: 'https://example.test/share' }; Object.assign(state, { shared }) }
  else if (config.url?.endsWith('/list') || config.url?.includes('simple-list')) data = []
  else if (config.url?.endsWith('/upload')) {
    const file = (config.data as FormData).get('file') as File
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (state.fail) throw new Error('fixture upload failure')
    state.uploads.push(file.name)
    data = { fileId: nextId++, fileName: file.name, fileSize: file.size }
    uploaded.push(data as KnowledgeAttachment)
  } else data = 'https://example.test/fixture.pdf'
  return {
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
    data: { code: 0, data },
    request: { responseType: 'json' }
  }
}
const boot = createApp({})
setupStore(boot)
const { default: Picker } =
  await import('../src/views/pms/kb/document/KnowledgeAttachmentPicker.vue')
const { default: Reader } = await import('../src/views/pms/kb/document/KnowledgeAttachments.vue')
const { default: DocumentEditor } = await import('../src/views/pms/kb/document/KnowledgeDocumentUpdateForm.vue')
const { default: Share } = await import('../src/views/pms/kb/document/KnowledgeDocumentShareDialog.vue')
const app = createApp({
  setup() {
    const files = ref<KnowledgeAttachment[]>([])
    const blocked = ref(false)
    const editor = ref<InstanceType<typeof DocumentEditor>>()
    const share = ref<InstanceType<typeof Share>>()
    return () =>
      h('main', { style: 'max-width:900px;margin:auto;padding:16px' }, [
        h('h1', '知识库文档附件'),
        h('p', '培训文档正文'),
        h('button', { onClick: () => editor.value?.open(1) }, '编辑完整文档'),
        h('button', { onClick: () => share.value?.open(1) }, '分享完整文档'),
        h(DocumentEditor, { ref: editor }),
        h(Share, { ref: share }),
        h(Picker, {
          modelValue: files.value,
          documentId: 1,
          'onUpdate:modelValue': (value: KnowledgeAttachment[]) => (files.value = value),
          onBlocked: (value: boolean) => (blocked.value = value)
        }),
        h(
          'button',
          {
            disabled: blocked.value,
            onClick: () => (state.saved = files.value.map((file) => file.fileId))
          },
          '保存'
        ),
        h(Reader, { files: files.value, documentId: 1 })
      ])
  }
})
setupStore(app)
await setupI18n(app)
app.use(ElementPlus)
app.component('Dialog', Dialog)
app.component('Editor', Editor)
app.directive('dompurify-html', { mounted: (el, binding) => { el.innerHTML = binding.value }, updated: (el, binding) => { el.innerHTML = binding.value } })
app.component('Icon', { render: () => h('span') })
app.mount('#app')
