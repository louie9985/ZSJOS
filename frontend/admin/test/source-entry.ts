// UTF-8. Synthetic reference data; no real requests or business mutations.
import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '../src/plugins/unocss'
import { service } from '../src/config/axios/service'
import { setupStore, store } from '../src/store'
import { setActivePinia } from 'pinia'
import { setupI18n } from '../src/plugins/vueI18n'
setActivePinia(store)
service.defaults.adapter = async config => {
  if (config.method !== 'get') throw new Error('Fixture forbids business writes')
  const url = config.url ?? ''
  const data = url.includes('new-media-providers') ? [{ id: 999, nickname: '验证新媒体' }] : url.includes('catalog') ? { spus: [], skus: [] } : []
  return { config, request: { responseType: 'json' }, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const { default: Form } = await import('../src/views/zsjos/components/LeadCreateDialog.vue')
const app = createApp({ render: () => h(Form, { ref: 'form', selfSourced: true, educationSelfSourced: location.search.includes('education') }) })
setupStore(app); await setupI18n(app); app.use(ElementPlus)
const vm = app.mount('#app')
;(vm.$refs.form as { open: () => void }).open()
