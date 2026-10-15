import { ElMessage, ElMessageBox } from 'element-plus'
export const useUserStore=()=>({getPermissions:new Set(['*:*:*'])})
export const useMessage=()=>({success:ElMessage.success,error:ElMessage.error,warning:ElMessage.warning,confirm:ElMessageBox.confirm})
const rows=[1,2].map(id=>({id,orderId:99,orderNo:'TEST-99',orderItemId:100+id,productName:'履约产品'+id,studentName:'验收学员',status:'pending',statusLabel:'待处理',version:0,assignmentMode:'class_per_item',classAssignments:[{orderItemId:100+id,productName:'履约产品'+id,classId:20,className:'首次班级'}],items:[],routes:[],completable:false,orderStatus:'pending_approval',orderStatusLabel:'待财务审核',completionBlockReason:'财务审核通过后才能完成报名履约',deliveryEstablished:false,assignable:true,deliveries:[] as any[]}))
Object.assign(window,{registrationRows:rows,approveFinance:()=>rows.forEach(row=>Object.assign(row,{orderStatus:'effective',orderStatusLabel:'已生效',completable:true,completionBlockReason:''}))})
export default {
 get:async ({url}:any)=>url.endsWith('pool-page')?{list:structuredClone(rows),total:rows.length}:url.includes('options')?[{id:20,className:'首次班级',homeroomUserName:'测试规划师'}]:structuredClone(rows.find(r=>r.id===Number(url.split('/').at(-1)))),
 put:async({url}:any)=>{const row=rows.find(r=>r.id===Number(url.split('/').at(-2)))!;Object.assign(row,{deliveryEstablished:true,assignable:false,assignmentBlockReason:'已建立交付服务，变更班级请通过班级管理办理转班',deliveries:[{serviceRelationId:1000+row.id,orderItemId:row.orderItemId,status:'active',className:'当前交付班级',ownerUserName:'测试规划师',acceptanceStatus:'accepted'}]});return structuredClone(row)},
 post:async({url}:any)=>{const row=rows.find(r=>r.id===Number(url.split('/').at(-2)))!;row.status=url.endsWith('close')?'ended':'completed';row.completable=false;return true}
}
