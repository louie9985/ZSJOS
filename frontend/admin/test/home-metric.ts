import {createApp,h} from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/homeMetric/index.vue'
import {metricApi} from '../src/api/zsjos/homeMetric'
import request from '../src/config/axios'
const opts=(values:string[])=>values.map(value=>({value,label:({COUNT:'计数',DISTINCT_COUNT:'去重计数',SUM:'求和',ALL:'不限时间',DAY:'今日',CUSTOM:'自定义区间',CURRENT:'本期',PREVIOUS:'上一周期',MODAL:'弹窗明细',ROUTE:'跳转指标明细'} as Record<string,string>)[value]}))
const spec={code:'fixture',name:'隔离测试指标',description:'非生产数据',unit:'人',sort:0,viewPermission:'fixture:read',userIds:[],deptIds:[],action:'MODAL',formula:'A',percentage:false,decimals:0,operands:[{calculation:{name:'A',source:'student',aggregate:'COUNT',period:'CUSTOM',customRange:{start:Date.parse('2026-10-01T00:00:00+08:00'),end:Date.parse('2026-11-01T00:00:00+08:00')},toDate:false,comparison:'CURRENT'},filter:{logic:'AND',conditions:[],groups:[]}}]}
const scenario=new URLSearchParams(location.search).get('scenario')
if(scenario==='editor'){
 spec.formula='A + B';(spec as any).dictionarySnapshots={'A:person.category:test_kind:old':'历史分类'};
 spec.operands[0].filter.conditions=[{fieldKey:'person.category',operator:'in',value:['old']}] as any;
 spec.operands.push({calculation:{...spec.operands[0].calculation,name:'B',source:'order',customRange:{start:Date.parse('2026-11-01T00:00:00+08:00'),end:Date.parse('2026-12-01T00:00:00+08:00')}},filter:{logic:'AND',conditions:[],groups:[]}})
}
let rows=[{id:1,version:1,enabled:false,publishedVersion:0,spec}]
metricApi.list=async()=>structuredClone(rows)
metricApi.get=async()=>structuredClone(rows[0])
metricApi.catalog=async()=>{if(scenario==='init')await new Promise<void>(r=>(window as any).releaseInit=r);else await new Promise(r=>setTimeout(r,500));return [{key:'student',label:'学员',scene:'student',fields:[],timeFields:[{key:'person.createdAt',label:'创建时间',unit:''}],route:''},{key:'order',label:'订单',scene:'order',fields:[],timeFields:[{key:'order.createdAt',label:'创建时间',unit:''}],route:''}]}
metricApi.options=async()=>({aggregates:opts(['COUNT','DISTINCT_COUNT','SUM']),periods:opts(['ALL','DAY','CUSTOM']),comparisons:opts(['CURRENT','PREVIOUS']),actions:opts(['MODAL','ROUTE'])})
metricApi.permissions=async()=>[{permission:'fixture:read',label:'隔离读取权限'}]
metricApi.preview=async()=>({result:{status:'OK',value:'7'}})
metricApi.save=async(data)=>{if((window as any).failSave){(window as any).failSave=false;rows[0].version++};if(data.version!==rows[0].version)throw new Error("配置已被其他人修改，请重新打开");(window as any).lastSaved=structuredClone(data);rows[0]={...rows[0],spec:data.spec,version:rows[0].version+1};return rows[0]}
metricApi.command=async(command,data)=>{if(data.version!==rows[0].version)throw new Error('版本冲突');rows[0].enabled=command==='publish';rows[0].version++;return rows[0]}
metricApi.versions=async()=>[{version:1,spec}]
request.get=async({url,params}:any)=>{
 if(url.includes('dict-data'))return [{dictType:'test_kind',value:'old',label:'已改名分类'},{dictType:'test_kind',value:'new',label:'当前新分类'}]
 if(url.includes('catalog')){await new Promise(r=>setTimeout(r,params?.scene==='student'?600:200));return {fields:[params?.scene==='student'?{fieldKey:'person.category',group:'基本信息',label:'学员分类',valueType:'select',operators:['in'],optionSource:'dict:test_kind',options:[]}:{fieldKey:'order.studentName',group:'基本信息',label:'订单姓名',valueType:'text',operators:['contains'],options:[]}],relativeDateOptions:[]}}
 return []
}
const app=createApp(Page);app.use(ElementPlus);app.component('ContentWrap',{render(){return h('section',{style:'padding:16px'},this.$slots.default?.())}});app.directive('hasPermi',{});app.mount('#app')
