import {createApp,h} from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Panel from '../src/views/zsjos/components/StudentArchivePanel.vue'
createApp({render:()=>h(Panel,{relationId:2})}).use(ElementPlus).mount('#app')
