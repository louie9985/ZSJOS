import { createApp, defineComponent, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ClassPage from '../../../src/views/zsjos/class-management.vue'
import RegistrationPage from '../../../src/views/zsjos/registration-pool.vue'
import './fixture'
const app = createApp(defineComponent({ setup: () => () => h(new URLSearchParams(location.search).has('classes') ? ClassPage : RegistrationPage) }))
app.use(ElementPlus)
for (const name of ['ContentWrap','Pagination','Icon']) app.component(name,defineComponent({ setup: (_, { slots }) => () => h('section',slots.default?.()) }))
app.directive('hasPermi', { mounted: () => {} })
app.mount('#app')
