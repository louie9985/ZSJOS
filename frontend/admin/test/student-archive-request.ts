let revision=0,job='原职业'
export default {get:async({url}:{url:string})=>{
 if(url.endsWith('/area/tree'))return []
 if(url.endsWith('/edit'))return {revision,values:{job,education_level:'old'},options:{education_level:[{value:'new',label:'当前学历标签'}]},images:[]}
 return {personId:1,serviceRelationId:2,revision,canMaintain:!location.search.includes('read'),productName:'合成报名产品',history:revision?[{revision,operatorName:'测试维护人',modifiedAt:'2026-10-18',reason:'测试修正'}]:[],information:{status:'SUBMITTED',fields:[{key:'job',label:'从事职业',type:'text',required:true},{key:'education_level',label:'现学历',type:'dict',required:true}],values:{job,education_level:'历史学历标签'},images:[],canReadSensitive:false,canExport:false}}
},post:async({data}:{data:{revision:number;changes:Record<string,unknown>}})=>{if(data.revision!==revision)throw Error('版本冲突');job=String(data.changes.job);return ++revision}}
