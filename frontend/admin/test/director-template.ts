// Synthetic acceptance entry, excluded from the production application.
import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { setupGlobCom } from '../src/components'
import { setupElementPlus } from '../src/plugins/elementPlus'
import { useUserStore } from '../src/store/modules/user'
import '../src/styles/index.scss'
async function mount() {
  const app = createApp({ render: () => h(RouterView) })
  const path = '/zsjos/director-config/interview-template'
  setupStore(app)
  const { default: Page } = await import('../src/views/zsjos/directorTemplate/index.vue')
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component: Page }] })
  await setupI18n(app)
  setupGlobCom(app)
  setupElementPlus(app)
  const readOnly = new URLSearchParams(location.search).get('permission') === 'read'
  useUserStore().permissions = new Set(['query', ...(readOnly ? [] : ['update', 'publish'])].map(x => `zsjos:director-interview-template:${x}`))
  const { setupAuth } = await import('../src/directives')
  setupAuth(app)
  app.use(router)
  await router.push(path)
  await router.isReady()
  app.mount('#app')
}
void mount()
