// UTF-8. Synthetic read-only HTTP fixture for existing Admin consumers.
import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/lead/index.vue'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const lead = { id:1, leadNo:'TEST-STAGE', submittedName:'阶段测试客资', salesStage:'contacted', salesStageLabelSnapshot:'历史阶段名称', status:'valid', assignmentStatus:'owned', intendedProducts:[], attachments:[] }
service.defaults.adapter = async config => {
  const url=config.url || ''; let data:unknown=[]
  if(config.method!=='get') throw new Error('Fixture blocks writes')
  if(url.endsWith('/lead/page')) data={list:[lead],total:1}
  else if(url.endsWith('/lead/get')) data=lead
  else if(url.endsWith('/follow-ups/page')) data={list:[{id:1,operatorUserId:1,operatorName:'测试人员',occurredAt:1790042400000,method:'phone',methodLabel:'电话',result:'contact',resultLabel:'已联系',salesStageBefore:'pending_contact',salesStageBeforeLabelSnapshot:'历史待触达',salesStageAfter:'contacted',salesStageAfterLabelSnapshot:'历史阶段名称',images:[]}],total:1}
  else if(url.endsWith('/catalog')) data={fields:[{fieldKey:'lead.ownerDeptId',group:'归属与人员',label:'负责人所属组织（含下级）',valueType:'select',operators:['in'],options:[{value:'10',label:'测试中心 / 测试部门'}]}]}
  return {data:{code:0,data},status:200,statusText:'OK',headers:{},config,request:{responseType:'json'}}
}
const app=createApp({render:()=>h('main',{style:'padding:12px'},[h(Page)])})
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:Page}]})
setupStore(app);await setupI18n(app);app.use(ElementPlus);app.use(router);await router.push('/');await router.isReady()
app.component('ContentWrap',{render(){return h('div',this.$slots.default?.())}});app.directive('hasPermi',{});app.mount('#app')
