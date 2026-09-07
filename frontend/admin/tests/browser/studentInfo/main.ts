import { createApp, defineComponent, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../../../src/views/zsjos/studentInfoFormConfig/index.vue'
import { readonly } from './fixture'
const app = createApp(Page)
app.use(ElementPlus)
app.component('ContentWrap', defineComponent({ setup: (_, { slots }) => () => h('section', { style: 'padding:16px;min-width:0' }, slots.default?.()) }))
app.directive('hasPermi', { mounted: element => { if (readonly) element.remove() } })
app.mount('#app')
