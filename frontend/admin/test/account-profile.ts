// Synthetic browser acceptance entry; excluded from the production application.
import { createApp } from 'vue'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { setupGlobCom } from '../src/components'
import { setupElementPlus } from '../src/plugins/elementPlus'
import { useUserStore } from '../src/store/modules/user'
import Page from '../src/views/zsjos/mediaAccountFieldConfig/index.vue'
import '../src/styles/index.scss'
async function mount() {
  const app = createApp(Page)
  setupStore(app)
  await setupI18n(app)
  setupGlobCom(app)
  setupElementPlus(app)
  useUserStore().permissions = new Set(['zsjos:media-account-field-config:query','zsjos:media-account-field-config:update','zsjos:media-account-field-config:publish'])
  const { setupAuth } = await import('../src/directives')
  setupAuth(app)
  app.mount('#app')
}
void mount()
