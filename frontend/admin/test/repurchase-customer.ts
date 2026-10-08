// UTF-8. Isolated transport fixture; never connects to a business database.
import { createApp,h,ref } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { service } from '../src/config/axios/service'
import { setupStore } from '../src/store'
import { setupI18n } from '../src/plugins/vueI18n'
const fixture={status:'EXISTING_CUSTOMER',fail:false,submitFail:false,delay:0,checks:[] as unknown[],orders:[] as unknown[]}
Object.assign(window,{repurchaseFixture:fixture})
service.defaults.adapter=async config=>{
 const path=config.url||'';let data:unknown=[]
 if(path.endsWith('/check-customer')){const payload=JSON.parse(String(config.data));fixture.checks.push(payload);const status=fixture.status;await new Promise(r=>setTimeout(r,fixture.delay));if(fixture.fail)throw new Error('模拟网络错误');data={matchStatus:status,canRepurchase:['EXISTING_CUSTOMER','NO_MATCH'].includes(status),reason:status==='EXISTING_CUSTOMER'?'已识别客户，本次订单归属当前录单人':status==='NO_MATCH'?'未找到系统客户，将创建客户主档':'身份冲突或客户暂不可复购',personId:status==='EXISTING_CUSTOMER'?10:undefined,customerName:payload.customerName,maskedMobile:'138****8000'}}
 else if(path.endsWith('/repurchase')){fixture.orders.push(JSON.parse(String(config.data)));if(fixture.submitFail)throw new Error('模拟提交失败');data=99}
 else if(path.endsWith('/voucher/upload'))data={infraFileId:100,url:'https://example.test/voucher.pdf',contentType:'application/pdf'}
 else if(path.includes('/area/'))data=[{id:990000000,name:'其他省份',selectionCode:'OTHER',children:[{id:990000001,name:'其他城市',selectionCode:'OTHER'}]}]
 else if(path.endsWith('/dict-data/simple-list'))data=['student_nature','service_period','student_source','fee_mode','payment_method'].map(type=>({dictType:'zsjos_order_'+type,value:'test',label:'测试选项',status:0}))
 else if(path.endsWith('/catalog'))data={spus:[{spuRef:'p1',spuName:'测试课程',attrs:[]}],skus:[{spuRef:'p1',skuRef:'s1',skuName:'测试方案',attrValues:{}}]}
 return {config,status:200,statusText:'OK',headers:{},request:{responseType:'json'},data:{code:0,data}}
}
const boot=createApp({});setupStore(boot)
const {default:Dialog}=await import('../src/views/zsjos/components/ExternalRepurchaseDialog.vue')
const app=createApp({setup(){const dialog=ref();return()=>h('main',{style:'padding:16px'},[h('button',{onClick:()=>dialog.value.open()},'打开复购'),h(Dialog,{ref:dialog})])}})
setupStore(app);await setupI18n(app);app.use(ElementPlus);app.mount('#app')
