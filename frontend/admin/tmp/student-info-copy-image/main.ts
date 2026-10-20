import { createApp, defineComponent, ref, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import StudentInfoQrCard from '../../src/views/zsjos/components/StudentInfoQrCard.vue'
createApp(defineComponent({setup(){const error=ref('');return ()=>h('main',{style:'max-width:440px;margin:auto;padding:16px'},[h(StudentInfoQrCard,{url:'https://example.test/student-info-form#token='+'a'.repeat(43),onError:(value:string)=>error.value=value}),h('p',{role:'alert'},error.value)])}})).use(ElementPlus).mount('#app')
