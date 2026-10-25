import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/studentEntry/index.vue'
const app = createApp(Page)
app.component('ContentWrap', { render() { return h('main', { style: 'padding:16px;max-width:900px;margin:auto' }, [h('h2', '学员录入'), this.$slots.default?.()]) } })
app.use(ElementPlus).mount('#app')
