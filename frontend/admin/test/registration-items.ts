import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/registration-pool.vue'
const app=createApp(Page)
app.component('ContentWrap',{render(){return h('div',this.$slots.default?.())}})
app.component('Pagination',{render(){return null}})
app.component('Icon',{render(){return null}})
app.use(ElementPlus).mount('#app')
