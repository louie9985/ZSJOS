import { createApp, defineComponent, h, ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '../../../src/views/zsjos/components/TeachingServiceLifecyclePanel.vue'
import ClassPage from '../../../src/views/zsjos/class-management.vue'
import { state } from './fixture'
const app = createApp(defineComponent({ setup() {
  const version = ref(state.version)
  return () => h('section', { style: 'max-width:900px;margin:20px auto;padding:12px' }, [new URLSearchParams(location.search).has('classes') ? h(ClassPage) : h(Panel, { relationId: 1, version: version.value, onChanged: () => { version.value = state.version } })])
} }))
app.use(ElementPlus)
app.component('ContentWrap', defineComponent({ setup: (_, { slots }) => () => h('section', { style: 'padding:12px;min-width:0' }, slots.default?.()) }))
app.component('Pagination', defineComponent({ setup: () => () => h('div') }))
app.component('Icon', defineComponent({ setup: () => () => h('span') }))
app.directive('hasPermi', { mounted: () => {} })
app.mount('#app')
