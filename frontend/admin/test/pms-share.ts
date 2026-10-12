// UTF-8. Isolated transport fixture using the production dialog, clipboard and QR component.
import { createApp, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import { Dialog } from '../src/components/Dialog'
const state = { active: true, version: 1, missing: false, fail: false }
Object.assign(window, { pmsShareFixture: state })
const current = () => state.active ? {
  id: 1, documentId: 2, token: 'a'.repeat(31) + state.version, shareUserIds: [], status: 0,
  url: state.missing ? undefined : 'https://public.example.com/education/public-api/pms/kb/document-share/page?token=' + 'a'.repeat(31) + state.version
} : undefined
service.defaults.adapter = async (config) => {
  const path = config.url || ''
  let data: unknown
  if (path.includes('/pms/kb/document-share/')) {
    if (state.fail) throw new Error('fixture share failure')
    if (path.endsWith('/open')) { state.active = true; state.version++ }
    if (path.endsWith('/close')) state.active = false
    data = current()
  } else if (path.includes('simple-list')) data = []
  else throw new Error('Unexpected fixture request ' + path)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data }, request: { responseType: 'json' } }
}
const boot = createApp({}); setupStore(boot)
const { default: Share } = await import('../src/views/pms/kb/document/KnowledgeDocumentShareDialog.vue')
const app = createApp({
  setup() {
    const dialog = ref<InstanceType<typeof Share>>()
    return () => h('main', [h('button', { onClick: () => dialog.value?.open(2) }, '打开分享'), h(Share, { ref: dialog })])
  }
})
setupStore(app); await setupI18n(app); app.use(ElementPlus); app.component('Dialog', Dialog)
app.mount('#app')
