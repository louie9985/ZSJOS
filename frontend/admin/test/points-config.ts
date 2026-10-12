import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '../src/plugins/unocss'
import { service } from '../src/config/axios/service'
import { setupStore, store } from '../src/store'
import { setActivePinia } from 'pinia'
setActivePinia(store)
import { setupI18n } from '../src/plugins/vueI18n'
const calls: unknown[] = []
Object.assign(window, { pointsConfigCalls: calls })
service.defaults.adapter = async config => {
  const path = config.url || ''; let data: unknown
  if (path.startsWith('/infra/config/get')) data = { id: 1, category: '积分兑换码', name: '积分平台API密钥', key: 'zsjos.points-redemption.api-key', value: '', sensitive: true, configured: true, visible: false, remark: '' }
  else if (path.endsWith('/infra/config/update')) { calls.push(JSON.parse(String(config.data))); data = true }
  else if (path.endsWith('/package-list')) data = [{ id: 1, name: '5000积分套餐', creditsAmount: 5000, validDays: 30, enabled: true }]
  else if (path.endsWith('/package-save')) { calls.push(JSON.parse(String(config.data))); data = 1 }
  else if (path === '/system/dict-data/simple-list') data = []
  else throw new Error('Unexpected isolated request ' + path)
  return { config, request: { responseType: 'json' }, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
const { useUserStore } = await import('../src/store/modules/user')
useUserStore().permissions = new Set(location.search.includes('readonly') ? ['zsjos:points-redemption:package'] : ['*:*:*'])
const { default: ConfigForm } = await import('../src/views/infra/config/ConfigForm.vue')
const { default: Packages } = await import('../src/views/zsjos/pointsPackages/index.vue')
const { default: Dialog } = await import('../src/components/Dialog/src/Dialog.vue')
const { Icon } = await import('../src/components/Icon')
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const app = createApp({ render: () => h('main', [h(ConfigForm, { ref: 'config' }), h(Packages)]) })
setupStore(app); await setupI18n(app); app.use(ElementPlus); hasPermi(app)
app.component('Dialog', Dialog); app.component('Icon', Icon);app.component('ContentWrap', { render() { return h('section', this.$slots.default?.()) } })
const vm = app.mount('#app')
Object.assign(window, { openPointsSecret: () => (vm.$refs.config as { open: (type: string,id: number) => void }).open('update',1) })
