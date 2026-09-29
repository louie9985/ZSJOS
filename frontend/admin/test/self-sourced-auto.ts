// UTF-8. Synthetic transport for the real Vue self-sourced dialog.
import { createApp,h,ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const query=new URLSearchParams(location.search)
const fixture={requests:[] as unknown[],outcome:query.get('outcome')||'created',fail:false}
Object.assign(window,{autoFixture:fixture})
service.defaults.adapter=async config=>{
 const path=config.url||'';let data:unknown=[]
 if(config.method==='post'){
  const payload=JSON.parse(String(config.data));fixture.requests.push(payload);if(fixture.fail)throw new Error('模拟提交失败')
  data={outcome:fixture.outcome,leadNo:'LD-UI-TEST',qualificationStatus:payload.newMediaProviderUserId?'pending':'valid',automaticQualificationApplied:!payload.newMediaProviderUserId}
 }else if(path.includes('/area/'))data=[{id:990000000,name:'其他省份',selectionCode:'OTHER',children:[{id:990000001,name:'其他城市',selectionCode:'OTHER'}]}]
 else if(path.endsWith('/dict-data/simple-list'))data=[{dictType:'zsjos_lead_category',value:'a',label:'测试分类',status:0},{dictType:'zsjos_lead_source_channel',value:'channel',label:'测试渠道',status:0}]
 else if(path.endsWith('/catalog'))data={spus:[{spuRef:'p1',spuName:'测试课程',attrs:[]}],skus:[{spuRef:'p1',skuRef:'s1',skuName:'测试方案',attrValues:{}}]}
 else if(path.endsWith('/new-media-providers'))data=[{id:2,nickname:'测试提供方'}]
 return {config,status:200,statusText:'OK',headers:{},request:{responseType:'json'},data:{code:0,data}}
}
const boot=createApp({});setupStore(boot)
const {default:Dialog}=await import('../src/views/zsjos/components/LeadCreateDialog.vue')
const app=createApp({setup(){const dialog=ref();return()=>h('main',{style:'padding:16px'},[h('button',{onClick:()=>dialog.value.open()},'打开录单'),h(Dialog,{ref:dialog,selfSourced:true,educationSelfSourced:query.has('education')})])}})
setupStore(app);await setupI18n(app);app.use(ElementPlus);app.mount('#app')
