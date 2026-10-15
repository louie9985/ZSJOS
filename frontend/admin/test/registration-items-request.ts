import { ElMessage, ElMessageBox } from 'element-plus'
export const useUserStore=()=>({getPermissions:new Set(['*:*:*'])})
export const useMessage=()=>({success:ElMessage.success,error:ElMessage.error,confirm:ElMessageBox.confirm})
const rows=[1,2].map(id=>({id,orderId:99,orderNo:'TEST-99',orderItemId:100+id,productName:'履约产品'+id,studentName:'验收学员',status:'pending',statusLabel:'待处理',version:0,assignmentMode:'class_per_item',classAssignments:[],items:[],routes:[],completable:true,orderStatus:'effective'}))
Object.assign(window,{registrationRows:rows})
export default {get:async ({url}:any)=>url.endsWith('pool-page')?{list:rows,total:rows.length}:url.includes('options')?[]:structuredClone(rows.find(r=>r.id===Number(url.split('/').at(-1)))),post:async({url}:any)=>{const r=rows.find(r=>r.id===Number(url.split('/').at(-2)))!;r.status=url.endsWith('close')?'ended':'completed';r.completable=false;return true}}
