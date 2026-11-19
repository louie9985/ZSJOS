import {createApp,h} from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../../src/views/zsjos/enrollment-agreement/index.vue'
import Editor from '../../src/components/Editor/src/Editor.vue'
import VueDOMPurifyHTML from 'vue-dompurify-html'
import {checkPermi} from './mock'
const app=createApp(Page)
app.component('Editor',Editor)
app.component('ContentWrap',{render(){return h('section',this.$slots.default?.())}})
app.directive('hasPermi',(el)=>{if(!checkPermi())el.remove()})
app.use(ElementPlus).use(VueDOMPurifyHTML).mount('#app')
