import {createApp,h,ref} from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Collection from '../src/views/zsjos/components/RegistrationStudentInfo.vue'
import Config from '../src/views/zsjos/studentInfoFormConfig/index.vue'
import {fixture} from './registration-collection-request'
const app=createApp({setup(){const version=ref(0),sent=ref(false);return()=>h('main',{style:'padding:20px;max-width:900px;margin:auto'},location.search.includes('config')?[h(Config)]:[h(Collection,{caseId:1,version:version.value,status:'processing',sent:sent.value,onChanged:()=>{version.value=fixture.version;sent.value=fixture.sent}}),h('button',{disabled:!sent.value},'完成报名履约'),h('p','学员仍未提交')])}})
app.component('ContentWrap',{render(){return h('div',this.$slots.default?.())}})
app.directive('hasPermi',(el)=>{if(location.search.includes('denied'))el.remove()})
app.use(ElementPlus).mount('#app')
