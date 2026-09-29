import '../src/plugins/unocss'
import '../src/styles/index.scss'
import { createApp, defineComponent, h } from 'vue'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// Isolated production page; the browser test intercepts every API call.
const app = createApp(defineComponent({ render: () => h(Page) }))
setupStore(app)
await setupI18n(app)
const { useUserStore } = await import('../src/store/modules/user')
useUserStore().permissions = new Set(['*:*:*'])
const { default: Page } = await import('../src/views/zsjos/class-management.vue')
const { setupGlobCom } = await import('../src/components')
setupGlobCom(app)
app.use(ElementPlus)
app.directive('hasPermi', () => {})
app.mount('#app')
